package dev.mathalama.identityservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEvent;
import dev.mathalama.identityservice.infrastructure.persistence.outbox.OutboxEventRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OutboxRelayIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("identity_test_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End: OutboxProcessor reads unprocessed event from Postgres, relays to real Kafka, and marks processed")
    void outboxEvent_relayedToKafkaAndMarkedProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        UserRegisteredEvent payload = UserRegisteredEvent.create(userId, "test_user", "test@mathalama.dev", "LOCAL");
        String payloadJson = objectMapper.writeValueAsString(payload);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(eventId)
                .aggregateId(userId)
                .eventType("USER_REGISTERED")
                .payload(payloadJson)
                .createdAt(new Date())
                .processed(false)
                .build();

        outboxEventRepository.save(outboxEvent);

        boolean processed = outboxProcessor.processNextEvent();
        assertTrue(processed, "Outbox event should be processed successfully");

        OutboxEvent updated = outboxEventRepository.findById(eventId).orElseThrow();
        assertTrue(updated.isProcessed(), "Outbox event must be marked processed=true in PostgreSQL");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-verify-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (Consumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("user-registered-topic"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            assertFalse(records.isEmpty(), "Kafka topic should receive the user registered event");
            ConsumerRecord<String, String> record = records.iterator().next();
            assertEquals(userId, record.key());
            assertTrue(record.value().contains("test_user"));
        }
    }
}
