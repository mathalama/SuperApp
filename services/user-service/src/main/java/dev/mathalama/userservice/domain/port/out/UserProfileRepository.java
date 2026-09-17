package dev.mathalama.userservice.domain.port.out;

import dev.mathalama.userservice.domain.model.UserProfile;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {

    UserProfile save(UserProfile profile);

    Optional<UserProfile> findById(UUID id);

    boolean existsById(UUID id);
}
