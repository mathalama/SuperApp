package dev.mathalama.identityservice.application.dto.event;

import java.util.UUID;

public record UserRegisteredEvent (
        String eventId,
        String userId,
        String username,
        String email,
        String authProvider,
        long timestamp
) {
    public static UserRegisteredEvent create (String userId, String username, String email, String authProvider) {
        return new UserRegisteredEvent(
                UUID.randomUUID().toString(),
                userId,
                username,
                email,
                authProvider,
                System.currentTimeMillis()
        );
    }
}
