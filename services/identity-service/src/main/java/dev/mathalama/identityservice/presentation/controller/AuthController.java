package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.request.OAuthExchangeRequest;
import dev.mathalama.identityservice.application.dto.request.SignUpRequest;
import dev.mathalama.identityservice.application.dto.request.SignInRequest;
import dev.mathalama.identityservice.application.dto.response.AuthResponse;
import dev.mathalama.identityservice.application.dto.response.MessageResponse;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.AuthUseCase;
import dev.mathalama.identityservice.domain.port.in.OAuthExchangeUseCase;
import dev.mathalama.identityservice.application.dto.request.LogoutRequest;
import dev.mathalama.identityservice.application.mapper.UserMapper;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import dev.mathalama.identityservice.infrastructure.util.DeviceDetector;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;
    private final TokenStore tokenStore;
    private final OAuthExchangeUseCase oAuthExchangeUseCase;
    private final DeviceDetector deviceDetector;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody SignUpRequest request) {
        authUseCase.register(request.username(), request.email(), request.password());
        return new MessageResponse("User registered successfully. Please check your email to verify your account.");
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthResponse> authenticate(
            @Valid @RequestBody SignInRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        User user = authUseCase.authenticate(request);
        String refreshToken = tokenStore.generateRefreshToken(user);
        String refreshTokenId = tokenStore.getTokenId(refreshToken);
        String accessToken = tokenStore.generateAccessToken(user, refreshTokenId);

        // Фиксируем устройство и сессию в Redis
        String userAgent = httpRequest.getHeader("User-Agent");
        String ip = deviceDetector.extractClientIp(httpRequest);

        dev.mathalama.identityservice.domain.model.UserSession session = dev.mathalama.identityservice.domain.model.UserSession
                .builder()
                .sessionId(refreshTokenId)
                .userId(user.getId().toString())
                .ipAddress(ip)
                .userAgent(userAgent)
                .os(deviceDetector.detectOs(userAgent))
                .browser(deviceDetector.detectBrowser(userAgent))
                .createdAt(System.currentTimeMillis())
                .lastActiveAt(System.currentTimeMillis())
                .build();

        tokenStore.storeSession(session);

        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, UserMapper.toCurrentUserResponse(user)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody LogoutRequest request) {
        String refreshToken = request.refreshToken();
        String userId = tokenStore.getUserIdFromToken(refreshToken);
        String tokenId = tokenStore.getTokenId(refreshToken);

        if (userId != null && tokenId != null) {
            tokenStore.revokeRefreshToken(userId, tokenId);
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            if (tokenStore.validateToken(accessToken)) {
                tokenStore.blacklistAccessToken(accessToken);
            }
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/oauth-exchange")
    public ResponseEntity<AuthResponse> exchangeCode(@Valid @RequestBody OAuthExchangeRequest request) {
        AuthResponse response = oAuthExchangeUseCase.exchangeCodeForTokens(request.code());
        return ResponseEntity.ok(response);
    }
}
