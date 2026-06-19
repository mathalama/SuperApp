package dev.mathalama.userservice.application.dto.event;

public record UserRegisteredEvent(
        String eventId,
        String userId,
        String username,
        String email,
        String authProvider,
        long timestamp
) {
}
