package dev.mathalama.identityservice.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxProcessor outboxProcessor;

    @InjectMocks
    private OutboxRelayScheduler scheduler;

    @Test
    void relayEventsToKafka_shouldProcessUntilNoMoreEvents() {
        when(outboxProcessor.processNextEvent())
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        scheduler.relayEventsToKafka();

        verify(outboxProcessor, times(3)).processNextEvent();
    }

    @Test
    void relayEventsToKafka_shouldStopAfterConsecutiveErrors() {
        when(outboxProcessor.processNextEvent())
                .thenThrow(new RuntimeException("DB connection error"));

        scheduler.relayEventsToKafka();

        verify(outboxProcessor, times(5)).processNextEvent();
    }
}
