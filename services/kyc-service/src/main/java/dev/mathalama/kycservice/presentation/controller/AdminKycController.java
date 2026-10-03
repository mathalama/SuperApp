package dev.mathalama.kycservice.presentation.controller;

import dev.mathalama.kycservice.application.dto.request.AdminReviewRequest;
import dev.mathalama.kycservice.application.dto.response.KycApplicationResponse;
import dev.mathalama.kycservice.domain.enums.KycStatus;
import dev.mathalama.kycservice.domain.port.in.KycUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/kyc/admin")
@RequiredArgsConstructor
public class AdminKycController {

    private final KycUseCase kycUseCase;

    @GetMapping("/applications")
    public ResponseEntity<Page<KycApplicationResponse>> getApplications(
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestParam(value = "status", required = false) KycStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        if (!isAdmin(rolesHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<KycApplicationResponse> result = kycUseCase.getAllApplications(status, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/applications/{applicationId}/decision")
    public ResponseEntity<KycApplicationResponse> reviewApplication(
            @RequestHeader(value = "X-User-Roles", required = false) String rolesHeader,
            @RequestHeader(value = "X-User-Name", required = false) String adminUserName,
            @RequestHeader(value = "X-User-Id", required = false) String adminUserId,
            @PathVariable UUID applicationId,
            @Valid @RequestBody AdminReviewRequest request) {

        if (!isAdmin(rolesHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String reviewer = (adminUserName != null && !adminUserName.isBlank())
                ? adminUserName
                : (adminUserId != null ? adminUserId : "admin");

        KycApplicationResponse response = kycUseCase.reviewApplication(applicationId, reviewer, request);
        return ResponseEntity.ok(response);
    }

    private boolean isAdmin(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return false;
        }
        return rolesHeader.contains("ADMIN") || rolesHeader.contains("ROLE_ADMIN");
    }
}
