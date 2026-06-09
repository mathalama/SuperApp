package dev.mathalama.identityservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TokenValidationRequest(
    @NotBlank(message = "Token is required")
    String token
) {
}

