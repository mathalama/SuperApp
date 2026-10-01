package dev.mathalama.userservice.domain.exception;

public class UserProfileNotFoundException extends RuntimeException {
    public UserProfileNotFoundException(String message) {
        super(message);
    }
}
