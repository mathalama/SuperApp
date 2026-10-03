package dev.mathalama.kycservice.application.dto.request;

import dev.mathalama.kycservice.domain.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReviewRequest {

    @NotNull(message = "Decision is required (VERIFIED or REJECTED)")
    private KycStatus decision;

    private String notes;
}
