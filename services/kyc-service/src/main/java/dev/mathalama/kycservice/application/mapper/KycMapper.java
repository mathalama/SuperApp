package dev.mathalama.kycservice.application.mapper;

import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;
import dev.mathalama.kycservice.domain.model.KycApplication;

public final class KycMapper {

    private KycMapper() {
    }

    public static KycApplicationResponse toResponse(KycApplication app) {
        if (app == null) {
            return null;
        }
        return KycApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUserId())
                .status(app.getStatus())
                .documentType(app.getDocumentType())
                .livenessScore(app.getLivenessScore())
                .faceMatchScore(app.getFaceMatchScore())
                .mrzValid(app.getMrzValid())
                .firstName(app.getExtractedFirstName())
                .lastName(app.getExtractedLastName())
                .documentNumber(app.getExtractedDocumentNumber())
                .dateOfBirth(app.getExtractedDateOfBirth())
                .expiryDate(app.getExtractedExpiryDate())
                .nationality(app.getExtractedNationality())
                .rejectionReason(app.getRejectionReason())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
