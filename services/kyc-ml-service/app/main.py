import logging
import cv2
import numpy as np
from typing import Optional
from fastapi import FastAPI, UploadFile, File, HTTPException
from app.schemas.response import KycVerifyResponse, ExtractedData
from app.pipeline.ocr_mrz_reader import OcrMrzReader
from app.pipeline.liveness_detector import LivenessDetector
from app.pipeline.face_matcher import FaceMatcher

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("kyc-ml-service")

app = FastAPI(
    title="KYC ML Inference Service",
    version="1.1.0",
    description="Microservice for Document OCR, Passive Liveness & 1:1 Face Matching with Head Pose & Face Cropping"
)

ocr_reader = OcrMrzReader()
liveness_detector = LivenessDetector()
face_matcher = FaceMatcher()


def resize_if_needed(image_bytes: bytes, max_dim: int = 1600) -> bytes:
    """Downscale very large mobile camera photos to max_dim to speed up OCR and inference."""
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
            logger.info(f"Image resized from {w}x{h} to {new_w}x{new_h} for fast inference.")
            return encoded.tobytes()
        return image_bytes
    except Exception as e:
        logger.warning(f"Failed to downscale image: {e}")
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
    """Evaluates a live video frame against the active real-time challenge in a worker thread."""
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

        # Fast downscale if mobile camera photo is huge (e.g. 12-48 megapixels)
        doc_bytes = resize_if_needed(doc_bytes, max_dim=1600)
        selfie_bytes = resize_if_needed(selfie_bytes, max_dim=1200)
        if back_bytes:
            back_bytes = resize_if_needed(back_bytes, max_dim=1600)

        # 1. OCR & MRZ Parsing on Front Side
        mrz_status, expiry_status, extracted_fields, raw_ocr = ocr_reader.process(doc_bytes)

        # 1.1 If back side is provided (e.g. ID card back with MRZ and expiry), process it too
        if back_bytes:
            b_mrz_status, b_expiry_status, b_extracted, b_raw_ocr = ocr_reader.process(back_bytes)
            if b_mrz_status == "VALID":
                mrz_status = "VALID"
                extracted_fields.update({k: v for k, v in b_extracted.items() if v})
            if b_expiry_status in ("VALID", "EXPIRED", "EXPIRING_SOON"):
                expiry_status = b_expiry_status
                if b_extracted.get("expiry_date"):
                    extracted_fields["expiry_date"] = b_extracted["expiry_date"]

        mrz_valid = mrz_status == "VALID"

        # 2. Liveness Detection & Head Pose Estimation on Selfie
        liveness_score, head_pose_valid, head_pose_angles, face_in_selfie, selfie_err = liveness_detector.predict(selfie_bytes)

        # 3. 1:1 Face Matching with Document Face Cropping
        similarity_score, face_in_doc, _, doc_err = face_matcher.compare(doc_bytes, selfie_bytes)

        # Determine error code if any prerequisite failed (priority order)
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

        logger.info(
            f"KYC Processed: Live={liveness_score:.4f}, Match={similarity_score:.4f}, "
            f"MRZStatus={mrz_status}, ExpiryStatus={expiry_status}, "
            f"HeadPoseValid={head_pose_valid}, FaceDoc={face_in_doc}, "
            f"FaceSelfie={face_in_selfie}, Err={error_code}"
        )

        return KycVerifyResponse(
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
                nationality=extracted_fields.get("nationality")
            ),
            raw_ocr_json=raw_ocr
        )

    except Exception as e:
        logger.error(f"Error during verification: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))
