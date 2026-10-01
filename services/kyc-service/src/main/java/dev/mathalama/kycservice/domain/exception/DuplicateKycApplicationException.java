package dev.mathalama.kycservice.domain.exception;

public class DuplicateKycApplicationException extends RuntimeException {
    public DuplicateKycApplicationException(String message) {
        super(message);
    }
}
