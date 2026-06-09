package dev.mathalama.identityservice.infrastructure.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.mathalama.identityservice.application.dto.event.EventType;
import dev.mathalama.identityservice.application.dto.event.PasswordResetEmailRequestedEvent;
import dev.mathalama.identityservice.application.dto.event.VerificationEmailRequestedEvent;
import dev.mathalama.identityservice.domain.port.out.EmailSender;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void sendVerificationEmail(String email, String username, String verificationToken) {
        try {
            VerificationEmailRequestedEvent event = VerificationEmailRequestedEvent.create(email, username, verificationToken);
            String jsonPayload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.fromString(event.eventId()))
                    .aggregateId(email)
                    .eventType(EventType.VERIFICATION_EMAIL_REQUESTED.name())
                    .payload(jsonPayload)
                    .createdAt(new Date(event.timeStamp()))
                    .processed(false)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("[Verification Email] Saved PasswordResetEmailRequestedEvent to OUTBOX table for email: {}", email);
        } catch (Exception e) {
            log.error("Failed to serialize PasswordResetEmailRequestedEvent", e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    @Override
    public void sendPasswordResetEmail(String email, String username, String resetToken) {
        try {
            PasswordResetEmailRequestedEvent event = PasswordResetEmailRequestedEvent.create(email, username, resetToken);
            String jsonPayload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.fromString(event.eventId()))
                    .aggregateId(email)
                    .eventType(EventType.PASSWORD_RESET_EMAIL_REQUESTED.name())
                    .payload(jsonPayload)
                    .createdAt(new Date(event.timeStamp()))
                    .processed(false)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("[Password Reset] Saved PasswordResetEmailRequestedEvent to OUTBOX table for email: {}", email);
        } catch (Exception e) {
            log.error("Failed to serialize PasswordResetEmailRequestedEvent", e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}