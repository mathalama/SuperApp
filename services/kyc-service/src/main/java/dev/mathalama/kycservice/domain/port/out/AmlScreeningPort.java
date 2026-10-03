package dev.mathalama.kycservice.domain.port.out;

import lombok.Builder;
import lombok.Data;

public interface AmlScreeningPort {

    AmlCheckResult screen(String firstName, String lastName, String nationality, String documentNumber);

    @Data
    @Builder
    class AmlCheckResult {
        private boolean flagged;
        private String riskLevel; // LOW, MEDIUM, HIGH
        private String matchDetails;
    }
}
