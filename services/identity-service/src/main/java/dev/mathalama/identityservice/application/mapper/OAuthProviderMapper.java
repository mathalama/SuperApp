package dev.mathalama.identityservice.application.mapper;

import dev.mathalama.identityservice.domain.model.OAuthProvider;
import dev.mathalama.identityservice.application.dto.response.OAuthProviderResponse;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class OAuthProviderMapper {
    private OAuthProviderMapper() {}

    public static OAuthProviderResponse toResponse(OAuthProvider provider) {
        return new OAuthProviderResponse(
            provider.getId(),
            provider.getProviderName(),
            provider.getProviderEmail(),
            provider.getCreatedAt() != null ? provider.getCreatedAt().toEpochMilli() : null,
            provider.getLastLoginAt() != null ? provider.getLastLoginAt().toEpochMilli() : null
        );
    }

    public static List<OAuthProviderResponse> toResponseList(List<OAuthProvider> providers) {
        return providers.stream().map(OAuthProviderMapper::toResponse).collect(Collectors.toList());
    }
}
