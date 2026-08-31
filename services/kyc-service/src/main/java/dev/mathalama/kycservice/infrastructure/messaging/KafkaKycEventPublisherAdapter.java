package dev.mathalama.kycservice.infrastructure.messaging;

import dev.mathalama.kycservice.domain.enums.KycStatus;
import dev.mathalama.kycservice.domain.port.out.KycEventPublisherPort;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaKycEventPublisherAdapter implements KycEventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC_KYC_EVENTS = "kyc.events";

    @Override
    public void publishKycStatusChanged(UUID userId, UUID applicationId, KycStatus status, String reason) {
        KycStatusChangedEvent event = KycStatusChangedEvent.builder()
                .userId(userId)
                .applicationId(applicationId)
                .status(status.name())
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();

        log.info("Publishing KYC status event to Kafka topic {}: userId={}, status={}", TOPIC_KYC_EVENTS, userId,
                status);
        kafkaTemplate.send(TOPIC_KYC_EVENTS, userId.toString(), event);
    }

    @Data
    @Builder
    public static class KycStatusChangedEvent {
        private UUID userId;
        private UUID applicationId;
        private String status;
        private String reason;
        private LocalDateTime timestamp;
    }
}
