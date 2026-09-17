package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent;

public interface EventPublisher {
    void publishUserRegistered(UserRegisteredEvent event);
}
