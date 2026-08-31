package dev.mathalama.kycservice.application.dto.response;

import dev.mathalama.kycservice.domain.enums.DocumentType;
import dev.mathalama.kycservice.domain.enums.KycStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycApplicationResponse {
    private UUID id;
    private UUID userId;
    private KycStatus status;
    private DocumentType documentType;

    private Double livenessScore;
    private Double faceMatchScore;
    private Boolean mrzValid;

    private String firstName;
    private String lastName;
    private String documentNumber;
    private LocalDate dateOfBirth;
    private LocalDate expiryDate;
    private String nationality;

    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
