package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.response.MessageResponse;
import dev.mathalama.identityservice.application.dto.response.UserSessionResponse;
import dev.mathalama.identityservice.domain.port.in.SessionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionUseCase sessionUseCase;

    @GetMapping
    public ResponseEntity<List<UserSessionResponse>> getSessions(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        List<UserSessionResponse> sessions = sessionUseCase.getActiveSessions(userId, token);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<MessageResponse> revokeSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String sessionId) {
        sessionUseCase.revokeSession(userId, sessionId);
        return ResponseEntity.ok(new MessageResponse("Session revoked successfully"));
    }

    @DeleteMapping("/other")
    public ResponseEntity<MessageResponse> revokeOtherSessions(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        sessionUseCase.revokeOtherSessions(userId, token);
        return ResponseEntity.ok(new MessageResponse("All other sessions revoked successfully"));
    }
}
