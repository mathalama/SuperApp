import logging
import cv2
import numpy as np
from deepface import DeepFace
from typing import Tuple, Dict, Optional

logger = logging.getLogger(__name__)


class LivenessDetector:
    def __init__(self):
        logger.info("Initializing Passive Liveness Detection & Head Pose Estimator...")
        # Warm up the anti-spoofing model
        try:
            dummy = np.zeros((112, 112, 3), dtype=np.uint8)
            DeepFace.extract_faces(
                img_path=dummy,
                anti_spoofing=True,
                enforce_detection=False
            )
            logger.info("Anti-spoofing model loaded successfully.")
        except Exception as e:
            logger.warning(f"Anti-spoofing warmup notice: {e}")

        # Load standard OpenCV cascades for geometric head pose checks
        try:
            self.face_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
            )
            self.profile_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + "haarcascade_profileface.xml"
            )
            self.eye_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + "haarcascade_eye.xml"
            )
        except Exception as e:
            logger.warning(f"Cascade load error: {e}")

    def estimate_head_pose(self, img_bgr: np.ndarray) -> Tuple[bool, Dict[str, float]]:
        """
        Estimates whether the user's face is oriented frontally.
        Returns (is_valid_frontal, angles_dict).
        Angles: yaw (left-right), pitch (up-down), roll (tilt).
        """
        try:
            gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
            faces = self.face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(60, 60))
            profiles = self.profile_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(60, 60))

            # If profile cascade detected strong profile and frontal didn't or profile is dominant
            if len(profiles) > 0 and len(faces) == 0:
                logger.warning("Profile face detected without frontal face match. Head turned sideways.")
                return False, {"yaw": 45.0, "pitch": 0.0, "roll": 0.0}

            if len(faces) == 0:
                # If no cascade face found, let DeepFace evaluate
                return True, {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}

            # Take the largest frontal face
            fx, fy, fw, fh = max(faces, key=lambda f: f[2] * f[3])
            face_roi_gray = gray[fy:fy + fh, fx:fx + fw]

            eyes = self.eye_cascade.detectMultiScale(face_roi_gray, scaleFactor=1.1, minNeighbors=3, minSize=(15, 15))

            yaw_angle = 0.0
            roll_angle = 0.0
            pitch_angle = 0.0

            if len(eyes) >= 2:
                # Sort eyes by X coordinate (left eye, right eye)
                eyes_sorted = sorted(eyes, key=lambda e: e[0])
                e1 = eyes_sorted[0]  # Left eye in image (right eye of person)
                e2 = eyes_sorted[-1] # Right eye in image (left eye of person)

                e1_center = (e1[0] + e1[2] / 2.0, e1[1] + e1[3] / 2.0)
                e2_center = (e2[0] + e2[2] / 2.0, e2[1] + e2[3] / 2.0)

                # Roll: Angle of the eye line
                dx = e2_center[0] - e1_center[0]
                dy = e2_center[1] - e1_center[1]
                if dx > 0:
                    roll_angle = float(np.degrees(np.arctan2(dy, dx)))

                # Yaw estimation based on eye symmetry relative to face bounding box center
                face_center_x = fw / 2.0
                eyes_midpoint_x = (e1_center[0] + e2_center[0]) / 2.0
                asymmetry_offset = (eyes_midpoint_x - face_center_x) / fw
                yaw_angle = float(asymmetry_offset * 100.0)

                # Pitch estimation based on vertical eye level
                eyes_midpoint_y = (e1_center[1] + e2_center[1]) / 2.0
                vertical_ratio = eyes_midpoint_y / fh
                # Typical eye level is 0.35 - 0.45 of face height
                pitch_angle = float((vertical_ratio - 0.40) * 80.0)

            angles = {
                "yaw": round(yaw_angle, 2),
                "pitch": round(pitch_angle, 2),
                "roll": round(roll_angle, 2)
            }

            # Validation thresholds: yaw < 20°, pitch < 20°, roll < 25°
            is_valid = abs(yaw_angle) <= 22.0 and abs(pitch_angle) <= 20.0 and abs(roll_angle) <= 25.0

            if not is_valid:
                logger.warning(f"Head pose check failed: {angles}")

            return is_valid, angles

        except Exception as e:
            logger.warning(f"Head pose calculation exception: {e}")
            return True, {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}

    def predict(self, selfie_bytes: bytes) -> Tuple[float, bool, Dict[str, float], bool, Optional[str]]:
        """
        Passive anti-spoofing and head pose check on a selfie image.
        Returns:
            liveness_score: float (0.0 to 1.0)
            head_pose_valid: bool
            head_pose_angles: dict (yaw, pitch, roll)
            face_detected: bool
            error_code: Optional[str] ("POOR_LIGHTING", "LOW_RESOLUTION", "MULTIPLE_FACES_DETECTED", etc.)
        """
        try:
            nparr = np.frombuffer(selfie_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is None:
                logger.error("Failed to decode selfie image")
                return 0.0, False, {}, False, "CORRUPT_IMAGE"

            h, w = img.shape[:2]
            if h < 200 or w < 200:
                logger.warning(f"Selfie image resolution too low: {w}x{h}")
                return 0.0, False, {}, False, "LOW_RESOLUTION"

            # Check lighting brightness
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            mean_brightness = float(np.mean(gray))
            if mean_brightness < 35.0:
                logger.warning(f"Selfie too dark. Mean brightness: {mean_brightness:.1f}")
                return 0.0, False, {}, False, "POOR_LIGHTING"

            # 1. Estimate Head Pose (Frontal Alignment)
            head_pose_valid, head_pose_angles = self.estimate_head_pose(img)

            # 2. Extract Face & Anti-spoofing
            faces = DeepFace.extract_faces(
                img_path=img,
                anti_spoofing=True,
                enforce_detection=False
            )

            if not faces:
                logger.warning("No face detected in selfie for liveness check")
                return 0.0, False, head_pose_angles, False, "NO_FACE_IN_SELFIE"

            # Multiple faces check
            detected_faces = [f for f in faces if f.get("confidence", 0.0) >= 0.4]
            if len(detected_faces) > 1:
                logger.warning(f"Multiple faces detected in selfie: {len(detected_faces)}")
                return 0.0, False, head_pose_angles, True, "MULTIPLE_FACES_DETECTED"

            face = faces[0]
            confidence = face.get("confidence", 0.0)
            antispoof_score = face.get("antispoof_score", 0.0)
            is_real = face.get("is_real", False)
            fa = face.get("facial_area", {})
            fw = fa.get("w", 0)
            fh = fa.get("h", 0)

            if fw < 60 or fh < 60:
                logger.warning(f"Face bounding box too small: {fw}x{fh}")
                return 0.0, False, head_pose_angles, True, "LOW_RESOLUTION"

            logger.info(
                f"Liveness result: is_real={is_real}, "
                f"antispoof_score={antispoof_score:.4f}, "
                f"face_confidence={confidence:.4f}, "
                f"head_pose_valid={head_pose_valid}"
            )

            # If face detector confidence is negligible, no clear face was found
            if confidence < 0.2:
                logger.warning(f"No clear face found, confidence: {confidence:.4f}")
                return 0.0, False, head_pose_angles, False, "NO_FACE_IN_SELFIE"

            final_liveness = float(antispoof_score)

            return final_liveness, head_pose_valid, head_pose_angles, True, None

        except Exception as e:
            logger.error(f"Liveness detection error: {e}", exc_info=True)
            return 0.0, False, {}, False, "INFERENCE_ERROR"
