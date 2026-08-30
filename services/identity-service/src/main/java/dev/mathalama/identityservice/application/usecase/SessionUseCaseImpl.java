package dev.mathalama.identityservice.application.usecase;

import dev.mathalama.identityservice.application.dto.response.UserSessionResponse;
import dev.mathalama.identityservice.domain.model.UserSession;
import dev.mathalama.identityservice.domain.port.in.SessionUseCase;
import dev.mathalama.identityservice.domain.port.out.TokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionUseCaseImpl implements SessionUseCase {

    private final TokenStore tokenStore;

    @Override
    public List<UserSessionResponse> getActiveSessions(String userId, String currentAccessToken) {
        String currentSessionId = currentAccessToken != null ? tokenStore.getSessionId(currentAccessToken) : null;
        List<UserSession> sessions = tokenStore.getUserSessions(userId);

        return sessions.stream()
                .map(s -> new UserSessionResponse(
                        s.getSessionId(),
                        s.getIpAddress(),
                        s.getOs(),
                        s.getBrowser(),
                        s.getCreatedAt(),
                        s.getLastActiveAt(),
                        currentSessionId != null && currentSessionId.equals(s.getSessionId())))
                .toList();
    }

    @Override
    public void revokeSession(String userId, String sessionId) {
        tokenStore.revokeSession(userId, sessionId);
    }

    @Override
    public void revokeOtherSessions(String userId, String currentAccessToken) {
        String currentSessionId = currentAccessToken != null ? tokenStore.getSessionId(currentAccessToken) : null;
        if (currentSessionId != null) {
            tokenStore.revokeOtherSessions(userId, currentSessionId);
        }
    }
}
