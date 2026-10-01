import cv2
import numpy as np
import logging
from typing import Tuple, Optional, Dict, Any

logger = logging.getLogger(__name__)


class DocumentPreprocessor:
    """
    Quality gate & geometric normalization for identity documents:
    - Detects blur (defocus / motion)
    - Detects specular glare / camera flash reflections
    - Performs 4-point perspective warp and deskew
    """

    BLUR_THRESHOLD = 65.0        # Laplacian variance threshold
    GLARE_PIXEL_THRESHOLD = 250  # 8-bit brightness threshold in L-channel
    GLARE_AREA_MAX_RATIO = 0.06  # Maximum allowable glare area (6%)
    MIN_BRIGHTNESS = 38.0        # Minimum mean brightness

    @classmethod
    def check_quality(cls, img_bgr: np.ndarray) -> Tuple[bool, Optional[str], Dict[str, Any]]:
        """
        Evaluates document image quality before heavy neural OCR inference.
        Returns: (is_good_quality, error_code, metrics)
        """
        if img_bgr is None or img_bgr.size == 0:
            return False, "CORRUPT_IMAGE", {}

        h, w = img_bgr.shape[:2]
        if h < 200 or w < 200:
            return False, "LOW_RESOLUTION", {"width": w, "height": h}

        gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)

        # 1. Lighting brightness check
        mean_brightness = float(np.mean(gray))
        if mean_brightness < cls.MIN_BRIGHTNESS:
            logger.warning(f"Document too dark. Mean brightness: {mean_brightness:.1f}")
            return False, "POOR_LIGHTING", {"brightness": mean_brightness}

        # 2. Defocus / Motion Blur check via Laplacian variance
        laplacian = cv2.Laplacian(gray, cv2.CV_64F)
        blur_score = float(laplacian.var())
        if blur_score < cls.BLUR_THRESHOLD:
            logger.warning(f"Document image is blurry. Variance: {blur_score:.2f} < {cls.BLUR_THRESHOLD}")
            return False, "DOCUMENT_BLURRY", {"blur_score": blur_score, "brightness": mean_brightness}

        # 3. Specular Glare / Flash reflection check via HLS Lightness
        hls = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2HLS)
        l_channel = hls[:, :, 1]
        glare_mask = l_channel >= cls.GLARE_PIXEL_THRESHOLD
        glare_ratio = float(np.sum(glare_mask) / (h * w))

        if glare_ratio > cls.GLARE_AREA_MAX_RATIO:
            logger.warning(f"Severe document glare detected: {glare_ratio * 100:.1f}% > {cls.GLARE_AREA_MAX_RATIO * 100}%")
            return False, "DOCUMENT_GLARE", {
                "glare_ratio": glare_ratio,
                "blur_score": blur_score,
                "brightness": mean_brightness
            }

        return True, None, {
            "blur_score": round(blur_score, 1),
            "glare_ratio": round(glare_ratio, 4),
            "brightness": round(mean_brightness, 1)
        }

    @classmethod
    def enhance_contrast(cls, img_bgr: np.ndarray) -> np.ndarray:
        """
        Enhances document legibility using CLAHE (Contrast Limited Adaptive Histogram Equalization).
        Significantly improves OCR accuracy on passports with holograms, security patterns, and low contrast.
        """
        try:
            lab = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            cl = clahe.apply(l)
            enhanced_lab = cv2.merge((cl, a, b))
            return cv2.cvtColor(enhanced_lab, cv2.COLOR_LAB2BGR)
        except Exception:
            return img_bgr

    @staticmethod
    def _order_points(pts: np.ndarray) -> np.ndarray:
        """Orders 4 coordinates: top-left, top-right, bottom-right, bottom-left."""
        rect = np.zeros((4, 2), dtype="float32")
        s = pts.sum(axis=1)
        rect[0] = pts[np.argmin(s)] # Top-left
        rect[2] = pts[np.argmax(s)] # Bottom-right

        diff = np.diff(pts, axis=1)
        rect[1] = pts[np.argmin(diff)] # Top-right
        rect[3] = pts[np.argmax(diff)] # Bottom-left
        return rect

    @classmethod
    def auto_warp_and_deskew(cls, img_bgr: np.ndarray) -> np.ndarray:
        """
        Finds the 4 corners of the document and warps perspective to a clean flat rectangle.
        If no distinct quadrilateral document boundary is detected, returns original image.
        """
        try:
            h, w = img_bgr.shape[:2]
            gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
            blurred = cv2.GaussianBlur(gray, (5, 5), 0)
            edged = cv2.Canny(blurred, 50, 180)

            contours, _ = cv2.findContours(edged, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
            contours = sorted(contours, key=cv2.contourArea, reverse=True)[:5]

            doc_contour = None
            for c in contours:
                peri = cv2.arcLength(c, True)
                approx = cv2.approxPolyDP(c, 0.02 * peri, True)
                # Looking for 4-point polygon occupying at least 25% of total image area
                if len(approx) == 4 and cv2.contourArea(approx) > (h * w * 0.25):
                    doc_contour = approx
                    break

            if doc_contour is None:
                return img_bgr

            pts = doc_contour.reshape(4, 2)
            rect = cls._order_points(pts)
            (tl, tr, br, bl) = rect

            # Compute width of the new image
            width_a = np.sqrt(((br[0] - bl[0]) ** 2) + ((br[1] - bl[1]) ** 2))
            width_b = np.sqrt(((tr[0] - tl[0]) ** 2) + ((tr[1] - tl[1]) ** 2))
            max_w = max(int(width_a), int(width_b))

            # Compute height of the new image
            height_a = np.sqrt(((tr[0] - br[0]) ** 2) + ((tr[1] - br[1]) ** 2))
            height_b = np.sqrt(((tl[0] - bl[0]) ** 2) + ((tl[1] - bl[1]) ** 2))
            max_h = max(int(height_a), int(height_b))

            # Validate reasonable document aspect ratio (e.g. ID-1 cards or passports: ~1.2 to 1.8)
            if max_h == 0 or max_w == 0:
                return img_bgr

            ratio = max_w / float(max_h)
            if ratio < 0.9 or ratio > 2.2:
                return img_bgr

            dst = np.array([
                [0, 0],
                [max_w - 1, 0],
                [max_w - 1, max_h - 1],
                [0, max_h - 1]
            ], dtype="float32")

            m = cv2.getPerspectiveTransform(rect, dst)
            warped = cv2.warpPerspective(img_bgr, m, (max_w, max_h))
            logger.info(f"Document perspective warped from {w}x{h} to {max_w}x{max_h}")
            return warped

        except Exception as e:
            logger.debug(f"Perspective warp fallback: {e}")
            return img_bgr
