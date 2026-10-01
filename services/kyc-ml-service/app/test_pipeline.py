import unittest
import numpy as np
from app.pipeline.ocr_mrz_reader import (
    transliterate_to_latin,
    calculate_icao_check_digit,
    verify_and_repair_numeric_field,
    OcrMrzReader
)
from app.pipeline.liveness_detector import LivenessDetector


class TestKycPipeline(unittest.TestCase):
    def test_icao_transliteration(self):
        # Kazakh + Russian Cyrillic names
        cyrillic_name = "АХМЕТОВ ЕРЛАН ӘБДІРАХМАНҰЛЫ"
        transliterated = transliterate_to_latin(cyrillic_name)
        self.assertEqual(transliterated, "AKHMETOV ERLAN ABDIRAKHMANULY")

    def test_icao_check_digit(self):
        # ICAO check digit calculation for "HA672241"
        # H=17*7=119, A=10*3=30, 6*1=6, 7*7=49, 2*3=6, 2*1=2, 4*7=28, 1*3=3 -> Total 243 % 10 = 3
        cd = calculate_icao_check_digit("HA672241")
        self.assertEqual(cd, "3")

    def test_check_digit_error_repair(self):
        # OCR read 'O' instead of '0' in birthdate: "74O812" instead of "740812"
        # Check digit for "740812": 7*7 + 3*4 + 1*0 + 7*8 + 3*1 + 1*2 = 49 + 12 + 0 + 56 + 3 + 2 = 122 % 10 = 2
        valid, repaired, cd = verify_and_repair_numeric_field("74O812", "2")
        self.assertTrue(valid)
        self.assertEqual(repaired, "740812")

    def test_td3_passport_parsing(self):
        reader = OcrMrzReader()
        l1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
        l2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
        res = reader.parse_td3(l1, l2)
        self.assertIsNotNone(res)
        self.assertEqual(res["format"], "TD3")
        self.assertEqual(res["last_name"], "ERIKSSON")
        self.assertEqual(res["first_name"], "ANNA MARIA")
        self.assertEqual(res["document_number"], "L898902C3")
        self.assertEqual(res["date_of_birth"], "1974-08-12")
        self.assertEqual(res["gender"], "F")

    def test_td1_id_card_parsing(self):
        reader = OcrMrzReader()
        l1 = "I<UTOD231458907<<<<<<<<<<<<<<<"
        l2 = "7408122F1204159UTO<<<<<<<<<<<6"
        l3 = "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"
        res = reader.parse_td1(l1, l2, l3)
        self.assertIsNotNone(res)
        self.assertEqual(res["format"], "TD1")
        self.assertEqual(res["last_name"], "ERIKSSON")
        self.assertEqual(res["first_name"], "ANNA MARIA")
        self.assertEqual(res["date_of_birth"], "1974-08-12")

    def test_td2_visa_id_parsing(self):
        reader = OcrMrzReader()
        l1 = "I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<"
        l2 = "D231458907UTO7408122F1204159<<<<<<<1"
        res = reader.parse_td2(l1, l2)
        self.assertIsNotNone(res)
        self.assertEqual(res["format"], "TD2")
        self.assertEqual(res["last_name"], "ERIKSSON")
        self.assertEqual(res["first_name"], "ANNA MARIA")
        self.assertEqual(res["document_number"], "D23145890")

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
