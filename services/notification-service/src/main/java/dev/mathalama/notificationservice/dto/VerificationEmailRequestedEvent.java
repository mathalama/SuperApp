package dev.mathalama.notificationservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VerificationEmailRequestedEvent(
        @NotBlank(message = "Event ID must not be blank")
        String eventId,

        @NotBlank(message = "Email must not be blank")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Username must not be blank")
        String username,

        @NotBlank(message = "Verification token must not be blank")
        String verificationToken,

        @NotNull(message = "Timestamp must not be null")
        long timestamp
) {}