package dev.mathalama.notificationservice.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeadLetterConsumer {

    @KafkaListener(topics = {
            "${app.kafka.topics.user-registered:user-registered-topic}.DLT",
            "${app.kafka.topics.verification-email:verification-email-topic}.DLT",
            "${app.kafka.topics.password-reset-email:password-reset-email-topic}.DLT"
    }, groupId = "${app.kafka.consumer.dlt-group:notification-dlt-group}")
    public void handleDeadLetterMessage(
            ConsumerRecord<String, Object> record,
            @Header(value = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(value = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        log.error("CRITICAL: Message moved to DLT! Topic: {}, Original Topic: {}, Key: {}, Payload: {}, Reason: {}",
                record.topic(),
                originalTopic,
                record.key(),
                record.value(),
                exceptionMessage);
        // In the future, notifications to the developers' Telegram channel, Sentry, or
        // Prometheus can be added here.
    }
}
