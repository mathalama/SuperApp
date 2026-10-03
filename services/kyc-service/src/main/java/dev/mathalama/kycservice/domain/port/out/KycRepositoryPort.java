package dev.mathalama.kycservice.domain.port.out;

import dev.mathalama.kycservice.domain.enums.KycStatus;
import dev.mathalama.kycservice.domain.model.KycApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface KycRepositoryPort {
    KycApplication save(KycApplication application);

    Optional<KycApplication> findById(UUID id);

    Optional<KycApplication> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<KycApplication> findAll(Pageable pageable);

    Page<KycApplication> findByStatus(KycStatus status, Pageable pageable);
}
