package dev.mathalama.kycservice.domain.port.in;

import dev.mathalama.kycservice.application.dto.request.SubmitKycRequest;
import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;

import java.util.Optional;
import java.util.UUID;

public interface KycUseCase {
    KycApplicationResponse submitApplication(UUID userId, SubmitKycRequest request);

    Optional<KycApplicationResponse> getLatestApplicationByUserId(UUID userId);

    Optional<KycApplicationResponse> getApplicationById(UUID applicationId);
}
