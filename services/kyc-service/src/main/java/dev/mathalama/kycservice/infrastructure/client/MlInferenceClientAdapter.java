package dev.mathalama.kycservice.infrastructure.client;

import dev.mathalama.kycservice.domain.port.out.KycInferencePort;
import dev.mathalama.kycservice.infrastructure.client.dto.MlInferenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class MlInferenceClientAdapter implements KycInferencePort {

    private final RestClient mlRestClient;

    @Override
    public MlInferenceResponse processKyc(byte[] documentFrontBytes, byte[] selfieBytes, byte[] documentBackBytes) {
        log.info("Sending document and selfie to ML inference service");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("document_image", new NamedByteArrayResource(documentFrontBytes, "document.jpg"));
        body.add("selfie_image", new NamedByteArrayResource(selfieBytes, "selfie.jpg"));
        if (documentBackBytes != null && documentBackBytes.length > 0) {
            body.add("document_back_image", new NamedByteArrayResource(documentBackBytes, "document_back.jpg"));
        }

        return mlRestClient.post()
                .uri("/api/v1/verify")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(MlInferenceResponse.class);
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        public NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }
}
