# Data pipeline

Downloads the MIMIT "Osservaprezzi Carburanti" open data, cleans it, and writes
two files for the app: `stations.json` (all stations with their prices) and
`meta.json` (data date, counts, checksum).

Python 3.12+, standard library only — nothing to install.

## Usage

Run everything from this `pipeline/` directory.

```sh
# Download today's files from MIMIT and build the output into out/
python3 -m fuel_pipeline

# Rebuild from files already on disk (no network)
python3 -m fuel_pipeline --prices data/prezzo_alle_8.csv \
                         --stations data/anagrafica_impianti_attivi.csv

# Options: --out DIR (default out/), --data-dir DIR (default data/)

# Run the tests
python3 -m unittest -v
```

Downloads go to `data/`, output goes to `out/`; both are git-ignored. Each run logs
what was dropped or fixed, by reason, with sample station ids. The exit code is 1
(and nothing is written) if a download fails, the file format changed, or the data
looks broken (see *Safety checks*).

## Source data

| File | URL |
|---|---|
| Prices | https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv |
| Stations | https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv |

Dataset page and metadata PDF:
https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti

- `|`-separated, UTF-8, no quoting. First line `Estrazione del YYYY-MM-DD`, second
  line column names.
- Prices are those **in force at 08:00 (Italian time) on the extraction date**; the
  files are published the next morning. The data date always comes from the file.
- Prices are € per litre, except methane (and LNG), which are € per kg.
- License: IODL 2.0, attribution required.

## Cleaning rules

| Problem in the source | What the pipeline does |
|---|---|
| Station rows with extra `\|` (junk ` \| gestori.prezzibenzina.it` in names) | Rebuilt using the station type column as an anchor; junk removed |
| Tabs, leading/trailing/double spaces | Collapsed to single spaces, trimmed |
| Missing, invalid, zero or out-of-Italy coordinates | Station dropped |
| Coordinates with 15 decimals | Rounded to 5 (~1 m) |
| Duplicate station id | First one kept |
| Operator (`Gestore`) | Not exported: unused, and often a person's name |
| Stations with no valid price | Dropped |
| Prices older than 8 days (some date back to 2013) | Dropped |
| Prices dated more than 1 day after the data date | Dropped |
| Implausible prices (e.g. 0.100, 8.888) | Dropped; bounds per fuel type in `prices.py` |
| Malformed price rows | Dropped |
| Duplicate price (same station, fuel name, self/served) | Newest kept |
| Fuel name never seen before | Typed by keyword (or `OTHER`), kept, logged |

### Safety checks

The run fails instead of writing output when the two files have different
extraction dates, when more than 10% of either file's rows are dropped, or when
fewer than 15,000 stations or 50,000 prices remain.

## Output format (schema version 1)

### `meta.json`

Small file the app can fetch first to see if there is new data.

```json
{
  "schemaVersion": 1,
  "dataDate": "2026-09-30",
  "pricesAt": "2026-09-30T08:00:00+02:00",
  "generatedAt": "2026-10-01T19:50:02Z",
  "stations": 21489,
  "prices": 91799,
  "file": "stations.json",
  "bytes": 5353018,
  "sha256": "…",
  "source": "Ministero delle Imprese e del Made in Italy — Osservaprezzi Carburanti",
  "license": "IODL-2.0"
}
```

### `stations.json`

Compact (no whitespace) UTF-8 JSON: ~5.4 MB, ~1.5 MB gzip-compressed.

```json
{
  "schemaVersion": 1,
  "dataDate": "2026-09-30",
  "brands": ["Agip Eni", "Api-Ip", "…"],
  "fuels": [
    {"name": "Benzina", "type": "PETROL", "unit": "L", "std": true},
    {"name": "Blue Super", "type": "PETROL", "unit": "L", "std": false},
    {"name": "Metano", "type": "CNG", "unit": "KG", "std": true}
  ],
  "stations": [
    {
      "id": 3464, "n": "PO EST", "b": 1, "hw": 1,
      "a": "Autostrada A13 BOLOGNA-PADOVA, Km. 43+400, dir. Nord - 44100",
      "c": "FERRARA", "pr": "FE", "lat": 44.88012, "lon": 11.57083,
      "f": [[0, 2409, 0, 1790706607], [0, 2049, 1, 1790706608]]
    }
  ]
}
```

`brands` — brand (`Bandiera`) names; stations refer to them by index.

`fuels` — one entry per fuel name found in the data:

| Key | Meaning |
|---|---|
| `name` | Name as published by MIMIT, shown to users for special fuels |
| `type` | `PETROL`, `DIESEL`, `LPG`, `CNG`, `LNG` or `OTHER` |
| `unit` | `L` (per litre) or `KG` (per kilogram) |
| `std` | `true` for the standard fuels (Benzina, Gasolio, GPL, Metano, GNL, L-GNC), `false` for branded/special products |

`stations` — sorted by id:

| Key | Meaning |
|---|---|
| `id` | MIMIT station id (`idImpianto`) |
| `n` | Station name; may be `""` (show the brand instead) |
| `b` | Index into `brands` |
| `hw` | `1` for motorway stations, else `0` |
| `a`, `c`, `pr` | Address, municipality, province code |
| `lat`, `lon` | WGS84 coordinates |
| `f` | Prices: `[fuelIndex, price, self, updated]` |

Each price entry: `fuelIndex` into `fuels`; `price` in thousandths of a euro
(`2409` = €2.409); `self` is `1` for self-service, `0` for served; `updated` is
when the operator last reported it, in Unix epoch seconds (UTC).

### Versioning

`schemaVersion` is a major version. It only changes for breaking changes; new
keys may be added without changing it, so readers must ignore unknown keys. The
app must refuse (gracefully) a major version it does not know.
