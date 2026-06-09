package dev.mathalama.identityservice.application.dto.response;

import java.util.Set;
import java.util.UUID;

public record TokenValidationResponse(
    Boolean valid,
    UUID userId,
    String username,
    String email,
    Set<String> roles,
    String message
) {

    public static TokenValidationResponse success(UUID userId, String username, String email, Set<String> roles) {
        return new TokenValidationResponse(true, userId, username, email, roles, "Token is valid");
    }

    public static TokenValidationResponse failure(String message) {
        return new TokenValidationResponse(false, null, null, null, Set.of(), message);
    }
}

