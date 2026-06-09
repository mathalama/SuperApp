package dev.mathalama.notificationservice.consumer;

import dev.mathalama.notificationservice.dto.PasswordResetEmailRequestedEvent;
import dev.mathalama.notificationservice.dto.UserRegisteredEvent;
import dev.mathalama.notificationservice.dto.VerificationEmailRequestedEvent;
import dev.mathalama.notificationservice.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventConsumer {
    private final EmailService emailService;

    @KafkaListener(
            topics = "user-registered-topic",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserRegistration(@Payload @Valid UserRegisteredEvent event) {
        log.info("Received registration event for user: {} (email: {})", event.username(), event.email());
        try {
            emailService.sendWelcomeEmail(event.email(), event.username());
        } catch (Exception e) {
            log.error("Failed to process email for user {}: {}", event.userId(), e.getMessage());
            throw e;
        }
    }

    @KafkaListener(
            topics = "verification-email-topic",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleVerificationEmailRequest(VerificationEmailRequestedEvent event) {
        log.info("Received verification email request for user: {} (email: {})", event.username(), event.email());
        try {
            emailService.sendVerificationEmail(event.email(), event.username(), event.verificationToken());
        } catch (Exception e) {
            log.error("Failed to process verification email for user {}: {}", event.username(), e.getMessage());
            throw e;
        }
    }

    @KafkaListener(
            topics = "password-reset-email-topic",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePasswordResetEmailRequest(PasswordResetEmailRequestedEvent event) {
        log.info("Received password reset email request for user: {} (email: {})", event.username(), event.email());
        try {
            emailService.sendPasswordResetEmail(event.email(), event.username(), event.resetToken());
        } catch (Exception e) {
            log.error("Failed to process password reset email for user {}: {}", event.username(), e.getMessage());
            throw e;
        }
    }
}