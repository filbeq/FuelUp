"""Command line: python -m fuel_pipeline [--prices FILE --stations FILE] [--out DIR]

Without --prices/--stations the latest files are downloaded from MIMIT first.
Exit code 0 on success, 1 when the data could not be downloaded or looks broken
(in which case no output is written).
"""

import argparse
import logging
import sys
from pathlib import Path

from .build import PipelineError, Thresholds, build, write_output
from .csvfile import FormatError
from .download import PRICES_URL, STATIONS_URL, download
from .prices import load_prices
from .stations import load_stations

PIPELINE_DIR = Path(__file__).resolve().parent.parent
log = logging.getLogger("fuel_pipeline")


def main(argv: list[str] | None = None, thresholds: Thresholds = Thresholds()) -> int:
    parser = argparse.ArgumentParser(prog="python -m fuel_pipeline", description=__doc__.split("\n")[0])
    parser.add_argument("--prices", type=Path, help="local prezzo_alle_8.csv (skips download)")
    parser.add_argument("--stations", type=Path, help="local anagrafica_impianti_attivi.csv (skips download)")
    parser.add_argument("--data-dir", type=Path, default=PIPELINE_DIR / "data", help="where downloads are saved")
    parser.add_argument("--out", type=Path, default=PIPELINE_DIR / "out", help="output directory")
    args = parser.parse_args(argv)
    if (args.prices is None) != (args.stations is None):
        parser.error("--prices and --stations must be given together")

    try:
        if args.prices is None:
            args.prices = download(PRICES_URL, args.data_dir / "prezzo_alle_8.csv")
            args.stations = download(STATIONS_URL, args.data_dir / "anagrafica_impianti_attivi.csv")
        stations = load_stations(args.stations)
        prices = load_prices(args.prices)
        stations.report.log(log)
        prices.report.log(log)
        result = build(stations, prices, thresholds)
        result.join_report.log(log)
        write_output(result.document, args.out)
    except (OSError, FormatError, PipelineError) as error:
        log.error("pipeline failed: %s", error)
        return 1
    return 0


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    sys.exit(main())
