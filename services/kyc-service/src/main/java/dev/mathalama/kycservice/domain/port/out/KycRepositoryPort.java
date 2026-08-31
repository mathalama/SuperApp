package dev.mathalama.kycservice.domain.port.out;

import dev.mathalama.kycservice.domain.model.KycApplication;

import java.util.Optional;
import java.util.UUID;

public interface KycRepositoryPort {
    KycApplication save(KycApplication application);

    Optional<KycApplication> findById(UUID id);

    Optional<KycApplication> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
}
