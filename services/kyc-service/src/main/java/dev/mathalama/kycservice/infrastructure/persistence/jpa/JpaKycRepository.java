package dev.mathalama.kycservice.infrastructure.persistence.jpa;

import dev.mathalama.kycservice.domain.model.KycApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaKycRepository extends JpaRepository<KycApplication, UUID> {
    Optional<KycApplication> findTopByUserIdOrderByCreatedAtDesc(UUID userId);
}
