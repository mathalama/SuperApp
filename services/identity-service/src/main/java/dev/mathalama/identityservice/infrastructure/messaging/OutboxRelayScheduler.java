package dev.mathalama.identityservice.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxProcessor outboxProcessor;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize = 50;

    @Scheduled(fixedDelayString = "${app.outbox.fixed-delay}")
    public void relayEventsToKafka() {
        int consecutiveErrors = 0;
        int processedCount = 0;

        while (processedCount < batchSize) {
            try {
                boolean hasEvent = outboxProcessor.processNextEvent();
                if (!hasEvent) {
                    break;
                }
                processedCount++;
                consecutiveErrors = 0;
            } catch (Exception e) {
                consecutiveErrors++;
                log.error("Failed to relay outbox event: {}", e.getMessage());
                if (consecutiveErrors >= 5) {
                    log.warn("Stopped outbox processing after 5 consecutive errors. Infrastructure might be down.");
                    break;
                }
            }
        }
    }
}
