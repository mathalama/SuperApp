package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.request.ForgotPasswordRequest;
import dev.mathalama.identityservice.application.dto.request.ResetPasswordRequest;
import dev.mathalama.identityservice.application.dto.request.ChangePasswordRequest;
import dev.mathalama.identityservice.application.dto.response.MessageResponse;
import dev.mathalama.identityservice.domain.port.in.PasswordUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordUseCase passwordUseCase;

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordUseCase.changePassword(
                request.username(),
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
