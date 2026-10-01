package dev.mathalama.identityservice.domain.port.in;

import java.util.UUID;

public interface PasswordUseCase {
    void changePassword(UUID userId, String oldPassword, String newPassword);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
}
