package dev.mathalama.identityservice.application.service;

import dev.mathalama.identityservice.application.dto.auth.AuthResponse;
import dev.mathalama.identityservice.application.dto.auth.SignInRequest;
import dev.mathalama.identityservice.domain.entity.Role;
import dev.mathalama.identityservice.domain.entity.User;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.exception.UserAlreadyExistException;
import dev.mathalama.identityservice.infrastructure.repository.RoleRepository;
import dev.mathalama.identityservice.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private VerificationTokenService verificationTokenService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    private SignInRequest signInRequest;
    private User user;
    private Role role;

    @BeforeEach
    void setUp() {
        signInRequest = new SignInRequest("test@example.com", "password123");

        role = new Role();
        role.setName("ROLE_USER");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("encodedPassword");
    }

    @Test
    void register_WhenValidRequest_ShouldCreateUserAndPublishEvent() {
        // Given
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(verificationTokenService.generateVerificationToken(any(User.class))).thenReturn("token123");

        // When
        authService.register("testuser", "test@example.com", "password123");

        // Then
        verify(userRepository).save(any(User.class));
        verify(emailService).sendVerificationEmail("test@example.com", "testuser", "token123");
    }

    @Test
    void authenticate_WhenValidCredentials_ShouldReturnTokens() {
        // Given
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // When
        User authUser = authService.authenticate(signInRequest);

        // Then
        assertNotNull(authUser);
        assertEquals(user.getEmail(), authUser.getEmail());
    }

    @Test
    void authenticate_WhenInvalidPassword_ShouldThrowException() {
        // Given
        when(userRepository.findByEmailIgnoreCaseOrUsernameIgnoreCase(anyString(), anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // When & Then
        assertThrows(UnauthorizedException.class, () -> authService.authenticate(signInRequest));
    }
}
