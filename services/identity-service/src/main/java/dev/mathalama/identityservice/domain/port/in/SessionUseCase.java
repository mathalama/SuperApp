package dev.mathalama.identityservice.domain.port.in;

import dev.mathalama.identityservice.application.dto.response.UserSessionResponse;

import java.util.List;

public interface SessionUseCase {
    List<UserSessionResponse> getActiveSessions(String userId, String currentAccessToken);

    void revokeSession(String userId, String sessionId);

    void revokeOtherSessions(String userId, String currentAccessToken);
}
