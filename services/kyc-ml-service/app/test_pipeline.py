import unittest
import numpy as np
from app.pipeline.ocr_mrz_reader import transliterate_to_latin, OcrMrzReader
from app.pipeline.liveness_detector import LivenessDetector


class TestKycPipeline(unittest.TestCase):
    def test_icao_transliteration(self):
        # Kazakh + Russian Cyrillic names
        cyrillic_name = "АХМЕТОВ ЕРЛАН ӘБДІРАХМАНҰЛЫ"
        transliterated = transliterate_to_latin(cyrillic_name)
        # In ICAO Doc 9303, 'Ы' -> 'Y'
        self.assertEqual(transliterated, "AKHMETOV ERLAN ABDIRAKHMANULY")

    def test_multilingual_dates_extraction(self):
        reader = OcrMrzReader()
        lines = [
            "РЕСПУБЛИКА КАЗАХСТАН",
            "Туған күні: 15.08.1995",
            "Жарамдылық мерзімі: 25.12.2032"
        ]
        status, dob, exp = reader._extract_dates_from_text(lines)
        self.assertEqual(dob, "1995-08-15")
        self.assertEqual(exp, "2032-12-25")
        self.assertEqual(status, "VALID")

    def test_visual_zone_non_mrz_extraction(self):
        reader = OcrMrzReader()
        lines = [
            "ҚАЗАҚСТАН РЕСПУБЛИКАСЫ",
            "ЖЕКЕ КУӘЛІК № 042918273",
            "ТЕГІ / SURNAME",
            "СЕЙТҚАЛИЕВ",
            "АТЫ / GIVEN NAME",
            "ӘЛІШЕР"
        ]
        fields = reader._extract_fields_from_visual_zone(lines)
        self.assertEqual(fields.get("document_number"), "042918273")
        self.assertEqual(fields.get("last_name"), "SEITQALIEV")
        self.assertEqual(fields.get("first_name"), "ALISHER")

    def test_liveness_challenge_evaluation(self):
        detector = LivenessDetector()
        img = np.zeros((300, 300, 3), dtype=np.uint8)
        res_center = detector.evaluate_challenge(img, "CENTER")
        self.assertIn("passed", res_center)
        self.assertIn("angles", res_center)


if __name__ == "__main__":
    unittest.main()
