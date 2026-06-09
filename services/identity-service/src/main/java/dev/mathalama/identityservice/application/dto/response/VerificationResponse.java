package dev.mathalama.identityservice.application.dto.response;

public record VerificationResponse(
    String message,
    Boolean verified) {}

