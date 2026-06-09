package dev.mathalama.identityservice.application.dto.response;

import java.util.Set;
import java.util.UUID;

public record CurrentUserResponse(
    UUID id,
    String username,
    String email,
    Boolean emailVerified,
    String accountState,
    Set<String> roles,
    Long createdAt
) {
}

