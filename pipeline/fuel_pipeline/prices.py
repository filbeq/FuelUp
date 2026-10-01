"""Parse the daily price file (prezzo_alle_8.csv)."""

from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path
from zoneinfo import ZoneInfo

from .csvfile import SEPARATOR, clean_text, parse_header, read_lines
from .fuels import FuelInfo, FuelType, classify
from .report import DropReport

COLUMNS = ["idImpianto", "descCarburante", "prezzo", "isSelf", "dtComu"]
ITALY_TZ = ZoneInfo("Europe/Rome")
DATE_FORMAT = "%d/%m/%Y %H:%M:%S"

# Prices are those in force at 08:00 (Italian time) on the extraction date.
PRICES_AT = time(8, 0)
MAX_AGE = timedelta(days=8)
# A few minutes past 08:00 is normal (extraction lag); a day ahead is a clock error.
MAX_AHEAD = timedelta(days=1)

# Plausible range per fuel type, in thousandths of a euro per litre (or per kg).
# Wide on purpose: they only catch typos such as 0.100 or 8.888.
PRICE_BOUNDS = {
    FuelType.PETROL: (800, 4000),
    FuelType.DIESEL: (800, 4000),
    FuelType.LPG: (300, 2000),
    FuelType.CNG: (500, 4000),
    FuelType.LNG: (500, 4000),
    FuelType.OTHER: (300, 4000),
}


@dataclass(frozen=True)
class Price:
    station_id: int
    fuel_name: str
    fuel: FuelInfo
    price_milli: int  # 2409 means 2.409 €
    is_self: bool
    updated: datetime  # in UTC


@dataclass
class PriceFile:
    extraction_date: date
    prices: list[Price]
    report: DropReport


def prices_at(extraction_date: date) -> datetime:
    """The moment the published prices refer to."""
    return datetime.combine(extraction_date, PRICES_AT, tzinfo=ITALY_TZ)


def load_prices(path: Path) -> PriceFile:
    return parse_prices(read_lines(path))


def parse_prices(lines: list[str]) -> PriceFile:
    extraction_date = parse_header(lines, COLUMNS)
    # All comparisons happen in UTC: Python ignores the UTC offset when comparing two
    # datetimes that share a tzinfo, which is off by an hour across a DST change.
    reference = prices_at(extraction_date).astimezone(UTC)
    report = DropReport("prices")
    # One price per (station, fuel name, self/served): the most recently updated.
    latest: dict[tuple[int, str, bool], Price] = {}

    for line in lines[2:]:
        if not line.strip():
            continue
        report.rows_read += 1
        fields = [clean_text(f) for f in line.split(SEPARATOR)]
        ident = fields[0]
        if len(fields) != len(COLUMNS):
            report.drop("wrong number of fields", ident)
            continue

        price = _parse_row(fields, report)
        if price is None:
            continue

        if price.updated < reference - MAX_AGE:
            report.drop("stale price (older than 8 days)", ident)
            continue
        if price.updated > reference + MAX_AHEAD:
            report.drop("update date in the future", ident)
            continue
        low, high = PRICE_BOUNDS[price.fuel.type]
        if not low <= price.price_milli <= high:
            report.drop("price out of plausible range", f"{ident} {price.fuel_name} {fields[2]}")
            continue
        if not price.fuel.known:
            report.note(f"unknown fuel name {price.fuel_name!r} -> {price.fuel.type}", ident)

        key = (price.station_id, price.fuel_name, price.is_self)
        previous = latest.get(key)
        if previous is not None:
            report.drop("duplicate price, older one dropped", ident)
            if previous.updated >= price.updated:
                continue
        latest[key] = price

    return PriceFile(extraction_date, list(latest.values()), report)


def _parse_row(fields: list[str], report: DropReport) -> Price | None:
    ident, fuel_name, price_text, self_text, date_text = fields
    try:
        station_id = int(ident)
    except ValueError:
        report.drop("invalid station id", ident)
        return None
    if not fuel_name:
        report.drop("missing fuel name", ident)
        return None
    try:
        price_milli = int((Decimal(price_text) * 1000).to_integral_value())
    except (InvalidOperation, ValueError, OverflowError):  # also NaN / Infinity
        report.drop("invalid price", ident)
        return None
    if self_text not in ("0", "1"):
        report.drop("invalid self/served flag", ident)
        return None
    try:
        local = datetime.strptime(date_text, DATE_FORMAT).replace(tzinfo=ITALY_TZ)
    except ValueError:
        report.drop("invalid update date", ident)
        return None
    return Price(
        station_id=station_id,
        fuel_name=fuel_name,
        fuel=classify(fuel_name),
        price_milli=price_milli,
        is_self=self_text == "1",
        updated=local.astimezone(UTC),
    )
