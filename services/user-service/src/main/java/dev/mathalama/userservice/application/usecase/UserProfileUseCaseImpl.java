package dev.mathalama.userservice.application.usecase;

import dev.mathalama.userservice.application.dto.request.UpdateProfileRequest;
import dev.mathalama.userservice.domain.model.UserProfile;
import dev.mathalama.userservice.domain.port.in.UserProfileUseCase;
import dev.mathalama.userservice.domain.port.out.AvatarStoragePort;
import dev.mathalama.userservice.domain.port.out.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileUseCaseImpl implements UserProfileUseCase {

    private final UserProfileRepository userProfileRepository;
    private final AvatarStoragePort avatarStoragePort;

    @Override
    @Transactional
    public UserProfile createProfile(UUID userId, String username, String email) {
        if (userProfileRepository.existsById(userId)) {
            log.warn("Profile already exists for userId={}", userId);
            return userProfileRepository.findById(userId).orElseThrow();
        }

        UserProfile profile = UserProfile.createFromRegistration(userId, username, email);
        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created profile for userId={}", userId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> getProfile(UUID userId) {
        return userProfileRepository.findById(userId);
    }

    @Override
    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for userId=" + userId));

        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl());
        }
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        if (request.phoneNumber() != null) {
            profile.setPhoneNumber(request.phoneNumber());
        }
        if (request.dateOfBirth() != null) {
            profile.setDateOfBirth(request.dateOfBirth());
        }
        if (request.locale() != null) {
            profile.setLocale(request.locale());
        }
        if (request.timezone() != null) {
            profile.setTimezone(request.timezone());
        }

        return userProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public UserProfile uploadAvatar(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for userId=" + userId));

        if (profile.getAvatarUrl() != null) {
            avatarStoragePort.deleteAvatar(profile.getAvatarUrl());
        }

        String newAvatarUrl = avatarStoragePort.uploadAvatar(userId, file);
        profile.setAvatarUrl(newAvatarUrl);
        return userProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public UserProfile deleteAvatar(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Profile not found for userId=" + userId));

        if (profile.getAvatarUrl() != null) {
            avatarStoragePort.deleteAvatar(profile.getAvatarUrl());
            profile.setAvatarUrl(null);
            return userProfileRepository.save(profile);
        }
        return profile;
    }
}
