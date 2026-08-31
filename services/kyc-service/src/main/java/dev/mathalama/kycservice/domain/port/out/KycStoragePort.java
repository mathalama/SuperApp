package dev.mathalama.kycservice.domain.port.out;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

public interface KycStoragePort {
    String uploadDocument(UUID userId, MultipartFile file, String documentType);

    byte[] downloadDocument(String objectKey);
}
