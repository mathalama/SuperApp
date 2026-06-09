package dev.mathalama.identityservice.presentation.controller;

import dev.mathalama.identityservice.application.dto.response.CurrentUserResponse;
import dev.mathalama.identityservice.application.dto.response.OAuthProviderResponse;
import dev.mathalama.identityservice.application.mapper.UserMapper;
import dev.mathalama.identityservice.application.mapper.OAuthProviderMapper;
import dev.mathalama.identityservice.domain.model.User;
import dev.mathalama.identityservice.domain.port.in.OAuthProviderUseCase;
import dev.mathalama.identityservice.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final OAuthProviderUseCase oAuthProviderUseCase;
    private final UserRepository userRepository;

    @GetMapping("")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(principal.getUsername());
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(UserMapper.toCurrentUserResponse(user));
    }

    @GetMapping("/providers")
    public ResponseEntity<List<OAuthProviderResponse>> getLinkedProviders() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(principal.getUsername());

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<OAuthProviderResponse> providers = oAuthProviderUseCase.getUserProviders(user)
                .stream()
                .map(OAuthProviderMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(providers);
    }

    @DeleteMapping("/providers/{provider}")
    public ResponseEntity<Void> unlinkProvider(@PathVariable String provider) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User principal)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(principal.getUsername());
        long providerCount = oAuthProviderUseCase.countLinkedProviders(userId);

        if (providerCount <= 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean unlinked = oAuthProviderUseCase.unlinkOAuthProvider(userId, provider.toUpperCase());

        if (!unlinked) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.noContent().build();
    }
}
