package dev.mathalama.identityservice.infrastructure.messaging;

import dev.mathalama.identityservice.application.dto.event.EventType;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProcessor outboxProcessor;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay}")
    public void relayEventsToKafka() {
        List<OutboxEvent> events = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc();

        int consecutiveErrors = 0;

        for (OutboxEvent event : events) {
            try {
                outboxProcessor.processEvent(event);
                consecutiveErrors = 0;
            } catch (Exception e) {
                consecutiveErrors++;
                log.error("Failed to relay event {}: {}", event.getId(), e.getMessage());
            }
            if (consecutiveErrors >= 5) {
                log.warn("Stopped batch processing after 5 consecutive errors. Infrastructure might be down.");
                break;
            }
        }
    }
}