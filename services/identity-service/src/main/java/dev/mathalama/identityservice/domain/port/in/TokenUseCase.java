package dev.mathalama.identityservice.domain.port.in;

import dev.mathalama.identityservice.application.dto.response.AuthResponse;
import dev.mathalama.identityservice.application.dto.response.TokenValidationResponse;

public interface TokenUseCase {
    AuthResponse refreshToken(String refreshToken);
    TokenValidationResponse validateToken(String token);
}
