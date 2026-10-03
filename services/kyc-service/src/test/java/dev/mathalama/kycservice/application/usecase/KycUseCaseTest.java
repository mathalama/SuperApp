package dev.mathalama.kycservice.application.usecase;

import dev.mathalama.kycservice.application.dto.request.AdminReviewRequest;
import dev.mathalama.kycservice.application.dto.request.SubmitKycRequest;
import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;
import dev.mathalama.kycservice.domain.enums.DocumentType;
import dev.mathalama.kycservice.domain.enums.KycStatus;
import dev.mathalama.kycservice.domain.exception.DuplicateKycApplicationException;
import dev.mathalama.kycservice.domain.model.KycApplication;
import dev.mathalama.kycservice.domain.port.out.AmlScreeningPort;
import dev.mathalama.kycservice.domain.port.out.KycEventPublisherPort;
import dev.mathalama.kycservice.domain.port.out.KycInferencePort;
import dev.mathalama.kycservice.domain.port.out.KycRepositoryPort;
import dev.mathalama.kycservice.domain.port.out.KycStoragePort;
import dev.mathalama.kycservice.infrastructure.client.dto.MlExtractedData;
import dev.mathalama.kycservice.infrastructure.client.dto.MlInferenceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycUseCaseTest {

    @Mock
    private KycRepositoryPort repositoryPort;

    @Mock
    private KycStoragePort storagePort;

    @Mock
    private KycInferencePort inferencePort;

    @Mock
    private KycEventPublisherPort eventPublisherPort;

    @Mock
    private AmlScreeningPort amlScreeningPort;

    @InjectMocks
    private KycUseCaseImpl kycUseCase;

    private UUID userId;
    private SubmitKycRequest submitRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        ReflectionTestUtils.setField(kycUseCase, "livenessThreshold", 0.5);
        ReflectionTestUtils.setField(kycUseCase, "similarityThreshold", 0.65);

        MockMultipartFile front = new MockMultipartFile("documentFront", "front.jpg", "image/jpeg", "front".getBytes());
        MockMultipartFile selfie = new MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", "selfie".getBytes());

        submitRequest = SubmitKycRequest.builder()
                .documentType(DocumentType.PASSPORT)
                .documentFront(front)
                .selfie(selfie)
                .build();
    }

    @Test
    @DisplayName("submitApplication: successful submission and auto-verification on high scores")
    void submitApplication_success_verified() {
        when(repositoryPort.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(storagePort.uploadDocument(eq(userId), any(), eq("front"))).thenReturn("front-key");
        when(storagePort.uploadDocument(eq(userId), any(), eq("selfie"))).thenReturn("selfie-key");

        when(repositoryPort.save(any(KycApplication.class))).thenAnswer(inv -> {
            KycApplication app = inv.getArgument(0);
            if (app.getId() == null) {
                app.setId(UUID.randomUUID());
            }
            return app;
        });

        MlExtractedData extractedData = MlExtractedData.builder()
                .firstName("John")
                .lastName("Doe")
                .documentNumber("N12345678")
                .nationality("KAZ")
                .build();

        MlInferenceResponse mlResponse = MlInferenceResponse.builder()
                .isLive(true)
                .isMatch(true)
                .faceDetectedInDoc(true)
                .faceDetectedInSelfie(true)
                .headPoseValid(true)
                .livenessScore(0.95)
                .similarityScore(0.88)
                .mrzValid(true)
                .mrzStatus("VALID")
                .expiryStatus("VALID")
                .extractedData(extractedData)
                .build();

        when(inferencePort.processKyc(any(), any(), any())).thenReturn(mlResponse);
        when(amlScreeningPort.screen(any(), any(), any(), any())).thenReturn(
                AmlScreeningPort.AmlCheckResult.builder().flagged(false).riskLevel("LOW").build()
        );

        KycApplicationResponse response = kycUseCase.submitApplication(userId, "john@example.com", submitRequest);

        assertNotNull(response);
        assertEquals(KycStatus.VERIFIED, response.getStatus());
        assertEquals("John", response.getFirstName());
        assertEquals("Doe", response.getLastName());
        verify(eventPublisherPort, atLeastOnce()).publishKycStatusChanged(eq(userId), any(), eq("john@example.com"), eq(KycStatus.VERIFIED), any());
    }

    @Test
    @DisplayName("submitApplication: routes to MANUAL_REVIEW when AML sanctions watchword is detected")
    void submitApplication_amlSanctionsFlagged_routesToManualReview() {
        when(repositoryPort.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(storagePort.uploadDocument(eq(userId), any(), eq("front"))).thenReturn("front-key");
        when(storagePort.uploadDocument(eq(userId), any(), eq("selfie"))).thenReturn("selfie-key");

        when(repositoryPort.save(any(KycApplication.class))).thenAnswer(inv -> {
            KycApplication app = inv.getArgument(0);
            if (app.getId() == null) {
                app.setId(UUID.randomUUID());
            }
            return app;
        });

        MlInferenceResponse mlResponse = MlInferenceResponse.builder()
                .isLive(true)
                .isMatch(true)
                .faceDetectedInDoc(true)
                .faceDetectedInSelfie(true)
                .livenessScore(0.95)
                .similarityScore(0.88)
                .mrzValid(true)
                .mrzStatus("VALID")
                .expiryStatus("VALID")
                .extractedData(MlExtractedData.builder().firstName("Test").lastName("Sanctioned").build())
                .build();

        when(inferencePort.processKyc(any(), any(), any())).thenReturn(mlResponse);
        when(amlScreeningPort.screen(any(), any(), any(), any())).thenReturn(
                AmlScreeningPort.AmlCheckResult.builder()
                        .flagged(true)
                        .riskLevel("HIGH")
                        .matchDetails("Match against international sanctions watchlist")
                        .build()
        );

        KycApplicationResponse response = kycUseCase.submitApplication(userId, "sanctioned@example.com", submitRequest);

        assertNotNull(response);
        assertEquals(KycStatus.MANUAL_REVIEW, response.getStatus());
        assertTrue(response.getRejectionReason().contains("AML/Sanctions Alert"));
        verify(eventPublisherPort, atLeastOnce()).publishKycStatusChanged(eq(userId), any(), eq("sanctioned@example.com"), eq(KycStatus.MANUAL_REVIEW), any());
    }

    @Test
    @DisplayName("submitApplication: throws DuplicateKycApplicationException if user is already VERIFIED")
    void submitApplication_alreadyVerified_throwsDuplicateException() {
        KycApplication existing = KycApplication.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(KycStatus.VERIFIED)
                .build();

        when(repositoryPort.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(existing));

        assertThrows(DuplicateKycApplicationException.class, () ->
                kycUseCase.submitApplication(userId, "john@example.com", submitRequest));

        verify(storagePort, never()).uploadDocument(any(), any(), any());
        verify(inferencePort, never()).processKyc(any(), any(), any());
    }

    @Test
    @DisplayName("submitApplication: throws DuplicateKycApplicationException if user has IN_PROGRESS application")
    void submitApplication_alreadyInProgress_throwsDuplicateException() {
        KycApplication existing = KycApplication.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status(KycStatus.IN_PROGRESS)
                .build();

        when(repositoryPort.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(existing));

        assertThrows(DuplicateKycApplicationException.class, () ->
                kycUseCase.submitApplication(userId, "john@example.com", submitRequest));

        verify(storagePort, never()).uploadDocument(any(), any(), any());
    }

    @Test
    @DisplayName("submitApplication: routes to MANUAL_REVIEW when ML inference service fails")
    void submitApplication_mlFailure_routesToManualReview() {
        when(repositoryPort.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(storagePort.uploadDocument(eq(userId), any(), eq("front"))).thenReturn("front-key");
        when(storagePort.uploadDocument(eq(userId), any(), eq("selfie"))).thenReturn("selfie-key");

        when(repositoryPort.save(any(KycApplication.class))).thenAnswer(inv -> {
            KycApplication app = inv.getArgument(0);
            if (app.getId() == null) {
                app.setId(UUID.randomUUID());
            }
            return app;
        });

        when(inferencePort.processKyc(any(), any(), any()))
                .thenThrow(new RuntimeException("ML service timeout"));

        KycApplicationResponse response = kycUseCase.submitApplication(userId, "john@example.com", submitRequest);

        assertNotNull(response);
        assertEquals(KycStatus.MANUAL_REVIEW, response.getStatus());
        assertTrue(response.getRejectionReason().contains("ML verification service temporarily unavailable"));
        verify(eventPublisherPort, atLeastOnce()).publishKycStatusChanged(eq(userId), any(), eq("john@example.com"), eq(KycStatus.MANUAL_REVIEW), any());
    }

    @Test
    @DisplayName("reviewApplication: admin approves manual review application")
    void reviewApplication_adminApproves() {
        UUID appId = UUID.randomUUID();
        KycApplication app = KycApplication.builder()
                .id(appId)
                .userId(userId)
                .userEmail("john@example.com")
                .status(KycStatus.MANUAL_REVIEW)
                .rejectionReason("Borderline match")
                .build();

        when(repositoryPort.findById(appId)).thenReturn(Optional.of(app));
        when(repositoryPort.save(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminReviewRequest request = AdminReviewRequest.builder()
                .decision(KycStatus.VERIFIED)
                .notes("Reviewed manually by compliance officer, ID confirmed valid.")
                .build();

        KycApplicationResponse response = kycUseCase.reviewApplication(appId, "compliance_admin", request);

        assertNotNull(response);
        assertEquals(KycStatus.VERIFIED, response.getStatus());
        assertEquals("compliance_admin", response.getReviewedBy());
        assertNull(response.getRejectionReason());
        verify(eventPublisherPort, times(1)).publishKycStatusChanged(eq(userId), eq(appId), eq("john@example.com"), eq(KycStatus.VERIFIED), isNull());
    }
}
