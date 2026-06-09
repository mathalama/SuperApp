package dev.mathalama.identityservice.infrastructure.service;

import dev.mathalama.identityservice.domain.entity.Role;
import dev.mathalama.identityservice.domain.entity.User;
import dev.mathalama.identityservice.infrastructure.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceImplTest {

    private JwtServiceImpl jwtService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserRepository userRepository;

    private final String testSecret = "test-super-secret-key-that-must-be-very-long-for-hmac-sha256";
    private final long testAccessTokenExpiration = 3600000; // 1 hour
    private final long testRefreshTokenExpiration = 86400000; // 1 day

    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(
                testSecret,
                testAccessTokenExpiration,
                testRefreshTokenExpiration,
                redisTemplate,
                userRepository
        );

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");

        Role userRole = new Role();
        userRole.setName("ROLE_USER");
        testUser.setRoles(Set.of(userRole));
    }

    @Test
    void generateAccessToken_ShouldReturnValidToken() {
        // Given is setup

        // When
        String token = jwtService.generateAccessToken(testUser);

        // Then
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals(testUser.getId().toString(), jwtService.getUserIdFromToken(token));
    }

    @Test
    void validateToken_WhenTokenIsValid_ShouldReturnTrue() {
        // Given
        String token = jwtService.generateAccessToken(testUser);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        boolean isValid = jwtService.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    void validateToken_WhenTokenIsExpired_ShouldReturnFalse() {
        // Given - generate an expired token manually
        String expiredToken = Jwts.builder()
                .subject(testUser.getId().toString())
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(testSecret.getBytes()))
                .compact();

        // When
        boolean isValid = jwtService.validateToken(expiredToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void blacklistAccessToken_ShouldStoreInRedis() {
        // Given
        String token = jwtService.generateAccessToken(testUser);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        jwtService.blacklistAccessToken(token);

        // Then
        verify(valueOperations, times(1)).set(anyString(), eq("revoked"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void validateTokenAndExtractUser_WhenValid_ShouldReturnUser() {
        // Given
        String token = jwtService.generateAccessToken(testUser);
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        // When
        User extractedUser = jwtService.validateTokenAndExtractUser(token);

        // Then
        assertNotNull(extractedUser);
        assertEquals(testUser.getId(), extractedUser.getId());
    }
}
