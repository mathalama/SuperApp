package dev.mathalama.identityservice.application.dto.event;

import java.util.UUID;

public record PasswordResetEmailRequestedEvent (
        String eventId,
        String email,
        String username,
        String resetToken,
        long timeStamp
){
    public static PasswordResetEmailRequestedEvent create(String email, String username, String resetToken) {
        return new PasswordResetEmailRequestedEvent(
                UUID.randomUUID().toString(),
                email,
                username,
                resetToken,
                System.currentTimeMillis()
        );
    }
}
