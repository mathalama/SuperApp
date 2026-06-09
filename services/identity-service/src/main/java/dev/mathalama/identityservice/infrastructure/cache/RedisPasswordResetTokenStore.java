package dev.mathalama.identityservice.infrastructure.cache;

import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.out.PasswordResetTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.verification.token-expiry-minutes}")
    private long tokenExpiryMinutes;

    @Value("${app.verification.resend-cooldown-seconds}")
    private long resendCooldownSeconds;

    private static final String TOKEN_PREFIX = "reset:token:";
    private static final String COOLDOWN_PREFIX = "reset:cooldown:";

    @Override
    public String generateResetToken(User user) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        String key = TOKEN_PREFIX + tokenHash;
        redisTemplate.opsForValue().set(
                key,
                user.getId().toString(),
                tokenExpiryMinutes,
                TimeUnit.MINUTES
        );

        String cooldownKey = COOLDOWN_PREFIX + user.getId();
        redisTemplate.opsForValue().set(
                cooldownKey,
                String.valueOf(System.currentTimeMillis()),
                resendCooldownSeconds,
                TimeUnit.SECONDS
        );

        log.info("Generated password reset UUID token for user: {}", user.getUsername());
        return rawToken;
    }

    @Override
    public boolean verifyToken(String rawToken) {
        try {
            String tokenHash = hashToken(rawToken);
            String key = TOKEN_PREFIX + tokenHash;
            String userId = redisTemplate.opsForValue().get(key);
            return userId != null;
        } catch (Exception ex) {
            log.error("Error verifying reset token", ex);
            return false;
        }
    }

    @Override
    public boolean canResendToken(User user) {
        try {
            String cooldownKey = COOLDOWN_PREFIX + user.getId();
            String lastSendTime = redisTemplate.opsForValue().get(cooldownKey);
            if (lastSendTime == null) {
                return true;
            }
            long timeSinceLastSend = System.currentTimeMillis() - Long.parseLong(lastSendTime);
            return timeSinceLastSend >= resendCooldownSeconds * 1000;
        } catch (Exception ex) {
            log.error("Error checking reset cooldown", ex);
            return false;
        }
    }

    @Override
    public void markTokenAsUsed(String rawToken) {
        try {
            String tokenHash = hashToken(rawToken);
            String key = TOKEN_PREFIX + tokenHash;
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.error("Error marking reset token as used", ex);
        }
    }

    @Override
    public String getUserIdByToken(String rawToken) {
        try {
            String tokenHash = hashToken(rawToken);
            String key = TOKEN_PREFIX + tokenHash;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.error("Error getting userId by reset token", ex);
            return null;
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = messageDigest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 algorithm not found", ex);
        }
    }
}