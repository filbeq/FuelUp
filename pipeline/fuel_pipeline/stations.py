"""Parse the station registry (anagrafica_impianti_attivi.csv)."""

import math
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from .csvfile import SEPARATOR, clean_text, parse_header, read_lines
from .report import DropReport

COLUMNS = [
    "idImpianto", "Gestore", "Bandiera", "Tipo Impianto", "Nome Impianto",
    "Indirizzo", "Comune", "Provincia", "Latitudine", "Longitudine",
]
STATION_TYPES = ("Stradale", "Autostradale")
MOTORWAY = "Autostradale"

# Generous bounding box around Italy, San Marino and Vatican City.
LAT_RANGE = (35.0, 47.2)
LON_RANGE = (6.5, 18.6)
COORD_DECIMALS = 5  # ~1 m, plenty for a map pin

# Misplaced stations: coordinates are entered by operators and sometimes copied
# from another registration (e.g. a Sorrento station drawn on top of one in
# Ventimiglia). A station is dropped when no station of its own province is within
# MISPLACED_KM, but a station of another province is MISPLACED_RATIO times closer
# than the nearest one of its own. Real islands (Ustica, Linosa, ...) pass: nothing
# of another province is near them either.
MISPLACED_KM = 25.0
MISPLACED_RATIO = 3.0
KM_PER_DEG_LAT = 111.2
# Grid cells at least MISPLACED_KM wide (in longitude, at Italy's northern tip), so
# the 3x3 cells around a station hold every station within MISPLACED_KM.
_CELL_DEG = 0.35

# Some station names carry a junk " | gestori.prezzibenzina.it" suffix, which also
# breaks the column count (the "|" is the separator).
_JUNK_RE = re.compile(r"\s*\|\s*gestori\.prezzibenzina\.it", re.IGNORECASE)


@dataclass(frozen=True)
class Station:
    id: int
    name: str
    brand: str
    motorway: bool
    address: str
    municipality: str
    province: str
    lat: float
    lon: float


@dataclass
class StationFile:
    extraction_date: date
    stations: dict[int, Station]
    report: DropReport


def load_stations(path: Path) -> StationFile:
    return parse_stations(read_lines(path))


def parse_stations(lines: list[str]) -> StationFile:
    extraction_date = parse_header(lines, COLUMNS)
    report = DropReport("stations")
    stations: dict[int, Station] = {}

    for line in lines[2:]:
        if not line.strip():
            continue
        report.rows_read += 1
        fields = line.split(SEPARATOR)
        ident = clean_text(fields[0])

        if len(fields) != len(COLUMNS):
            fields = _recover_fields(fields)
            if fields is None:
                report.drop("wrong number of fields", ident)
                continue
            report.note("extra separators recovered", ident)

        try:
            station_id = int(ident)
        except ValueError:
            report.drop("invalid station id", ident)
            continue

        coords = _parse_coords(fields[8], fields[9])
        if isinstance(coords, str):
            report.drop(coords, station_id)
            continue

        if station_id in stations:
            report.drop("duplicate station id", station_id)
            continue

        name = clean_text(_JUNK_RE.sub("", fields[4]))
        if not name:
            report.note("empty station name", station_id)
        stations[station_id] = Station(
            id=station_id,
            name=name,
            brand=clean_text(fields[2]),
            motorway=clean_text(fields[3]) == MOTORWAY,
            address=clean_text(fields[5]),
            municipality=clean_text(fields[6]),
            province=clean_text(fields[7]),
            lat=coords[0],
            lon=coords[1],
        )

    _drop_misplaced(stations, report)
    return StationFile(extraction_date, stations, report)


def _drop_misplaced(stations: dict[int, Station], report: DropReport) -> None:
    """Drop stations drawn in another province (see MISPLACED_KM)."""
    by_province: dict[str, list[Station]] = {}
    cells: dict[tuple[str, int, int], list[Station]] = {}
    for s in stations.values():
        by_province.setdefault(s.province, []).append(s)
        cells.setdefault((s.province, *_cell(s)), []).append(s)

    misplaced = []
    for s in stations.values():
        row, col = _cell(s)
        near = (
            o
            for r in (row - 1, row, row + 1)
            for c in (col - 1, col, col + 1)
            for o in cells.get((s.province, r, c), ())
        )
        # Most stations have one of their own province nearby: cheap check first.
        if any(_km(s, o) <= MISPLACED_KM for o in near if _other_spot(s, o)):
            continue
        own = min((_km(s, o) for o in by_province[s.province] if _other_spot(s, o)), default=None)
        if own is None:
            continue  # the only station of its province: nothing to compare with
        other = min((_km(s, o) for o in stations.values() if o.province != s.province), default=None)
        if other is not None and other * MISPLACED_RATIO < own:
            misplaced.append((s, own, other))

    for s, own, other in misplaced:
        del stations[s.id]
        report.drop(
            "coordinates in another province",
            f"{s.id} {s.municipality} ({s.province}): {own:.0f} km from own province, "
            f"{other:.1f} km from another",
        )


def _other_spot(s: Station, o: Station) -> bool:
    """A different station at different coordinates (a copy of s's own doesn't count)."""
    return o.id != s.id and (o.lat, o.lon) != (s.lat, s.lon)


def _cell(s: Station) -> tuple[int, int]:
    return math.floor(s.lat / _CELL_DEG), math.floor(s.lon / _CELL_DEG)


def _km(a: Station, b: Station) -> float:
    """Distance in km; a flat approximation, fine at these scales."""
    x = (a.lon - b.lon) * KM_PER_DEG_LAT * math.cos(math.radians((a.lat + b.lat) / 2))
    y = (a.lat - b.lat) * KM_PER_DEG_LAT
    return math.hypot(x, y)


def _recover_fields(fields: list[str]) -> list[str] | None:
    """Rebuild a row that has extra "|" inside its text fields.

    The station type (Stradale/Autostradale) is used as an anchor: Gestore sits
    between the id and Bandiera, which is right before the type. The last five
    fields (Indirizzo, Comune, Provincia, lat, lon) never contain "|" in the data
    seen so far, so whatever is left between the type and them is the name.
    """
    if len(fields) < len(COLUMNS):
        return None
    anchors = [i for i, f in enumerate(fields) if f.strip() in STATION_TYPES]
    if len(anchors) != 1:
        return None
    t = anchors[0]
    if t < 3 or t > len(fields) - 7:
        return None
    return [
        fields[0],
        SEPARATOR.join(fields[1 : t - 1]),
        fields[t - 1],
        fields[t],
        SEPARATOR.join(fields[t + 1 : -5]),
        *fields[-5:],
    ]


def _parse_coords(lat_text: str, lon_text: str) -> tuple[float, float] | str:
    """Return (lat, lon), or a drop reason."""
    lat_text, lon_text = lat_text.strip(), lon_text.strip()
    if not lat_text or not lon_text:
        return "missing coordinates"
    try:
        lat, lon = float(lat_text), float(lon_text)
    except ValueError:
        return "invalid coordinates"
    if not (math.isfinite(lat) and math.isfinite(lon)):
        return "invalid coordinates"
    if lat == 0 or lon == 0:
        return "zero coordinates"
    if not (LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LON_RANGE[0] <= lon <= LON_RANGE[1]):
        return "coordinates outside Italy"
    return round(lat, COORD_DECIMALS), round(lon, COORD_DECIMALS)
