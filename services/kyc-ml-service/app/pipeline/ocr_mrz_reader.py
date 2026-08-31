import json
import logging
import re
import cv2
import numpy as np
import easyocr
from typing import Optional
from mrz.checker.td1 import TD1CodeChecker
from mrz.checker.td2 import TD2CodeChecker
from mrz.checker.td3 import TD3CodeChecker

logger = logging.getLogger(__name__)


class OcrMrzReader:
    def __init__(self):
        logger.info("Initializing EasyOCR (PyTorch) + MRZ pipeline...")
        # Self-hosted PyTorch OCR model (offline)
        self.reader = easyocr.Reader(['en'], gpu=False, verbose=False)
        logger.info("EasyOCR initialized successfully.")

    def _extract_mrz_lines(self, all_text_lines: list[str]) -> list[str]:
        """
        From all detected text lines, find likely MRZ lines.
        MRZ lines contain uppercase letters, digits, and '<' characters.
        """
        mrz_pattern = re.compile(r'^[A-Z0-9<]{28,}$')
        candidates = []

        for line in all_text_lines:
            cleaned = line.strip().replace(' ', '').upper()
            cleaned = cleaned.replace('«', '<').replace('‹', '<').replace('›', '<')
            if len(cleaned) >= 28 and mrz_pattern.match(cleaned):
                candidates.append(cleaned)

        return candidates

    def _evaluate_expiry(self, expiry_str: str) -> tuple[str, Optional[str]]:
        """
        Evaluates YYMMDD expiry date string from MRZ.
        Returns: (expiry_status, formatted_date_str)
        """
        try:
            from datetime import datetime, timedelta
            if not expiry_str or len(expiry_str) != 6 or not expiry_str.isdigit():
                return "UNKNOWN", None
            yy = int(expiry_str[:2])
            mm = int(expiry_str[2:4])
            dd = int(expiry_str[4:6])
            year = 2000 + yy if yy < 80 else 1900 + yy
            exp_date = datetime(year, mm, dd)
            today = datetime.utcnow()
            formatted = exp_date.strftime("%Y-%m-%d")

            if exp_date < today:
                return "EXPIRED", formatted
            elif exp_date < today + timedelta(days=30):
                return "EXPIRING_SOON", formatted
            else:
                return "VALID", formatted
        except Exception:
            return "UNKNOWN", None

    def _extract_dates_from_text(self, text_lines: list[str]) -> tuple[str, Optional[str], Optional[str]]:
        """
        Distinguishes Date of Birth from Expiry Date using context labels and date logic.
        Returns: (expiry_status: VALID|EXPIRED|EXPIRING_SOON|UNKNOWN, dob_str, expiry_str)
        """
        date_pattern = re.compile(r'\b(0[1-9]|[12]\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](19\d{2}|20\d{2})\b')
        dob_keywords = ['туған', 'рождения', 'birth', 'dob', 'born', 'дата рожд', 'д/р', 'күні']
        exp_keywords = ['жарамды', 'действителен', 'дейін', 'expiry', 'until', 'exp', 'valid', 'мерзімі', 'срок']

        found_dates = []
        try:
            from datetime import datetime, timedelta
            today = datetime.utcnow()

            for line in text_lines:
                line_lower = line.lower()
                matches = date_pattern.findall(line)
                for m in matches:
                    dd, mm, yyyy = int(m[0]), int(m[1]), int(m[2])
                    try:
                        d_obj = datetime(yyyy, mm, dd)
                        d_str = f"{yyyy:04d}-{mm:02d}-{dd:02d}"
                        found_dates.append((d_obj, d_str, line_lower))
                    except Exception:
                        continue

            if not found_dates:
                return "UNKNOWN", None, None

            dob_candidate = None
            exp_candidate = None

            # 1. Label/keyword matching
            for d_obj, d_str, line_lower in found_dates:
                if any(kw in line_lower for kw in dob_keywords) and not dob_candidate:
                    dob_candidate = (d_obj, d_str)
                elif any(kw in line_lower for kw in exp_keywords) and not exp_candidate:
                    exp_candidate = (d_obj, d_str)

            # 2. Disambiguation if keywords absent
            if not exp_candidate and not dob_candidate:
                if len(found_dates) >= 2:
                    sorted_dates = sorted(found_dates, key=lambda x: x[0])
                    dob_candidate = sorted_dates[0][:2]
                    exp_candidate = sorted_dates[-1][:2]
                else:
                    single_date = found_dates[0]
                    # If single date is in the past (> 10 years ago), it is DOB!
                    if single_date[0] < today - timedelta(days=365 * 10):
                        dob_candidate = single_date[:2]
                    elif single_date[0] < today:
                        # Recent past date (< 10 yrs ago) could be expired document
                        exp_candidate = single_date[:2]
                    else:
                        exp_candidate = single_date[:2]
            elif dob_candidate and not exp_candidate and len(found_dates) > 1:
                remaining = [d for d in found_dates if d[1] != dob_candidate[1]]
                if remaining:
                    exp_candidate = sorted(remaining, key=lambda x: x[0])[-1][:2]

            dob_str = dob_candidate[1] if dob_candidate else None
            exp_str = exp_candidate[1] if exp_candidate else None

            if exp_candidate:
                exp_date = exp_candidate[0]
                if exp_date < today:
                    return "EXPIRED", dob_str, exp_str
                elif exp_date < today + timedelta(days=30):
                    return "EXPIRING_SOON", dob_str, exp_str
                else:
                    return "VALID", dob_str, exp_str
            else:
                return "UNKNOWN", dob_str, None
        except Exception as e:
            logger.debug(f"Date extraction error: {e}")
            return "UNKNOWN", None, None

    def _try_parse_mrz(self, mrz_lines: list[str]) -> dict:
        """Try to parse MRZ using TD3, TD2, TD1 checkers."""
        # TD3 (Passport: 2 lines x 44 chars)
        for i in range(len(mrz_lines)):
            if i + 1 < len(mrz_lines):
                line1 = mrz_lines[i]
                line2 = mrz_lines[i + 1]
                if 40 <= len(line1) <= 48 and 40 <= len(line2) <= 48:
                    line1 = (line1 + '<' * 44)[:44]
                    line2 = (line2 + '<' * 44)[:44]
                    candidate = f"{line1}\n{line2}"
                    try:
                        checker = TD3CodeChecker(candidate)
                        if bool(checker):
                            fields = checker.fields()
                            exp_status, exp_formatted = self._evaluate_expiry(str(fields.expiry_date))
                            return {
                                "valid": True,
                                "expiry_status": exp_status,
                                "format": "TD3",
                                "first_name": str(fields.name).strip().replace('<', ' ').strip(),
                                "last_name": str(fields.surname).strip().replace('<', ' ').strip(),
                                "document_number": str(fields.document_number).strip(),
                                "date_of_birth": str(fields.birth_date),
                                "expiry_date": exp_formatted or str(fields.expiry_date),
                                "gender": str(fields.sex),
                                "nationality": str(fields.nationality),
                            }
                    except Exception as e:
                        logger.debug(f"TD3 parse failed: {e}")

        # TD1 (ID Card: 3 lines x 30 chars)
        for i in range(len(mrz_lines)):
            if i + 2 < len(mrz_lines):
                lines = [mrz_lines[i], mrz_lines[i + 1], mrz_lines[i + 2]]
                if all(26 <= len(l) <= 34 for l in lines):
                    lines = [(l + '<' * 30)[:30] for l in lines]
                    candidate = "\n".join(lines)
                    try:
                        checker = TD1CodeChecker(candidate)
                        if bool(checker):
                            fields = checker.fields()
                            exp_status, exp_formatted = self._evaluate_expiry(str(fields.expiry_date))
                            return {
                                "valid": True,
                                "expiry_status": exp_status,
                                "format": "TD1",
                                "first_name": str(fields.name).strip().replace('<', ' ').strip(),
                                "last_name": str(fields.surname).strip().replace('<', ' ').strip(),
                                "document_number": str(fields.document_number).strip(),
                                "date_of_birth": str(fields.birth_date),
                                "expiry_date": exp_formatted or str(fields.expiry_date),
                                "gender": str(fields.sex),
                                "nationality": str(fields.nationality),
                            }
                    except Exception as e:
                        logger.debug(f"TD1 parse failed: {e}")

        return {"valid": False, "expiry_status": "UNKNOWN"}

    def process(self, image_bytes: bytes) -> tuple[str, str, dict, str]:
        """
        Process document image with EasyOCR and validate MRZ & Expiry.
        Returns:
            mrz_status: str ("VALID", "CHECKSUM_FAILED", "ABSENT")
            expiry_status: str ("VALID", "EXPIRED", "EXPIRING_SOON", "UNKNOWN")
            extracted: dict
            raw_ocr_json: str
        """
        try:
            nparr = np.frombuffer(image_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is None:
                return "ABSENT", "UNKNOWN", {}, json.dumps({"error": "Failed to decode image"})

            # Run EasyOCR
            results = self.reader.readtext(img)

            all_lines = []
            raw_ocr_entries = []

            for item in results:
                bbox, text, conf = item
                text_clean = text.strip()
                if text_clean:
                    all_lines.append(text_clean)
                    raw_ocr_entries.append({
                        "text": text_clean,
                        "confidence": round(float(conf), 4),
                    })

            raw_ocr_json = json.dumps({
                "lines": raw_ocr_entries,
                "total_lines": len(all_lines)
            }, ensure_ascii=False)

            logger.info(f"EasyOCR found {len(all_lines)} text lines")

            # Check MRZ
            mrz_lines = self._extract_mrz_lines(all_lines)
            if mrz_lines:
                mrz_result = self._try_parse_mrz(mrz_lines)
                if mrz_result.get("valid"):
                    extracted = {
                        "first_name": mrz_result.get("first_name"),
                        "last_name": mrz_result.get("last_name"),
                        "document_number": mrz_result.get("document_number"),
                        "date_of_birth": mrz_result.get("date_of_birth"),
                        "expiry_date": mrz_result.get("expiry_date"),
                        "gender": mrz_result.get("gender"),
                        "nationality": mrz_result.get("nationality"),
                    }
                    expiry_status = mrz_result.get("expiry_status", "VALID")
                    logger.info(f"MRZ Valid: {mrz_result.get('format')}, ExpiryStatus: {expiry_status}")
                    return "VALID", expiry_status, extracted, raw_ocr_json
                else:
                    logger.warning("MRZ lines detected but checksum validation failed.")
                    # Try text OCR date fallback
                    text_exp_status, text_dob, text_exp_date = self._extract_dates_from_text(all_lines)
                    return "CHECKSUM_FAILED", text_exp_status, {"date_of_birth": text_dob, "expiry_date": text_exp_date}, raw_ocr_json

            # If no MRZ lines found at all (e.g. ID card front or driver license)
            text_exp_status, text_dob, text_exp_date = self._extract_dates_from_text(all_lines)
            return "ABSENT", text_exp_status, {"date_of_birth": text_dob, "expiry_date": text_exp_date}, raw_ocr_json

        except Exception as e:
            logger.error(f"OCR processing failed: {e}", exc_info=True)
            return "ABSENT", "UNKNOWN", {}, json.dumps({"error": str(e)})

