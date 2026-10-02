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
- Docs: `README.md` is for users of the app (what it does, data notice,
  privacy, credits); developer instructions go in `DEVELOPMENT.md`.
- Never commit secrets (API keys, keystores, `local.properties`). Keep
  `.gitignore` up to date for both Python and Android/Gradle.
- All code, identifiers, comments, docs and commit messages in English.

## Data source (MIMIT open data)

- Two daily CSV files (URLs verified on the dataset page on 2026-10-01):
  - prices: `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv`
  - stations: `https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv`
  - dataset page: https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti
  - metadata PDF: `Metadati_prezzi_carburanti_20260128.pdf`, linked from the
    dataset page: file dated 28 Jan 2026, format **in force since 10 Feb 2026**
    (as the dataset page states)
- Reporting rule (MIMIT, legal basis art. 51 L. 99/2009): operators report
  prices **weekly, and whenever a price rises or falls**. A price not
  re-reported for days is usually just unchanged: show its age neutrally,
  never as a warning. The pipeline's 8-day cut-off = one week + margin.
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
  - coordinates copied from another registration (e.g. "8144 SORRENTO" drawn
    on top of a Ventimiglia station): the pipeline drops a station whose
    nearest same-province station is > 25 km away while another province's is
    over 3× closer (islands pass: nothing else is near them). ~25 published
    stations per day.
  - ~140 sites with two registrations at identical coordinates (often the same
    name, e.g. Sarni motorway areas with a separate diesel listing): both are
    kept; the station sheet links the other one ("Also at this location").
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
- CI (`.github/workflows/ci.yml`, every push/PR): pipeline tests, the
  pipeline/app contract check (`make_android_fixture.py --check`), and the
  app's `./gradlew test lint`. The publish workflow runs the same contract
  check before deploying.

## Android app (/android)

- Kotlin, Jetpack Compose, Material 3, single-activity, MVVM
  (ViewModel + StateFlow). Package `io.github.filbeq.fuelup`, app name FuelUp.
- Toolchain: AGP 9 (Kotlin is built in: do not apply `org.jetbrains.kotlin.android`),
  Compose compiler plugin matching the Kotlin version, version catalog in
  `gradle/libs.versions.toml`. minSdk 26, targetSdk 36, compileSdk 37 (required
  by current AndroidX). Open/build the `android/` folder, not the repo root.
- Navigation (step 6b): Navigation 3 (`androidx.navigation3`). The back stack
  is a saved list of `@Serializable` screens (`MapRoute`, `SettingsRoute`,
  `AboutRoute`) in `ui/FuelUpApp.kt`; system Back pops it. The map's ViewModel,
  camera and selected station live above the navigation, so they survive other
  screens. The station sheet is part of the map screen and gets Back first.
- Settings (step 6b, controls 6c): gear icon top right → Settings → About.
  Theme: switch "follow system theme" + "dark theme" (enabled only when the
  first is off); map style: "automatic map style" + "dark map", same pattern.
  Turning an automatic switch off keeps what is on screen (no flip). Language:
  dropdown (system / Italiano / English).
  Theme and map style saved in SharedPreferences (`AppSettingsStore`, same file
  as the fuel choice); the saved theme is applied before the first frame.
- AppCompat (step 6b): `MainActivity` is an `AppCompatActivity` with an
  AppCompat DayNight window theme, so `AppCompatDelegate` can switch the app
  language (`setApplicationLocales`, stored by AppCompat's
  `AppLocalesMetadataHolderService` on Android < 13 and by the system on 13+,
  in sync with the system per-app picker) and light/dark
  (`setDefaultNightMode`). Compose and Material 3 are unaffected.
- Station sheet: non-modal `BottomSheetScaffold` (map stays interactive);
  collapsed = name + main prices, expanded = details. Back: expanded →
  collapsed → closed. "Navigate" uses a `geo:` intent (any navigation app,
  no Google dependency). Stations at identical coordinates link to each
  other in the collapsed sheet (their markers overlap).
- Fuel filter (step 6): standard product per type; Self/Servito only for petrol
  and diesel (LPG/methane/LNG are ~90–97% served-only); stations not selling the
  choice are hidden (not greyed); choice saved in SharedPreferences.
- Price colours: compare with the median of the 25 nearest stations (same
  fuel/mode, 50 km), classify by cents (±2 c), never by rank (prices are clumped,
  e.g. Eni 1.990 nationwide). Separate groups: motorway (8 nearest, 100 km) and
  duty-free Livigno. Prices ≥ 35 c below (GPL 20, CNG/LNG 50) are "to verify":
  visible, never "cheap", excluded from cluster "from" prices. Thresholds per
  fuel in `PriceRanking.THRESHOLDS`. Colours never depend on the visible area.
- Colour-blind safety: every class has a distinct marker shape and the sheet
  says it in words; colour is never the only signal.
- Clusters (step 6c): radius by station count 9/12/16/21/27 dp (2–9, 10–49,
  50–199, 200–999, 1000+), 30% fill with a solid 2 dp outline, label with a
  halo, grouping radius 40 px (constants in `StationLayers`). Cluster colour is
  its own pair (`ClusterColorLight` dark blue on the light map,
  `ClusterColorDark` pale blue on the dark map): it follows the map's darkness,
  not the app theme. On the dark map its colour is only moderately different
  from the grey "average" marker (ΔE ≈ 11); the form (translucent ring + "da"
  label vs small solid disc) keeps them apart.
- Debug builds are arm64-only to keep installs small; release keeps all ABIs.
- Theme: fixed FuelUp light/dark palette, light or dark per the Theme setting
  (system default unless chosen in Settings); no dynamic colour.
- Palette (step 6c): "Ink blue", Material 3 schemes generated from seed
  `#2F5DA8` (Google's Material colour algorithm; secondary/tertiary in the same
  blue family). Rule: **UI colours never green and never orange/red**, so they
  can't be confused with the price colours. Chosen over violet and graphite
  after on-phone screenshots; checked: WCAG AA text contrast (6.2:1 or more) and
  ΔE2000 ≥ 20 from the price colours under normal vision and simulated
  protan/deutan/tritan vision. Palette in `ui/theme/Color.kt`, mirrored in
  `res/values*/colors.xml` (window background, launcher icon `brand`).
- Maps: MapLibre Native for Android (`org.maplibre.gl:android-sdk`) wrapped in
  Compose with `AndroidView` around `MapView`, kept in a single file. Chosen
  over the `maplibre-compose` wrapper, which is pre-1.0 with frequent breaking
  releases; reconsider once it reaches 1.0.
- Tiles: OpenFreeMap vector styles (`liberty` light, `fiord` dark), no API key.
  `fiord` replaced `dark` (too hard to read on a phone) after comparing all five
  OpenFreeMap styles in the dark app theme (liberty, bright, positron, dark, fiord).
  Style URLs and attribution live in one place (`map/MapProvider.kt`,
  `CurrentMapProvider`) so the provider (e.g. MapTiler) can be swapped by
  changing a single constant. A provider that needs a key must keep it out of git.
- Clustering (step 4): 24k points, clustering is mandatory. Use MapLibre's
  built-in GeoJSON source clustering (`GeoJsonOptions().withCluster(true)`),
  not per-marker annotations.
- Cluster labels (step 6d): the station count inside the circle; the "from"
  price in a rounded pill (surface fill, cluster-colour outline, stretchable
  image + `icon-text-fit`) under the circle, shown only where there is room.
  Pills avoid each other and an invisible collision box over 60% of every
  circle (100% hid most prices at national zoom). Tapping a pill zooms like
  the circle.
- Map attribution: "© OpenStreetMap contributors" plus the tile provider
  (OpenFreeMap, © OpenMapTiles) always visible in a corner of the map (OSMF
  guidelines), and listed with links in the About screen. MapLibre's own
  attribution button and logo are disabled to avoid duplicates.
- MapLibre's library manifest declares location permissions: they are removed
  in our manifest (`tools:node="remove"`) until the "near me" step.
- No API keys or secrets are needed by the app.
- Data: download the published JSON at most once a day, cache it on device,
  work offline from cache, show the data date and a clear error state.
  Decisions (step 4):
  - parsing: kotlinx.serialization, streamed from the cached file; prices as
    `List<LongArray>` (no boxing)
  - cache: plain files in `filesDir/data/` (not Room: the whole dataset is
    needed in memory, no queries); a download is checked against meta.json
    (size, SHA-256, schema, date) before atomically replacing the cache
  - background work: loaded on app start by a ViewModel with coroutines (no
    WorkManager: data only matters while the app is open)
  - network: `HttpURLConnection` (gzip built in), 15 s connect / 30 s read
    timeouts; a timeout counts as offline
  - refresh rule (`RefreshPolicy`): no network if the cache already has
    yesterday's data; otherwise check meta.json at most hourly (Retry
    overrides); download stations.json only if date or checksum changed
  - unknown major `schemaVersion`: keep the cache, show "please update"
  - stations reach MapLibre as a GeoJSON string built off the main thread
  - pipeline/app contract: the app's test fixture is generated by
    `pipeline/scripts/make_android_fixture.py`; regenerate it after schema
    changes (CI fails otherwise)
- Localization from day one:
  - no hard-coded user-facing strings; everything in `strings.xml`
  - `values/` = English (default), `values-it/` = Italian
  - per-app language support (`locales_config.xml`, Android 13+ picker)
  - localized number/currency formatting with the fuel's own unit
    (e.g. `1,849 €/l` vs `€1.849/l`, and `1,794 €/kg` for methane)
  - fuel type labels come from string resources keyed by the enum;
    station names/addresses stay as provided by MIMIT
- Language: the in-app setting works on every Android version (AppCompat);
  on Android 13+ it is the same setting as the system per-app language picker.
- Location permission: request only when the user taps "near me"; the app
  must work without it. Re-add the location permissions removed from
  MapLibre's manifest at that step.
- No analytics, no tracking, no personal data collected.

## Roadmap (MVP first, one step at a time)

1. Pipeline script producing clean JSON locally, with tests.
2. GitHub Action + GitHub Pages publishing.
3. Android skeleton: builds, runs, shows a MapLibre map (OpenFreeMap), i18n set up.
4. Load JSON, show clustered station markers (MapLibre GeoJSON clustering).
   Map labels follow the app language (`name:it` / `name:en`), falling back
   to the local name (`name`).
5. Station detail: prices per fuel, self/served, last update date.
6. Fuel filter + marker colors by relative price (cheap → expensive).
6b. Settings screen (theme, map style, language incl. Android < 13) and a
   readable dark map style (fiord); Navigation 3 for map, settings and About.
6c. UI fixes: settings switches + language dropdown, smaller translucent
   clusters sized by count, "Ink blue" palette (no green/orange/red in the UI).
6d. Map fixes: drop stations placed in another province (pipeline), link
   stations at the same spot, cluster count + "from" price pill.
7. "Near me" with location permission.
8. Later: favorites, search, price history, UI polish, Play Store prep.
   Note: the map view is rebuilt (~1 s style reload) when returning from
   another screen. Before adding frequently used screens (favorites, search),
   find a way to keep the map alive.

Finish each step with a working build and a commit before starting the next.
