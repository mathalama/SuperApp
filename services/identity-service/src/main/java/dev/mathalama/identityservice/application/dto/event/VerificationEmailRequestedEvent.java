package dev.mathalama.identityservice.application.dto.event;

import java.util.UUID;

public record VerificationEmailRequestedEvent (
        String eventId,
        String email,
        String username,
        String verificationToken,
        long timeStamp
) {
    public static VerificationEmailRequestedEvent create(String email, String username, String verificationToken) {
        return new VerificationEmailRequestedEvent(
                UUID.randomUUID().toString(),
                email,
                username,
                verificationToken,
                System.currentTimeMillis()
        );
    }
}
