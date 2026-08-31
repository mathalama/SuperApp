package dev.mathalama.identityservice.infrastructure.messaging;

import dev.mathalama.identityservice.application.dto.event.KycStatusChangedEvent;
import dev.mathalama.identityservice.domain.enums.KYCLifeCycle;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class KycStatusChangedKafkaListener {

    private final UserRepository userRepository;

    @Transactional
    @KafkaListener(topics = "${app.kafka.topics.kyc-events:kyc.events}", groupId = "${spring.kafka.consumer.group-id:identity-service-group}")
    public void handleKycStatusChanged(KycStatusChangedEvent event) {
        log.info("Received KycStatusChangedEvent: userId={}, status={}, reason={}",
                event.userId(), event.status(), event.reason());

        if (event.userId() == null) {
            log.warn("KycStatusChangedEvent ignored: userId is null");
            return;
        }

        userRepository.findById(event.userId()).ifPresentOrElse(user -> {
            KYCLifeCycle newStatus = mapToKycLifeCycle(event.status());
            user.setKycStatus(newStatus);
            userRepository.save(user);
            log.info("Updated KYC status for user {}: {}", user.getId(), newStatus);
        }, () -> log.warn("User with ID {} not found for KYC update", event.userId()));
    }

    private KYCLifeCycle mapToKycLifeCycle(String status) {
        if (status == null) {
            return KYCLifeCycle.PENDING;
        }
        return switch (status.toUpperCase()) {
            case "VERIFIED" -> KYCLifeCycle.VERIFIED;
            case "IN_PROGRESS", "MANUAL_REVIEW" -> KYCLifeCycle.IN_PROGRESS;
            case "REJECTED" -> KYCLifeCycle.REJECTED;
            case "EXPIRED" -> KYCLifeCycle.EXPIRED;
            default -> KYCLifeCycle.PENDING;
        };
    }
}
