package dev.mathalama.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "notification:processed:event:";
    private static final Duration TTL = Duration.ofDays(1); // 24-hour deduplication window

    /**
     * Atomic check (SETNX) in Redis:
     *
     * @return true if event is new and successfully recorded.
     *         false if event was already processed previously (duplicate).
     */
    public boolean markIfNew(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        String key = KEY_PREFIX + eventId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSED", TTL);
        return Boolean.TRUE.equals(isNew);
    }

    public void remove(String eventId) {
        if (eventId != null && !eventId.isBlank()) {
            redisTemplate.delete(KEY_PREFIX + eventId);
        }
    }
}
