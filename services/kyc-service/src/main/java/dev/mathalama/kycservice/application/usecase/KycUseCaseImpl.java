package dev.mathalama.kycservice.application.usecase;

import dev.mathalama.kycservice.application.dto.request.SubmitKycRequest;
import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;
import dev.mathalama.kycservice.application.mapper.KycMapper;
import dev.mathalama.kycservice.domain.enums.KycStatus;
import dev.mathalama.kycservice.domain.model.KycApplication;
import dev.mathalama.kycservice.domain.port.in.KycUseCase;
import dev.mathalama.kycservice.domain.port.out.KycEventPublisherPort;
import dev.mathalama.kycservice.domain.port.out.KycInferencePort;
import dev.mathalama.kycservice.domain.port.out.KycRepositoryPort;
import dev.mathalama.kycservice.domain.port.out.KycStoragePort;
import dev.mathalama.kycservice.infrastructure.client.dto.MlInferenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycUseCaseImpl implements KycUseCase {

    private final KycRepositoryPort repositoryPort;
    private final KycStoragePort storagePort;
    private final KycInferencePort inferencePort;
    private final KycEventPublisherPort eventPublisherPort;

    @Value("${ml-service.thresholds.liveness:0.85}")
    private double livenessThreshold;

    @Value("${ml-service.thresholds.similarity:0.65}")
    private double similarityThreshold;

    @Override
    @Transactional
    public KycApplicationResponse submitApplication(UUID userId, SubmitKycRequest request) {
        log.info("Processing KYC submission for userId: {}", userId);

        // 1. Сохраняем исходники в приватный S3 MinIO
        String frontKey = storagePort.uploadDocument(userId, request.getDocumentFront(), "front");
        String backKey = request.getDocumentBack() != null
                ? storagePort.uploadDocument(userId, request.getDocumentBack(), "back")
                : null;
        String selfieKey = storagePort.uploadDocument(userId, request.getSelfie(), "selfie");

        // 2. Создаем черновик заявки в статусе IN_PROGRESS
        KycApplication application = KycApplication.builder()
                .userId(userId)
                .documentType(request.getDocumentType())
                .status(KycStatus.IN_PROGRESS)
                .documentFrontKey(frontKey)
                .documentBackKey(backKey)
                .selfieKey(selfieKey)
                .build();

        application = repositoryPort.save(application);
        eventPublisherPort.publishKycStatusChanged(userId, application.getId(), KycStatus.IN_PROGRESS, null);

        // 3. Вызываем ML-инференс сервис (OCR + Liveness + Face Match)
        try {
            byte[] frontBytes = request.getDocumentFront().getBytes();
            byte[] backBytes = request.getDocumentBack() != null ? request.getDocumentBack().getBytes() : null;
            byte[] selfieBytes = request.getSelfie().getBytes();

            MlInferenceResponse mlResult = inferencePort.processKyc(frontBytes, selfieBytes, backBytes);
            enrichAndEvaluateApplication(application, mlResult);

        } catch (IOException e) {
            log.error("Failed to read uploaded files for userId: {}", userId, e);
            application.setStatus(KycStatus.REJECTED);
            application.setRejectionReason("Failed to process image files.");
        } catch (Exception e) {
            log.error("ML Inference error for userId: {}", userId, e);
            // При сбое ML отправляем на ручную проверку, чтобы заявка не потерялась
            application.setStatus(KycStatus.MANUAL_REVIEW);
            application.setRejectionReason("ML verification service temporarily unavailable: " + e.getMessage());
        }

        application = repositoryPort.save(application);
        eventPublisherPort.publishKycStatusChanged(userId, application.getId(), application.getStatus(),
                application.getRejectionReason());

        return KycMapper.toResponse(application);
    }

    private void enrichAndEvaluateApplication(KycApplication app, MlInferenceResponse ml) {
        app.setLivenessScore(ml.getLivenessScore());
        app.setFaceMatchScore(ml.getSimilarityScore());
        app.setMrzValid(ml.getMrzValid());
        app.setRawOcrData(ml.getRawOcrJson());

        if (ml.getExtractedData() != null) {
            app.setExtractedFirstName(ml.getExtractedData().getFirstName());
            app.setExtractedLastName(ml.getExtractedData().getLastName());
            app.setExtractedDocumentNumber(ml.getExtractedData().getDocumentNumber());
            app.setExtractedDateOfBirth(ml.getExtractedData().getDateOfBirth());
            app.setExtractedExpiryDate(ml.getExtractedData().getExpiryDate());
            app.setExtractedGender(ml.getExtractedData().getGender());
            app.setExtractedNationality(ml.getExtractedData().getNationality());
        }

        // Многоуровневый Decision Engine

        // 1. Проверка системных кодов качества изображений (освещение, разрешение, мульти-лица)
        String err = ml.getErrorCode();
        if ("POOR_LIGHTING".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Lighting is too dark. Please take photo in a well-lit area.");
            return;
        }
        if ("LOW_RESOLUTION".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Image or face resolution is too low. Please upload a clear photo (min 200x200px, face 60x60px).");
            return;
        }
        if ("MULTIPLE_FACES_DETECTED".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Multiple faces detected in selfie. Please ensure only one person is in the frame.");
            return;
        }
        if ("MULTIPLE_FACES_IN_DOCUMENT".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Multiple faces detected in document image. Potential document collage or fraud attempt.");
            return;
        }

        // 2. Проверка обнаружения лица в документе
        if (Boolean.FALSE.equals(ml.getFaceDetectedInDoc()) || "NO_FACE_IN_DOCUMENT".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("No face detected in document photo. Please upload a clear photo of your ID.");
            return;
        }

        // 3. Проверка обнаружения лица в селфи
        if (Boolean.FALSE.equals(ml.getFaceDetectedInSelfie()) || "NO_FACE_IN_SELFIE".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("No face detected in selfie. Please ensure your face is clearly visible.");
            return;
        }

        // 4. Проверка фронтального ракурса (Head Pose)
        if (Boolean.FALSE.equals(ml.getHeadPoseValid()) || "HEAD_POSE_ROTATED".equals(err)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Face turned sideways. Please look straight into the camera.");
            return;
        }

        // 5. Проверка срока действия документа (Document Expiry Validation)
        String expStatus = ml.getExpiryStatus() != null ? ml.getExpiryStatus() : "UNKNOWN";
        java.time.LocalDate expDate = ml.getExtractedData() != null ? ml.getExtractedData().getExpiryDate() : null;

        if ("EXPIRED".equals(expStatus)) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Document has expired" + (expDate != null ? " on " + expDate : "") + ". Expired identity documents are not accepted.");
            return;
        }

        // 6. Проверка Anti-spoofing (Liveness)
        boolean isLive = ml.getLivenessScore() != null && ml.getLivenessScore() >= livenessThreshold;
        if (!isLive) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Liveness check failed (Spoofing detected).");
            return;
        }

        // 7. Проверка сходства лиц (ArcFace), Grace Period и MRZ статуса
        double similarity = ml.getSimilarityScore() != null ? ml.getSimilarityScore() : 0.0;
        String mrzStatus = ml.getMrzStatus() != null ? ml.getMrzStatus() : (Boolean.TRUE.equals(ml.getMrzValid()) ? "VALID" : "ABSENT");

        if (similarity < 0.55) {
            app.setStatus(KycStatus.REJECTED);
            app.setRejectionReason("Face on selfie does not match document photo.");
        } else if ("EXPIRING_SOON".equals(expStatus)) {
            app.setStatus(KycStatus.MANUAL_REVIEW);
            app.setRejectionReason("Document expires in less than 30 days" + (expDate != null ? " (" + expDate + ")" : "") + ". Sent for manual operator review.");
        } else if ("UNKNOWN".equals(expStatus)) {
            app.setStatus(KycStatus.MANUAL_REVIEW);
            app.setRejectionReason("Document expiry date could not be verified automatically. Sent for manual operator review.");
        } else if (similarity >= similarityThreshold && "VALID".equals(mrzStatus) && "VALID".equals(expStatus)) {
            app.setStatus(KycStatus.VERIFIED);
            app.setRejectionReason(null);
        } else if (similarity >= 0.55 && similarity < similarityThreshold) {
            app.setStatus(KycStatus.MANUAL_REVIEW);
            app.setRejectionReason("Borderline face match score (0.55 <= similarity < 0.70). Sent for manual review.");
        } else if ("CHECKSUM_FAILED".equals(mrzStatus)) {
            app.setStatus(KycStatus.MANUAL_REVIEW);
            app.setRejectionReason("MRZ detected but checksum verification failed (possible glare/damage). Sent for manual review.");
        } else if ("ABSENT".equals(mrzStatus)) {
            app.setStatus(KycStatus.MANUAL_REVIEW);
            app.setRejectionReason("Document has no MRZ zone (e.g. national ID card). Sent for manual review.");
        } else {
            app.setStatus(KycStatus.MANUAL_REVIEW);
            app.setRejectionReason("Verification sent for manual review.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KycApplicationResponse> getLatestApplicationByUserId(UUID userId) {
        return repositoryPort.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(KycMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KycApplicationResponse> getApplicationById(UUID applicationId) {
        return repositoryPort.findById(applicationId)
                .map(KycMapper::toResponse);
    }
}
