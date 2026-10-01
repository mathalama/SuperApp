package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.request.ForgotPasswordRequest;
import dev.mathalama.identityservice.application.dto.request.ResetPasswordRequest;
import dev.mathalama.identityservice.application.dto.request.ChangePasswordRequest;
import dev.mathalama.identityservice.application.dto.response.MessageResponse;
import dev.mathalama.identityservice.domain.port.in.PasswordUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordUseCase passwordUseCase;

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User principal,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody ChangePasswordRequest request) {

        UUID userId = null;
        if (principal != null) {
            userId = UUID.fromString(principal.getUsername());
        } else if (headerUserId != null && !headerUserId.isBlank()) {
            userId = UUID.fromString(headerUserId);
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User authUser) {
                userId = UUID.fromString(authUser.getUsername());
            }
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        passwordUseCase.changePassword(
                userId,
                request.oldPassword(),
                request.newPassword()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordUseCase.forgotPassword(request.email());
        return ResponseEntity.ok(new MessageResponse("If the email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordUseCase.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully. Please log in with your new password."));
    }
}
