package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.request.ResendVerificationRequest;
import dev.mathalama.identityservice.application.dto.request.VerifyEmailRequest;
import dev.mathalama.identityservice.application.dto.response.VerificationResponse;
import dev.mathalama.identityservice.domain.port.in.VerificationUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationUseCase verificationUseCase;

    @PostMapping("/verify-email")
    public ResponseEntity<VerificationResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {


        VerificationResponse response = verificationUseCase.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<VerificationResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        VerificationResponse response = verificationUseCase.resendVerificationEmail(request.email());
        return ResponseEntity.ok(response);
    }
}
