package dev.mathalama.userservice.infrastructure.storage;

import dev.mathalama.userservice.domain.port.out.AvatarStoragePort;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3AvatarStorageAdapter implements AvatarStoragePort {

    private final MinioClient s3Client;

    @Value("${s3.bucket.avatars}")
    private String bucketName;

    @Value("${s3.public-url}")
    private String publicUrl;

    private static final int MAX_DIMENSION = 4096; // Максимум 4K разрешение

    @Override
    public String uploadAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file cannot be empty");
        }

        // 1. Проверяем реальный тип файла по сигнатуре (Magic Bytes)
        String detectedFormat = detectAndValidateImageFormat(file);

        // 2. Проверяем валидность картинки и её разрешение (защита от Pixel Flood)
        validateImageDimensions(file);

        String extension = "." + detectedFormat;
        String contentType = "image/" + (detectedFormat.equals("jpg") ? "jpeg" : detectedFormat);
        String objectName = userId + "/avatar-" + System.currentTimeMillis() + extension;

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build());

            String avatarUrl = String.format("%s/%s/%s", publicUrl, bucketName, objectName);
            log.info("Uploaded verified avatar to S3: userId={}, format={}, url={}", userId, detectedFormat, avatarUrl);
            return avatarUrl;
        } catch (Exception e) {
            log.error("Failed to upload avatar to S3 for userId={}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload avatar", e);
        }
    }

    @Override
    public void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }

        try {
            String prefix = publicUrl + "/" + bucketName + "/";
            if (avatarUrl.startsWith(prefix)) {
                String objectName = avatarUrl.substring(prefix.length());
                s3Client.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build());
                log.info("Deleted avatar from S3: {}", objectName);
            }
        } catch (Exception e) {
            log.warn("Failed to delete avatar from S3 {}: {}", avatarUrl, e.getMessage());
        }
    }

    /**
     * Проверка первых байт файла (Magic Numbers)
     */
    private String detectAndValidateImageFormat(MultipartFile file) {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 12) {
                throw new IllegalArgumentException("Corrupted or invalid image file");
            }

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return "jpg";
            }

            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "png";
            }

            // WebP: RIFF .... WEBP
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "webp";
            }

            throw new IllegalArgumentException(
                    "Unsupported image format. Only real JPEG, PNG, and WebP files are allowed.");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read image header", e);
        }
    }

    /**
     * Защита от Decompression Bomb (Pixel Flood)
     */
    private void validateImageDimensions(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new IllegalArgumentException(
                        String.format("Image dimensions too large (%dx%d). Max allowed is %dx%d px.",
                                width, height, MAX_DIMENSION, MAX_DIMENSION));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Image dimension check skipped: {}", e.getMessage());
        }
    }
}
