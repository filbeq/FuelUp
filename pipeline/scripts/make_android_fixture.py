"""Regenerate the Android app's test fixture from the pipeline's own test fixtures.

The app's unit tests parse this output, so they check that what the pipeline
publishes is what the app expects. Run from pipeline/ after changing the schema:

    python3 scripts/make_android_fixture.py
"""

import sys
from datetime import UTC, datetime
from pathlib import Path

PIPELINE_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PIPELINE_DIR))

from fuel_pipeline.build import Thresholds, build, write_output  # noqa: E402
from fuel_pipeline.prices import load_prices  # noqa: E402
from fuel_pipeline.stations import load_stations  # noqa: E402

FIXTURES = PIPELINE_DIR / "tests" / "fixtures"
OUT = PIPELINE_DIR.parent / "android" / "app" / "src" / "test" / "resources" / "fixtures"

# The fixtures are tiny and full of bad rows on purpose: disable the size checks.
document = build(
    load_stations(FIXTURES / "stations.csv"),
    load_prices(FIXTURES / "prices.csv"),
    Thresholds(min_stations=0, min_prices=0, max_drop_ratio=1.0),
).document
# Fixed timestamp so the output only changes when the data or format changes.
meta = write_output(document, OUT, generated_at=datetime(2026, 10, 1, 7, 12, 3, tzinfo=UTC))
print(f"wrote {OUT} ({meta['stations']} stations, {meta['prices']} prices)")
