package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.domain.model.User;

public interface PasswordResetTokenStore {
    String generateResetToken(User user);
    boolean verifyToken(String token);
    boolean canResendToken(User user);
    void markTokenAsUsed(String token);
    String getUserIdByToken(String token);
}
