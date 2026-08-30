package dev.mathalama.identityservice.infrastructure.cache;

import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import dev.mathalama.identityservice.infrastructure.persistence.jpa.JpaUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RedisTokenStore implements TokenStore {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    private static final String BLACKLIST_PREFIX = "blacklist:access:";
    private static final String SESSION_PREFIX = "session:";
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public RedisTokenStore(@Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration,
            RedisTemplate<String, String> redisTemplate,
            UserRepository userRepository) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessExpiration;
        this.refreshTokenExpiration = refreshExpiration;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    @Override
    public String generateAccessToken(User user) {
        return generateAccessToken(user, null);
    }

    @Override
    public String generateAccessToken(User user, String sessionId) {
        Map<String, Object> claims = new HashMap<>();

        List<String> rolesList = user.getRoles() != null && !user.getRoles().isEmpty()
                ? user.getRoles().stream()
                        .map(role -> role.getName())
                        .toList()
                : List.of("ROLE_USER");

        claims.put("roles", rolesList);
        claims.put("type", "access");
        if (sessionId != null) {
            claims.put("sid", sessionId);
        }

        return Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuer("Identity Service")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public String generateRefreshToken(User user) {
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");

        String token = Jwts.builder()
                .claims(claims)
                .subject(user.getId().toString())
                .issuer("Identity Service")
                .id(tokenId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();

        storeRefreshToken(user.getId().toString(), tokenId);
        return token;
    }

    @Override
    public void storeRefreshToken(String userId, String tokenId) {
        String tokenKey = REFRESH_TOKEN_PREFIX + userId + ":" + tokenId;
        String userSetKey = "user:tokens:" + userId;

        redisTemplate.opsForValue().set(tokenKey, tokenId, refreshTokenExpiration, TimeUnit.MILLISECONDS);

        redisTemplate.opsForSet().add(userSetKey, tokenId);

        redisTemplate.expire(userSetKey, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean validateRefreshToken(String userId, String tokenId) {
        String key = REFRESH_TOKEN_PREFIX + userId + ":" + tokenId;
        String storedTokenId = redisTemplate.opsForValue().get(key);
        return tokenId != null && tokenId.equals(storedTokenId);
    }

    @Override
    public void revokeRefreshToken(String userId, String tokenId) {
        String key = REFRESH_TOKEN_PREFIX + userId + ":" + tokenId;
        redisTemplate.delete(key);
        log.debug("Revoked refresh token for user: {}", userId);
    }

    public void revokeAllRefreshTokens(String userId) {
        String userSetKey = "user:tokens:" + userId;

        Set<String> tokenIds = redisTemplate.opsForSet().members(userSetKey);

        if (tokenIds != null && !tokenIds.isEmpty()) {
            List<String> keysToDelete = tokenIds.stream()
                    .map(id -> REFRESH_TOKEN_PREFIX + userId + ":" + id)
                    .collect(Collectors.toList());

            redisTemplate.delete(keysToDelete);
        }
        redisTemplate.delete(userSetKey);
        log.info("Revoked ALL tokens for user: {} using SET instead of KEYS", userId);
    }

    @Override
    public String getTokenId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getId();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getId();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Cannot extract token id: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void blacklistAccessToken(String token) {
        String tokenId = getTokenId(token);
        if (tokenId == null) {
            log.warn("Cannot blacklist token: unable to extract token id");
            return;
        }

        long remainingMs = getRemainingExpiration(token);
        if (remainingMs <= 0) {
            log.debug("Token already expired, no need to blacklist");
            return;
        }

        String key = BLACKLIST_PREFIX + tokenId;
        redisTemplate.opsForValue().set(key, "revoked", remainingMs, TimeUnit.MILLISECONDS);
        log.debug("Access token blacklisted, jti={}, ttl={}ms", tokenId, remainingMs);
    }

    @Override
    public boolean isAccessTokenBlacklisted(String tokenId) {
        String key = BLACKLIST_PREFIX + tokenId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public long getRemainingExpiration(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (ExpiredJwtException e) {
            return 0;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Cannot extract expiration: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public String getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (subject == null || subject.isEmpty()) {
                log.warn("Token subject is empty");
                return null;
            }
            return subject;
        } catch (ExpiredJwtException e) {
            log.warn("Token is expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                log.warn("Token is not an access token");
                return false;
            }

            String tokenId = claims.getId();
            if (tokenId != null && isAccessTokenBlacklisted(tokenId)) {
                log.warn("Access token has been revoked, jti={}", tokenId);
                return false;
            }

            log.debug("JWT access token is valid");
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public User validateTokenAndExtractUser(String token) {
        try {
            if (!validateToken(token)) {
                log.debug("Token validation failed");
                return null;
            }

            String userId = getUserIdFromToken(token);
            if (userId == null || userId.isEmpty()) {
                log.warn("Could not extract user ID from token");
                return null;
            }

            User user = userRepository.findById(UUID.fromString(userId)).orElse(null);

            if (user == null) {
                log.warn("User not found for ID: {}", userId);
                return null;
            }

            log.debug("User extracted from token: {}", user.getUsername());
            return user;

        } catch (Exception e) {
            log.warn("Error validating token and extracting user: {}", e.getMessage());
            return null;
        }
    }

    public List<String> getRolesFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.get("roles", List.class);
        } catch (Exception e) {
            log.warn("Cannot extract roles from token: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void storeSession(dev.mathalama.identityservice.domain.model.UserSession session) {
        String sessionKey = SESSION_PREFIX + session.getUserId() + ":" + session.getSessionId();
        String userSetKey = "user:tokens:" + session.getUserId();
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(sessionKey, json, refreshTokenExpiration, TimeUnit.MILLISECONDS);
            redisTemplate.opsForSet().add(userSetKey, session.getSessionId());
            redisTemplate.expire(userSetKey, refreshTokenExpiration, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to serialize session", e);
        }
    }

    @Override
    public List<dev.mathalama.identityservice.domain.model.UserSession> getUserSessions(String userId) {
        String userSetKey = "user:tokens:" + userId;
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSetKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<dev.mathalama.identityservice.domain.model.UserSession> sessions = new ArrayList<>();
        List<String> deadSessionIds = new ArrayList<>();
        for (String sessionId : sessionIds) {
            String sessionKey = SESSION_PREFIX + userId + ":" + sessionId;
            String json = redisTemplate.opsForValue().get(sessionKey);
            if (json != null) {
                try {
                    sessions.add(
                            objectMapper.readValue(json, dev.mathalama.identityservice.domain.model.UserSession.class));
                } catch (Exception e) {
                    log.warn("Failed to parse session json for sessionId={}", sessionId);
                }
            } else {
                deadSessionIds.add(sessionId);
            }
        }
        // Чистим старые ID сессий, у которых истек TTL в Redis
        if (!deadSessionIds.isEmpty()) {
            deadSessionIds.forEach(id -> redisTemplate.opsForSet().remove(userSetKey, id));
        }
        // Сортируем: свежие сессии сверху
        sessions.sort((a, b) -> Long.compare(b.getLastActiveAt(), a.getLastActiveAt()));
        return sessions;
    }

    @Override
    public void revokeSession(String userId, String sessionId) {
        String sessionKey = SESSION_PREFIX + userId + ":" + sessionId;
        String tokenKey = REFRESH_TOKEN_PREFIX + userId + ":" + sessionId;
        String userSetKey = "user:tokens:" + userId;
        redisTemplate.delete(sessionKey);
        redisTemplate.delete(tokenKey);
        redisTemplate.opsForSet().remove(userSetKey, sessionId);
        log.info("Revoked session {} for userId {}", sessionId, userId);
    }

    @Override
    public void revokeOtherSessions(String userId, String currentSessionId) {
        String userSetKey = "user:tokens:" + userId;
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSetKey);
        if (sessionIds != null && !sessionIds.isEmpty()) {
            for (String sessionId : sessionIds) {
                if (!sessionId.equals(currentSessionId)) {
                    revokeSession(userId, sessionId);
                }
            }
        }
        log.info("Revoked all other sessions for userId {}, kept {}", userId, currentSessionId);
    }

    @Override
    public String getSessionId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("sid", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
