package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.response.MessageResponse;
import dev.mathalama.identityservice.application.dto.response.UserSessionResponse;
import dev.mathalama.identityservice.domain.port.in.SessionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionUseCase sessionUseCase;

    @GetMapping
    public ResponseEntity<List<UserSessionResponse>> getSessions(
            @AuthenticationPrincipal User principal,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = resolveUserId(principal, headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        List<UserSessionResponse> sessions = sessionUseCase.getActiveSessions(userId, token);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<MessageResponse> revokeSession(
            @AuthenticationPrincipal User principal,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @PathVariable String sessionId) {
        String userId = resolveUserId(principal, headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        sessionUseCase.revokeSession(userId, sessionId);
        return ResponseEntity.ok(new MessageResponse("Session revoked successfully"));
    }

    @DeleteMapping("/other")
    public ResponseEntity<MessageResponse> revokeOtherSessions(
            @AuthenticationPrincipal User principal,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = resolveUserId(principal, headerUserId);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        sessionUseCase.revokeOtherSessions(userId, token);
        return ResponseEntity.ok(new MessageResponse("All other sessions revoked successfully"));
    }

    private String resolveUserId(User principal, String headerUserId) {
        if (principal != null && principal.getUsername() != null && !principal.getUsername().isBlank()) {
            return principal.getUsername();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User authUser) {
            return authUser.getUsername();
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        return null;
    }
}
