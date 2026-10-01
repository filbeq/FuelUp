"""End-to-end tests on the fixture files: join, JSON document and written files."""

import hashlib
import json
import tempfile
import unittest
from dataclasses import replace
from datetime import UTC, date, datetime
from pathlib import Path

from fuel_pipeline.build import PipelineError, Thresholds, build, write_output
from fuel_pipeline.prices import load_prices
from fuel_pipeline.stations import load_stations

FIXTURES = Path(__file__).parent / "fixtures"
# The fixtures are tiny and full of bad rows on purpose: disable the size checks.
NO_LIMITS = Thresholds(min_stations=0, min_prices=0, max_drop_ratio=1.0)


def epoch(*args) -> int:
    return int(datetime(*args, tzinfo=UTC).timestamp())


class BuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stations = load_stations(FIXTURES / "stations.csv")
        cls.prices = load_prices(FIXTURES / "prices.csv")
        cls.result = build(cls.stations, cls.prices, NO_LIMITS)
        cls.doc = cls.result.document

    def test_header(self):
        self.assertEqual(self.doc["schemaVersion"], 1)
        self.assertEqual(self.doc["dataDate"], "2026-09-30")

    def test_only_stations_with_prices_are_kept(self):
        self.assertEqual([s["id"] for s in self.doc["stations"]], [3464, 40820, 54386])

    def test_lookup_tables(self):
        self.assertEqual(self.doc["brands"], ["Api-Ip", "Pompe Bianche"])
        self.assertEqual(
            self.doc["fuels"],
            [
                {"name": "Benzina", "type": "PETROL", "unit": "L", "std": True},
                {"name": "Gasolio", "type": "DIESEL", "unit": "L", "std": True},
                {"name": "GPL", "type": "LPG", "unit": "L", "std": True},
                {"name": "Metano", "type": "CNG", "unit": "KG", "std": True},
                {"name": "Etanolo E85", "type": "OTHER", "unit": "L", "std": False},
            ],
        )

    def test_station_record(self):
        t1, t2 = epoch(2026, 9, 29, 18, 30, 7), epoch(2026, 9, 29, 18, 30, 8)
        t0 = epoch(2026, 9, 29, 18, 30, 6)
        self.assertEqual(
            self.doc["stations"][0],
            {
                "id": 3464,
                "n": "PO EST",
                "b": 0,
                "hw": 1,
                "a": "Autostrada A13 BOLOGNA-PADOVA, Km. 43+400, dir. Nord - 44100",
                "c": "FERRARA",
                "pr": "FE",
                "lat": 44.88012,
                "lon": 11.57083,
                "f": [
                    [0, 2409, 0, t1],
                    [0, 2049, 1, t2],
                    [1, 2609, 0, t1],
                    [1, 2249, 1, t2],
                    [2, 849, 0, t0],
                    [3, 1794, 0, t0],
                ],
            },
        )

    def test_recovered_station_and_special_fuel(self):
        s = self.doc["stations"][2]
        self.assertEqual((s["n"], s["b"], s["hw"]), ("PRADELLI - MONTEOMBRARO", 1, 0))
        self.assertEqual([p[0] for p in s["f"]], [0, 1, 4])

    def test_join_report(self):
        report = self.result.join_report
        self.assertEqual(report.dropped["price for unknown station"], 8)
        self.assertEqual(report.dropped["station without valid prices"], 9)


class SafetyCheckTest(unittest.TestCase):
    def setUp(self):
        self.stations = load_stations(FIXTURES / "stations.csv")
        self.prices = load_prices(FIXTURES / "prices.csv")

    def test_extraction_dates_must_match(self):
        prices = replace(self.prices, extraction_date=date(2026, 9, 29))
        with self.assertRaisesRegex(PipelineError, "extraction dates differ"):
            build(self.stations, prices, NO_LIMITS)

    def test_too_few_stations(self):
        with self.assertRaisesRegex(PipelineError, "too little data"):
            build(self.stations, self.prices, replace(NO_LIMITS, min_stations=4))

    def test_too_few_prices(self):
        with self.assertRaisesRegex(PipelineError, "too little data"):
            build(self.stations, self.prices, replace(NO_LIMITS, min_prices=100))

    def test_too_many_dropped_rows(self):
        # 8 of 20 station rows are dropped in the fixture.
        with self.assertRaisesRegex(PipelineError, "stations: 8 of 20"):
            build(self.stations, self.prices, replace(NO_LIMITS, max_drop_ratio=0.3))


class WriteOutputTest(unittest.TestCase):
    def test_files_and_meta(self):
        doc = build(
            load_stations(FIXTURES / "stations.csv"), load_prices(FIXTURES / "prices.csv"), NO_LIMITS
        ).document
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            meta = write_output(doc, out, generated_at=datetime(2026, 10, 1, 7, 12, 3, tzinfo=UTC))
            payload = (out / "stations.json").read_bytes()
            self.assertEqual(json.loads(payload), doc)
            self.assertEqual(json.loads((out / "meta.json").read_text(encoding="utf-8")), meta)
            self.assertEqual(sorted(p.name for p in out.iterdir()), ["meta.json", "stations.json"])

        self.assertNotIn(b": ", payload)  # compact separators
        self.assertEqual(
            meta,
            {
                "schemaVersion": 1,
                "dataDate": "2026-09-30",
                "pricesAt": "2026-09-30T08:00:00+02:00",
                "generatedAt": "2026-10-01T07:12:03Z",
                "stations": 3,
                "prices": 10,
                "file": "stations.json",
                "bytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
                "source": "Ministero delle Imprese e del Made in Italy — Osservaprezzi Carburanti",
                "license": "IODL-2.0",
            },
        )


if __name__ == "__main__":
    unittest.main()
