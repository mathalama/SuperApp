package dev.mathalama.identityservice.infrastructure.messaging;

import dev.mathalama.identityservice.application.dto.event.EventType;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String topic = EventType.valueOf(event.getEventType()).getTopic();

            kafkaTemplate.send(topic, event.getAggregateId(), payload).get();

            event.setProcessed(true);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process event " + event.getId(), e);
        }
    }
}
