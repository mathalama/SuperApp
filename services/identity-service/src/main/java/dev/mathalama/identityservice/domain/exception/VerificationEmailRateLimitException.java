package dev.mathalama.identityservice.domain.exception;

public class VerificationEmailRateLimitException extends RuntimeException {
    public VerificationEmailRateLimitException(String message) {
        super(message);
    }
}
