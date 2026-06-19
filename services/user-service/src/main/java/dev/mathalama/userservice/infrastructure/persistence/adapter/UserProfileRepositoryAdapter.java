package dev.mathalama.userservice.infrastructure.persistence.adapter;

import dev.mathalama.userservice.domain.model.UserProfile;
import dev.mathalama.userservice.domain.port.out.UserProfileRepository;
import dev.mathalama.userservice.infrastructure.persistence.jpa.JpaUserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryAdapter implements UserProfileRepository {

    private final JpaUserProfileRepository jpaRepository;

    @Override
    public UserProfile save(UserProfile profile) {
        return jpaRepository.save(profile);
    }

    @Override
    public Optional<UserProfile> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }
}
