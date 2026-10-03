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
- Before any data analysis, rebuild `pipeline/out/` from current code
  (`python3 -m fuel_pipeline`, or `--prices/--stations` on fresh CSVs) or use
  the published `stations.json`; never a stale `out/` (git-ignored, rebuilt
  only by hand). A stale one once showed stations the pipeline already drops.
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
- Scheduled GitHub Action: run tests, build JSON, publish to GitHub Pages via
  `actions/deploy-pages` (do NOT commit daily data to `main`). GitHub starts
  scheduled runs hours late or drops them (seen 2–3 Oct 2026: runs 4.5–6.5 h late,
  then none), so it runs every 2 h (07:10–21:10 UTC, off the hour); a first
  `check` job (`python -m fuel_pipeline.freshness needs-publish`) skips the
  build when MIMIT's `Estrazione del` date equals the published `dataDate`.
  A manual run always publishes.
- Staleness alarm (`.github/workflows/check-data.yml`, 4 runs a day): fails, so
  GitHub emails the owner, when MIMIT has had newer prices for > 3 h, or when
  yesterday's prices aren't published by 14:00 Italian time. Dry run: "Run
  workflow" with a fake published date. README shows its badge.
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
- Map kept alive (step 8.1): `MapScreen` is composed outside `NavDisplay`,
  underneath it; the `MapRoute` entry is an empty placeholder and other screens
  are drawn over the map, so Back never rebuilds the MapView (was ~1.3 s of
  style + data reload). While covered (`covered` = back-stack top isn't the
  map) the map's Back handler is off and its semantics are cleared (TalkBack).
  The MapView is **not** paused while covered: MapLibre draws only on change
  (CPU ~0% behind Settings), and pausing it flashed dark on Back after the app
  had been in the background (seen in a screen recording).
- Settings (step 6b, controls 6c): gear button top right → Settings → About.
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
- Wide layout (step 8.3): when the window is >= 600 dp wide (Material width
  class medium+, `isWideWindow()` in `ui/WindowSize.kt`, needs
  `material3-adaptive`), the station details and the near-me list are a side
  panel (`ui/map/SidePanel.kt`) instead of the bottom sheet: left, 360 dp
  (320 tried on the phone in landscape: rows wrapped, fewer fitted), floating
  over the full-size map, height follows content (minimised list = header
  card), × in each header (= Back). Width decides, not orientation; portrait
  phones keep the sheet unchanged. Back: station → list → closed. Camera moves
  take the panel into account (`leftPx`; `CameraMove.Reveal` pans a tapped
  station out from under it). Credits move right of the panel; the date pill
  centres in the free map. Rotation keeps content, scroll and minimised state;
  the sheet's state is built from MapScreen's saved `sheetExpanded` (not the
  sheet's own saver, which restored stale values after a stay in landscape);
  a scrolled panel comes back as an expanded sheet. Settings/About content is
  capped at 600 dp, centred (`Modifier.readableWidth()`).
  MapLibre gotchas found here: a circle fit leaves camera padding behind (so
  `Show` sets its padding explicitly), and `scrollBy` doesn't end in a
  camera-idle event (the camera saved for rotation misses it): use
  `animateCamera`.
- Full-screen map (step 8.4): no top bar on the map screen (Settings and About
  keep theirs, with Back); the map runs edge to edge under the status bar.
  Top controls (`ui/map/MapTopControls.kt`): since step 8.5 the search bar
  with the gear beside it, then date pill and fuel button (see "Search").
  Status bar: icons follow the map's darkness (app
  theme while Settings/About is on top) over a faint scrim in the map's tone;
  icon colour alone was tried, place names clashed with the clock. Insets:
  controls and side panel use `safeDrawing` (status bar, cutout, side
  navigation bar); the expanded sheet stops 8 dp below the status bar; a tall
  sheet covers the credits and location button instead of lifting them into
  the controls. Camera moves take `topControlsPx` (status bar included);
  `Reveal` also moves a tapped station down out from under the controls.
  A plain `Box` replaced the wide layout's `Scaffold`: Material text colour
  must then be set explicitly on translucent surfaces (`contentColor`).
- Search (step 8.5): offline, over the data on the phone (no geocoding):
  stations by name/brand/address/municipality/province, and municipalities
  (`data/StationSearch.kt`, rules in DEVELOPMENT.md "Search"). Every typed
  word must start a word of the target; case/accents/apostrophes ignored;
  "S." = San/Santo/Santa/Sant' (added on the data side only). Ranking:
  ≤ 5 municipalities (exact, prefix, words; bigger first), then ≤ 50
  stations (whole-word matches first, then name/brand prefix, name+brand,
  anywhere; sellers of the chosen fuel first, then nearest to the map centre).
  Index: sorted vocabulary + word ids per station, built in the background
  once per download (~1 s, +5 MB); a keystroke costs 4–10 ms (≤ 70 ms for
  2 letters). UI: Material 3 `SearchBar` + `ExpandedFullScreenSearchBar`;
  `ExpandedDockedSearchBar` only when wide and >= 480 dp tall (tablets:
  the dropdown fitted one result above the keyboard on a landscape phone). Portrait: bar +
  gear, then date pill (left) + fuel button (right). Wide: one row, bar
  360 dp aligned with the side panel, which opens under it; gear, pill
  centred in the rest, fuel button at the right. A municipality result acts
  like an empty-map tap and frames its stations (`CameraMove.FitPoints`),
  leaving out misfiled ones (> 5× median distance from the median point and
  > 10 km; towns with < 3 stations keep all: `Municipality.mainStations`); a
  station result selects it and shows it (`CameraMove.Show`, after the sheet
  settles). A selected station without a marker (doesn't sell the chosen
  fuel) gets a hollow dot inside the ring (own one-point source).
  Full-screen search: status bar icons follow the app theme.
  The ~25 stations dropped for misplaced coordinates are not searchable
  (owner's choice for now; adding them = new optional `unlocated` key, no
  schema bump, fixture regenerated).
- Favourites (step 8.6): star (`IconToggleButton`, outline/filled, ink blue,
  never yellow) after the station name in the sheet/panel. Listed when the
  search opens with an empty query, above the hint (`ui/map/FavoritesList.kt`),
  newest first: chosen fuel's price + class, municipality, report age, distance
  when "near me" has a position (whole km from 10 km). Stored as JSON in their
  own SharedPreferences file `favorites` (`data/Favorites.kt`), keyed by station
  id, with last-seen labels, coordinates and `lastSeen` data date. A favourite
  missing from the data is never removed by itself: "not in the data since
  <date>", a star button to remove it, a tap shows its last spot (hollow dot,
  the off-map source); if a station now sits at exactly its coordinates
  (MIMIT re-registered it under a new id), "At this spot now: <name>" moves the
  star to it in place (`Favorites.successor`/`replace`, same-location lookup
  `StationsFile.stationsAt`). Map: a small star at the marker's top right, in
  the cluster colour with an opposite-tone outline (`StationLayers.setFavorites`,
  filter on the station source, above the selection ring); none on clusters.
  Recently viewed stations: not done (owner's choice, could be a later step).
- Backup (step 8.6): Android Auto Backup and device transfer include **only**
  `sharedpref/favorites.xml` (`res/xml/backup_rules.xml` for Android ≤ 11,
  `data_extraction_rules.xml` for 12+); cache, settings and language stay out.
  The app never sends or reads the backup (Android does, in the user's Google
  account), so the Data safety form still declares nothing collected; recheck
  Google's wording when filling it in.
- Municipality names: shown in Italian title case everywhere
  (`data/PlaceNames.kt`, "Reggio nell'Emilia"); station names and addresses
  as MIMIT writes them. 9 names in the data differ from ISTAT's spelling,
  listed in DEVELOPMENT.md; no hard-coded exceptions.
- Station sheet: non-modal `BottomSheetScaffold` (map stays interactive);
  collapsed = name + main prices, expanded = details. Back: expanded →
  collapsed → closed. "Navigate" uses a `geo:` intent (any navigation app,
  no Google dependency). Stations at identical coordinates link to each
  other in the collapsed sheet (their markers overlap).
- Fuel filter (step 6): standard product per type; Self/Servito only for petrol
  and diesel (LPG/methane/LNG are ~90–97% served-only); stations not selling the
  choice are hidden (not greyed); choice saved in SharedPreferences.
- Fuel selector (step 8.2): one floating button on the right of the map, under
  the gear or beside it (step 8.4; like Google Maps' map-type button), always showing the
  current choice ("Gasolio · Self"). It opens a modal bottom sheet: fuel chips,
  Self/Servito, and the price legend with a meaning per class (incl. "not
  compared"). The mode row keeps its height for LPG/CNG/LNG (a note instead),
  so chips don't move when the fuel changes. Panel open state survives rotation.
  Fuels are tiles (pump icon + name): the one exception to the "no green / no
  orange-red UI" rule, used **only inside the panel** (`fuelColor` in
  `ui/theme/Color.kt`): Benzina green, Gasolio ochre, GPL purple, Metano azure,
  GNL indigo, darker tones in light theme. Checked: icon contrast >= 3.7:1, and
  ΔE2000 >= 20 from the price colours (>= 10 under simulated protan/deutan/
  tritan; Benzina exempt from "cheap"). The map button keeps a neutral pump.
  Selected tile: thick outline + tinted background + check mark. All labels use
  one size: the largest at which the longest label fits.
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
- MapLibre's library manifest also declares `ACCESS_WIFI_STATE`: removed in
  our manifest (`tools:node="remove"`).
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
  - "Update data now" (Settings → Data, step 8.2): always fetches meta.json
    (ignores the hourly limit and the same-date rule), downloads only if date or
    checksum changed, shows the outcome and when the server was last checked
    (`last_meta_check`); a failed check doesn't count as checked
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
- "Near me" (step 7):
  - Permission: asked only when the my-location button (a regular-size FAB,
    bottom right of the map, lifted above the sheet) is tapped; foreground only, never
    background. Approximate and precise are requested together: on Android
    12+ the user picks (approximate is enough). Approximate-only was tried
    first, but on Android ≤ 11 such apps can't use GPS and the network source
    alone stayed silent on the test phone.
  - The app works fully without it. Refused, refused for good (button to the
    app's settings page), location off (button to location settings) and
    "no position" each get a localized explanation in the sheet.
  - Position: one fix per tap (no tracking), platform `LocationManager` (no
    Google Play services), racing fused/network/GPS (GPS only if precise is
    allowed), 20 s timeout, recent last-known fix used first. Kept in memory
    only; never saved or sent.
  - Drawn honestly: a translucent disc of the reported accuracy plus a small
    centre mark (no precise-looking dot); the search radius as a
    circle (solid line on a soft halo); the camera fits it between the top
    controls and the sheet.
    Colours follow the map's darkness (`LocationColor*`, like the clusters),
    every line has a halo in the opposite tone.
    The camera fits the circle only for a new fix or radius while "near me" is
    open (last fit saved across rotation); the saved camera is the view's
    centre, since after a fit MapLibre keeps the padding in the camera target. Disc and circle lie under the
    stations; the centre mark is a symbol above them, so colliding price
    labels/pills are left out rather than half-covered. Shown only while
    "near me" is open.
  - List in the station sheet when no station is selected: radius chips
    5/10/20 km (default 10 km: median ~40 stations, ≥ 3 in 99% of places,
    measured), sort Price (default; "to verify" last) / Distance, both saved.
    Rows: class icon and words, price, brand, distance as the crow flies
    (whole km when the fix is worse than 500 m), motorway badge, report age.
    A row selects the station and shows it (the sheet is lowered first, so
    the station opens collapsed with the chosen fuel); Back returns to the
    list (collapsed, even if it was expanded).
    Only the header (title, sort, radius) is fixed; all rows scroll in one
    list, back at the top when the sheet collapses.
  - Empty-map tap: with a station selected, leaves it (back to the list as
    it was, or nothing); otherwise lowers the list to its header: title,
    order and radius (kept, so the circle on the map stays explained).
    Title, drag up or the location button bring it back. Back: station → list → collapsed → closed;
    minimised → closed.
  - Play Store: no background-location declaration needed; location never
    leaves the phone, so the Data safety form declares nothing collected.
    A privacy policy URL is still required for every app.
- Cluster "from" prices exclude prices "to verify" and duty-free Livigno
  (`StationLayers.fromPrice`).
- No analytics, no tracking, no personal data collected.

## Status (2026-10-03)

Steps 1–7 are done, committed and pushed; CI green. The app is in daily
personal use on the test phone. Done in step 8 so far:
- 8.1: map kept alive across screens (no reload after Settings/About); camera
  stays put across rotation (no jump back to an old "near me" circle).
- 8.2: fuel selector button + bottom sheet with coloured fuel tiles and legend,
  regular-size location FAB, "Update data now" in Settings.
- 8.3: side panel on wide windows (landscape phones, tablets), see "Wide
  layout" above; checked on the phone and a Pixel Tablet emulator, incl.
  screenrecord of panel open/close and rotations.
- 8.4: full-screen map, floating gear, status bar icons + scrim following the
  map, see "Full-screen map" above; checked on the phone (light/dark map,
  portrait/landscape, screenrecords) and the tablet emulator.
- 8.5: search (stations, municipalities) with a search bar on top of the map,
  municipality names in title case; see "Search" above; checked on the phone
  (portrait/landscape, light/dark map, near-me interplay, screenrecord).
- 8.6: favourite stations (star, empty-search list, map mark, re-registered
  stations, backup of the favourites file only); see "Favourites" above;
  checked on the phone (light/dark map, landscape, near-me distances, faked
  missing and re-registered favourites). Backup restore itself untested (needs
  a Play Store install or `bmgr`).
- Publishing made robust: every 2 h with skip-if-unchanged, plus a staleness
  alarm (`check-data.yml`); both verified on GitHub on 2026-10-03.

Next: the rest of step 8 (see the backlog below).

### Open issues (known, not yet fixed)

- "Refused once, the system will ask again" permission path is untested on a
  device: on the test phone a single refusal was already final (USER_FIXED
  flag left by earlier grant/revoke tests). The "refused for good" path and
  its settings button are verified.
- Approximate vs precise choice in the Android 12+ permission dialog is
  untested (the only test phone runs Android 11).
- The user's centre mark can hide a nearby cluster's "from" pill (by design:
  colliding labels are left out rather than half-covered).
- Lint's only warning is `OldTargetApi` (targetSdk 36): accepted for now.

### Backlog for step 8 (in no particular order)

- **Cold start** takes ~750 ms; measure where it goes (cache parse, ranking,
  GeoJSON, style load) and trim.
- **Publishing prep**:
  - **The name "FuelUp" is taken on the Play Store** (`com.takeapp.fuelup`):
    the app name and the package name (`io.github.filbeq.fuelup`, also the
    Kotlin package) must change before publishing.
  - Privacy policy URL (required for every app; the README's privacy section
    can become a page on GitHub Pages); Data safety form: nothing collected.
  - Release signing (keystore kept out of git), release build check on all
    ABIs, store listing, screenshots, IODL 2.0 attribution in the listing.

### Working notes for the next session

- Test phone: Redmi Note 9 Pro, Android 11 (MIUI), adb device `cd0e2646`,
  1080×2400. It is the owner's daily phone and the owner may be using it during
  tests. Before testing, snapshot the real state and restore exactly that
  afterwards (never assume values), then diff: `shared_prefs/settings.xml`,
  the AppCompat language record in `files/`, system `accelerometer_rotation`,
  `user_rotation`, `font_scale` (and radios if airplane mode is used).
- Tablet tests: emulator AVD `fuelup_tablet` (Pixel Tablet, API 36 x86_64,
  SDK cmdline-tools installed); never change the phone's `wm size`/density.
  Setup, x86_64 build and mock location in DEVELOPMENT.md ("Wide screens").
- Landscape on the phone: `uiautomator` bounds can be clipped/offset near the
  edges (credits, gear); check against a screenshot before tapping there. MIUI
  resets `user_rotation` to 0 when the (portrait-only) launcher comes to front.
- Drive the phone by element text (`uiautomator dump`, tap the node's bounds
  centre), not fixed coordinates: layouts move (sheets, panels). Check the app is
  in front before every step; a reinstall closes it. Screenshots show the
  owner's area: keep them local, don't publish them.
- `gh` is not installed: watch CI through the GitHub REST API
  (`/repos/filbeq/FuelUp/actions/runs?head_sha=…`, run jobs/steps under
  `/actions/runs/<id>/jobs`). Without a token, workflows can't be started from
  here: the owner clicks "Run workflow" in the Actions tab.
- App settings live in `shared_prefs/settings.xml`; edit them for tests with
  `adb shell "run-as io.github.filbeq.fuelup sed -i '…' shared_prefs/settings.xml"`
  (quote the whole command, or the device shell treats `<` as a redirect).
- Background waits: never `pgrep -f` a string that is also in the waiting
  command (it matches itself); never leave a bare `cat` in a pipeline (it
  waits on stdin forever).

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
7. "Near me" with location permission: my-location button, accuracy disc,
   cheapest-nearby list (5/10/20 km, price/distance).
8. Later: see "Backlog for step 8" above (layout fixes, map reload, cold
   start, search, favourites, price history, publishing prep incl. renaming).

Finish each step with a working build and a commit before starting the next.
