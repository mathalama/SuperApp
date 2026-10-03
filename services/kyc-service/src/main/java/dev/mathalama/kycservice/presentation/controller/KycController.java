package dev.mathalama.kycservice.presentation.controller;

import dev.mathalama.kycservice.application.dto.request.SubmitKycRequest;
import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;
import dev.mathalama.kycservice.domain.port.in.KycUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycUseCase kycUseCase;

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycApplicationResponse> submitKyc(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @Valid @ModelAttribute SubmitKycRequest request) {

        KycApplicationResponse response = kycUseCase.submitApplication(UUID.fromString(userId), userEmail, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<KycApplicationResponse> getMyKycStatus(
            @RequestHeader("X-User-Id") String userId) {

        return kycUseCase.getLatestApplicationByUserId(UUID.fromString(userId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<KycApplicationResponse> getKycById(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @PathVariable UUID applicationId) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID requestUserId = UUID.fromString(userIdHeader);
        boolean isAdmin = rolesHeader != null && (rolesHeader.contains("ADMIN") || rolesHeader.contains("ROLE_ADMIN"));

        return kycUseCase.getApplicationById(applicationId)
                .map(app -> {
                    if (!isAdmin && !app.getUserId().equals(requestUserId)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<KycApplicationResponse>build();
                    }
                    return ResponseEntity.ok(app);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
