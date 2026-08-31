package dev.mathalama.kycservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlInferenceResponse {
    @JsonProperty("liveness_score")
    private Double livenessScore;

    @JsonProperty("similarity_score")
    private Double similarityScore;

    @JsonProperty("is_live")
    private Boolean isLive;

    @JsonProperty("is_match")
    private Boolean isMatch;

    @JsonProperty("mrz_valid")
    private Boolean mrzValid;

    @JsonProperty("mrz_status")
    private String mrzStatus;

    @JsonProperty("expiry_status")
    private String expiryStatus;

    @JsonProperty("head_pose_valid")
    private Boolean headPoseValid;

    @JsonProperty("face_detected_in_doc")
    private Boolean faceDetectedInDoc;

    @JsonProperty("face_detected_in_selfie")
    private Boolean faceDetectedInSelfie;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("extracted_data")
    private MlExtractedData extractedData;

    @JsonProperty("raw_ocr_json")
    private String rawOcrJson;
}
