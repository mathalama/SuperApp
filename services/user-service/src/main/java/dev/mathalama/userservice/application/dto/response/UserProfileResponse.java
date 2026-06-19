package dev.mathalama.userservice.application.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String avatarUrl,
        String bio,
        String phoneNumber,
        LocalDate dateOfBirth,
        String locale,
        String timezone,
        String profileStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
