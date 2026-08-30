package dev.mathalama.identityservice.application.dto.response;

public record UserSessionResponse(
        String sessionId,
        String ipAddress,
        String os,
        String browser,
        long createdAt,
        long lastActiveAt,
        boolean current) {
}
