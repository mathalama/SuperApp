package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.application.dto.event.UserRegisteredEvent;
import dev.mathalama.identityservice.application.dto.request.SignInRequest;
import dev.mathalama.identityservice.domain.model.Role;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.enums.AccountState;
import dev.mathalama.identityservice.domain.enums.SecurityStatus;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.exception.UserAlreadyExistException;
import dev.mathalama.identityservice.domain.exception.UserNotFoundException;
import dev.mathalama.identityservice.domain.port.in.AuthUseCase;
import dev.mathalama.identityservice.domain.port.out.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthUseCaseImpl implements AuthUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenStore tokenStore;
    private final VerificationTokenStore verificationTokenStore;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Override
    public void register(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistException("Email is already registered");
        }

        String encodePassword = passwordEncoder.encode(password);
        
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Default role not found"));
        
        try {
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(encodePassword)
                    .roles(Set.of(userRole))
                    .build();
            user.setAccountState(AccountState.PENDING_VERIFICATION);
            user.setSecurityStatus(SecurityStatus.MFA_REQUIRED);
            user.setEmailVerified(false);
            user.setCreatedAt(new Date());

            User savedUser = userRepository.save(user);

            var event = UserRegisteredEvent.create(savedUser.getId().toString(), savedUser.getUsername(), savedUser.getEmail(), "LOCAL");
            eventPublisher.publishUserRegistered(event);

            String verificationToken = verificationTokenStore.generateVerificationToken(savedUser);
            emailSender.sendVerificationEmail(email, username, verificationToken);

            log.info("User registered successfully: {} (email: {}). Verification email sent.", username, email);
            
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistException("Username or email already exists");
        }
    }

    @Override
    public User authenticate(SignInRequest request) {
        User user = userRepository
                .findByEmailIgnoreCaseOrUsernameIgnoreCase(
                        request.login(), request.login()
                )
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid email or password")
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (user.getAccountState() == AccountState.PENDING_VERIFICATION) {
            throw new UnauthorizedException("Account is pending email verification. Please check your inbox.");
        }

        if (user.getAccountState() == AccountState.DISABLED) {
            throw new UnauthorizedException("Account is inactive or has been deleted.");
        }

        return user;
    }

    @Override
    public void logout(String userId, String accessToken) {
        tokenStore.revokeAllRefreshTokens(userId);
        tokenStore.blacklistAccessToken(accessToken);
        log.info("User logged out, tokens revoked for userId: {}", userId);
    }

    @Override
    public void assignRoleToUser(String username, String roleName) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new UserNotFoundException("Role not found"));
        
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("Assigned role {} to user {}", roleName, username);
    }
}
