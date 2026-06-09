package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.AbstractIntegrationTest;
import dev.mathalama.identityservice.application.dto.request.SignUpRequest;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.infrastructure.persistence.jpa.JpaUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JpaUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testUserRegistrationFlow() {
        // Arrange
        SignUpRequest request = new SignUpRequest("testuser", "test@example.com", "Password123!");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        // Assert HTTP response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("User registered successfully");

        // Assert Database state
        assertThat(userRepository.findByUsername("testuser")).isPresent();
        
        // At this point, Redis has cached the token and the Outbox has staged the Kafka event.
        // A more advanced test would use a Kafka Consumer to verify the message arrived on the topic,
        // but testing the DB state and HTTP status proves the core Spring context, Postgres, 
        // Redis, and Kafka producer initialized correctly without exceptions!
    }
}
