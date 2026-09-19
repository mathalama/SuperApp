import json
import logging
import re
import cv2
import numpy as np
from typing import Optional, Tuple, Dict, Any

try:
    import easyocr
    HAS_EASYOCR = True
except ImportError:
    HAS_EASYOCR = False

try:
    from mrz.checker.td1 import TD1CodeChecker
    from mrz.checker.td2 import TD2CodeChecker
    from mrz.checker.td3 import TD3CodeChecker
    HAS_MRZ = True
except ImportError:
    HAS_MRZ = False

# Try importing zxing-cpp for high-speed 2D Barcode / PDF417 Driver License decoding
try:
    import zxingcpp
    HAS_ZXING = True
except ImportError:
    HAS_ZXING = False

from app.pipeline.iin_validator import IinValidator

logger = logging.getLogger(__name__)


# ICAO Doc 9303 Cyrillic-to-Latin Transliteration Table + Kazakh specific extensions
ICAO_CYRILLIC_MAP = {
    'А': 'A', 'Б': 'B', 'В': 'V', 'Г': 'G', 'Д': 'D', 'Е': 'E', 'Ё': 'E',
    'Ж': 'ZH', 'З': 'Z', 'И': 'I', 'Й': 'I', 'К': 'K', 'Л': 'L', 'М': 'M',
    'Н': 'N', 'О': 'O', 'П': 'P', 'Р': 'R', 'С': 'S', 'Т': 'T', 'У': 'U',
    'Ф': 'F', 'Х': 'KH', 'Ц': 'TS', 'Ч': 'CH', 'Ш': 'SH', 'Щ': 'SHCH',
    'Ъ': '', 'Ы': 'Y', 'Ь': '', 'Э': 'E', 'Ю': 'IU', 'Я': 'IA',
    # Kazakh Cyrillic extensions
    'Ә': 'A', 'І': 'I', 'Ң': 'N', 'Ғ': 'G', 'Ү': 'U', 'Ұ': 'U', 'Қ': 'Q',
    'Ө': 'O', 'Һ': 'H'
}


def transliterate_to_latin(text: str) -> str:
    """Converts Cyrillic characters to Latin according to ICAO Doc 9303 standards."""
    if not text:
        return text
    result = []
    for ch in text.upper():
        result.append(ICAO_CYRILLIC_MAP.get(ch, ch))
    return "".join(result)


class OcrMrzReader:
    def __init__(self):
        logger.info("Initializing Multilingual EasyOCR (English + Cyrillic) + MRZ + Barcode pipeline...")
        self.reader = None
        if HAS_EASYOCR:
            try:
                self.reader = easyocr.Reader(['en', 'ru'], gpu=False, verbose=False)
                logger.info("Multilingual EasyOCR ['en', 'ru'] initialized successfully.")
            except Exception as e:
                logger.warning(f"Failed to load ['en', 'ru'] OCR, falling back to ['en']: {e}")
                try:
                    self.reader = easyocr.Reader(['en'], gpu=False, verbose=False)
                except Exception as inner_e:
                    logger.warning(f"EasyOCR fallback initialization failed: {inner_e}")
        else:
            logger.info("EasyOCR not installed in local environment (runs in Docker).")

    def _extract_barcode_data(self, img_bgr: np.ndarray) -> Optional[Dict[str, Any]]:
        """
        Attempts to read 2D barcodes (PDF417 on driver's licenses, QR, DataMatrix).
        Returns extracted structured fields if found.
        """
        if not HAS_ZXING or img_bgr is None:
            return None

        try:
            barcodes = zxingcpp.read_barcodes(img_bgr)
            for barcode in barcodes:
                text = barcode.text
                if not text:
                    continue

                logger.info(f"Barcode detected: format={barcode.format}")
                # AAMVA PDF417 driver license format parsing
                if "ANSI " in text or "AAMVA" in text or "\nDAA" in text or "\nDCS" in text or "\nDAC" in text:
                    extracted: Dict[str, Any] = {"format": "PDF417_AAMVA"}
                    lines = text.replace('\r', '\n').split('\n')
                    for line in lines:
                        line = line.strip()
                        if line.startswith("DAC") or line.startswith("DCT"): # First name
                            extracted["first_name"] = line[3:].strip()
                        elif line.startswith("DCS") or line.startswith("DAA"): # Family name / Last name
                            extracted["last_name"] = line[3:].strip()
                        elif line.startswith("DAQ"): # Document number
                            extracted["document_number"] = line[3:].strip()
                        elif line.startswith("DBB"): # DOB: YYYYMMDD or MMDDYYYY
                            raw_dob = line[3:].strip()
                            if len(raw_dob) == 8:
                                extracted["date_of_birth"] = f"{raw_dob[:4]}-{raw_dob[4:6]}-{raw_dob[6:]}"
                        elif line.startswith("DBA"): # Expiry date
                            raw_exp = line[3:].strip()
                            if len(raw_exp) == 8:
                                extracted["expiry_date"] = f"{raw_exp[:4]}-{raw_exp[4:6]}-{raw_exp[6:]}"
                        elif line.startswith("DBC"): # Gender 1=M, 2=F
                            g = line[3:].strip()
                            extracted["gender"] = "M" if g == "1" else ("F" if g == "2" else g)

                    return extracted
        except Exception as e:
            logger.debug(f"Barcode processing exception: {e}")

        return None

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
        Distinguishes Date of Birth from Expiry Date using multilingual context labels and date logic.
        Supports DD.MM.YYYY, DD/MM/YYYY, YYYY-MM-DD, DD-MM-YYYY.
        """
        date_pattern = re.compile(
            r'\b(?:(0[1-9]|[12]\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](19\d{2}|20\d{2})|(19\d{2}|20\d{2})[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\d|3[01]))\b'
        )
        dob_keywords = [
            'туған', 'рождения', 'birth', 'dob', 'born', 'дата рожд', 'д/р', 'күні',
            'geboren', 'naissance', 'nacimiento', 'doğum', 'tugilgan'
        ]
        exp_keywords = [
            'жарамды', 'действителен', 'дейін', 'expiry', 'until', 'exp', 'valid',
            'мерзімі', 'срок', 'bis', 'valable', 'vencimiento', 'gecerlilik', 'amal'
        ]

        found_dates = []
        try:
            from datetime import datetime, timedelta
            today = datetime.utcnow()

            for line in text_lines:
                line_lower = line.lower()
                matches = date_pattern.findall(line)
                for m in matches:
                    try:
                        if m[0]: # DD.MM.YYYY
                            dd, mm, yyyy = int(m[0]), int(m[1]), int(m[2])
                        else: # YYYY.MM.DD
                            yyyy, mm, dd = int(m[3]), int(m[4]), int(m[5])
                        d_obj = datetime(yyyy, mm, dd)
                        d_str = f"{yyyy:04d}-{mm:02d}-{dd:02d}"
                        found_dates.append((d_obj, d_str, line_lower))
                    except Exception:
                        continue

            if not found_dates:
                return "UNKNOWN", None, None

            dob_candidate = None
            exp_candidate = None

            # 1. Keyword context matching
            for d_obj, d_str, line_lower in found_dates:
                if any(kw in line_lower for kw in dob_keywords) and not dob_candidate:
                    dob_candidate = (d_obj, d_str)
                elif any(kw in line_lower for kw in exp_keywords) and not exp_candidate:
                    exp_candidate = (d_obj, d_str)

            # 2. Heuristic disambiguation
            if not exp_candidate and not dob_candidate:
                if len(found_dates) >= 2:
                    sorted_dates = sorted(found_dates, key=lambda x: x[0])
                    dob_candidate = sorted_dates[0][:2]
                    exp_candidate = sorted_dates[-1][:2]
                else:
                    single_date = found_dates[0]
                    if single_date[0] < today - timedelta(days=365 * 10):
                        dob_candidate = single_date[:2]
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

    def _extract_fields_from_visual_zone(self, text_lines: list[str]) -> Dict[str, Any]:
        """
        Extracts names and document numbers from non-MRZ documents (e.g. ID card front, Driver License)
        using layout keywords and multilingual patterns.
        """
        extracted: Dict[str, Any] = {}
        last_name_labels = ['тегі', 'фамилия', 'surname', 'last name', 'nom', 'apellido', 'familiya']
        first_name_labels = ['аты', 'имя', 'given name', 'first name', 'prenom', 'nombre', 'ism']
        doc_num_labels = ['номер', 'number', 'no', '№', 'куәлік', 'document', 'id']

        # Document number regex (typically 8 to 12 alphanumeric digits)
        doc_num_pattern = re.compile(r'\b[A-Z0-9]{8,12}\b')

        for idx, raw_line in enumerate(text_lines):
            line = raw_line.strip()
            line_lower = line.lower()

            # Detect document number
            if not extracted.get("document_number"):
                if any(lbl in line_lower for lbl in doc_num_labels):
                    # Check next line or inline
                    matches = doc_num_pattern.findall(line)
                    if matches and not any(lbl in matches[0].lower() for lbl in doc_num_labels):
                        extracted["document_number"] = matches[0]
                    elif idx + 1 < len(text_lines):
                        next_matches = doc_num_pattern.findall(text_lines[idx + 1])
                        if next_matches:
                            extracted["document_number"] = next_matches[0]

            # Detect surname
            if not extracted.get("last_name"):
                if any(lbl in line_lower for lbl in last_name_labels):
                    parts = re.split(r'[:/]', line)
                    val = parts[-1].strip()
                    if len(parts) > 1 and len(val) >= 2 and not any(kw in val.lower() for kw in last_name_labels):
                        extracted["last_name"] = transliterate_to_latin(val)
                    elif idx + 1 < len(text_lines) and len(text_lines[idx + 1].strip()) >= 2:
                        candidate = text_lines[idx + 1].strip()
                        if not any(kw in candidate.lower() for kw in first_name_labels + last_name_labels):
                            extracted["last_name"] = transliterate_to_latin(candidate)

            # Detect first name
            if not extracted.get("first_name"):
                if any(lbl in line_lower for lbl in first_name_labels):
                    parts = re.split(r'[:/]', line)
                    val = parts[-1].strip()
                    if len(parts) > 1 and len(val) >= 2 and not any(kw in val.lower() for kw in first_name_labels):
                        extracted["first_name"] = transliterate_to_latin(val)
                    elif idx + 1 < len(text_lines) and len(text_lines[idx + 1].strip()) >= 2:
                        candidate = text_lines[idx + 1].strip()
                        if not any(kw in candidate.lower() for kw in first_name_labels + last_name_labels):
                            extracted["first_name"] = transliterate_to_latin(candidate)

        return extracted

    def _try_parse_mrz(self, mrz_lines: list[str]) -> dict:
        """Try to parse MRZ using TD3, TD2, TD1 checkers with ICAO transliteration."""
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
        Process document image:
        1. Check for 2D Barcodes (PDF417 on Driver Licenses).
        2. Run Multilingual EasyOCR.
        3. Check ICAO MRZ (Passports & ID cards).
        4. Fallback to Visual Zone heuristic field extraction.
        """
        try:
            nparr = np.frombuffer(image_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is None:
                return "ABSENT", "UNKNOWN", {}, json.dumps({"error": "Failed to decode image"})

            # Step 1: Check Barcode (PDF417 / QR)
            barcode_data = self._extract_barcode_data(img)
            if barcode_data and barcode_data.get("document_number"):
                exp_date = barcode_data.get("expiry_date")
                exp_status = "VALID"
                if exp_date:
                    try:
                        from datetime import datetime
                        if datetime.strptime(exp_date, "%Y-%m-%d") < datetime.utcnow():
                            exp_status = "EXPIRED"
                    except Exception:
                        pass
                logger.info("Driver License PDF417 decoded successfully.")
                return "VALID", exp_status, barcode_data, json.dumps({"source": "barcode", "data": barcode_data})

            # Step 2: Run Multilingual OCR
            results = self.reader.readtext(img) if self.reader else []
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

            logger.info(f"Multilingual EasyOCR found {len(all_lines)} text lines")

            # Extract and validate IIN (Kazakhstan 12-digit number)
            iin_val = IinValidator.extract_and_validate_from_text(all_lines)

            # Step 3: Check ICAO MRZ
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
                    if iin_val:
                        extracted["personal_number"] = iin_val
                    expiry_status = mrz_result.get("expiry_status", "VALID")
                    logger.info(f"MRZ Valid: {mrz_result.get('format')}, ExpiryStatus: {expiry_status}")
                    return "VALID", expiry_status, extracted, raw_ocr_json
                else:
                    logger.warning("MRZ lines detected but checksum validation failed.")
                    text_exp_status, text_dob, text_exp_date = self._extract_dates_from_text(all_lines)
                    v_fields = self._extract_fields_from_visual_zone(all_lines)
                    v_fields.update({"date_of_birth": text_dob, "expiry_date": text_exp_date})
                    if iin_val:
                        v_fields["personal_number"] = iin_val
                    return "CHECKSUM_FAILED", text_exp_status, v_fields, raw_ocr_json

            # Step 4: Non-MRZ Visual Zone Extraction
            text_exp_status, text_dob, text_exp_date = self._extract_dates_from_text(all_lines)
            visual_fields = self._extract_fields_from_visual_zone(all_lines)
            visual_fields.update({
                "date_of_birth": text_dob,
                "expiry_date": text_exp_date
            })
            if iin_val:
                visual_fields["personal_number"] = iin_val
            return "ABSENT", text_exp_status, visual_fields, raw_ocr_json

        except Exception as e:
            logger.error(f"OCR processing failed: {e}", exc_info=True)
            return "ABSENT", "UNKNOWN", {}, json.dumps({"error": str(e)})
