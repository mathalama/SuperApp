package dev.mathalama.identityservice.application.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken
) {}

