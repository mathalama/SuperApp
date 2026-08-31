package dev.mathalama.kycservice.infrastructure.persistence.adapter;

import dev.mathalama.kycservice.domain.model.KycApplication;
import dev.mathalama.kycservice.domain.port.out.KycRepositoryPort;
import dev.mathalama.kycservice.infrastructure.persistence.jpa.JpaKycRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KycRepositoryAdapter implements KycRepositoryPort {

    private final JpaKycRepository jpaRepository;

    @Override
    public KycApplication save(KycApplication application) {
        return jpaRepository.save(application);
    }

    @Override
    public Optional<KycApplication> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<KycApplication> findTopByUserIdOrderByCreatedAtDesc(UUID userId) {
        return jpaRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }
}
