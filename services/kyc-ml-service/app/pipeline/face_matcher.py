import logging
import cv2
import numpy as np
from deepface import DeepFace
from typing import Tuple, Optional

logger = logging.getLogger(__name__)


class FaceMatcher:
    def __init__(self):
        logger.info("Initializing Face Matcher (DeepFace ArcFace 1:1 Verification & Doc Face Cropper)...")
        try:
            self.face_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
            )
        except Exception as e:
            logger.warning(f"Cascade load error in FaceMatcher: {e}")

        # Warm up the model
        try:
            dummy = np.zeros((112, 112, 3), dtype=np.uint8)
            DeepFace.represent(
                img_path=dummy,
                model_name="ArcFace",
                enforce_detection=False
            )
            logger.info("ArcFace model loaded successfully.")
        except Exception as e:
            logger.warning(f"ArcFace warmup notice: {e}")

    def extract_face_crop(self, img_bgr: np.ndarray, margin: float = 0.20) -> Tuple[Optional[np.ndarray], bool]:
        """
        Locates the person's photo on a document/passport and crops it with a 20% margin.
        Returns: (cropped_img_or_none, has_multiple_faces)
        """
        try:
            h, w = img_bgr.shape[:2]
            gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
            faces = self.face_cascade.detectMultiScale(
                gray, scaleFactor=1.1, minNeighbors=4, minSize=(50, 50)
            )

            if len(faces) > 1:
                logger.warning(f"Multiple faces detected in document via cascade: {len(faces)}")
                return None, True

            fx, fy, fw, fh = 0, 0, 0, 0

            if len(faces) == 1:
                fx, fy, fw, fh = faces[0]
            else:
                # Fallback to DeepFace extract_faces
                df_faces = DeepFace.extract_faces(img_path=img_bgr, enforce_detection=False)
                high_conf = [f for f in df_faces if f.get("confidence", 0.0) >= 0.35]
                if len(high_conf) > 1:
                    logger.warning(f"Multiple faces detected in document via DeepFace: {len(high_conf)}")
                    return None, True

                if df_faces and len(df_faces) > 0:
                    conf = df_faces[0].get("confidence", 0.0)
                    if conf >= 0.25:
                        fa = df_faces[0]["facial_area"]
                        fx, fy, fw, fh = fa["x"], fa["y"], fa["w"], fa["h"]
                    else:
                        return None, False
                else:
                    return None, False

            pad_w = int(fw * margin)
            pad_h = int(fh * margin)
            x1 = max(0, fx - pad_w)
            y1 = max(0, fy - pad_h)
            x2 = min(w, fx + fw + pad_w)
            y2 = min(h, fy + fh + pad_h)

            crop = img_bgr[y1:y2, x1:x2]
            if crop.size == 0 or crop.shape[0] < 40 or crop.shape[1] < 40:
                return None, False

            return crop, False

        except Exception as e:
            logger.warning(f"Face crop extraction exception: {e}")
            return None, False

    def compare(self, doc_bytes: bytes, selfie_bytes: bytes) -> Tuple[float, bool, bool, Optional[str]]:
        """
        Compares face from document photo with selfie photo.
        Returns:
            similarity: float (0.0 to 1.0)
            face_detected_in_doc: bool
            face_detected_in_selfie: bool
            error_code: Optional[str]
        """
        try:
            doc_arr = np.frombuffer(doc_bytes, np.uint8)
            selfie_arr = np.frombuffer(selfie_bytes, np.uint8)

            doc_img = cv2.imdecode(doc_arr, cv2.IMREAD_COLOR)
            selfie_img = cv2.imdecode(selfie_arr, cv2.IMREAD_COLOR)

            if doc_img is None or selfie_img is None:
                logger.error("Failed to decode images for face match")
                return 0.0, False, False, "CORRUPT_IMAGE"

            dh, dw = doc_img.shape[:2]
            if dh < 200 or dw < 200:
                logger.warning(f"Document resolution too low: {dw}x{dh}")
                return 0.0, False, False, "LOW_RESOLUTION"

            # 1. Isolate and crop face from the document
            doc_face_crop, doc_multi_faces = self.extract_face_crop(doc_img)
            if doc_multi_faces:
                return 0.0, False, True, "MULTIPLE_FACES_IN_DOCUMENT"

            face_detected_in_doc = doc_face_crop is not None
            img1_to_compare = doc_face_crop if face_detected_in_doc else doc_img

            # 2. Check face in selfie
            selfie_face_crop, selfie_multi_faces = self.extract_face_crop(selfie_img, margin=0.15)
            if selfie_multi_faces:
                return 0.0, face_detected_in_doc, False, "MULTIPLE_FACES_DETECTED"

            face_detected_in_selfie = selfie_face_crop is not None
            img2_to_compare = selfie_face_crop if face_detected_in_selfie else selfie_img

            if not face_detected_in_doc:
                logger.warning("No face detected in document image")
                return 0.0, False, face_detected_in_selfie, "NO_FACE_IN_DOCUMENT"

            if not face_detected_in_selfie:
                logger.warning("No face detected in selfie image")
                return 0.0, face_detected_in_doc, False, "NO_FACE_IN_SELFIE"

            result = DeepFace.verify(
                img1_path=img1_to_compare,
                img2_path=img2_to_compare,
                model_name="ArcFace",
                enforce_detection=False
            )

            # DeepFace returns cosine distance (0 = identical, 1 = different)
            distance = result.get("distance", 1.0)
            threshold = result.get("threshold", 0.68)
            verified = result.get("verified", False)

            similarity = max(0.0, min(1.0, 1.0 - distance))

            logger.info(
                f"Face match result: distance={distance:.4f}, "
                f"similarity={similarity:.4f}, "
                f"threshold={threshold}, verified={verified}"
            )

            return float(similarity), True, True, None

        except Exception as e:
            logger.error(f"Face matching error: {e}", exc_info=True)
            return 0.0, False, False, "INFERENCE_ERROR"

