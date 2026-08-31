package dev.mathalama.identityservice.application.dto.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record KycStatusChangedEvent(
        UUID userId,
        UUID applicationId,
        String status,
        String reason,
        LocalDateTime timestamp) {
}
