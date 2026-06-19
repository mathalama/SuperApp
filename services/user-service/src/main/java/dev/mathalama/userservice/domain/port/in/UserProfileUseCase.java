package dev.mathalama.userservice.domain.port.in;

import dev.mathalama.userservice.application.dto.request.UpdateProfileRequest;
import dev.mathalama.userservice.domain.model.UserProfile;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileUseCase {

    UserProfile createProfile(UUID userId, String username, String email);

    Optional<UserProfile> getProfile(UUID userId);

    UserProfile updateProfile(UUID userId, UpdateProfileRequest request);
}
