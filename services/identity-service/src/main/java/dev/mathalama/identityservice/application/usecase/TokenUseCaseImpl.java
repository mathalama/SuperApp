package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.application.dto.response.AuthResponse;
import dev.mathalama.identityservice.application.dto.response.TokenValidationResponse;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.exception.UserNotFoundException;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.TokenUseCase;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TokenUseCaseImpl implements TokenUseCase {

    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        String userId = tokenStore.getUserIdFromToken(refreshToken);
        String tokenId = tokenStore.getTokenId(refreshToken);

        if (userId == null || tokenId == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (!tokenStore.validateRefreshToken(userId, tokenId)) {
            tokenStore.revokeRefreshToken(userId, tokenId);
            throw new UnauthorizedException("Refresh token has been revoked or is invalid");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        tokenStore.revokeRefreshToken(userId, tokenId);
        String newAccessToken = tokenStore.generateAccessToken(user);
        String newRefreshToken = tokenStore.generateRefreshToken(user);

        log.info("Tokens refreshed for user: {}", user.getUsername());
        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    @Override
    public TokenValidationResponse validateToken(String token) {
        if (!tokenStore.validateToken(token)) {
            return TokenValidationResponse.failure("Invalid or expired token");
        }

        String tokenId = tokenStore.getTokenId(token);
        if (tokenStore.isAccessTokenBlacklisted(tokenId)) {
            return TokenValidationResponse.failure("Token has been blacklisted");
        }

        try {
            String userId = tokenStore.getUserIdFromToken(token);
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new UserNotFoundException("User not found"));
                    
            if (user.getAccountState() == dev.mathalama.identityservice.domain.enums.AccountState.DELETED || user.getAccountState() == dev.mathalama.identityservice.domain.enums.AccountState.DISABLED) {
                return TokenValidationResponse.failure("User account is inactive");
            }
            
            return TokenValidationResponse.success(user.getId(), user.getUsername(), user.getEmail(), user.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.toSet()));
        } catch (Exception ex) {
            return TokenValidationResponse.failure("Failed to validate user: " + ex.getMessage());
        }
    }
}
