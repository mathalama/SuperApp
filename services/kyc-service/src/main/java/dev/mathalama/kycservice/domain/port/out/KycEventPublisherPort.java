package dev.mathalama.kycservice.domain.port.out;

import dev.mathalama.kycservice.domain.enums.KycStatus;
import java.util.UUID;

public interface KycEventPublisherPort {
    void publishKycStatusChanged(UUID userId, UUID applicationId, String email, KycStatus status, String reason);
}
