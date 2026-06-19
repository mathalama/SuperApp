package dev.mathalama.userservice.presentation.controller;

import dev.mathalama.userservice.application.dto.request.UpdateProfileRequest;
import dev.mathalama.userservice.application.dto.response.UserProfileResponse;
import dev.mathalama.userservice.application.mapper.UserProfileMapper;
import dev.mathalama.userservice.domain.model.UserProfile;
import dev.mathalama.userservice.domain.port.in.UserProfileUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileUseCase userProfileUseCase;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @RequestHeader("X-User-Id") String userId) {

        return userProfileUseCase.getProfile(UUID.fromString(userId))
                .map(UserProfileMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfile updated = userProfileUseCase.updateProfile(UUID.fromString(userId), request);
        return ResponseEntity.ok(UserProfileMapper.toResponse(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfileById(@PathVariable UUID id) {
        return userProfileUseCase.getProfile(id)
                .map(UserProfileMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
