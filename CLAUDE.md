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

- Two daily CSV files (URLs verified on the dataset page on 2026-10-01):
  - prices: `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv`
  - stations: `https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv`
  - dataset page: https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti
  - metadata PDF: `Metadati_prezzi_carburanti_20260128.pdf` (dated 28 Jan 2026),
    linked from the dataset page
- Prices are those in force at **08:00 (Italian time) on the date in the
  `Estrazione del YYYY-MM-DD` header line**; the files are published the next
  morning. Always take the data date from the file, never from "today". It is
  NOT real time; the app must always show the data date clearly.
- Units: € per **litre** for petrol, diesel and LPG; € per **kg** for methane
  (CNG, incl. L-GNC) and LNG. Never assume €/l: every fuel carries its unit.
- Format (verified against the real files on 2026-10-01):
  - UTF-8 without BOM, LF line endings, field separator `|`, no quoting
  - line 1: `Estrazione del YYYY-MM-DD`; line 2: column names
  - prices: `idImpianto|descCarburante|prezzo|isSelf|dtComu` (~93k rows)
    - `prezzo`: dot decimal, 3 decimals (`2.049`)
    - `isSelf`: `1` self-service, `0` served
    - `dtComu`: `DD/MM/YYYY HH:MM:SS`, Italian local time (Europe/Rome)
  - stations: `idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine` (~24k rows)
    - `Tipo Impianto`: `Stradale` or `Autostradale` (motorway)
    - coordinates: decimal degrees, up to 15 decimals; entered voluntarily by
      operators and "not always verified" (metadata PDF)
- Known quirks (all seen in real data) — the parser must be defensive and log
  what it drops:
  - extra `|` inside fields (~110 station rows): a junk
    ` | gestori.prezzibenzina.it` suffix in the station name (once also in
    Gestore). Real separators can have spaces on both sides, so never split on
    `" | "`; rebuild the row using `Tipo Impianto` as an anchor.
  - tabs inside names/addresses; leading/trailing and double spaces everywhere;
    some empty station names
  - empty coordinates (with empty name) and coordinates padded with spaces;
    zero/out-of-Italy coordinates are possible, handle them too
  - stale prices, some from 2013: drop prices older than 8 days. `dtComu` a few
    minutes after 08:00 of the data date is normal (extraction lag).
  - typo prices such as `0.100` or `8.888`: drop via per-fuel plausible bounds
  - ~60 distinct fuel names: the standard ones (Benzina, Gasolio, GPL, Metano,
    GNL, L-GNC) plus branded/"speciale" products, with case variants
    (`Gasolio artico` / `Gasolio Artico`, `F101` / `F-101`)
  - ~2.3k stations have no price at all
  - `Gestore` is often a person's name (sole traders): do not publish it
- License IODL 2.0: reuse and commercial use allowed, attribution mandatory.
  The app must show: "Fonte dati: Ministero delle Imprese e del Made in Italy —
  Osservaprezzi Carburanti" (localized), and state it is not an official app.

## Pipeline (/pipeline)

- Python 3.12, `pathlib` over `os`, minimal dependencies (stdlib only so far;
  add packages only with a clear reason). Lines are split on `|` by hand rather
  than with the `csv` module, since the files have no quoting.
- Run from `pipeline/`: `python3 -m unittest`, `python3 -m fuel_pipeline`.
  JSON format and cleaning rules are documented in `pipeline/README.md`.
- Steps: download → parse both files → join on `idImpianto` → normalize fuel
  types → emit one compact JSON (plus a small `meta.json` with data date and
  schema version).
- Normalize `descCarburante` into a fixed enum: `PETROL`, `DIESEL`, `LPG`,
  `CNG`, `LNG`, `OTHER`; keep the original name for display of special fuels.
  Each fuel also has a unit (`L`/`KG`) and a `std` flag (standard fuel vs
  branded/special product). HVO products count as `DIESEL` (non-standard);
  `F101`/`F-101` stay `OTHER` until their fuel kind is confirmed.
- Times are compared in UTC (Python ignores UTC offsets when comparing
  datetimes that share a tzinfo, which breaks across DST changes).
- Refuse to write output when the data looks broken (different extraction
  dates, >10% of rows dropped, too few stations/prices), so a bad upstream
  file is never published.
- Unit tests with fixture rows that reproduce the real quirks.
- Scheduled GitHub Action, twice a day: run tests, build JSON, publish to
  GitHub Pages via `actions/deploy-pages` (do NOT commit daily data to `main`).
- Version the JSON schema; the app must reject unknown major versions gracefully.

## Android app (/android)

- Kotlin, Jetpack Compose, Material 3, single-activity, MVVM
  (ViewModel + StateFlow). Package `io.github.filbeq.fuelup`, app name FuelUp.
- Toolchain: AGP 9 (Kotlin is built in: do not apply `org.jetbrains.kotlin.android`),
  Compose compiler plugin matching the Kotlin version, version catalog in
  `gradle/libs.versions.toml`. minSdk 26, targetSdk 36, compileSdk 37 (required
  by current AndroidX). Open/build the `android/` folder, not the repo root.
- Theme: fixed FuelUp light/dark palette (follows the system dark setting);
  no dynamic colour.
- Maps: MapLibre Native for Android (`org.maplibre.gl:android-sdk`) wrapped in
  Compose with `AndroidView` around `MapView`, kept in a single file. Chosen
  over the `maplibre-compose` wrapper, which is pre-1.0 with frequent breaking
  releases; reconsider once it reaches 1.0.
- Tiles: OpenFreeMap vector styles (`liberty` light, `dark` dark), no API key.
  Style URLs and attribution live in one place (`map/MapProvider.kt`,
  `CurrentMapProvider`) so the provider (e.g. MapTiler) can be swapped by
  changing a single constant. A provider that needs a key must keep it out of git.
- Clustering (step 4): 24k points, clustering is mandatory. Use MapLibre's
  built-in GeoJSON source clustering (`GeoJsonOptions().withCluster(true)`),
  not per-marker annotations.
- Map attribution: "© OpenStreetMap contributors" plus the tile provider
  (OpenFreeMap, © OpenMapTiles) always visible in a corner of the map (OSMF
  guidelines), and listed with links in the About screen. MapLibre's own
  attribution button and logo are disabled to avoid duplicates.
- MapLibre's library manifest declares location permissions: they are removed
  in our manifest (`tools:node="remove"`) until the "near me" step.
- No API keys or secrets are needed by the app.
- Data: download the published JSON at most once a day, cache it on device
  (Room or a plain file — choose the simplest that works), work offline
  from cache, show the data date and a clear error state.
- Localization from day one:
  - no hard-coded user-facing strings; everything in `strings.xml`
  - `values/` = English (default), `values-it/` = Italian
  - per-app language support (`locales_config.xml`, Android 13+ picker)
  - localized number/currency formatting with the fuel's own unit
    (e.g. `1,849 €/l` vs `€1.849/l`, and `1,794 €/kg` for methane)
  - fuel type labels come from string resources keyed by the enum;
    station names/addresses stay as provided by MIMIT
- Per-app language picker only exists on Android 13+; on older Android the
  app follows the system language (an in-app picker may come with UI polish).
- Location permission: request only when the user taps "near me"; the app
  must work without it. Re-add the location permissions removed from
  MapLibre's manifest at that step.
- No analytics, no tracking, no personal data collected.

## Roadmap (MVP first, one step at a time)

1. Pipeline script producing clean JSON locally, with tests.
2. GitHub Action + GitHub Pages publishing.
3. Android skeleton: builds, runs, shows a MapLibre map (OpenFreeMap), i18n set up.
4. Load JSON, show clustered station markers (MapLibre GeoJSON clustering).
5. Station detail: prices per fuel, self/served, last update date.
6. Fuel filter + marker colors by relative price (cheap → expensive).
7. "Near me" with location permission.
8. Later: favorites, search, price history, UI polish, Play Store prep.

Finish each step with a working build and a commit before starting the next.
