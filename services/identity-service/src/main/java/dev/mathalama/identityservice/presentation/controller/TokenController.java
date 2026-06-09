package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.request.RefreshTokenRequest;
import dev.mathalama.identityservice.application.dto.request.TokenValidationRequest;
import dev.mathalama.identityservice.application.dto.response.AuthResponse;
import dev.mathalama.identityservice.application.dto.response.TokenValidationResponse;
import dev.mathalama.identityservice.domain.port.in.TokenUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TokenController {

    private final TokenUseCase tokenUseCase;

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(tokenUseCase.refreshToken(request.refreshToken()));
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(@Valid @RequestBody TokenValidationRequest request) {
        return ResponseEntity.ok(tokenUseCase.validateToken(request.token()));
    }
}
