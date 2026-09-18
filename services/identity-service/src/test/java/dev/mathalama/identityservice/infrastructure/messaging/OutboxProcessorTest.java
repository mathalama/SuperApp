package dev.mathalama.identityservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxProcessor outboxProcessor;

    @Test
    void processNextEvent_whenNoEvents_shouldReturnFalse() {
        when(outboxEventRepository.findNextUnprocessedEventForUpdate()).thenReturn(Optional.empty());

        boolean result = outboxProcessor.processNextEvent();

        assertFalse(result);
        verify(outboxEventRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void processNextEvent_whenEventExists_shouldPublishAndMarkProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .aggregateId("user-123")
                .eventType("USER_REGISTERED")
                .payload("{\"userId\":\"user-123\"}")
                .createdAt(new Date())
                .processed(false)
                .build();

        UserRegisteredEvent payloadObj = UserRegisteredEvent.create("user-123", "testuser", "test@example.com", "LOCAL");

        when(outboxEventRepository.findNextUnprocessedEventForUpdate()).thenReturn(Optional.of(event));
        when(objectMapper.readValue(eq(event.getPayload()), any(Class.class))).thenReturn(payloadObj);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        boolean result = outboxProcessor.processNextEvent();

        assertTrue(result);
        assertTrue(event.isProcessed());
        verify(kafkaTemplate).send(eq("user-registered-topic"), eq("user-123"), eq(payloadObj));
        verify(outboxEventRepository).save(event);
    }
}
