package dev.mathalama.userservice.infrastructure.messaging;

import dev.mathalama.userservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.userservice.domain.port.in.UserProfileUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredKafkaListener {

    private final UserProfileUseCase userProfileUseCase;

    @KafkaListener(topics = "user-registered-topic", groupId = "user-service-group")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: userId={}, username={}", event.userId(), event.username());

        try {
            UUID userId = UUID.fromString(event.userId());
            userProfileUseCase.createProfile(userId, event.username(), event.email());
            log.info("Profile created for userId={}", event.userId());
        } catch (Exception e) {
            log.error("Failed to create profile for userId={}: {}", event.userId(), e.getMessage(), e);
            throw e;
        }
    }
}
