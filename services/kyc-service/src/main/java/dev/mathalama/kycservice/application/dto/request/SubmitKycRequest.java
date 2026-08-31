package dev.mathalama.kycservice.application.dto.request;

import dev.mathalama.kycservice.domain.enums.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitKycRequest {

    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @NotNull(message = "Document front photo is required")
    private MultipartFile documentFront;

    private MultipartFile documentBack;

    @NotNull(message = "Selfie photo is required")
    private MultipartFile selfie;
}
