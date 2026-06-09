package dev.mathalama.identityservice.domain.port.in;

public interface PasswordUseCase {
    void changePassword(String username, String oldPassword, String newPassword);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
}