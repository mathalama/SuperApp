package dev.mathalama.identityservice.domain.port.out;

import dev.mathalama.identityservice.domain.model.User;

import java.util.List;

public interface TokenStore {
    String generateAccessToken(User user);

    String generateAccessToken(User user, String sessionId);

    String generateRefreshToken(User user);

    String getUserIdFromToken(String token);

    String getSessionId(String token);

    boolean validateToken(String token);

    void storeRefreshToken(String userId, String tokenId);

    boolean validateRefreshToken(String userId, String tokenId);

    void revokeRefreshToken(String userId, String tokenId);

    void revokeAllRefreshTokens(String userId);

    String getTokenId(String token);

    void blacklistAccessToken(String token);

    boolean isAccessTokenBlacklisted(String tokenId);

    long getRemainingExpiration(String token);

    User validateTokenAndExtractUser(String token);

    List<String> getRolesFromToken(String token);

    void storeSession(dev.mathalama.identityservice.domain.model.UserSession session);

    java.util.List<dev.mathalama.identityservice.domain.model.UserSession> getUserSessions(String userId);

    void revokeSession(String userId, String sessionId);

    void revokeOtherSessions(String userId, String currentSessionId);

}
