"""Join stations and prices and write the published files (stations.json, meta.json)."""

import hashlib
import json
import logging
from collections import defaultdict
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from .fuels import FuelType
from .prices import Price, PriceFile, prices_at
from .report import DropReport
from .stations import Station, StationFile

# Bump only for breaking changes; the app rejects unknown major versions.
# Adding new keys is not a breaking change (the app must ignore unknown keys).
SCHEMA_VERSION = 1
STATIONS_FILE = "stations.json"
META_FILE = "meta.json"
SOURCE = "Ministero delle Imprese e del Made in Italy — Osservaprezzi Carburanti"
LICENSE = "IODL-2.0"

log = logging.getLogger(__name__)


class PipelineError(Exception):
    """The data looks wrong: stop instead of publishing it."""


@dataclass(frozen=True)
class Thresholds:
    """Run-level sanity checks. Defaults fit the real files (~21k stations, ~92k prices)."""

    min_stations: int = 15_000
    min_prices: int = 50_000
    max_drop_ratio: float = 0.10  # per input file


@dataclass
class BuildResult:
    document: dict
    join_report: DropReport


def build(stations: StationFile, prices: PriceFile, thresholds: Thresholds = Thresholds()) -> BuildResult:
    if stations.extraction_date != prices.extraction_date:
        raise PipelineError(
            f"extraction dates differ: stations {stations.extraction_date}, "
            f"prices {prices.extraction_date}"
        )
    for report in (stations.report, prices.report):
        if report.rows_read and report.dropped_total / report.rows_read > thresholds.max_drop_ratio:
            raise PipelineError(
                f"{report.name}: {report.dropped_total} of {report.rows_read} rows dropped"
            )

    join_report = DropReport("join")
    by_station: dict[int, list[Price]] = defaultdict(list)
    for price in prices.prices:
        if price.station_id in stations.stations:
            by_station[price.station_id].append(price)
        else:
            join_report.drop("price for unknown station", price.station_id)
    for station_id in stations.stations:
        if station_id not in by_station:
            join_report.drop("station without valid prices", station_id)

    kept = [stations.stations[i] for i in sorted(by_station)]
    price_count = sum(len(v) for v in by_station.values())
    if len(kept) < thresholds.min_stations or price_count < thresholds.min_prices:
        raise PipelineError(f"too little data: {len(kept)} stations, {price_count} prices")

    document = _document(stations.extraction_date.isoformat(), kept, by_station)
    return BuildResult(document, join_report)


def _document(data_date: str, kept: list[Station], by_station: dict[int, list[Price]]) -> dict:
    brands = sorted({s.brand for s in kept}, key=str.casefold)
    brand_index = {b: i for i, b in enumerate(brands)}

    # Fuel table: grouped by type (enum order), standard fuel first, then by name.
    type_order = list(FuelType)
    fuel_infos = {p.fuel_name: p.fuel for prices in by_station.values() for p in prices}
    fuel_names = sorted(
        fuel_infos,
        key=lambda n: (type_order.index(fuel_infos[n].type), not fuel_infos[n].std, n.casefold()),
    )
    fuel_index = {n: i for i, n in enumerate(fuel_names)}

    return {
        "schemaVersion": SCHEMA_VERSION,
        "dataDate": data_date,
        "brands": brands,
        "fuels": [
            {
                "name": n,
                "type": fuel_infos[n].type.value,
                "unit": fuel_infos[n].unit.value,
                "std": fuel_infos[n].std,
            }
            for n in fuel_names
        ],
        "stations": [
            {
                "id": s.id,
                "n": s.name,
                "b": brand_index[s.brand],
                "hw": int(s.motorway),
                "a": s.address,
                "c": s.municipality,
                "pr": s.province,
                "lat": s.lat,
                "lon": s.lon,
                "f": sorted(
                    (
                        [fuel_index[p.fuel_name], p.price_milli, int(p.is_self), int(p.updated.timestamp())]
                        for p in by_station[s.id]
                    ),
                    # by fuel, then served before self-service
                    key=lambda entry: (entry[0], entry[2]),
                ),
            }
            for s in kept
        ],
    }


def write_output(document: dict, out_dir: Path, generated_at: datetime | None = None) -> dict:
    """Write stations.json, then meta.json (last, so it never points to a missing file)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    _write_atomic(out_dir / STATIONS_FILE, payload)

    generated_at = generated_at or datetime.now(UTC)
    data_date = datetime.fromisoformat(document["dataDate"]).date()
    meta = {
        "schemaVersion": SCHEMA_VERSION,
        "dataDate": document["dataDate"],
        "pricesAt": prices_at(data_date).isoformat(),
        "generatedAt": generated_at.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "stations": len(document["stations"]),
        "prices": sum(len(s["f"]) for s in document["stations"]),
        "file": STATIONS_FILE,
        "bytes": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "source": SOURCE,
        "license": LICENSE,
    }
    meta_payload = json.dumps(meta, ensure_ascii=False, indent=2).encode("utf-8")
    _write_atomic(out_dir / META_FILE, meta_payload)
    log.info("wrote %s (%d bytes, %d stations, %d prices)",
             out_dir / STATIONS_FILE, len(payload), meta["stations"], meta["prices"])
    return meta


def _write_atomic(path: Path, payload: bytes) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_bytes(payload)
    tmp.replace(path)
