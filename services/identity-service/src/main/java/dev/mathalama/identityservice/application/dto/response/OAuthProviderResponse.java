package dev.mathalama.identityservice.application.dto.response;

import java.util.UUID;

public record OAuthProviderResponse(
    UUID id,
    String providerName,
    String providerEmail,
    Long linkedAt,
    Long lastLoginAt
) {
}

