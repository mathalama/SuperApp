package dev.mathalama.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class JwtRelayFilter implements GlobalFilter, Ordered {

        private final SecretKey signingKey;
        private final ReactiveStringRedisTemplate redisTemplate;

        private static final Set<String> PUBLIC_PATHS = Set.of(
                        "/auth/register",
                        "/auth/authenticate",
                        "/auth/refresh",
                        "/auth/verify-email",
                        "/auth/resend-verification",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/reset-forgotten-password",
                        "/auth/oauth-exchange",
                        "/actuator/health");

        private static final Set<String> PUBLIC_PREFIXES = Set.of(
                        "/identity/v3/api-docs",
                        "/user/v3/api-docs",
                        "/swagger-ui",
                        "/login/oauth2",
                        "/oauth2",
                        "/.well-known");

        public JwtRelayFilter(
                        @Value("${jwt.secret}") String jwtSecret,
                        ReactiveStringRedisTemplate redisTemplate) {
                this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                this.redisTemplate = redisTemplate;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                String path = exchange.getRequest().getURI().getPath();

                // Публичные эндпоинты: удаляем любые входящие заголовки авторизации от клиента
                if (isPublicPath(path)) {
                        ServerHttpRequest cleanRequest = exchange.getRequest()
                                        .mutate()
                                        .headers(httpHeaders -> {
                                                httpHeaders.remove("X-User-Id");
                                                httpHeaders.remove("X-User-Roles");
                                        })
                                        .build();
                        return chain.filter(exchange.mutate().request(cleanRequest).build());
                }

                String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

                // Отсутствует JWT
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                }

                String token = authHeader.substring(7);

                try {
                        Claims claims = Jwts.parser()
                                        .verifyWith(signingKey)
                                        .build()
                                        .parseSignedClaims(token)
                                        .getPayload();

                        // Разрешены только access-токены
                        String type = claims.get("type", String.class);
                        if (!"access".equals(type)) {
                                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                return exchange.getResponse().setComplete();
                        }

                        String userId = claims.getSubject();

                        @SuppressWarnings("unchecked")
                        List<String> roles = claims.get("roles", List.class);
                        String rolesHeader = roles != null ? String.join(",", roles) : "";
                        String tokenId = claims.getId();

                        // Проверяем токен в блэклисте Redis (при логауте)
                        return redisTemplate.hasKey("blacklist:access:" + tokenId)
                                        .flatMap(isBlacklisted -> {
                                                if (Boolean.TRUE.equals(isBlacklisted)) {
                                                        log.warn("Access token is blacklisted, jti: {}", tokenId);
                                                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                                        return exchange.getResponse().setComplete();
                                                }

                                                // Токен валиден: передаем контекст пользователя дальше
                                                ServerHttpRequest mutatedRequest = exchange.getRequest()
                                                                .mutate()
                                                                .header("X-User-Id", userId)
                                                                .header("X-User-Roles", rolesHeader)
                                                                .build();

                                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                                        });

                } catch (Exception e) {
                        log.warn("JWT validation failed: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                }
        }

        private boolean isPublicPath(String path) {
                if (PUBLIC_PATHS.contains(path)) {
                        return true;
                }
                return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
        }

        @Override
        public int getOrder() {
                return -1;
        }
}
