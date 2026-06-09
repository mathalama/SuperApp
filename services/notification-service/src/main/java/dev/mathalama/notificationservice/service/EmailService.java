package dev.mathalama.notificationservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendWelcomeEmail(String email, String username) {
        try {
            log.info("Sending welcome email to: {}", email);

            Context context = new Context();
            context.setVariable("username", username);
            String htmlContent = templateEngine.process("welcome-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "SuperApp");
            helper.setTo(email);
            helper.setSubject("Welcome to SuperApp!");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", email, e);
        }
    }

    public void sendVerificationEmail(String email, String username, String verificationCode) {
        try {
            log.info("Sending verification code email to: {}", email);

            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("verificationCode", verificationCode);

            String htmlContent = templateEngine.process("verification-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Identity Service");
            helper.setTo(email);
            helper.setSubject("Verify your email address");
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", email);
        } catch (Exception ex) {
            log.error("Failed to send verification email to: {}", email, ex);
        }
    }

    public void sendPasswordResetEmail(String email, String username, String resetToken) {
        try {
            String resetLink = String.format("%s/reset-password?token=%s", frontendUrl, resetToken);
            log.info("Sending password reset email to: {}", email);

            Context context = new Context();
            context.setVariable("username", username);
            context.setVariable("resetLink", resetLink);
            String htmlContent = templateEngine.process("reset-password-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Identity Service");
            helper.setTo(email);
            helper.setSubject("Password Reset Request");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", email);
        } catch (Exception ex) {
            log.error("Failed to send password reset email to: {}", email, ex);
        }
    }
}