package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.application.dto.response.AuthResponse;
import dev.mathalama.identityservice.application.mapper.UserMapper;
import dev.mathalama.identityservice.domain.exception.UnauthorizedException;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.OAuthExchangeUseCase;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OAuthExchangeUseCaseImpl implements OAuthExchangeUseCase {
    private final RedisTemplate<String, String> redisTemplate;
    private final TokenStore tokenStore;
    private final UserRepository userRepository;

    private static final String REDIS_PREFIX = "temp_oauth_code:";

    @Override
    public String createExchangeCode(String userId) {
        String tempCode = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(REDIS_PREFIX + tempCode, userId, 1, TimeUnit.MINUTES);

        return tempCode;
    }

    @Override
    @Transactional
    public AuthResponse exchangeCodeForTokens(String code) {
        String redisKey = REDIS_PREFIX + code;
        String userId = redisTemplate.opsForValue().get(redisKey);

        if (userId == null) {
            throw new UnauthorizedException("Invalid or expired exchange code");
        }
        redisTemplate.delete(redisKey);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        String accessToken = tokenStore.generateAccessToken(user);
        String refreshToken = tokenStore.generateRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, UserMapper.toCurrentUserResponse(user));
    }


}
