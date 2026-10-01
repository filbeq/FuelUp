# Fuel Price Map — Project Guide

Android app showing Italian fuel station prices on a map, built on the MIMIT
"Osservaprezzi Carburanti" open data. Personal use first, Play Store later:
build everything as if it will be published.

The owner is not an Android developer. Prefer simple, conventional solutions
over clever ones, explain non-obvious decisions briefly, and keep every change
small enough to be tested on a real phone before moving on.

## Repository & git workflow

- Monorepo on GitHub:
  ```
  /pipeline    Python data pipeline (download, clean, publish JSON)
  /android     Android app (Kotlin + Jetpack Compose)
  /.github     GitHub Actions workflows
  ```
- Commits: small and atomic, one logical change each. Messages in English,
  imperative mood, short subject line (≤ 60 chars), Conventional Commits prefix:
  `feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`, `ci:`.
  Example: `feat(android): cluster station markers on map`.
- Never commit secrets (API keys, keystores, `local.properties`). Keep
  `.gitignore` up to date for both Python and Android/Gradle.
- All code, identifiers, comments, docs and commit messages in English.

## Data source (MIMIT open data)

- Two daily CSV files (verify URLs against the official dataset page before use):
  - prices: `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv`
  - stations: `https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv`
  - dataset page: https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti
- Data reflects prices in force at 08:00 of the day before publication. It is
  NOT real time; the app must always show the data date clearly.
- Format (since 10 Feb 2026, check the official metadata PDF):
  - field separator `|`, two header lines (first line is an extraction-date line)
  - prices: `idImpianto|descCarburante|prezzo|isSelf|dtComu` (~90k rows)
  - stations: `idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine` (~24k rows)
- Known quirks — the parser must be defensive and log what it drops:
  stray tabs, extra separators inside fields, zero/missing coordinates,
  stale prices (drop prices older than ~8 days), special/branded fuel names.
- License IODL 2.0: reuse and commercial use allowed, attribution mandatory.
  The app must show: "Fonte dati: Ministero delle Imprese e del Made in Italy —
  Osservaprezzi Carburanti" (localized), and state it is not an official app.

## Pipeline (/pipeline)

- Python 3.12, `pathlib` over `os`, minimal dependencies (stdlib `csv`/`json`
  preferred; add packages only with a clear reason).
- Steps: download → parse both files → join on `idImpianto` → normalize fuel
  types → emit one compact JSON (plus a small `meta.json` with data date and
  schema version).
- Normalize `descCarburante` into a fixed enum: `PETROL`, `DIESEL`, `LPG`,
  `CNG`, `LNG`, `OTHER`; keep the original name for display of special fuels.
- Unit tests with fixture rows that reproduce the real quirks.
- Scheduled GitHub Action, twice a day: run tests, build JSON, publish to
  GitHub Pages via `actions/deploy-pages` (do NOT commit daily data to `main`).
- Version the JSON schema; the app must reject unknown major versions gracefully.

## Android app (/android)

- Kotlin, Jetpack Compose, Material 3, single-activity, MVVM
  (ViewModel + StateFlow). Min SDK 26 unless there is a reason to change.
- Maps: Google Maps via `maps-compose` + `maps-compose-utils` for marker
  clustering (24k points: clustering is mandatory).
- API key: loaded through the Secrets Gradle Plugin from `local.properties`;
  never hard-coded or committed. Document key restriction (package name +
  SHA-1) in the README.
- Data: download the published JSON at most once a day, cache it on device
  (Room or a plain file — choose the simplest that works), work offline
  from cache, show the data date and a clear error state.
- Localization from day one:
  - no hard-coded user-facing strings; everything in `strings.xml`
  - `values/` = English (default), `values-it/` = Italian
  - per-app language support (`locales_config.xml`, Android 13+ picker)
  - localized number/currency formatting (e.g. `1,849 €/l` vs `€1.849/l`)
  - fuel type labels come from string resources keyed by the enum;
    station names/addresses stay as provided by MIMIT
- Location permission: request only when the user taps "near me"; the app
  must work without it.
- No analytics, no tracking, no personal data collected.

## Roadmap (MVP first, one step at a time)

1. Pipeline script producing clean JSON locally, with tests.
2. GitHub Action + GitHub Pages publishing.
3. Android skeleton: builds, runs, shows a Google Map, i18n set up.
4. Load JSON, show clustered station markers.
5. Station detail: prices per fuel, self/served, last update date.
6. Fuel filter + marker colors by relative price (cheap → expensive).
7. "Near me" with location permission.
8. Later: favorites, search, price history, UI polish, Play Store prep.

Finish each step with a working build and a commit before starting the next.
