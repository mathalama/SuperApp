package dev.mathalama.kycservice.domain.port.in;

import dev.mathalama.kycservice.application.dto.request.AdminReviewRequest;
import dev.mathalama.kycservice.application.dto.request.SubmitKycRequest;
import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;
import dev.mathalama.kycservice.domain.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface KycUseCase {
    KycApplicationResponse submitApplication(UUID userId, String userEmail, SubmitKycRequest request);

    Optional<KycApplicationResponse> getLatestApplicationByUserId(UUID userId);

    Optional<KycApplicationResponse> getApplicationById(UUID applicationId);

    Page<KycApplicationResponse> getAllApplications(KycStatus status, Pageable pageable);

    KycApplicationResponse reviewApplication(UUID applicationId, String adminUser, AdminReviewRequest request);
}
