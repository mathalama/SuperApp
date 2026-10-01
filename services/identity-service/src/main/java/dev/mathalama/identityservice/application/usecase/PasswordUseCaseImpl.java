package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.domain.enums.AccountState;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.exception.UserNotFoundException;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.PasswordUseCase;
import dev.mathalama.identityservice.domain.port.out.EmailSender;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import dev.mathalama.identityservice.domain.port.out.PasswordResetTokenStore;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import dev.mathalama.identityservice.domain.exception.InvalidAccountStateException;
import dev.mathalama.identityservice.domain.exception.InvalidPasswordException;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PasswordUseCaseImpl implements PasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final EmailSender emailSender;
    private final TokenStore tokenStore;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new InvalidPasswordException("Invalid old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenStore.revokeAllRefreshTokens(user.getId().toString());
        log.info("Password changed successfully for user: {}", user.getUsername());
    }


    @Override
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email: {}", email);
            return;
        }

        User user = userOpt.get();
        if (user.getAccountState() != AccountState.ACTIVE) {
            log.warn("Password reset requested for inactive account: {}", email);
            return;
        }

        if (!passwordResetTokenStore.canResendToken(user)) {
            log.warn("Password reset cooldown active for email: {}", email);
            return;
        }

        String resetToken = passwordResetTokenStore.generateResetToken(user);
        user.setLastVerificationSentAt(new Date());
        userRepository.save(user);

        emailSender.sendPasswordResetEmail(email, user.getUsername(), resetToken);
        log.info("Password reset email sent to user: {}", user.getUsername());
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        if (!passwordResetTokenStore.verifyToken(token)) {
            throw new UnauthorizedException("Invalid or expired password reset token");
        }
        String userIdStr = passwordResetTokenStore.getUserIdByToken(token);
        if (userIdStr == null) {
            throw new UserNotFoundException("Token not found or expired");
        }
        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetTokenStore.markTokenAsUsed(token);
        tokenStore.revokeAllRefreshTokens(userId.toString());
        log.info("Password reset successfully for user: {}", user.getUsername());
    }
}
