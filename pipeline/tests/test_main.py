"""Command-line and download tests. No network: downloads use file:// URLs."""

import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path

from fuel_pipeline.__main__ import main
from fuel_pipeline.build import Thresholds
from fuel_pipeline.download import download

FIXTURES = Path(__file__).parent / "fixtures"
NO_LIMITS = Thresholds(min_stations=0, min_prices=0, max_drop_ratio=1.0)
LOCAL_FILES = ["--prices", str(FIXTURES / "prices.csv"), "--stations", str(FIXTURES / "stations.csv")]


class MainTest(unittest.TestCase):
    def test_local_files_produce_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "out"
            with self.assertLogs("fuel_pipeline", "INFO"):
                code = main(LOCAL_FILES + ["--out", str(out)], NO_LIMITS)
            self.assertEqual(code, 0)
            meta = json.loads((out / "meta.json").read_text(encoding="utf-8"))
            self.assertEqual(meta["stations"], 3)

    def test_broken_data_fails_without_writing(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "out"
            with self.assertLogs("fuel_pipeline", "ERROR") as logs:
                code = main(LOCAL_FILES + ["--out", str(out)])  # real thresholds
            self.assertEqual(code, 1)
            self.assertIn("pipeline failed", logs.output[-1])
            self.assertFalse(out.exists())

    def test_missing_file_fails(self):
        with self.assertLogs("fuel_pipeline", "ERROR"):
            code = main(["--prices", "nope.csv", "--stations", "nope.csv"], NO_LIMITS)
        self.assertEqual(code, 1)

    def test_prices_and_stations_go_together(self):
        with redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            main(["--prices", "x.csv"])


class DownloadTest(unittest.TestCase):
    def test_download_saves_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = download((FIXTURES / "prices.csv").as_uri(), Path(tmp) / "sub" / "p.csv")
            self.assertEqual(dest.read_bytes(), (FIXTURES / "prices.csv").read_bytes())

    def test_download_gives_up_after_retries(self):
        with tempfile.TemporaryDirectory() as tmp, self.assertLogs("fuel_pipeline", "WARNING") as logs:
            with self.assertRaises(OSError):
                download(Path(tmp, "missing.csv").as_uri(), Path(tmp) / "p.csv", attempts=2, retry_delay=0)
        self.assertEqual(len(logs.output), 2)


if __name__ == "__main__":
    unittest.main()
