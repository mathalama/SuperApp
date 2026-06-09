package dev.mathalama.identityservice.infrastructure.cache;

import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.out.VerificationTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisVerificationTokenStore implements VerificationTokenStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.verification.token-expiry-minutes}")
    private long tokenExpiryMinutes;

    @Value("${app.verification.resend-cooldown-seconds}")
    private long resendCooldownSeconds;

    private static final String TOKEN_PREFIX = "verify:token:";
    private static final String COOLDOWN_PREFIX = "verify:cooldown:";

    @Override
    public String generateVerificationToken(User user) {
        String rawCode = String.valueOf(100000 + secureRandom.nextInt(900000));
        String codeHash = hashToken(rawCode);

        String key = TOKEN_PREFIX + user.getEmail();
        redisTemplate.opsForValue().set(
                key,
                codeHash,
                tokenExpiryMinutes,
                TimeUnit.MINUTES
        );

        String cooldownKey = COOLDOWN_PREFIX + user.getEmail();
        redisTemplate.opsForValue().set(
                cooldownKey,
                String.valueOf(System.currentTimeMillis()),
                resendCooldownSeconds,
                TimeUnit.SECONDS
        );

        log.info("Generated 6-digit verification code for user: {}", user.getUsername());

        return rawCode;
    }

    @Override
    public boolean verifyToken(String email, String rawCode) {
        try {
            String key = TOKEN_PREFIX + email;
            String storedHash = redisTemplate.opsForValue().get(key);

            if (storedHash == null) {
                log.warn("Verification code not found or expired for email: {}", email);
                return false;
            }

            String inputHash = hashToken(rawCode);
            return storedHash.equals(inputHash);
        } catch (Exception ex) {
            log.error("Error verifying code for email: {}", email, ex);
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
            long cooldownMs = resendCooldownSeconds * 1000;

            return timeSinceLastSend >= cooldownMs;
        } catch (Exception ex) {
            log.error("Error checking resend cooldown", ex);
            return false;
        }
    }

    @Override
    public void markTokenAsUsed(String email) {
        try {
            String key = TOKEN_PREFIX + email;
            redisTemplate.delete(key);
            log.info("Deleted verification code for email: {}", email);
        } catch (Exception ex) {
            log.error("Error deleting verification code", ex);
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
