"""Regenerate the Android app's test fixture from the pipeline's own test fixtures.

The app's unit tests parse this output, so they check that what the pipeline
publishes is what the app expects. Run from pipeline/ after changing the schema:

    python3 scripts/make_android_fixture.py          # rewrite the fixture
    python3 scripts/make_android_fixture.py --check  # CI: fail if it is out of date
"""

import argparse
import difflib
import sys
import tempfile
from datetime import UTC, datetime
from pathlib import Path

PIPELINE_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PIPELINE_DIR))

from fuel_pipeline.build import Thresholds, build, write_output  # noqa: E402
from fuel_pipeline.prices import load_prices  # noqa: E402
from fuel_pipeline.stations import load_stations  # noqa: E402

FIXTURES = PIPELINE_DIR / "tests" / "fixtures"
OUT = PIPELINE_DIR.parent / "android" / "app" / "src" / "test" / "resources" / "fixtures"
FILES = ("stations.json", "meta.json")


def generate(out_dir: Path) -> dict:
    # The fixtures are tiny and full of bad rows on purpose: disable the size checks.
    document = build(
        load_stations(FIXTURES / "stations.csv"),
        load_prices(FIXTURES / "prices.csv"),
        Thresholds(min_stations=0, min_prices=0, max_drop_ratio=1.0),
    ).document
    # Fixed timestamp so the output only changes when the data or format changes.
    return write_output(document, out_dir, generated_at=datetime(2026, 10, 1, 7, 12, 3, tzinfo=UTC))


def check() -> int:
    """Regenerate into a temporary folder and compare with the committed fixture."""
    with tempfile.TemporaryDirectory() as tmp:
        fresh_dir = Path(tmp)
        generate(fresh_dir)
        stale = []
        for name in FILES:
            committed = OUT / name
            fresh = (fresh_dir / name).read_text(encoding="utf-8")
            current = committed.read_text(encoding="utf-8") if committed.exists() else ""
            if fresh != current:
                stale.append(name)
                diff = difflib.unified_diff(
                    current.splitlines(), fresh.splitlines(),
                    f"committed/{name}", f"regenerated/{name}", lineterm="",
                )
                print("\n".join(list(diff)[:40]))
    if stale:
        print(
            f"\nThe Android test fixture is out of date ({', '.join(stale)}): the pipeline "
            "output no longer matches what the app's tests expect.\n"
            "Run `python3 pipeline/scripts/make_android_fixture.py`, check that the app "
            "still handles the new format (`./gradlew test`), and commit the result."
        )
        return 1
    print(f"Android test fixture is up to date ({OUT})")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--check", action="store_true", help="fail if the committed fixture is out of date")
    args = parser.parse_args()
    if args.check:
        return check()
    meta = generate(OUT)
    print(f"wrote {OUT} ({meta['stations']} stations, {meta['prices']} prices)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
