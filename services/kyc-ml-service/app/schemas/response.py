from pydantic import BaseModel
from typing import Optional, Dict, Literal

class ExtractedData(BaseModel):
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    document_number: Optional[str] = None
    date_of_birth: Optional[str] = None
    expiry_date: Optional[str] = None
    gender: Optional[str] = None
    nationality: Optional[str] = None
    personal_number: Optional[str] = None

class KycVerifyResponse(BaseModel):
    # Tri-state KYC decisions: PASS, MANUAL_REVIEW, REJECT
    decision: Literal["PASS", "MANUAL_REVIEW", "REJECT"]
    decision_reason: str
    liveness_status: Literal["PASS", "MANUAL_REVIEW", "REJECT"]
    face_match_status: Literal["PASS", "MANUAL_REVIEW", "REJECT"]
    document_status: Literal["PASS", "MANUAL_REVIEW", "REJECT"]

    # Biometric & OCR Telemetry metrics
    liveness_score: float
    similarity_score: float
    is_live: bool
    is_match: bool
    mrz_valid: bool
    mrz_status: str = "ABSENT"
    expiry_status: str = "UNKNOWN"
    head_pose_valid: bool = True
    head_pose_angles: Optional[Dict[str, float]] = None
    face_detected_in_doc: bool = True
    face_detected_in_selfie: bool = True
    error_code: Optional[str] = None
    extracted_data: ExtractedData
    raw_ocr_json: Optional[str] = None
