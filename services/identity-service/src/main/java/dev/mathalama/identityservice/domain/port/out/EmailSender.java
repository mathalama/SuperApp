package dev.mathalama.identityservice.domain.port.out;

public interface EmailSender {
    void sendVerificationEmail(String email, String username, String verificationToken);
    void sendPasswordResetEmail(String email, String username, String resetToken);
}
