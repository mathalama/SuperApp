import re
import logging
from typing import Optional, Tuple
from datetime import datetime

logger = logging.getLogger(__name__)

# Official weights for Republic of Kazakhstan IIN 12th check digit
IIN_WEIGHTS_R1 = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]
IIN_WEIGHTS_R2 = [3, 4, 5, 6, 7, 8, 9, 10, 11, 1, 2]


class IinValidator:
    """
    Validates Kazakhstan 12-digit Individual Identification Number (ИИН / ЖСН).
    Performs algorithmic check digit verification and DOB / gender structure consistency.
    """

    @staticmethod
    def calculate_check_digit(iin_11: str) -> int:
        """Calculates official 12th control digit according to national standard."""
        if len(iin_11) != 11 or not iin_11.isdigit():
            return -1

        digits = [int(c) for c in iin_11]

        # Round 1
        s1 = sum(d * w for d, w in zip(digits, IIN_WEIGHTS_R1))
        rem = s1 % 11
        if rem < 10:
            return rem

        # Round 2 if rem == 10
        s2 = sum(d * w for d, w in zip(digits, IIN_WEIGHTS_R2))
        rem2 = s2 % 11
        if rem2 < 10:
            return rem2

        return 0

    @classmethod
    def validate(cls, iin_str: str) -> Tuple[bool, Optional[str]]:
        """
        Validates 12-digit IIN.
        Returns: (is_valid, optional_reason)
        """
        if not iin_str or not isinstance(iin_str, str):
            return False, "EMPTY_IIN"

        cleaned = iin_str.strip().replace(" ", "")
        if len(cleaned) != 12 or not cleaned.isdigit():
            return False, "INVALID_LENGTH_OR_NON_DIGIT"

        # Check 7th digit (century and gender: 1-6)
        century_digit = int(cleaned[6])
        if century_digit < 1 or century_digit > 6:
            return False, "INVALID_CENTURY_GENDER_DIGIT"

        # Validate Date of Birth embedded in first 6 digits (YYMMDD)
        yy = int(cleaned[:2])
        mm = int(cleaned[2:4])
        dd = int(cleaned[4:6])

        if mm < 1 or mm > 12 or dd < 1 or dd > 31:
            return False, "INVALID_DOB_RANGE"

        # Century mapping
        century_base = 1800 if century_digit in (1, 2) else (1900 if century_digit in (3, 4) else 2000)
        full_year = century_base + yy

        try:
            dob = datetime(full_year, mm, dd)
            if dob > datetime.utcnow():
                return False, "DOB_IN_FUTURE"
        except ValueError:
            return False, "INVALID_CALENDAR_DATE"

        # Check Digit calculation
        expected_check = cls.calculate_check_digit(cleaned[:11])
        actual_check = int(cleaned[11])

        if expected_check != actual_check:
            logger.warning(f"IIN Checksum mismatch: expected {expected_check}, got {actual_check}")
            return False, "CHECKSUM_FAILED"

        return True, None

    @classmethod
    def extract_and_validate_from_text(cls, text_lines: list[str]) -> Optional[str]:
        """
        Searches OCR text for valid 12-digit IIN pattern.
        """
        iin_pattern = re.compile(r'\b(\d{12})\b')
        iin_keywords = ['жсн', 'иин', 'iin', 'personal', 'идентификационный']

        # First priority: lines containing IIN keywords
        for line in text_lines:
            line_lower = line.lower()
            if any(kw in line_lower for kw in iin_keywords):
                matches = iin_pattern.findall(line)
                for candidate in matches:
                    is_valid, _ = cls.validate(candidate)
                    if is_valid:
                        return candidate

        # Second priority: any valid 12-digit number across all lines
        for line in text_lines:
            matches = iin_pattern.findall(line)
            for candidate in matches:
                is_valid, _ = cls.validate(candidate)
                if is_valid:
                    return candidate

        return None
