package dev.mathalama.kycservice.domain.model;

import dev.mathalama.kycservice.domain.enums.DocumentType;
import dev.mathalama.kycservice.domain.enums.KycStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kyc_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_front_key", nullable = false, length = 500)
    private String documentFrontKey;

    @Column(name = "document_back_key", length = 500)
    private String documentBackKey;

    @Column(name = "selfie_key", nullable = false, length = 500)
    private String selfieKey;

    // ML Scores
    @Column(name = "liveness_score")
    private Double livenessScore;

    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Column(name = "mrz_valid")
    private Boolean mrzValid;

    // Extracted OCR Data
    @Column(name = "extracted_first_name", length = 100)
    private String extractedFirstName;

    @Column(name = "extracted_last_name", length = 100)
    private String extractedLastName;

    @Column(name = "extracted_document_number", length = 50)
    private String extractedDocumentNumber;

    @Column(name = "extracted_date_of_birth")
    private LocalDate extractedDateOfBirth;

    @Column(name = "extracted_expiry_date")
    private LocalDate extractedExpiryDate;

    @Column(name = "extracted_gender", length = 10)
    private String extractedGender;

    @Column(name = "extracted_nationality", length = 50)
    private String extractedNationality;

    @Column(name = "raw_ocr_data", columnDefinition = "TEXT")
    private String rawOcrData;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = KycStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
