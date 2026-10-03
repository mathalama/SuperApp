package dev.mathalama.kycservice.infrastructure.aml;

import dev.mathalama.kycservice.domain.port.out.AmlScreeningPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class RuleBasedAmlScreeningAdapter implements AmlScreeningPort {

    // High-risk sanctioned territories / jurisdictions per OFAC / FATF high-risk monitor
    private static final Set<String> HIGH_RISK_JURISDICTIONS = Set.of(
            "PRK", "KP", "NORTH KOREA",
            "IRN", "IR", "IRAN",
            "SYR", "SY", "SYRIA",
            "CUB", "CU", "CUBA"
    );

    // Known watchwords and synthetic test sanction entities
    private static final Set<String> SANCTIONED_NAMES = Set.of(
            "OSAMA BIN LADEN",
            "VLADIMIR PUTIN",
            "KIM JONG UN",
            "TEST SANCTIONED",
            "BLOCKED PERSON"
    );

    @Override
    public AmlCheckResult screen(String firstName, String lastName, String nationality, String documentNumber) {
        String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : ""))
                .trim()
                .toUpperCase(Locale.ROOT);

        log.debug("Screening person: '{}', nat: '{}', doc: '{}'", fullName, nationality, documentNumber);

        // 1. Check sanctioned individuals watchlist
        if (!fullName.isEmpty()) {
            for (String sanctioned : SANCTIONED_NAMES) {
                if (fullName.contains(sanctioned)) {
                    log.warn("AML ALERT: Direct match on sanctions watchlist for '{}'", fullName);
                    return AmlCheckResult.builder()
                            .flagged(true)
                            .riskLevel("HIGH")
                            .matchDetails("Match against international sanctions watchlist: " + sanctioned)
                            .build();
                }
            }
        }

        // 2. Check sanctioned jurisdictions / country codes
        if (nationality != null && !nationality.isBlank()) {
            String normNat = nationality.trim().toUpperCase(Locale.ROOT);
            if (HIGH_RISK_JURISDICTIONS.contains(normNat)) {
                log.warn("AML ALERT: High-risk jurisdiction detected for nationality '{}'", nationality);
                return AmlCheckResult.builder()
                        .flagged(true)
                        .riskLevel("HIGH")
                        .matchDetails("Jurisdiction under comprehensive FATF/OFAC sanctions: " + nationality)
                        .build();
            }
        }

        // 3. Document number fraud heuristics (e.g. repeated digits like 00000000 or 12345678)
        if (documentNumber != null && !documentNumber.isBlank()) {
            String normDoc = documentNumber.replaceAll("[^A-Za-z0-9]", "");
            if (normDoc.matches("^0{6,}$") || normDoc.matches("^1{6,}$") || "12345678".equalsIgnoreCase(normDoc)) {
                log.warn("AML ALERT: Suspicious document number pattern '{}'", documentNumber);
                return AmlCheckResult.builder()
                        .flagged(true)
                        .riskLevel("MEDIUM")
                        .matchDetails("Suspicious/sequential document number pattern: " + documentNumber)
                        .build();
            }
        }

        return AmlCheckResult.builder()
                .flagged(false)
                .riskLevel("LOW")
                .matchDetails("No adverse findings or sanctions matches.")
                .build();
    }
}
