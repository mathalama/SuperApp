package dev.mathalama.userservice.infrastructure.persistence.jpa;

import dev.mathalama.userservice.domain.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaUserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
