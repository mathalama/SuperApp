package dev.mathalama.identityservice.infrastructure.messaging;

import dev.mathalama.identityservice.application.dto.event.EventType;
import dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.identityservice.application.dto.event.VerificationEmailRequestedEvent;
import dev.mathalama.identityservice.application.dto.event.PasswordResetEmailRequestedEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextEvent() {
        java.util.Optional<OutboxEvent> optionalEvent = outboxEventRepository.findNextUnprocessedEventForUpdate();
        if (optionalEvent.isEmpty()) {
            return false;
        }
        OutboxEvent event = optionalEvent.get();
        try {
            Class<?> eventClass = getEventClass(event.getEventType());
            Object payload = objectMapper.readValue(event.getPayload(), eventClass);
            String topic = EventType.valueOf(event.getEventType()).getTopic();

            kafkaTemplate.send(topic, event.getAggregateId(), payload).get();

            event.setProcessed(true);
            outboxEventRepository.save(event);
            log.debug("Successfully relayed outbox event {} of type {} to topic {}", event.getId(), event.getEventType(), topic);
            return true;
        } catch (Exception e) {
            log.error("Failed to process event {}: {}", event.getId(), e.getMessage());
            throw new RuntimeException("Failed to process event " + event.getId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEvent event) {
        try {
            Class<?> eventClass = getEventClass(event.getEventType());
            Object payload = objectMapper.readValue(event.getPayload(), eventClass);
            String topic = EventType.valueOf(event.getEventType()).getTopic();

            kafkaTemplate.send(topic, event.getAggregateId(), payload).get();

            event.setProcessed(true);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process event " + event.getId(), e);
        }
    }

    private Class<?> getEventClass(String eventType) {
        return switch (eventType) {
            case "USER_REGISTERED" -> UserRegisteredEvent.class;
            case "VERIFICATION_EMAIL_REQUESTED" -> VerificationEmailRequestedEvent.class;
            case "PASSWORD_RESET_EMAIL_REQUESTED" -> PasswordResetEmailRequestedEvent.class;
            default -> Object.class;
        };
    }
}
