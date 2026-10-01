package dev.mathalama.userservice.application.usecase;

import dev.mathalama.userservice.application.dto.request.UpdateProfileRequest;
import dev.mathalama.userservice.domain.exception.UserProfileNotFoundException;
import dev.mathalama.userservice.domain.model.UserProfile;
import dev.mathalama.userservice.domain.port.out.AvatarStoragePort;
import dev.mathalama.userservice.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileUseCaseTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private AvatarStoragePort avatarStoragePort;

    @InjectMocks
    private UserProfileUseCaseImpl userProfileUseCase;

    private UUID userId;
    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testProfile = UserProfile.createFromRegistration(userId, "john_doe", "john@example.com");
        testProfile.setBio("Software Engineer");
        testProfile.setAvatarUrl("https://s3.example.com/avatars/old.jpg");
    }

    @Test
    @DisplayName("createProfile: saves new profile when not already existing")
    void createProfile_whenNotExists_savesNewProfile() {
        when(userProfileRepository.existsById(userId)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile result = userProfileUseCase.createProfile(userId, "john_doe", "john@example.com");

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("john_doe", result.getUsername());
        assertEquals("john@example.com", result.getEmail());
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("createProfile: returns existing profile if already present")
    void createProfile_whenAlreadyExists_returnsExisting() {
        when(userProfileRepository.existsById(userId)).thenReturn(true);
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(testProfile));

        UserProfile result = userProfileUseCase.createProfile(userId, "john_doe", "john@example.com");

        assertNotNull(result);
        assertEquals(userId, result.getId());
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    @DisplayName("getProfile: returns profile when found")
    void getProfile_found() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(testProfile));

        Optional<UserProfile> result = userProfileUseCase.getProfile(userId);

        assertTrue(result.isPresent());
        assertEquals("john_doe", result.get().getUsername());
    }

    @Test
    @DisplayName("updateProfile: updates fields successfully")
    void updateProfile_success() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(testProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
                null,
                "New Bio",
                "+77011234567",
                LocalDate.of(1995, 5, 20),
                "ru_KZ",
                "Asia/Almaty"
        );

        UserProfile updated = userProfileUseCase.updateProfile(userId, request);

        assertEquals("New Bio", updated.getBio());
        assertEquals("+77011234567", updated.getPhoneNumber());
        assertEquals("Asia/Almaty", updated.getTimezone());
        verify(userProfileRepository).save(testProfile);
    }

    @Test
    @DisplayName("updateProfile: throws UserProfileNotFoundException when user does not exist")
    void updateProfile_notFound_throwsException() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest(
                null, "New Bio", null, null, null, null
        );

        assertThrows(UserProfileNotFoundException.class, () ->
                userProfileUseCase.updateProfile(userId, request));

        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("uploadAvatar: replaces old avatar in S3 and updates profile")
    void uploadAvatar_success() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(testProfile));
        when(avatarStoragePort.uploadAvatar(eq(userId), any())).thenReturn("https://s3.example.com/avatars/new.jpg");
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("avatar", "avatar.png", "image/png", "img_data".getBytes());

        UserProfile result = userProfileUseCase.uploadAvatar(userId, file);

        verify(avatarStoragePort).deleteAvatar("https://s3.example.com/avatars/old.jpg");
        verify(avatarStoragePort).uploadAvatar(eq(userId), eq(file));
        assertEquals("https://s3.example.com/avatars/new.jpg", result.getAvatarUrl());
        verify(userProfileRepository).save(testProfile);
    }

    @Test
    @DisplayName("deleteAvatar: deletes avatar from S3 and sets null on profile")
    void deleteAvatar_success() {
        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(testProfile));
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProfile result = userProfileUseCase.deleteAvatar(userId);

        verify(avatarStoragePort).deleteAvatar("https://s3.example.com/avatars/old.jpg");
        assertNull(result.getAvatarUrl());
        verify(userProfileRepository).save(testProfile);
    }
}
