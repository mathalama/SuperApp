package dev.mathalama.walletservice.infrastructure.messaging.kafka;

import dev.mathalama.walletservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.walletservice.domain.port.in.WalletUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredKafkaListener {

    private final WalletUseCase walletUseCase;

    @KafkaListener(topics = "user-registered-topic", groupId = "wallet-group")
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for user: {}", event.getId());
        try {
            UUID userId = UUID.fromString(event.getId());
            walletUseCase.createWalletForUser(userId);
            log.info("Successfully created default wallet for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to create wallet for user: {}. Error: {}", event.getId(), e.getMessage());
        }
    }
}
