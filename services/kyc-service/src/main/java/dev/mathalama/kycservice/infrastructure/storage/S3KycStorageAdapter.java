package dev.mathalama.kycservice.infrastructure.storage;

import dev.mathalama.kycservice.domain.port.out.KycStoragePort;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3KycStorageAdapter implements KycStoragePort {

    private final MinioClient minioClient;

    @Value("${s3.bucket.kyc}")
    private String kycBucket;

    @Override
    public String uploadDocument(UUID userId, MultipartFile file, String documentType) {
        String extension = getFileExtension(file.getOriginalFilename());
        String objectKey = String.format("kyc/%s/%s_%s%s", userId, documentType, UUID.randomUUID(), extension);

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(kycBucket)
                            .object(objectKey)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.info("Uploaded KYC file to S3: bucket={}, key={}", kycBucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to upload KYC file to S3: {}", objectKey, e);
            throw new RuntimeException("Failed to upload KYC file: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadDocument(String objectKey) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(kycBucket)
                        .object(objectKey)
                        .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download document from S3: {}", objectKey, e);
            throw new RuntimeException("Failed to download KYC document: " + e.getMessage(), e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
