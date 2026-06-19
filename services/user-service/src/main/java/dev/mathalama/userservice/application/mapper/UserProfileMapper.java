package dev.mathalama.userservice.application.mapper;

import dev.mathalama.userservice.application.dto.response.UserProfileResponse;
import dev.mathalama.userservice.domain.model.UserProfile;

public final class UserProfileMapper {

    private UserProfileMapper() {
    }

    public static UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getAvatarUrl(),
                profile.getBio(),
                profile.getPhoneNumber(),
                profile.getDateOfBirth(),
                profile.getLocale(),
                profile.getTimezone(),
                profile.getProfileStatus().name(),
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }
}
