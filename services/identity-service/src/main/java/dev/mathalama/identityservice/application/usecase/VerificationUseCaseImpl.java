package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.application.dto.response.VerificationResponse;
import dev.mathalama.identityservice.domain.enums.AccountState;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.exception.UserNotFoundException;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.VerificationUseCase;
import dev.mathalama.identityservice.domain.port.out.EmailSender;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import dev.mathalama.identityservice.domain.port.out.VerificationTokenStore;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import dev.mathalama.identityservice.domain.exception.InvalidAccountStateException;
import dev.mathalama.identityservice.domain.exception.VerificationEmailRateLimitException;

import java.util.Date;
import java.util.UUID;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VerificationUseCaseImpl implements VerificationUseCase {

    private final UserRepository userRepository;
    private final VerificationTokenStore verificationTokenStore;
    private final EmailSender emailSender;

    @Override
    public VerificationResponse verifyEmail(String email, String code) {
        if (!verificationTokenStore.verifyToken(email, code)) {
            throw new UnauthorizedException("Invalid or expired verification code");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setEmailVerified(true);
        user.setVerifiedAt(new Date());
        user.setAccountState(AccountState.ACTIVE);
        userRepository.save(user);

        verificationTokenStore.markTokenAsUsed(email);

        log.info("Email verified successfully for user: {}", user.getUsername());
        return new VerificationResponse("Email verified successfully", true);
    }

    @Override
    public VerificationResponse resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getAccountState() != AccountState.PENDING_VERIFICATION) {
            throw new InvalidAccountStateException("User email is already verified or account is not pending");
        }

        if (!verificationTokenStore.canResendToken(user)) {
            throw new VerificationEmailRateLimitException("Verification email was recently sent. Please wait before requesting another.");
        }

        String verificationToken = verificationTokenStore.generateVerificationToken(user);
        user.setLastVerificationSentAt(new Date());
        userRepository.save(user);

        emailSender.sendVerificationEmail(email, user.getUsername(), verificationToken);

        log.info("Verification email resent to user: {}", user.getUsername());

        return new VerificationResponse("Verification email sent successfully", false);
    }
}
