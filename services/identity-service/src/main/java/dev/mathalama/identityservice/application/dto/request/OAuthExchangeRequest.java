package dev.mathalama.identityservice.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(
                @NotBlank(message = "Code is required") String code) {
}
