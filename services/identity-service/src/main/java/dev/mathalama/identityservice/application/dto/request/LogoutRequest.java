package dev.mathalama.identityservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token is required to logout")
        String refreshToken
) {}
