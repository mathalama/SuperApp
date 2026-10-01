import json
import logging
import re
import cv2
import numpy as np
from datetime import datetime, timedelta
from typing import Optional, Tuple, Dict, Any, List

try:
    import easyocr
    HAS_EASYOCR = True
except ImportError:
    HAS_EASYOCR = False

# Try importing zxing-cpp for high-speed 2D Barcode / PDF417 Driver License decoding
try:
    import zxingcpp
    HAS_ZXING = True
except ImportError:
    HAS_ZXING = False

from app.pipeline.iin_validator import IinValidator

logger = logging.getLogger(__name__)

# ICAO Doc 9303 Cyrillic-to-Latin Transliteration Table + Kazakh, Ukrainian, Uzbek extensions
ICAO_CYRILLIC_MAP = {
    'А': 'A', 'Б': 'B', 'В': 'V', 'Г': 'G', 'Д': 'D', 'Е': 'E', 'Ё': 'E',
    'Ж': 'ZH', 'З': 'Z', 'И': 'I', 'Й': 'I', 'К': 'K', 'Л': 'L', 'М': 'M',
    'Н': 'N', 'О': 'O', 'П': 'P', 'Р': 'R', 'С': 'S', 'Т': 'T', 'У': 'U',
    'Ф': 'F', 'Х': 'KH', 'Ц': 'TS', 'Ч': 'CH', 'Ш': 'SH', 'Щ': 'SHCH',
    'Ъ': '', 'Ы': 'Y', 'Ь': '', 'Э': 'E', 'Ю': 'IU', 'Я': 'IA',
    # Kazakh extensions
    'Ә': 'A', 'І': 'I', 'Ң': 'N', 'Ғ': 'G', 'Ү': 'U', 'Ұ': 'U', 'Қ': 'Q',
    'Ө': 'O', 'Һ': 'H',
    # Ukrainian extensions
    'Є': 'YE', 'Ї': 'YI', 'Ґ': 'G',
    # Uzbek extensions
    'Ў': 'O', 'Қ': 'Q', 'Ғ': 'G', 'Ҳ': 'H',
}

ICAO_WEIGHTS = [7, 3, 1]

def transliterate_to_latin(text: str) -> str:
    """Converts Cyrillic characters to Latin according to ICAO Doc 9303 standards."""
    if not text:
        return text
    result = []
    for ch in text.upper():
        result.append(ICAO_CYRILLIC_MAP.get(ch, ch))
    return "".join(result)

def icao_char_value(c: str) -> int:
    """Returns numerical weight value for ICAO Doc 9303 character."""
    if '0' <= c <= '9':
        return ord(c) - ord('0')
    if 'A' <= c <= 'Z':
        return ord(c) - ord('A') + 10
    return 0  # '<' and anything else has value 0

def calculate_icao_check_digit(data: str) -> str:
    """Calculates standard ICAO Doc 9303 check digit (modulo 10 with 7-3-1 weights)."""
    total = sum(icao_char_value(c) * ICAO_WEIGHTS[i % 3] for i, c in enumerate(data))
    return str(total % 10)

def verify_and_repair_numeric_field(field_text: str, expected_cd: str) -> Tuple[bool, str, str]:
    """
    Validates field against check digit. If check digit fails, tests common OCR confusions
    ('O' <-> '0', 'I' <-> '1', 'B' <-> '8', 'S' <-> '5', 'Z' <-> '2') to repair single OCR typos.
    Returns: (is_valid, repaired_field, repaired_cd)
    """
    field_text = field_text.upper()
    expected_cd = expected_cd.upper()

    if calculate_icao_check_digit(field_text) == expected_cd:
        return True, field_text, expected_cd

    # OCR Substitution candidates
    replacements = {
        'O': '0', '0': 'O',
        'I': '1', '1': 'I',
        'Z': '2', '2': 'Z',
        'S': '5', '5': 'S',
        'B': '8', '8': 'B',
    }

    # Try repairing the check digit itself
    if expected_cd in replacements:
        alt_cd = replacements[expected_cd]
        if calculate_icao_check_digit(field_text) == alt_cd:
            return True, field_text, alt_cd

    # Try repairing a single character in the data field
    chars = list(field_text)
    for i, ch in enumerate(chars):
        if ch in replacements:
            orig = chars[i]
            chars[i] = replacements[ch]
            candidate = "".join(chars)
            if calculate_icao_check_digit(candidate) == expected_cd:
                return True, candidate, expected_cd
            chars[i] = orig

    return False, field_text, expected_cd


class OcrMrzReader:
    """
    Universal Multilingual Document OCR & MRZ Reader.
    Supports:
    1. 2D Barcodes (PDF417 on US/Canadian Driver's Licenses).
    2. ICAO Doc 9303 MRZ across all standard formats:
       - TD3 (Passports: 2x44)
       - TD2 (ID cards, visas, residence cards: 2x36)
       - TD1 (National ID cards: 3x30)
    3. Error-tolerant MRZ check-digit validation with auto-repair.
    4. Non-MRZ visual zone multilingual extraction (names, document numbers, dates)
       in English, Russian, Kazakh, Spanish, French, German, Portuguese, Italian, Turkish, Uzbek.
    """

    def __init__(self):
        logger.info("Initializing Universal Multilingual OCR + ICAO MRZ pipeline...")
        self.reader = None
        if HAS_EASYOCR:
            try:
                # Load English and Russian/Cyrillic (covers worldwide Latin + Cyrillic documents)
                self.reader = easyocr.Reader(['en', 'ru'], gpu=False, verbose=False)
                logger.info("Universal Multilingual EasyOCR initialized.")
            except Exception as e:
                logger.warning(f"Failed to load ['en', 'ru'], falling back to ['en']: {e}")
                try:
                    self.reader = easyocr.Reader(['en'], gpu=False, verbose=False)
                except Exception as inner_e:
                    logger.warning(f"EasyOCR fallback initialization failed: {inner_e}")
        else:
            logger.info("EasyOCR not installed locally (will run in Docker).")

    def _extract_barcode_data(self, img_bgr: np.ndarray) -> Optional[Dict[str, Any]]:
        """Reads 2D barcodes (PDF417 on driver's licenses, QR, DataMatrix)."""
        if not HAS_ZXING or img_bgr is None:
            return None

        try:
            barcodes = zxingcpp.read_barcodes(img_bgr)
            for barcode in barcodes:
                text = barcode.text
                if not text:
                    continue

                if "ANSI " in text or "AAMVA" in text or "\nDAA" in text or "\nDCS" in text or "\nDAC" in text:
                    extracted: Dict[str, Any] = {"format": "PDF417_AAMVA"}
                    lines = text.replace('\r', '\n').split('\n')
                    for line in lines:
                        line = line.strip()
                        if line.startswith("DAC") or line.startswith("DCT"):
                            extracted["first_name"] = line[3:].strip()
                        elif line.startswith("DCS") or line.startswith("DAA"):
                            extracted["last_name"] = line[3:].strip()
                        elif line.startswith("DAQ"):
                            extracted["document_number"] = line[3:].strip()
                        elif line.startswith("DBB"):
                            raw_dob = line[3:].strip()
                            if len(raw_dob) == 8:
                                extracted["date_of_birth"] = f"{raw_dob[:4]}-{raw_dob[4:6]}-{raw_dob[6:]}"
                        elif line.startswith("DBA"):
                            raw_exp = line[3:].strip()
                            if len(raw_exp) == 8:
                                extracted["expiry_date"] = f"{raw_exp[:4]}-{raw_exp[4:6]}-{raw_exp[6:]}"
                        elif line.startswith("DBC"):
                            g = line[3:].strip()
                            extracted["gender"] = "M" if g == "1" else ("F" if g == "2" else g)

                    return extracted
        except Exception as e:
            logger.debug(f"Barcode processing exception: {e}")

        return None

    def _clean_mrz_line(self, line: str) -> str:
        """Sanitizes line characters to strict ICAO MRZ alphabet (A-Z, 0-9, <)."""
        cleaned = line.strip().upper()
        # Common OCR bracket confusions
        bracket_subs = {'«': '<', '‹': '<', '›': '<', '(': '<', ')': '<',
                        '{': '<', '}': '<', '[': '<', ']': '<', '|': '<',
                        '/': '<', '\\': '<', ' ': '', '-': '<', '_': '<'}
        for old, new in bracket_subs.items():
            cleaned = cleaned.replace(old, new)

        # Filter strictly to alphanumeric and '<'
        cleaned = re.sub(r'[^A-Z0-9<]', '', cleaned)
        return cleaned

    def _extract_mrz_lines(self, all_text_lines: List[str]) -> List[str]:
        """Extracts candidate lines matching ICAO MRZ length and character constraints."""
        candidates = []
        for line in all_text_lines:
            cleaned = self._clean_mrz_line(line)
            if len(cleaned) >= 26 and '<' in cleaned:
                candidates.append(cleaned)
        return candidates

    def _evaluate_expiry(self, expiry_yymmdd: str) -> Tuple[str, Optional[str]]:
        """
        Evaluates YYMMDD expiry date string from MRZ.
        Returns: (expiry_status, formatted_date_str)
        """
        try:
            if not expiry_yymmdd or len(expiry_yymmdd) != 6 or not expiry_yymmdd.isdigit():
                return "UNKNOWN", None
            yy = int(expiry_yymmdd[:2])
            mm = int(expiry_yymmdd[2:4])
            dd = int(expiry_yymmdd[4:6])

            if mm < 1 or mm > 12 or dd < 1 or dd > 31:
                return "UNKNOWN", None

            # Passports are valid for max 10-15 years, so 00-69 is 2000s, 70-99 is 1900s
            year = 2000 + yy if yy < 70 else 1900 + yy
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

    def _format_mrz_dob(self, dob_yymmdd: str) -> Optional[str]:
        """Formats MRZ YYMMDD into YYYY-MM-DD with century estimation."""
        try:
            if not dob_yymmdd or len(dob_yymmdd) != 6 or not dob_yymmdd.isdigit():
                return None
            yy = int(dob_yymmdd[:2])
            mm = int(dob_yymmdd[2:4])
            dd = int(dob_yymmdd[4:6])

            if mm < 1 or mm > 12 or dd < 1 or dd > 31:
                return None

            current_yy = datetime.utcnow().year % 100
            # If person's 2-digit birth year is greater than current year, it must be 1900s
            year = 2000 + yy if yy <= current_yy else 1900 + yy
            return f"{year:04d}-{mm:02d}-{dd:02d}"
        except Exception:
            return None

    def parse_td3(self, line1: str, line2: str) -> Optional[Dict[str, Any]]:
        """Parses ICAO TD3 (Passport: 2 lines of 44 characters)."""
        line1 = (line1 + '<' * 44)[:44]
        line2 = (line2 + '<' * 44)[:44]

        # Line 1: P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
        # Line 2: L898902C36UTO7408122F1204159ZE184226B<<<<<10
        doc_type = line1[0]
        if doc_type not in ('P', 'V', 'I', 'A', 'C'):
            return None

        issuing_country = line1[2:5].replace('<', '').strip()
        names_part = line1[5:].strip('<')
        name_tokens = names_part.split('<<')
        surname = name_tokens[0].replace('<', ' ').strip()
        given_names = name_tokens[1].replace('<', ' ').strip() if len(name_tokens) > 1 else ""

        # Line 2 parsing
        raw_doc_num = line2[0:9]
        doc_num_cd = line2[9]
        nationality = line2[10:13].replace('<', '').strip()
        raw_dob = line2[13:19]
        dob_cd = line2[19]
        sex = line2[20].replace('<', 'X')
        raw_expiry = line2[21:27]
        expiry_cd = line2[27]
        personal_num = line2[28:42].replace('<', '').strip()
        composite_cd = line2[43]

        # Check-digit validation and error correction
        ok_doc, clean_doc, _ = verify_and_repair_numeric_field(raw_doc_num, doc_num_cd)
        ok_dob, clean_dob, _ = verify_and_repair_numeric_field(raw_dob, dob_cd)
        ok_exp, clean_exp, _ = verify_and_repair_numeric_field(raw_expiry, expiry_cd)

        doc_num_clean = clean_doc.replace('<', '').strip()
        exp_status, exp_date = self._evaluate_expiry(clean_exp)
        dob_date = self._format_mrz_dob(clean_dob)

        # Composite check
        composite_data = line2[0:10] + line2[13:20] + line2[21:43]
        ok_composite = (calculate_icao_check_digit(composite_data) == composite_cd)

        valid = (ok_doc and ok_dob and ok_exp) or ok_composite

        return {
            "valid": valid,
            "format": "TD3",
            "document_type": "PASSPORT",
            "first_name": given_names,
            "last_name": surname,
            "document_number": doc_num_clean,
            "date_of_birth": dob_date,
            "expiry_date": exp_date,
            "expiry_status": exp_status,
            "gender": sex,
            "nationality": nationality or issuing_country,
            "personal_number": personal_num or None,
            "checksum_valid": valid,
        }

    def parse_td2(self, line1: str, line2: str) -> Optional[Dict[str, Any]]:
        """Parses ICAO TD2 (ID Card / Visa / Official Travel Doc: 2 lines of 36 characters)."""
        line1 = (line1 + '<' * 36)[:36]
        line2 = (line2 + '<' * 36)[:36]

        doc_type = line1[0:2].replace('<', '')
        issuing_country = line1[2:5].replace('<', '').strip()
        names_part = line1[5:].strip('<')
        name_tokens = names_part.split('<<')
        surname = name_tokens[0].replace('<', ' ').strip()
        given_names = name_tokens[1].replace('<', ' ').strip() if len(name_tokens) > 1 else ""

        # Line 2 parsing
        raw_doc_num = line2[0:9]
        doc_num_cd = line2[9]
        nationality = line2[10:13].replace('<', '').strip()
        raw_dob = line2[13:19]
        dob_cd = line2[19]
        sex = line2[20].replace('<', 'X')
        raw_expiry = line2[21:27]
        expiry_cd = line2[27]
        personal_num = line2[28:35].replace('<', '').strip()
        composite_cd = line2[35]

        ok_doc, clean_doc, _ = verify_and_repair_numeric_field(raw_doc_num, doc_num_cd)
        ok_dob, clean_dob, _ = verify_and_repair_numeric_field(raw_dob, dob_cd)
        ok_exp, clean_exp, _ = verify_and_repair_numeric_field(raw_expiry, expiry_cd)

        doc_num_clean = clean_doc.replace('<', '').strip()
        exp_status, exp_date = self._evaluate_expiry(clean_exp)
        dob_date = self._format_mrz_dob(clean_dob)

        composite_data = line2[0:10] + line2[13:20] + line2[21:35]
        ok_composite = (calculate_icao_check_digit(composite_data) == composite_cd)

        valid = (ok_doc and ok_dob and ok_exp) or ok_composite

        return {
            "valid": valid,
            "format": "TD2",
            "document_type": "ID_CARD",
            "first_name": given_names,
            "last_name": surname,
            "document_number": doc_num_clean,
            "date_of_birth": dob_date,
            "expiry_date": exp_date,
            "expiry_status": exp_status,
            "gender": sex,
            "nationality": nationality or issuing_country,
            "personal_number": personal_num or None,
            "checksum_valid": valid,
        }

    def parse_td1(self, line1: str, line2: str, line3: str) -> Optional[Dict[str, Any]]:
        """Parses ICAO TD1 (National ID Card: 3 lines of 30 characters)."""
        line1 = (line1 + '<' * 30)[:30]
        line2 = (line2 + '<' * 30)[:30]
        line3 = (line3 + '<' * 30)[:30]

        issuing_country = line1[2:5].replace('<', '').strip()
        raw_doc_num = line1[5:14]
        doc_num_cd = line1[14]
        optional_data1 = line1[15:30].replace('<', '').strip()

        raw_dob = line2[0:6]
        dob_cd = line2[6]
        sex = line2[7].replace('<', 'X')
        raw_expiry = line2[8:14]
        expiry_cd = line2[14]
        nationality = line2[15:18].replace('<', '').strip()
        optional_data2 = line2[18:29].replace('<', '').strip()
        composite_cd = line2[29]

        names_part = line3.strip('<')
        name_tokens = names_part.split('<<')
        surname = name_tokens[0].replace('<', ' ').strip()
        given_names = name_tokens[1].replace('<', ' ').strip() if len(name_tokens) > 1 else ""

        ok_doc, clean_doc, _ = verify_and_repair_numeric_field(raw_doc_num, doc_num_cd)
        ok_dob, clean_dob, _ = verify_and_repair_numeric_field(raw_dob, dob_cd)
        ok_exp, clean_exp, _ = verify_and_repair_numeric_field(raw_expiry, expiry_cd)

        doc_num_clean = clean_doc.replace('<', '').strip()
        exp_status, exp_date = self._evaluate_expiry(clean_exp)
        dob_date = self._format_mrz_dob(clean_dob)

        composite_data = line1[5:30] + line2[0:7] + line2[8:15] + line2[18:29]
        ok_composite = (calculate_icao_check_digit(composite_data) == composite_cd)

        valid = (ok_doc and ok_dob and ok_exp) or ok_composite

        personal_num = optional_data1 or optional_data2 or None

        return {
            "valid": valid,
            "format": "TD1",
            "document_type": "ID_CARD",
            "first_name": given_names,
            "last_name": surname,
            "document_number": doc_num_clean,
            "date_of_birth": dob_date,
            "expiry_date": exp_date,
            "expiry_status": exp_status,
            "gender": sex,
            "nationality": nationality or issuing_country,
            "personal_number": personal_num,
            "checksum_valid": valid,
        }

    def _try_parse_mrz(self, mrz_lines: List[str]) -> Dict[str, Any]:
        """
        Universal MRZ search and evaluation across TD3 (44x2), TD2 (36x2), and TD1 (30x3).
        """
        # 1. Search TD3 (Passports: 2 x 44)
        for i in range(len(mrz_lines) - 1):
            l1, l2 = mrz_lines[i], mrz_lines[i + 1]
            if 40 <= len(l1) <= 48 and 40 <= len(l2) <= 48:
                res = self.parse_td3(l1, l2)
                if res:
                    return res

        # 2. Search TD2 (ID cards / visas: 2 x 36)
        for i in range(len(mrz_lines) - 1):
            l1, l2 = mrz_lines[i], mrz_lines[i + 1]
            if 33 <= len(l1) <= 39 and 33 <= len(l2) <= 39:
                res = self.parse_td2(l1, l2)
                if res:
                    return res

        # 3. Search TD1 (ID cards: 3 x 30)
        for i in range(len(mrz_lines) - 2):
            l1, l2, l3 = mrz_lines[i], mrz_lines[i + 1], mrz_lines[i + 2]
            if 27 <= len(l1) <= 33 and 27 <= len(l2) <= 33 and 27 <= len(l3) <= 33:
                res = self.parse_td1(l1, l2, l3)
                if res:
                    return res

        return {"valid": False, "expiry_status": "UNKNOWN"}

    def _extract_dates_from_text(self, text_lines: List[str]) -> Tuple[str, Optional[str], Optional[str]]:
        """
        Universal multilingual Date parser supporting:
        DD.MM.YYYY, DD/MM/YYYY, YYYY-MM-DD, DD-MM-YYYY, YYYY.MM.DD, YYYY/MM/DD
        """
        date_pattern = re.compile(
            r'\b(?:(0[1-9]|[12]\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](19\d{2}|20\d{2})|'
            r'(19\d{2}|20\d{2})[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\d|3[01]))\b'
        )

        dob_keywords = [
            'birth', 'dob', 'born', 'date of birth',
            'рождения', 'рожд', 'д/р', 'дата рождения',
            'туған', 'күні', 'туган',
            'geboren', 'geburtsdatum', 'geburtsort',
            'naissance', 'date de naissance',
            'nacimiento', 'fecha de nacimiento',
            'nascita', 'data di nascita',
            'doğum', 'dogum tarihi',
            'tugilgan', 'tug\'ilgan',
            'narodenia', 'urodzenia'
        ]

        exp_keywords = [
            'expiry', 'exp', 'until', 'valid', 'expiration', 'valid until',
            'действителен', 'срок', 'дейін', 'жарамды', 'мерзімі',
            'bis', 'gultig bis', 'ablaufdatum',
            'valable', 'expiration',
            'vencimiento', 'validez', 'fecha de caducidad',
            'scadenza', 'valido fino al',
            'gecerlilik', 'son kullanma',
            'amal', 'muddati',
            'ważny do', 'platnost'
        ]

        found_dates = []
        today = datetime.utcnow()

        for line in text_lines:
            line_lower = line.lower()
            matches = date_pattern.findall(line)
            for m in matches:
                try:
                    if m[0]:  # DD.MM.YYYY
                        dd, mm, yyyy = int(m[0]), int(m[1]), int(m[2])
                    else:     # YYYY.MM.DD
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

        # 2. Heuristic disambiguation if labels missing
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

    def _extract_fields_from_visual_zone(self, text_lines: List[str]) -> Dict[str, Any]:
        """
        Multilingual Visual Zone extraction for non-MRZ ID cards and Driver's Licenses
        (English, Russian, Kazakh, Spanish, French, German, Portuguese, Italian, Turkish, Uzbek).
        """
        extracted: Dict[str, Any] = {}

        last_name_labels = [
            'тегі', 'фамилия', 'прізвище', 'familiya', 'familiyasi',
            'surname', 'last name', 'family name',
            'nom', 'nom de famille', 'apellido', 'apellidos',
            'nachname', 'name', 'cognome', 'soyadi', 'nazwisko'
        ]

        first_name_labels = [
            'аты', 'имя', 'ім\'я', 'ism', 'ismi',
            'given name', 'first name', 'forename',
            'prenom', 'nombre', 'nombres',
            'vorname', 'nome', 'adi', 'imie'
        ]

        doc_num_labels = [
            'номер', 'номер документа', '№', 'куәлік', 'құжат', 'hujjat',
            'document number', 'id number', 'license no', 'card no', 'doc no',
            'numero', 'documento', 'ausweisnummer', 'identite'
        ]

        doc_num_pattern = re.compile(r'\b[A-Z0-9]{7,15}\b')

        for idx, raw_line in enumerate(text_lines):
            line = raw_line.strip()
            line_lower = line.lower()

            # Document Number
            if not extracted.get("document_number"):
                if any(lbl in line_lower for lbl in doc_num_labels):
                    matches = doc_num_pattern.findall(line)
                    cleaned_matches = [m for m in matches if not any(lbl in m.lower() for lbl in doc_num_labels)]
                    if cleaned_matches:
                        extracted["document_number"] = cleaned_matches[0]
                    elif idx + 1 < len(text_lines):
                        next_matches = doc_num_pattern.findall(text_lines[idx + 1])
                        if next_matches:
                            extracted["document_number"] = next_matches[0]

            # Surname
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

            # Given Name
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

    def process(self, image_bytes: bytes) -> Tuple[str, str, Dict[str, Any], str]:
        """
        Executes complete multilingual document processing pipeline:
        1. 2D Barcode (PDF417 / AAMVA on Driver's Licenses).
        2. Universal OCR & Text Extraction.
        3. Standard ICAO MRZ Parsing (TD3, TD2, TD1) with Checksum Repair.
        4. Multilingual Visual Zone fallback extraction.
        Returns: (mrz_status, expiry_status, extracted_fields, raw_ocr_json)
        """
        try:
            nparr = np.frombuffer(image_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is None:
                return "ABSENT", "UNKNOWN", {}, json.dumps({"error": "Failed to decode image"})

            # Step 1: Check 2D Barcode
            barcode_data = self._extract_barcode_data(img)
            if barcode_data and barcode_data.get("document_number"):
                exp_date = barcode_data.get("expiry_date")
                exp_status = "VALID"
                if exp_date:
                    try:
                        if datetime.strptime(exp_date, "%Y-%m-%d") < datetime.utcnow():
                            exp_status = "EXPIRED"
                    except Exception:
                        pass
                logger.info("Driver License PDF417 barcode decoded successfully.")
                return "VALID", exp_status, barcode_data, json.dumps({"source": "barcode", "data": barcode_data})

            # Step 2: Multilingual OCR
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

            logger.info(f"Universal EasyOCR detected {len(all_lines)} text lines")

            # Check for national personal number (e.g. Kazakh 12-digit IIN)
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
                    logger.warning("MRZ lines detected but checksum validation failed after repair.")
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
