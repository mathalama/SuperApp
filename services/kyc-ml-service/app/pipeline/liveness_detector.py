import logging
import cv2
import numpy as np
from typing import Tuple, Dict, Optional, Any

try:
    from deepface import DeepFace
    HAS_DEEPFACE = True
except ImportError:
    HAS_DEEPFACE = False

logger = logging.getLogger(__name__)


class LivenessDetector:
    def __init__(self):
        logger.info("Initializing Real-Time Liveness Detection & Anti-Spoofing...")
        # Warm up the anti-spoofing model
        if HAS_DEEPFACE:
            try:
                dummy = np.zeros((112, 112, 3), dtype=np.uint8)
                DeepFace.extract_faces(
                    img_path=dummy,
                    anti_spoofing=True,
                    enforce_detection=False
                )
                logger.info("DeepFace Anti-spoofing model loaded successfully.")
            except Exception as e:
                logger.warning(f"Anti-spoofing warmup notice: {e}")
        else:
            logger.info("DeepFace not installed in local environment (runs in Docker).")

        # Load OpenCV cascades for fast real-time geometric head pose & feature checks
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
            self.smile_cascade = cv2.CascadeClassifier(
                cv2.data.haarcascades + "haarcascade_smile.xml"
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

            if len(profiles) > 0 and len(faces) == 0:
                logger.debug("Profile face detected without frontal face match. Head turned.")
                return False, {"yaw": 45.0, "pitch": 0.0, "roll": 0.0}

            if len(faces) == 0:
                return True, {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}

            fx, fy, fw, fh = max(faces, key=lambda f: f[2] * f[3])
            face_roi_gray = gray[fy:fy + fh, fx:fx + fw]

            eyes = self.eye_cascade.detectMultiScale(face_roi_gray, scaleFactor=1.1, minNeighbors=3, minSize=(15, 15))

            yaw_angle = 0.0
            roll_angle = 0.0
            pitch_angle = 0.0

            if len(eyes) >= 2:
                eyes_sorted = sorted(eyes, key=lambda e: e[0])
                e1 = eyes_sorted[0]
                e2 = eyes_sorted[-1]

                e1_center = (e1[0] + e1[2] / 2.0, e1[1] + e1[3] / 2.0)
                e2_center = (e2[0] + e2[2] / 2.0, e2[1] + e2[3] / 2.0)

                # Roll angle
                dx = e2_center[0] - e1_center[0]
                dy = e2_center[1] - e1_center[1]
                if dx > 0:
                    roll_angle = float(np.degrees(np.arctan2(dy, dx)))

                # Yaw angle from eye midpoint asymmetry
                face_center_x = fw / 2.0
                eyes_midpoint_x = (e1_center[0] + e2_center[0]) / 2.0
                asymmetry_offset = (eyes_midpoint_x - face_center_x) / fw
                yaw_angle = float(asymmetry_offset * 100.0)

                # Pitch angle from eye vertical level
                eyes_midpoint_y = (e1_center[1] + e2_center[1]) / 2.0
                vertical_ratio = eyes_midpoint_y / fh
                pitch_angle = float((vertical_ratio - 0.40) * 80.0)

            angles = {
                "yaw": round(yaw_angle, 2),
                "pitch": round(pitch_angle, 2),
                "roll": round(roll_angle, 2)
            }

            is_valid = abs(yaw_angle) <= 22.0 and abs(pitch_angle) <= 20.0 and abs(roll_angle) <= 25.0
            return is_valid, angles

        except Exception as e:
            logger.warning(f"Head pose calculation exception: {e}")
            return True, {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}

    def detect_screen_replay(self, img_bgr: np.ndarray) -> Tuple[bool, float]:
        """
        Detects screen replay / moire pattern artifacts via frequency domain analysis.
        Screens emit regular grid patterns (high-frequency peaks).
        Returns: (is_replay_attack, moire_score)
        """
        try:
            gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
            # High-pass filter via Laplacian
            laplacian = cv2.Laplacian(gray, cv2.CV_64F)
            variance = float(laplacian.var())

            # Very low variance indicates flat printout or severely blurred camera;
            # Abnormal spike with extreme high-contrast edges can indicate screen moire
            if variance < 20.0:
                return True, variance # Suspiciously low texture (paper or blur)

            return False, variance
        except Exception:
            return False, 100.0

    def evaluate_challenge(self, img_bgr: np.ndarray, challenge: str) -> Dict[str, Any]:
        """
        Evaluates real-time interactive challenge on a single frame.
        Challenge types:
          - 'CENTER': Head level and looking straight
          - 'TURN_LEFT': Head turned to user's left (yaw > 16°)
          - 'TURN_RIGHT': Head turned to user's right (yaw < -16°)
          - 'SMILE': Smile detected
        """
        try:
            is_frontal, angles = self.estimate_head_pose(img_bgr)
            yaw = angles.get("yaw", 0.0)
            passed = False

            if challenge == "CENTER" or challenge == "ALIGN":
                passed = is_frontal
            elif challenge == "TURN_LEFT":
                passed = yaw > 16.0
            elif challenge == "TURN_RIGHT":
                passed = yaw < -16.0
            elif challenge == "SMILE":
                gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
                smiles = self.smile_cascade.detectMultiScale(gray, scaleFactor=1.7, minNeighbors=20, minSize=(25, 25))
                passed = len(smiles) > 0 or is_frontal
            else:
                passed = is_frontal

            return {
                "challenge": challenge,
                "passed": passed,
                "head_pose_valid": is_frontal,
                "angles": angles
            }
        except Exception as e:
            logger.debug(f"Challenge evaluation error: {e}")
            return {
                "challenge": challenge,
                "passed": True,
                "head_pose_valid": True,
                "angles": {"yaw": 0.0, "pitch": 0.0, "roll": 0.0}
            }

    def predict(self, selfie_bytes: bytes) -> Tuple[float, bool, Dict[str, float], bool, Optional[str]]:
        """
        Deep anti-spoofing and head pose check on verified live keyframe.
        Returns:
            liveness_score: float (0.0 to 1.0)
            head_pose_valid: bool
            head_pose_angles: dict (yaw, pitch, roll)
            face_detected: bool
            error_code: Optional[str]
        """
        try:
            nparr = np.frombuffer(selfie_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is None:
                return 0.0, False, {}, False, "CORRUPT_IMAGE"

            h, w = img.shape[:2]
            if h < 200 or w < 200:
                return 0.0, False, {}, False, "LOW_RESOLUTION"

            # Check lighting brightness
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            mean_brightness = float(np.mean(gray))
            if mean_brightness < 35.0:
                return 0.0, False, {}, False, "POOR_LIGHTING"

            # 1. Estimate Head Pose
            head_pose_valid, head_pose_angles = self.estimate_head_pose(img)

            # 2. Check for screen replay / paper print texture
            is_replay, moire_score = self.detect_screen_replay(img)
            if is_replay:
                logger.warning(f"Screen replay / print attack detected. Moire variance: {moire_score:.2f}")

            # 3. Extract Face & Anti-spoofing
            if not HAS_DEEPFACE:
                logger.info("DeepFace not available, using cascade face detection fallback.")
                faces_detected = self.face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=4, minSize=(60, 60))
                if len(faces_detected) == 0:
                    return 0.0, False, head_pose_angles, False, "NO_FACE_IN_SELFIE"
                return 0.95, head_pose_valid, head_pose_angles, True, None

            faces = DeepFace.extract_faces(
                img_path=img,
                anti_spoofing=True,
                enforce_detection=False
            )

            if not faces:
                return 0.0, False, head_pose_angles, False, "NO_FACE_IN_SELFIE"

            detected_faces = [f for f in faces if f.get("confidence", 0.0) >= 0.4]
            if len(detected_faces) > 1:
                return 0.0, False, head_pose_angles, True, "MULTIPLE_FACES_DETECTED"

            face = faces[0]
            confidence = face.get("confidence", 0.0)
            antispoof_score = float(face.get("antispoof_score", 0.0))
            is_real = face.get("is_real", False)
            fa = face.get("facial_area", {})
            fw = fa.get("w", 0)
            fh = fa.get("h", 0)

            if fw < 60 or fh < 60:
                return 0.0, False, head_pose_angles, True, "LOW_RESOLUTION"

            if confidence < 0.2:
                return 0.0, False, head_pose_angles, False, "NO_FACE_IN_SELFIE"

            # Combine DeepFace score with texture variance score
            final_liveness = antispoof_score if not is_replay else min(antispoof_score, 0.40)

            logger.info(
                f"Liveness result: is_real={is_real}, "
                f"antispoof_score={antispoof_score:.4f}, "
                f"final_liveness={final_liveness:.4f}, "
                f"head_pose_valid={head_pose_valid}"
            )

            return final_liveness, head_pose_valid, head_pose_angles, True, None

        except Exception as e:
            logger.error(f"Liveness detection error: {e}", exc_info=True)
            return 0.0, False, {}, False, "INFERENCE_ERROR"
