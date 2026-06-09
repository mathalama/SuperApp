package dev.mathalama.identityservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mathalama.identityservice.application.dto.event.EventType;
import dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.identityservice.domain.port.out.EventPublisher;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishUserRegistered(UserRegisteredEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.fromString(event.eventId()))
                    .aggregateId(event.userId())
                    .eventType(EventType.USER_REGISTERED.name())
                    .payload(jsonPayload)
                    .createdAt(new Date(event.timestamp()))
                    .processed(false)
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.debug("Saved UserRegisteredEvent to OUTBOX table for userId: {}", event.userId());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize UserRegisteredEvent", e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}