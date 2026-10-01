import unittest

from fuel_pipeline.fuels import FuelType, Unit, classify

P, D, LPG, CNG, LNG = (
    FuelType.PETROL,
    FuelType.DIESEL,
    FuelType.LPG,
    FuelType.CNG,
    FuelType.LNG,
)

# Every descCarburante value seen in the 2026-09-30 MIMIT file, spelled as published.
REAL_NAMES = {
    "Benzina": P, "Gasolio": D, "GPL": LPG, "Metano": CNG, "GNL": LNG, "L-GNC": CNG,
    "Blue Diesel": D, "HVOlution": D, "Supreme Diesel": D, "Blue Super": P, "HVO": D,
    "Hi-Q Diesel": D, "Gasolio speciale": D, "HiQ Perform+": P, "Gasolio Premium": D,
    "Benzina speciale": P, "Benzina WR 100": P, "Diesel Shell V Power": D,
    "DieselMax": D, "HVO100": D, "S-Diesel": D, "Gasolio artico": D,
    "Benzina Shell V Power": P, "Excellium Diesel": D, "Gasolio Oro Diesel": D,
    "REHVO": D, "F101": P, "Diesel HVO": D, "Gasolio Energy D": D, "E-DIESEL": D,
    "Gasolio Ecoplus": D, "GP DIESEL": D, "Gasolio Artico": D,
    "Benzina Energy 98 ottani": P, "BCHVO": D, "Gasolio Bio HVO": D,
    "Benzina Plus 98": P, "Gasolio HVO": D, "Gasolio Gelo": D, "Gasolio Alpino": D,
    "V-Power": P, "Excellium diesel": D, "HVO Future": D, "HVO eco diesel": D,
    "Blu Diesel Alpino": D, "HiQ Perform B100 Ottani": P, "Gasolio Prestazionale": D,
    "Gasolio Plus": D, "Diesel HVO Energy": D, "Verde speciale": P,
    "HVOvolution": D, "HVO Energy Diesel": D, "Benzina Speciale 98 Ottani": P,
    "F-101": P, "Gasolio Artico Igloo": D, "V-Power Diesel": D,
    "Benzina 100 ottani": P, "GASOLIO HVO": D, "Benzina 102 Ottani": P,
}


class ClassifyTest(unittest.TestCase):
    def test_all_real_names_are_known_and_typed(self):
        for name, expected in REAL_NAMES.items():
            with self.subTest(name=name):
                info = classify(name)
                self.assertEqual(info.type, expected)
                self.assertTrue(info.known)

    def test_standard_fuels(self):
        for name in ("Benzina", "Gasolio", "GPL", "Metano", "GNL", "L-GNC"):
            self.assertTrue(classify(name).std, name)
        self.assertFalse(classify("Blue Diesel").std)
        self.assertFalse(classify("HVO").std)

    def test_units(self):
        self.assertEqual(classify("Benzina").unit, Unit.LITRE)
        self.assertEqual(classify("GPL").unit, Unit.LITRE)
        self.assertEqual(classify("Metano").unit, Unit.KILOGRAM)
        self.assertEqual(classify("L-GNC").unit, Unit.KILOGRAM)
        self.assertEqual(classify("GNL").unit, Unit.KILOGRAM)

    def test_case_and_whitespace_are_ignored(self):
        self.assertEqual(classify("  gasolio   ARTICO ").type, D)
        self.assertTrue(classify("BENZINA").std)

    def test_unknown_name_uses_keywords_and_is_flagged(self):
        info = classify("Super Diesel Xtra")
        self.assertEqual(info.type, D)  # diesel rule wins over "super"
        self.assertFalse(info.known)
        self.assertFalse(info.std)
        self.assertEqual(classify("Metano Bio").type, CNG)
        self.assertEqual(classify("Metano Bio").unit, Unit.KILOGRAM)

    def test_unrecognizable_name_is_other(self):
        info = classify("AdBlue")
        self.assertEqual(info.type, FuelType.OTHER)
        self.assertFalse(info.known)


if __name__ == "__main__":
    unittest.main()
