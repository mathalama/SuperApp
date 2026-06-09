package dev.mathalama.identityservice.domain.port.in;

import dev.mathalama.identityservice.application.dto.response.AuthResponse;

public interface OAuthExchangeUseCase {
    String createExchangeCode(String userId);
    AuthResponse exchangeCodeForTokens(String code);
}
