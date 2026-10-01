package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.domain.enums.AccountState;
import dev.mathalama.identityservice.domain.exception.InvalidPasswordException;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.exception.UserNotFoundException;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.out.EmailSender;
import dev.mathalama.identityservice.domain.port.out.PasswordResetTokenStore;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;

    @Mock
    private EmailSender emailSender;

    @Mock
    private TokenStore tokenStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordUseCaseImpl passwordUseCase;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("john_doe");
        testUser.setEmail("john@example.com");
        testUser.setPassword("hashedOldPassword");
        testUser.setAccountState(AccountState.ACTIVE);
    }

    @Test
    @DisplayName("changePassword: successfully changes password and revokes all refresh tokens")
    void changePassword_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("OldP@ss123", "hashedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewP@ss123")).thenReturn("hashedNewPassword");

        passwordUseCase.changePassword(userId, "OldP@ss123", "NewP@ss123");

        verify(userRepository).save(testUser);
        verify(tokenStore).revokeAllRefreshTokens(userId.toString());
    }

    @Test
    @DisplayName("changePassword: throws InvalidPasswordException if old password does not match")
    void changePassword_wrongOldPassword_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "hashedOldPassword")).thenReturn(false);

        assertThrows(InvalidPasswordException.class, () ->
                passwordUseCase.changePassword(userId, "WrongPassword", "NewP@ss123"));

        verify(userRepository, never()).save(any());
        verify(tokenStore, never()).revokeAllRefreshTokens(anyString());
    }

    @Test
    @DisplayName("changePassword: throws UserNotFoundException if userId does not exist")
    void changePassword_userNotFound_throwsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () ->
                passwordUseCase.changePassword(userId, "OldP@ss123", "NewP@ss123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("forgotPassword: anti-enumeration: returns silently when email does not exist")
    void forgotPassword_emailNotFound_returnsSilently() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> passwordUseCase.forgotPassword("unknown@example.com"));

        verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
        verify(passwordResetTokenStore, never()).generateResetToken(any());
    }

    @Test
    @DisplayName("forgotPassword: anti-enumeration: returns silently when account is inactive")
    void forgotPassword_inactiveAccount_returnsSilently() {
        testUser.setAccountState(AccountState.DISABLED);
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

        assertDoesNotThrow(() -> passwordUseCase.forgotPassword("john@example.com"));

        verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("forgotPassword: sends password reset email when user exists and active")
    void forgotPassword_success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));
        when(passwordResetTokenStore.canResendToken(testUser)).thenReturn(true);
        when(passwordResetTokenStore.generateResetToken(testUser)).thenReturn("reset-token-xyz");

        passwordUseCase.forgotPassword("john@example.com");

        verify(userRepository).save(testUser);
        verify(emailSender).sendPasswordResetEmail("john@example.com", "john_doe", "reset-token-xyz");
    }

    @Test
    @DisplayName("resetPassword: throws UnauthorizedException on invalid or expired token")
    void resetPassword_invalidToken_throwsUnauthorized() {
        when(passwordResetTokenStore.verifyToken("invalid-token")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () ->
                passwordUseCase.resetPassword("invalid-token", "NewP@ss123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: successfully resets password and revokes tokens")
    void resetPassword_success() {
        String token = "valid-reset-token";
        when(passwordResetTokenStore.verifyToken(token)).thenReturn(true);
        when(passwordResetTokenStore.getUserIdByToken(token)).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewP@ss123")).thenReturn("hashedNewPassword");

        passwordUseCase.resetPassword(token, "NewP@ss123");

        verify(userRepository).save(testUser);
        verify(passwordResetTokenStore).markTokenAsUsed(token);
        verify(tokenStore).revokeAllRefreshTokens(userId.toString());
    }
}
