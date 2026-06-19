package dev.mathalama.userservice.application.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @Size(max = 500)
        String avatarUrl,

        @Size(max = 2000)
        String bio,

        @Size(max = 20)
        String phoneNumber,

        LocalDate dateOfBirth,

        @Size(max = 10)
        String locale,

        @Size(max = 50)
        String timezone
) {
}
