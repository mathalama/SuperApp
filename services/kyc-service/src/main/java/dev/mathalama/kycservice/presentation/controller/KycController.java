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
            @Valid @ModelAttribute SubmitKycRequest request) {

        KycApplicationResponse response = kycUseCase.submitApplication(UUID.fromString(userId), request);
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
    public ResponseEntity<KycApplicationResponse> getKycById(@PathVariable UUID applicationId) {
        return kycUseCase.getApplicationById(applicationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
