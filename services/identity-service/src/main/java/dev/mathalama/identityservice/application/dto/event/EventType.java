package dev.mathalama.identityservice.application.dto.event;

import lombok.Getter;

@Getter
public enum EventType {

    USER_REGISTERED("user-registered-topic"),
    VERIFICATION_EMAIL_REQUESTED("verification-email-topic"),
    PASSWORD_RESET_EMAIL_REQUESTED("password-reset-email-topic");

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }
}