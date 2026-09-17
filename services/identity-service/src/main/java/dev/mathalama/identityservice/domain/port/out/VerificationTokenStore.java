package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.domain.model.User;

public interface VerificationTokenStore {
    String generateVerificationToken(User user);
    boolean verifyToken(String email, String code);
    boolean canResendToken(User user);
    void markTokenAsUsed(String email);
    // String getUserIdByToken(String token);
}
