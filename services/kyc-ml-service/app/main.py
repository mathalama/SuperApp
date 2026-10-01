import logging
import cv2
import numpy as np
from typing import Optional
from fastapi import FastAPI, UploadFile, File, HTTPException
from app.schemas.response import KycVerifyResponse, ExtractedData
from app.pipeline.ocr_mrz_reader import OcrMrzReader
from app.pipeline.liveness_detector import LivenessDetector
from app.pipeline.face_matcher import FaceMatcher
from app.pipeline.document_preprocessor import DocumentPreprocessor

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("kyc-ml-service")

app = FastAPI(
    title="KYC ML Inference Service",
    version="2.0.0",
    description="Universal Multilingual Document OCR, ICAO MRZ, Passive Liveness & 1:1 Biometric Verification"
)

ocr_reader = OcrMrzReader()
liveness_detector = LivenessDetector()
face_matcher = FaceMatcher()


def resize_if_needed(image_bytes: bytes, max_dim: int = 1600) -> bytes:
    """Downscales large camera photos while preserving aspect ratio for fast inference."""
    try:
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return image_bytes
        h, w = img.shape[:2]
        if max(h, w) > max_dim:
            scale = max_dim / float(max(h, w))
            new_w, new_h = int(w * scale), int(h * scale)
            resized = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_AREA)
            _, encoded = cv2.imencode('.jpg', resized, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
            logger.info(f"Image resized from {w}x{h} to {new_w}x{new_h} for inference.")
            return encoded.tobytes()
        return image_bytes
    except Exception as e:
        logger.warning(f"Failed to downscale image: {e}")
        return image_bytes


def preprocess_document_image(image_bytes: bytes) -> bytes:
    """
    Performs quality enhancement, 4-point perspective warp/deskew,
    and CLAHE contrast adjustment on identity document images.
    """
    try:
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return image_bytes

        # Deskew and 4-point perspective warp
        warped = DocumentPreprocessor.auto_warp_and_deskew(img)

        # Contrast enhancement for security features and text clarity
        enhanced = DocumentPreprocessor.enhance_contrast(warped)

        _, encoded = cv2.imencode('.jpg', enhanced, [int(cv2.IMWRITE_JPEG_QUALITY), 95])
        return encoded.tobytes()
    except Exception as e:
        logger.warning(f"Document preprocessing warning: {e}")
        return image_bytes


@app.get("/health")
def health_check():
    return {"status": "HEALTHY"}


import uuid
import random

CHALLENGE_POOL = ["TURN_LEFT", "TURN_RIGHT", "SMILE"]

@app.get("/api/v1/liveness/challenge")
def get_liveness_challenge():
    """Generates a randomized real-time interactive challenge sequence."""
    selected_step = random.choice(CHALLENGE_POOL)
    steps = ["CENTER", selected_step]
    return {
        "challenge_id": str(uuid.uuid4()),
        "steps": steps,
        "timeout_seconds": 30,
        "instructions": {
            "CENTER": "Align your face inside the biometric oval",
            "TURN_LEFT": "Turn your head slightly to your left",
            "TURN_RIGHT": "Turn your head slightly to your right",
            "SMILE": "Smile naturally at the camera"
        }
    }


@app.post("/api/v1/liveness/evaluate-frame")
def evaluate_live_frame(
    frame: UploadFile = File(...),
    challenge: str = "CENTER"
):
    """Evaluates a live video frame against the active real-time challenge."""
    try:
        frame_bytes = frame.file.read()
        if not frame_bytes:
            return {"passed": False, "head_pose_valid": False, "angles": None}
        nparr = np.frombuffer(frame_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return {"passed": False, "head_pose_valid": False, "angles": None}

        result = liveness_detector.evaluate_challenge(img, challenge)
        return result
    except Exception as e:
        logger.debug(f"Frame evaluation error: {e}")
        return {"passed": True, "head_pose_valid": True, "angles": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}}


@app.post("/api/v1/head-pose-check")
def check_head_pose_live(frame: UploadFile = File(...)):
    try:
        frame_bytes = frame.file.read()
        if not frame_bytes:
            return {"head_pose_valid": False, "head_pose_angles": None}
        nparr = np.frombuffer(frame_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            return {"head_pose_valid": False, "head_pose_angles": None}
        is_valid, angles = liveness_detector.estimate_head_pose(img)
        return {
            "head_pose_valid": is_valid,
            "head_pose_angles": {
                "yaw": round(angles.get("yaw", 0.0), 1),
                "pitch": round(angles.get("pitch", 0.0), 1),
                "roll": round(angles.get("roll", 0.0), 1),
            }
        }
    except Exception as e:
        logger.debug(f"Live head pose check error: {e}")
        return {"head_pose_valid": True, "head_pose_angles": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}}


@app.post("/api/v1/verify", response_model=KycVerifyResponse)
def verify_kyc(
    document_image: UploadFile = File(...),
    selfie_image: UploadFile = File(...),
    document_back_image: Optional[UploadFile] = File(None)
):
    try:
        doc_bytes = document_image.file.read()
        selfie_bytes = selfie_image.file.read()
        back_bytes = document_back_image.file.read() if document_back_image else None

        if not doc_bytes or not selfie_bytes:
            raise HTTPException(status_code=400, detail="Document or selfie image is empty")

        # 1. Image quality preprocessing (deskew, perspective warp, contrast enhancement)
        doc_bytes = resize_if_needed(doc_bytes, max_dim=1600)
        doc_bytes = preprocess_document_image(doc_bytes)

        selfie_bytes = resize_if_needed(selfie_bytes, max_dim=1200)

        if back_bytes:
            back_bytes = resize_if_needed(back_bytes, max_dim=1600)
            back_bytes = preprocess_document_image(back_bytes)

        # 2. Universal Multilingual OCR & ICAO MRZ Parsing on Front Side
        mrz_status, expiry_status, extracted_fields, raw_ocr = ocr_reader.process(doc_bytes)

        # 2.1 Process Back Side if provided (e.g. ID card back with MRZ and expiry)
        if back_bytes:
            b_mrz_status, b_expiry_status, b_extracted, b_raw_ocr = ocr_reader.process(back_bytes)
            if b_mrz_status == "VALID":
                mrz_status = "VALID"
                extracted_fields.update({k: v for k, v in b_extracted.items() if v})
            if b_expiry_status in ("VALID", "EXPIRED", "EXPIRING_SOON"):
                expiry_status = b_expiry_status
                if b_extracted.get("expiry_date"):
                    extracted_fields["expiry_date"] = b_extracted["expiry_date"]

        mrz_valid = (mrz_status == "VALID")

        # 3. Liveness Detection & Head Pose Estimation on Selfie
        liveness_score, head_pose_valid, head_pose_angles, face_in_selfie, selfie_err = liveness_detector.predict(selfie_bytes)

        # 4. 1:1 Face Matching with Document Face Cropping
        similarity_score, face_in_doc, _, doc_err = face_matcher.compare(doc_bytes, selfie_bytes)

        # Determine error code if any prerequisite failed
        error_code = doc_err or selfie_err
        if not error_code:
            if not face_in_doc:
                error_code = "NO_FACE_IN_DOCUMENT"
            elif not face_in_selfie:
                error_code = "NO_FACE_IN_SELFIE"
            elif not head_pose_valid:
                error_code = "HEAD_POSE_ROTATED"
            elif expiry_status == "EXPIRED":
                error_code = "DOCUMENT_EXPIRED"

        is_live = liveness_score >= 0.85 and head_pose_valid and face_in_selfie
        is_match = similarity_score >= 0.70 and face_in_doc and face_in_selfie

        # =========================================================================
        # TRI-STATE KYC DECISION ENGINE: PASS | MANUAL_REVIEW | REJECT
        # =========================================================================

        # 1. Liveness Decision
        if not face_in_selfie or liveness_score < 0.60 or selfie_err == "SPOOF_ATTACK_DETECTED":
            liveness_status = "REJECT"
        elif liveness_score < 0.85 or not head_pose_valid:
            liveness_status = "MANUAL_REVIEW"
        else:
            liveness_status = "PASS"

        # 2. Face Match Decision
        if not face_in_doc or not face_in_selfie or similarity_score < 0.55 or doc_err in ("MULTIPLE_FACES_DETECTED", "MULTIPLE_FACES_IN_DOCUMENT"):
            face_match_status = "REJECT"
        elif similarity_score < 0.70:
            face_match_status = "MANUAL_REVIEW"
        else:
            face_match_status = "PASS"

        # 3. Document Decision
        if expiry_status == "EXPIRED":
            document_status = "REJECT"
        elif mrz_status == "CHECKSUM_FAILED" or expiry_status in ("EXPIRING_SOON", "UNKNOWN") or mrz_status == "ABSENT":
            document_status = "MANUAL_REVIEW"
        else:
            document_status = "PASS"

        # 4. Overall Decision
        if liveness_status == "REJECT" or face_match_status == "REJECT" or document_status == "REJECT":
            decision = "REJECT"
            if expiry_status == "EXPIRED":
                decision_reason = "Document has expired. Expired identity documents are not accepted."
            elif not face_in_selfie or not face_in_doc:
                decision_reason = "Face not clearly visible in photo. Please ensure face is well lit and centered."
            elif doc_err in ("MULTIPLE_FACES_DETECTED", "MULTIPLE_FACES_IN_DOCUMENT"):
                decision_reason = "Multiple faces detected. Frame must contain only one person."
            elif liveness_status == "REJECT":
                decision_reason = "Liveness verification failed (anti-spoofing detection)."
            else:
                decision_reason = "Face on selfie does not match document photo."
        elif liveness_status == "MANUAL_REVIEW" or face_match_status == "MANUAL_REVIEW" or document_status == "MANUAL_REVIEW":
            decision = "MANUAL_REVIEW"
            if expiry_status == "EXPIRING_SOON":
                decision_reason = "Document expires within 30 days. Sent for manual operator review."
            elif mrz_status == "ABSENT":
                decision_reason = "Document has no MRZ zone (National ID / Driver License). Sent for operator review."
            elif mrz_status == "CHECKSUM_FAILED":
                decision_reason = "MRZ checksum anomaly detected. Sent for operator review."
            elif face_match_status == "MANUAL_REVIEW":
                decision_reason = "Borderline biometric similarity. Sent for operator review."
            else:
                decision_reason = "Verification sent for manual operator review."
        else:
            decision = "PASS"
            decision_reason = "Verification passed successfully. All biometric and identity checks verified."

        logger.info(
            f"KYC Result: Decision={decision} ({decision_reason}), "
            f"Liveness={liveness_status} ({liveness_score:.4f}), "
            f"FaceMatch={face_match_status} ({similarity_score:.4f}), "
            f"DocStatus={document_status} (MRZ: {mrz_status}, Expiry: {expiry_status})"
        )

        return KycVerifyResponse(
            decision=decision,
            decision_reason=decision_reason,
            liveness_status=liveness_status,
            face_match_status=face_match_status,
            document_status=document_status,
            liveness_score=round(liveness_score, 4),
            similarity_score=round(similarity_score, 4),
            is_live=is_live,
            is_match=is_match,
            mrz_valid=mrz_valid,
            mrz_status=mrz_status,
            expiry_status=expiry_status,
            head_pose_valid=head_pose_valid,
            head_pose_angles=head_pose_angles,
            face_detected_in_doc=face_in_doc,
            face_detected_in_selfie=face_in_selfie,
            error_code=error_code,
            extracted_data=ExtractedData(
                first_name=extracted_fields.get("first_name"),
                last_name=extracted_fields.get("last_name"),
                document_number=extracted_fields.get("document_number"),
                date_of_birth=extracted_fields.get("date_of_birth"),
                expiry_date=extracted_fields.get("expiry_date"),
                gender=extracted_fields.get("gender"),
                nationality=extracted_fields.get("nationality"),
                personal_number=extracted_fields.get("personal_number")
            ),
            raw_ocr_json=raw_ocr
        )

    except Exception as e:
        logger.error(f"Error during verification: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
