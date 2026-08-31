package dev.mathalama.kycservice.domain.port.out;

import dev.mathalama.kycservice.infrastructure.client.dto.MlInferenceResponse;

public interface KycInferencePort {
    MlInferenceResponse processKyc(byte[] documentFrontBytes, byte[] selfieBytes, byte[] documentBackBytes);
}
