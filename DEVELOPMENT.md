# Development guide

How the project is organised and how to build, run and publish it. For what the
app is, see the [README](README.md).

```
pipeline/   Python data pipeline: download MIMIT data, clean it, write JSON
android/    Android app (Kotlin, Jetpack Compose, MapLibre)
.github/    GitHub Actions: CI on every push (tests, lint, contract check), the
            workflow that publishes the data to GitHub Pages, and a check
            that the published data is up to date
```

## Data pipeline

Requires Python 3.12, no third-party packages. Run from `pipeline/`:

```sh
python3 -m unittest            # run the tests
python3 -m fuel_pipeline       # download MIMIT data, write out/stations.json + out/meta.json

# rebuild from files already downloaded (no network)
python3 -m fuel_pipeline --prices data/prezzo_alle_8.csv \
                         --stations data/anagrafica_impianti_attivi.csv
```

`data/` and `out/` are git-ignored, and `out/` is whatever you last built: before
analysing the data, rebuild it or use the published file
(https://filbeq.github.io/FuelUp/stations.json). An old `out/` misses later
pipeline fixes (it once still held the stations dropped for misplaced
coordinates). Cleaning rules, safety checks and the JSON
format are documented in [pipeline/README.md](pipeline/README.md).

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and
pull request:

| Job | Checks |
|---|---|
| Pipeline tests and app contract | `python -m unittest` in `pipeline/`, then `python scripts/make_android_fixture.py --check` |
| App unit tests and lint | `./gradlew test lint assembleRelease` in `android/` (JDK 21; the Gradle wrapper is validated; the release APK is built unsigned, to catch R8 problems early) |

The contract check regenerates the app's test fixture from the pipeline and
fails, with a diff, if it differs from the committed one. When it fails, the
pipeline's output format changed: run `python3 pipeline/scripts/make_android_fixture.py`,
run `./gradlew test` to see whether the app still reads it, and commit both.

Run the same checks locally before pushing:

```sh
(cd pipeline && python3 -m unittest && python3 scripts/make_android_fixture.py --check)
(cd android && ./gradlew test lint assembleRelease)
```

## Data publishing (GitHub Pages)

The workflow [`.github/workflows/publish-data.yml`](.github/workflows/publish-data.yml)
runs the pipeline tests and the same contract check as CI, then the pipeline,
then deploys the output to GitHub Pages. It runs every 2 hours from 07:10 to
21:10 UTC (MIMIT publishes around 06:45 UTC) and on demand. A first `check` job
compares MIMIT's extraction date with the published `dataDate` and skips the
build when nothing is new (about 20 seconds); a manual run always publishes.
If the tests or the pipeline fail, nothing is deployed and the previous data stays
online. The data is never committed to the repository.

Why so many runs: GitHub starts scheduled runs late (4.5–6.5 hours on 2 Oct
2026) or drops them (3 Oct 2026: none). One run out of eight is enough.

| File | URL |
|---|---|
| Station data | https://filbeq.github.io/FuelUp/stations.json |
| Metadata (data date, counts, checksum) | https://filbeq.github.io/FuelUp/meta.json |

**One-time setup:** repository Settings → Pages → Build and deployment →
Source: "GitHub Actions".

**Run it manually:** Actions tab → **Publish fuel data** → **Run workflow**
(branch `main`) → **Run workflow**. When the run is green, check:

```sh
curl -s https://filbeq.github.io/FuelUp/meta.json   # dataDate = date the prices refer to
```

GitHub disables scheduled workflows in public repositories after 60 days without
commits; re-enable it from the Actions tab if that happens.

### Staleness alarm

[`.github/workflows/check-data.yml`](.github/workflows/check-data.yml) runs four
times a day (10:40, 13:40, 16:40, 19:40 UTC) and **fails** when:

- MIMIT has had newer prices for more than 3 hours and they are not published, or
- by 14:00 Italian time the published data doesn't hold yesterday's prices (this
  also catches MIMIT itself not publishing).

GitHub emails the repository owner about failed scheduled runs (Settings →
Notifications → Actions), and the README badge turns red. The run page lists
what's wrong and the fix (run **Publish fuel data** by hand).

Rules and tests: `pipeline/fuel_pipeline/freshness.py`,
`pipeline/tests/test_freshness.py`. To see a failure without waiting for one:
Actions tab → **Check published data** → **Run workflow** → enter an old date
(e.g. `2026-09-30`) as the fake published date. Or locally, from `pipeline/`:

```sh
python3 -m fuel_pipeline.freshness check                               # real check
python3 -m fuel_pipeline.freshness check --published-date 2026-09-30   # dry run
```

## Android app

### Toolchain

| Item | Version |
|---|---|
| Gradle (wrapper, checksum-pinned) | 9.8.0 |
| Android Gradle Plugin (Kotlin built in) | 9.4.1 |
| Kotlin / Compose compiler plugin | 2.4.20 |
| Compose BOM (Material 3) | 2026.09.00 |
| MapLibre Native for Android | 13.6.1 |
| SDK levels | min 26, target 36, compile 37 |

Versions live in `android/gradle/libs.versions.toml`. Requirements: Android
Studio (or JDK 17+ and the Android SDK with platform 37 for command-line builds).

### Open in Android Studio

File → Open → select the **`android/`** folder (not the repository root) →
Trust project → wait for the Gradle sync to finish.

### Build from the command line

```sh
cd android
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew lint                 # also checks that every string is translated
./gradlew installDebug         # install on the connected phone (as "FuelUp Dev")
./gradlew assembleRelease      # minified release APK, see "Releases"
```

No API keys are needed: the map uses OpenFreeMap tiles.

### Run on a phone

1. On the phone: Settings → About phone → tap **Build number** (on Xiaomi:
   **MIUI version**) seven times to unlock Developer options.
2. Developer options → enable **USB debugging**. On Xiaomi also enable
   **Install via USB** (MIUI may ask you to sign in to a Mi account).
3. Connect the phone with a USB cable and tap **Allow** on the "Allow USB
   debugging?" prompt (tick "Always allow from this computer").
4. Check it is visible: `adb devices` should list it as `device`.
5. Android Studio ▶ Run with the phone selected, or `./gradlew installDebug`.

Debug builds install as a separate app, **FuelUp Dev** (id
`io.github.filbeq.fuelup.debug`, "DEV" badge on the icon), next to the release
app (`io.github.filbeq.fuelup`). Each has its own data, settings and
favourites; `adb` commands for testing (`run-as`, `am start`, `pm`) use the
`.debug` id.

If `adb devices` shows nothing although the cable is connected: unlock the
phone, look for the "Allow USB debugging?" prompt, or turn USB debugging off
and on again.

**Testing languages:** in the app, gear button → Settings → Language (works on
every Android version). On Android 13+ the same choice also appears in the
system's Settings → Apps → FuelUp → Language.

### Code map

| Path (under `android/app/src/main/`) | What it does |
|---|---|
| `java/…/MainActivity.kt` | Entry point: initialises MapLibre, sets the theme |
| `java/…/ui/FuelUpApp.kt` | Screens and back stack (Navigation 3: settings, About drawn over the map, which stays alive underneath); keeps the camera and the selected station |
| `java/…/ui/settings/SettingsScreen.kt` | Theme, map style and language choices, "Update data now"; link to About |
| `java/…/ui/settings/SettingsViewModel.kt` | Saves theme/map style; applies theme and language through AppCompat |
| `java/…/data/AppSettings.kt` | Theme and map-style settings and their SharedPreferences store |
| `java/…/ui/map/MapScreen.kt` | Full-screen map, station sheet (non-modal) or side panel on wide windows, credits that follow them, status bar icons |
| `java/…/ui/map/CompassButton.kt` | Compass button above the my-location button (only while the map is rotated or tilted) |
| `java/…/ui/map/MapTopControls.kt` | Search bar, settings button, date pill and fuel button over the top of the map (one row or two) |
| `java/…/ui/map/MapSearch.kt` | The search bar and its results (full screen, or dropping down on wide windows) |
| `java/…/data/StationSearch.kt` | Offline search over stations and municipalities: matching and ranking |
| `java/…/data/Favorites.kt` | Favourite stations: list operations, re-registered stations, their SharedPreferences store |
| `java/…/ui/map/FavoritesList.kt` | The favourites in the empty search |
| `java/…/data/PlaceNames.kt` | Municipality names in Italian title case ("Reggio nell'Emilia") |
| `java/…/ui/map/SidePanel.kt` | The side panel (station details, "near me" list) on wide windows |
| `java/…/ui/WindowSize.kt` | Wide-window test (Material window size classes) and the 600 dp content width for Settings/About |
| `java/…/map/MapLibreMap.kt` | MapLibre `MapView` inside Compose (all MapLibre glue) |
| `java/…/map/MapProvider.kt` | Map style URLs and credits: change `CurrentMapProvider` to switch provider |
| `java/…/ui/map/MapViewModel.kt` | Loads the cache, then refreshes in the background |
| `java/…/ui/map/DataStatusCard.kt` | Data date and loading / offline / error / update messages |
| `java/…/data/StationData.kt` | Kotlin mirror of the published JSON (schema v1) + parser |
| `java/…/data/StationRepository.kt` | Download, validation, file cache |
| `java/…/data/RefreshPolicy.kt` | When to contact the server |
| `java/…/data/DataSource.kt` | Where the data is published (one URL) |
| `java/…/map/StationLayers.kt` | Stations as clustered markers (count in the circle, "from" price pill), selection ring, tap handling (cluster or pill → zoom in, station → select) |
| `java/…/map/LabelLanguage.kt` | Map place names in the app language |
| `java/…/map/StationIcons.kt` | Marker icons per price class (colour + shape), also used by the legend |
| `java/…/data/FuelChoice.kt` | Chosen fuel/mode, its price per station, saved choice |
| `java/…/data/PriceRanking.kt` | Price comparison with nearby stations (constants in one place) |
| `java/…/ui/map/FuelSelector.kt` | Fuel selector button over the map and its bottom sheet (fuel, Self/Servito, legend) |
| `java/…/ui/station/StationDetails.kt` | Station + prices per fuel, from the cached data (pure Kotlin) |
| `java/…/ui/station/PriceFormat.kt` | Price numbers and "days since reported" |
| `java/…/ui/station/StationSheet.kt` | Station sheet content and the Navigate (`geo:`) intent |
| `java/…/data/UserLocation.kt` | One position on request (LocationManager, no Google services) |
| `java/…/data/Geo.kt` | Distances and circles as the crow flies |
| `java/…/data/Nearby.kt` | "Near me" list: stations within a radius, sorted; saved radius/sort |
| `java/…/ui/map/NearMeViewModel.kt` | "Near me" state: permission answers, position, radius/sort |
| `java/…/ui/map/NearMePanel.kt` | Sheet messages while locating, or why there is no position |
| `java/…/ui/map/NearbyList.kt` | The "near me" list in the sheet |
| `java/…/map/UserLocationLayers.kt` | Accuracy disc and search circle on the map |
| `java/…/ui/about/AboutScreen.kt` | Data source, notices, map credits |
| `java/…/ui/theme/` | Fixed FuelUp light/dark palette |
| `res/values/strings.xml`, `res/values-it/strings.xml` | English and Italian text |
| `res/xml/locales_config.xml` | Languages offered in Android 13+ settings |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | What Android backs up: only the favourites |

### Station data: download, cache, refresh

On start the app shows the cached data at once, then decides whether to
contact the server (`data/RefreshPolicy.kt`):

1. No cache → download (`meta.json`, then `stations.json`).
2. The cache already holds **yesterday's** data → no network at all: MIMIT
   publishes day D's prices on the morning of D+1, so nothing newer exists.
3. Otherwise fetch `meta.json` (≈400 bytes) at most **once an hour**; the
   Retry button ignores this limit.
4. Download `stations.json` only if `meta.json` has a different date or
   checksum; check size, SHA-256, schema version and date, parse it, and only
   then replace the cache (`files/data/` in the app's private storage).
5. A `schemaVersion` the app doesn't know → keep the cache and ask the user to
   update the app.

**Update data now** (Settings → Data) skips rules 2 and 3: it always fetches
`meta.json`, then follows rules 4 and 5, and shows the outcome (updated / already
up to date / offline / error) and when the server was last checked.

Timeouts: 15 s to connect, 30 s per read; a timeout shows the offline state.

**Known limitation:** because of rule 2, a dataset **republished for the same
date** (for example after fixing a pipeline bug) does not reach phones that
already have that date by themselves. They get the fix with the next day's
data, or at once with **Update data now** in Settings. Publishing a fix under a
new date is not possible (the date is the price date).

**Test fixture:** the app's unit tests parse files generated by the pipeline
itself. After changing the JSON format, run
`python3 pipeline/scripts/make_android_fixture.py` and commit the result
(CI and the publish workflow fail until you do; see *Continuous integration*).

### Performance (measured)

Debug build on a Redmi Note 9 Pro (Android 11), 21.5k stations, 1 Oct 2026.
Release builds will be faster.

| What | Result |
|---|---|
| Download `stations.json` (gzip, Wi-Fi) | 0.4–1.1 s |
| Parse `stations.json` (background thread) | 0.55–0.9 s (one outlier 1.2 s) |
| Build GeoJSON for the map (background) | 55–135 ms |
| Hand GeoJSON to MapLibre (main thread) | 15–25 ms |
| Map labels in the app language (main thread) | 4–60 ms per style load |
| Java heap kept by the data | ~11 MB |
| Whole app memory (PSS) with map + data | ~225 MB (Java 30, native 59, graphics 65, debug code 55) |
| UI frames while the cache is parsed | 1–2% janky, 99th percentile 15–36 ms |
| Map frames while panning (SurfaceFlinger) | steady 16.7 ms (60 fps), none missed |
| Cold start | one long frame (~750 ms) from MapLibre start-up, with or without data |
| Build the search index (background, once per download) | ~1 s, +5 MB Java heap (3 Oct 2026) |
| Search per keystroke (21.5k stations) | 4–10 ms; up to ~70 ms for a 2-letter query matching thousands (3 Oct 2026) |
| Back from Settings/About to the map | before: map rebuilt, 1.04–2.16 s (median 1.27 s, 10 runs) until fully drawn; now: no redraw needed, the map is complete in every frame of the ~0.5 s fade (3 Oct 2026) |

To see the timings yourself: `adb logcat -s FuelUpPerf` (debug builds only).
Debug builds also enable StrictMode, which logs any disk or network access on
the main thread (MapLibre's own start-up shows up there; our code doesn't).

### Fuel filter and price comparison

**Fuel choice.** The button on the right of the map shows the current choice
and opens a bottom sheet with tiles for the standard product of each type
(Benzina, Gasolio, GPL, Metano incl. L-GNC, GNL); special products like
"Blue Super" don't count.
Self/Servito only for petrol and diesel: on 30/09/2026, LPG, methane and LNG were
89–97% served-only (GPL self: 152 of 4,530 stations). Stations not selling the
choice are removed from the map, so clusters and their "from" prices only count
relevant stations. Saved in `SharedPreferences`; default Benzina self.

**Comparison** (`data/PriceRanking.kt`). For each station, the median price of
its **25 nearest** stations selling the same fuel/mode **within 50 km**, then the
difference in cents:

| Difference from the local median | Class | Marker |
|---|---|---|
| ≥ 2 c below | cheap | green `#009E73`, down chevron |
| within ±2 c | average | light grey, plain |
| ≥ 2 c above | expensive | vermillion `#D55E00`, up chevron |
| ≥ 35 c below (petrol/diesel; GPL 20 c, methane/LNG 50 c) | to verify | grey, "?" |
| fewer than 5 neighbours within 50 km | not compared | hollow |

Why this way (all measured on 30/09/2026 data):
- **Nearest stations, not the visible area:** colours must not change while
  panning; it depends only on the data and the fuel choice. The 25th
  neighbour is ~2–3 km away in cities, ~8 km in the median case, ~18 km in the
  countryside, so it adapts to density, unlike a fixed radius or provinces.
- **Cents, not ranks:** prices are clumped. Agip Eni sells petrol at exactly 1.990
  at 3,171 of 3,847 stations, 10–20 c under local prices. Percentiles would
  call a station 1 c above an Eni "expensive".
- **Groups:** motorway stations are compared only with each other (8 nearest,
  100 km), since their median is ~5.5 c higher. **Livigno** (duty-free, ~50 c
  cheaper) is compared only with itself; otherwise every Livigno station would be
  "to verify".
- **To verify:** the brand-priced stations sit at 10–20 c below; from ~35 c
  it's mostly scattered unbranded stations at 1.56–1.62 €/l. Petrol self: 103
  stations flagged, diesel self: 115 (~0.5%). Flagged prices stay visible but
  are never coloured cheap and never become a cluster's "from" price.
- Result for petrol self, ordinary roads: 36% cheap, 26% average, 37%
  expensive. The band and outlier thresholds are per fuel in
  `PriceRanking.THRESHOLDS`, ready to be tuned separately.

**Colour-blind safety.** Shape always carries the class; colour is extra. The
light grey for "average" differs in lightness from both colours, checked with
protanopia/deuteranopia/tritanopia simulations of screenshots.

**Performance** (Redmi Note 9 Pro, debug build): ranking all stations for a
fuel takes 200–260 ms on a background thread (spread over the CPU cores), plus
~35 ms to build the map data; nothing is recomputed while panning.

**Debug builds are arm64-only** (~26 MB instead of ~62 MB). To use an x86_64
emulator, add `"x86_64"` to `abiFilters` in `app/build.gradle.kts` for that
build only (don't commit it).

### Near me

The my-location button (bottom right of the map) finds the user once and lists
the stations around them that sell the chosen fuel.

**At launch**, if the location permission is already granted (it is never
asked at launch), the app opens on the user: a 5 km circle framed, the list
lowered to its header. A last known position up to 10 minutes old is used at
once; a fresh one is then asked for in the background and, when it arrives,
updates the position, the list and the search distances. The map is framed
again only if the fresh position is more than 500 m away and the map hasn't
been moved meanwhile. The 5 km is for that view only: the saved radius comes
back with the button. Nothing is shown while locating, and a failure (no
position, location off) simply leaves the usual start. It happens once per
start, not after a rotation. `adb logcat -s FuelUpPerf` shows `launch fix`.

**Permission.** Asked only when the button is tapped, foreground only (never
background). Approximate and precise are requested together: on Android 12+
the user picks, and approximate is enough. Approximate alone was tried first,
but on Android ≤ 11 such apps can't use GPS and the network source alone stayed
silent on the test phone. Without permission the app works as before; refused,
refused for good (button to the app's settings page), location off (button to
location settings) and "no position" each get an explanation in the sheet.

**Position** (`data/UserLocation.kt`). One fix per tap, no tracking, through
the platform `LocationManager` (no Google Play services):

1. A last known position at most **2 minutes** old is used at once.
2. Otherwise the fused, network and (if precise is allowed) GPS sources are
   asked together; the first answer wins.
3. Nothing within **20 s** → a last known position up to **30 minutes** old,
   else "no position".

The position stays in memory only: it is never saved or sent anywhere, so the
Play Store Data safety form declares nothing collected.

**On the map** (`map/UserLocationLayers.kt`). A translucent disc as large as
the reported accuracy with a small centre mark (no precise-looking dot), and
the search radius as a solid circle on a soft halo. Colours follow the map's
darkness, like the clusters. Disc and circle lie under the stations; the centre
mark is drawn above them, so price labels that would collide with it are left
out rather than half-covered. Shown only while "near me" is open.

**The list** (`data/Nearby.kt`, `ui/map/NearbyList.kt`). Radius 5, 10 or 20 km
as the crow flies (no roads); default 10 km: ~40 stations at the median place
and at least 3 in 99% of places (measured). Sort by price (default; ties by
distance, prices "to verify" always last) or by distance (ties by price).
Radius and sort are saved in `SharedPreferences`. Each row shows the price
class in icon and words, price, brand, distance (whole km when the fix is
worse than 500 m), a motorway badge and the report age.

**Sheet behaviour.** Tapping a row opens that station (collapsed, with the
chosen fuel); Back returns to the list. Tapping empty map leaves the selected
station, or else lowers the list to its header (title, sort, radius) so the
circle on the map stays explained. Back: station → list → collapsed → closed.

### Wide screens (landscape, tablets)

When the window is at least **600 dp wide** (Material's "medium" width class:
phones in landscape, tablets in both orientations, a wide split-screen half),
the station details and the "near me" list move from the bottom sheet to a
**side panel** (`ui/map/SidePanel.kt`). Width decides, not orientation
(`ui/WindowSize.kt`, `isWideWindow()`); portrait phones keep the sheet.

- **Left, 360 dp, floating** over the map with an 8 dp margin, like Google Maps.
  360 dp is about a portrait phone's width, so the sheet's contents fit as
  they are. 320 dp was tried on the phone in landscape: list rows wrapped to a
  third line and fewer fitted.
- The panel's height follows its content, so the **minimised** list (empty-map
  tap) is a short card with just the header; tapping the title brings it back.
- A **close button** (×) at the end of each header does what swiping the sheet
  away does. In the panel, the list's Price/Distance switch has its own line
  under the title.
- **Back:** station → list (same scroll) → closed. No collapsed/expanded step:
  the panel always shows everything.
- **Camera:** the near-me circle is framed beside the panel, a station picked
  from the list is centred in the free part of the map, and a station tapped
  where the panel opens is panned just enough to stay visible.
- **Overlays:** the panel opens under the search bar, which is aligned with it
  (see *Search*), so the top controls never move; the credits line moves to
  the right of the open panel; the my-location button stays on the right.
- **Rotation** keeps the content (station or list, minimised or not) and the
  scroll position. The sheet comes back collapsed or expanded as it was, or
  expanded if the panel was scrolled, so the position stays visible. The
  sheet state is built from the screen's own saved flags, not from the
  sheet's built-in saved state, which would bring back how the sheet was
  before a stay in landscape.
- **Settings and About** keep their content at most 600 dp wide, centred.

### Full-screen map

The map screen has no top bar: the map runs edge to edge, under the status
bar. Settings and About keep their own top bar with Back.

- **Surfaces:** everything floating over the map (search bar, gear, date
  pill, fuel button, bottom sheet, side panel) has one background,
  `floatingSurfaceColor()`: white in the light theme, a raised dark grey in the
  dark one, whatever the map style. The sheet's shadow matches the side panel's.
- **Top controls** (`ui/map/MapTopControls.kt`): the search bar (56 dp) with
  a round settings (gear) button beside it, the date pill and the fuel button
  (48 dp). Portrait: bar and gear on the first row, pill (left) and fuel button
  (right) on the second. Wide windows: one row (bar aligned with the side
  panel, gear, pill centred in the rest, fuel button at the right end); if the
  pill and the fuel button don't fit there, they move to a second row on the
  right. Measured, so longer labels (e.g. "Benzina · Servito") fall back by themselves.
- **Compass:** MapLibre's own compass is turned off (it sat under the search
  bar). While the map is rotated or tilted, a small round button with a needle
  appears above the my-location button and moves with it; tapping it turns
  the map back to north up, flat. To test without two-finger gestures (adb
  can't make them), temporarily add `.bearing(45.0)` to the start camera in
  `MapLibreMap.kt` (don't commit it).
- **Status bar:** its icons follow the map (dark icons on the light map,
  light on the dark one; the app theme while Settings or About is open), over
  a faint wash of the map's own tone so place names don't mix with the clock.
- **Insets:** controls and side panel keep clear of the status bar, the camera
  cutout and a side navigation bar. The fully expanded sheet stops just below
  the status bar; a tall sheet covers the credits and the my-location button
  rather than pushing them up into the controls.
- **Camera:** the "near me" circle and a station picked from the list are
  framed below the controls; a tapped station under the panel's place or the
  controls is panned just enough to come out.

**Tablet emulator** (no tablet needed): with the SDK command-line tools,

```sh
sdkmanager "system-images;android-36;google_apis;x86_64" emulator
avdmanager create avd -n fuelup_tablet -k "system-images;android-36;google_apis;x86_64" -d pixel_tablet
emulator -avd fuelup_tablet
```

Build with `x86_64` (see above). `adb emu geo fix` didn't reach the app's
location sources there; a test provider does:

```sh
adb shell appops set com.android.shell android:mock_location allow
for p in gps network fused; do
  adb shell cmd location providers add-test-provider $p
  adb shell cmd location providers set-test-provider-enabled $p true
  adb shell cmd location providers set-test-provider-location $p --location 43.66,10.63 --accuracy 20
done
```

### Search

The search bar at the top of the map finds **stations** (name, brand,
address, municipality, province) and **municipalities**, offline, in the data
already on the phone (`data/StationSearch.kt`). No geocoding: an address that
isn't a station's isn't found.

**Matching.** Texts and queries are cut into words the same way: lowercase,
accents removed ("forli" = "Forlì"), split at anything that isn't a letter or
digit, apostrophes included ("sant elpidio" = "Sant'Elpidio"). Dotted
abbreviations become one word ("S.S." = "ss"). Every word typed must be the
**start** of a word of the target, in any order ("eni pisa", "piag viale").
On the data side only, some words are added so the full form also matches:
a lone "S." also counts as san / santo / santa / sant ("san ilario" finds
"S.ILARIO"), words joined at an apostrophe ("denza" finds "D'ENZA"), and
V.LE / C.SO / P.ZA / P.ZZA / F.LLI as viale / corso / piazza / fratelli.
Municipalities also match written without spaces ("santelpidio", "laquila");
the province code is a word too ("san giuliano pi"). Fewer than 2 letters: no results.

**Ranking.** Up to 5 municipalities: exact name, then names starting with the
text, then all words matching; bigger towns (more stations) first. Up to 50
stations: those matching every word **in full** before those matching only the
start of a longer word ("roma": stations in Rome before "ROMAIRONE"); then
stations selling the chosen fuel; then **nearest to the user** when the
position is known (launch or "near me"), else to the centre of the map. Rows
then show the distance (whole km from 10 km).

**Place or brand?** Municipalities come first only when the query is a place:
it is a municipality's exact name ("roma", "pisa", "apiro"), or more stations
lie in the matching municipalities than carry a brand matching it. Otherwise
the stations come first and the towns follow them. On the 2 Oct 2026 data:
"eni" (Agip Eni, 3,919 stations; no town), "api" (Api-Ip, 3,686, against
Apiro and Apice), "ip" (3,700, against Gazoldo degli Ippoliti) are brands;
"san" (486 towns against 106 stations of two "San…" brands), "bar", "reggio"
are places. Worked out from the data, so no list of brands in the code.

**Speed.** Each word of the data is stored once in a sorted list, and each
station keeps its words as numbers; the words starting with what was typed are
then one range of numbers (two binary searches), so checking a station is a few
integer comparisons. The index is built in the background after the map data,
once per download; each keystroke cancels the previous search.

**Results.** A municipality shows its number of stations and the cheapest
usable price of the chosen fuel (as a cluster's "from" price: no prices to
verify, no Livigno); tapping it frames its stations. Stations filed under the
wrong municipality (one "PISA" station is in Capannoli, 26 km away) would zoom
the map out, so the frame leaves out those more than **5× the median distance**
from the municipality's median point **and more than 10 km** away
(`Municipality.mainStations`). Towns with fewer than 3 stations keep all of them
(no telling which one is wrong). On the published 2 Oct 2026 data this leaves
out 177 stations in 133 municipalities, at most 87 km away. These are a
different error from the stations the pipeline drops (coordinates in another
province): the province is right but the municipality is wrong (e.g. an "ALA"
(TN) station 87 km from Ala, Pisa's Capannoli one), or the station is in an
outlying part of a large town (Rome's "COIL ANZIO", 47 km out). A station of the
same province is near each of them, so the pipeline's rule can't catch them. With a 5 km
floor, real outlying parts of compact towns were cut too. Pisa: the limit is
13 km, so Tirrenia (11 km, part of Pisa) stays and Capannoli goes. A station
shows the chosen fuel's price and class, like the "near me" rows; tapping it
selects it as a marker tap would. A station that doesn't sell the chosen fuel
has no marker: it is drawn as a hollow dot inside the selection ring.

**Layout.** Portrait: the search bar across the top with the gear on its
right; below, the date pill on the left and the fuel button on the right. Open,
the search covers the screen (Material 3 `ExpandedFullScreenSearchBar`; status
bar icons follow the app theme). Wide windows: one row; the search bar is as
wide as the side panel and aligned with it, the panel opens under it, and the
results drop down under the bar (`ExpandedDockedSearchBar`, at most ⅔ of the
height) with the map still in view, **only on windows at least 480 dp tall**
(tablets, `isTallWindow()`). Landscape phones use the full-screen search as in
portrait: the dropdown fitted one result above the keyboard. Back closes the keyboard, then the search.

### Favourites

The star after a station's name (sheet or side panel) adds it to the
favourites or removes it: outline = not a favourite, filled = favourite (shape,
not only colour; ink blue like the rest of the UI, since yellow would be close
to the price colours).

**Where they show.** When the search opens with nothing typed (like Google
Maps), the favourites come first, newest first: the chosen fuel's price and
class, municipality, report age, and the distance when "near me" has found the
user (whole km from 10 km). Tapping one opens it like a search result. On the
map, a favourite's marker has a small star at its top right, in the cluster
colour (dark blue on the light map, pale blue on the dark one) with an outline
in the opposite tone, so it can't be read as a price class; none on clusters.

**Stored** (`data/Favorites.kt`) as one JSON text in their own
SharedPreferences file, `favorites`, keyed by MIMIT's station id, with the last
name, brand, municipality, coordinates and data date seen. Nothing is sent
anywhere.

**Missing stations.** A favourite that isn't in the current data stays in the
list: "not in the data since <date>" (the last data date that had it), with a
star button to remove it; tapping it shows its last known spot as a hollow
dot. MIMIT gives a station a new id when it is registered again (e.g. a new
operator), at the same spot: if a station now sits at exactly the favourite's
coordinates (`StationsFile.stationsAt`, the same lookup as the sheet's "also at
this location"), the row offers "At this spot now: <name>", which moves the
star to it, in the same place in the list.

**Backup.** Android's Auto Backup (Google Drive, at most daily, end-to-end
encrypted with the screen lock on Android 9+) and phone-to-phone transfer
include **only** `favorites.xml` (`res/xml/backup_rules.xml` for Android 11 and
older, `res/xml/data_extraction_rules.xml` for 12+). So favourites come back
after a reinstall from the Play Store or on a new phone; the cached data,
settings and language don't. An `adb install` doesn't restore by itself; to
test: `adb shell bmgr backupnow io.github.filbeq.fuelup.debug`, uninstall,
install, `adb shell bmgr restore io.github.filbeq.fuelup.debug` (needs a backup transport on
the phone). Play's Data safety form counts as "collected" only what the app
itself sends off the phone; the backup is Android's, in the user's account, so
nothing is declared.

### Municipality names

MIMIT writes municipalities in capitals; the app shows them in Italian title
case (`data/PlaceNames.kt`): every word capitalised, also after a hyphen or an
apostrophe ("L'Aquila", "Antey-Saint-André"), except linking words (di, del,
della, nell', sul, in, la, li, …) unless they start the name ("Reggio
nell'Emilia", "La Spezia"). Station names and addresses stay as MIMIT writes them.

Checked against ISTAT's list of the 7,896 municipalities (3 Oct 2026): 15 differ.
Among those in the data (9):

| The app shows | Official name |
|---|---|
| Boffalora Sopra Ticino, Castelletto Sopra Ticino | … sopra Ticino |
| Appiano / Caldaro / Salorno / Termeno sulla Strada del Vino | … sulla strada del vino |
| Trodena nel Parco Naturale | Trodena nel parco naturale |
| Morra de Sanctis | Morra De Sanctis |
| San Giorgio la Molara | San Giorgio La Molara |

Not in today's data: Cortaccia, Cortina, Magrè and Montagna sulla strada del
vino, Sotto il Monte Giovanni XXIII ("Xxiii"), San Vincenzo La Costa. "Sopra"
is capitalised in 11 official names and lowercase in 2, so the rule keeps it
capitalised. 39 names in the data are not in today's ISTAT list (merged or
renamed municipalities, e.g. "CORIGLIANO CALABRO"), so they couldn't be checked.

### Localization rules

- No user-facing text in code: every string goes in `res/values/strings.xml`
  (English) **and** `res/values-it/strings.xml` (Italian). Lint fails the build
  if a translation is missing.
- Proper names (FuelUp, OpenFreeMap, …) are marked `translatable="false"`.
- When adding a language, add `res/values-xx/`, a line in
  `res/xml/locales_config.xml` and its code in `localeFilters`
  (`app/build.gradle.kts`; the APK keeps only those languages, also for the
  libraries' texts).

## Releases

The app is published as a signed APK on
[GitHub Releases](https://github.com/filbeq/FuelUp/releases) by
[`.github/workflows/release.yml`](.github/workflows/release.yml).

### Versions

`appVersionName` at the top of `android/app/build.gradle.kts` is the only
place to change: `MAJOR.MINOR.PATCH`, numbers 0–99. The `versionCode` Android
uses to accept an update is derived from it: `MAJOR × 10000 + MINOR × 100 +
PATCH` (0.1.0 → 100, 1.2.3 → 10203), so it grows with every release. While
`MAJOR` is 0 the GitHub Release is marked as a pre-release.

- PATCH: fixes only. MINOR: new features. MAJOR: 1.0.0 for the first stable
  (Play Store) version.
- Never reuse or lower a version: phones refuse to install a lower
  `versionCode` over a higher one.

### Release build

`isMinifyEnabled` and `isShrinkResources` are on: R8 removes unused code
(including everything behind `BuildConfig.DEBUG`: StrictMode, `PerfLog`) and
unused resources, and renames classes. Libraries ship their own keep rules
(kotlinx.serialization, MapLibre, AndroidX); `android/app/proguard-rules.pro`
holds only rules that a release build or a release test proved necessary, each
with its reason. R8 bugs only show in release builds: after upgrading a library,
test a release build on a phone (checklist below).

The APK carries MapLibre's native code for `arm64-v8a` and `armeabi-v7a`
(~25 MB): every real phone, including budget phones with a 32-bit Android.
x86/x86_64 (emulators, Chromebooks) are left out: +26 MB.

Debug builds install as a separate app, **FuelUp Dev**
(`io.github.filbeq.fuelup.debug`), so the release app and its data are never
touched by testing.

### Signing key

Every release must be signed with the **same key**, forever: Android installs
an update only if its signature matches the installed app. Create it once:

```sh
mkdir -p ~/fuelup-keys
keytool -genkeypair -v -keystore ~/fuelup-keys/fuelup-release.jks \
  -storetype PKCS12 -alias fuelup -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=filbeq"
```

`keytool` (part of the JDK; Android Studio's is in `jbr/bin/`) asks for a
password; with PKCS12 the key uses the same password as the keystore.

**Back up** the file `fuelup-release.jks` **and** its password, in two places
off this computer (e.g. a password manager entry with the file attached, plus
an encrypted USB stick). The alias is `fuelup`. If the key is lost, no update
can be installed over existing installs (users must uninstall, losing their
favourites); if it leaks, someone else can sign "updates". Never put it in the
repository (`*.jks` and `keystore.properties` are git-ignored).

To build a signed release locally, create `android/keystore.properties`
(git-ignored) pointing to the key outside the repository:

```properties
storeFile=/home/<you>/fuelup-keys/fuelup-release.jks
storePassword=<password>
keyAlias=fuelup
```

then `./gradlew assembleRelease` → `android/app/build/outputs/apk/release/app-release.apk`
(without `keystore.properties`: `app-release-unsigned.apk`). Check it with
`$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs <apk>`.
CI reads the same values from environment variables (`RELEASE_KEYSTORE_FILE`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`).

### Repository secrets

On GitHub: repository → **Settings** → **Secrets and variables** → **Actions**
→ **New repository secret**, three times:

| Name | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the key file as text: `base64 -w0 ~/fuelup-keys/fuelup-release.jks` (copy the whole output) |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `fuelup` |

Secrets can't be read back, only replaced. Workflows triggered by pull
requests from forks don't receive them.

**Dry run:** Actions → Release → **Run workflow** builds and signs the APK and
keeps it as a workflow artifact (7 days) without publishing anything. Its
signing-certificate SHA-256 (in the log) must match the local build's.

### Publishing a release

1. Bump `appVersionName` in `android/app/build.gradle.kts`.
2. Write `release-notes/<version>.md` (user-facing: what changed). The
   workflow appends install instructions, the APK's SHA-256 and the signing
   certificate's SHA-256.
3. Test a release build on a phone (below), commit, push, wait for CI.
4. Tag and push the tag: `git tag v<version> && git push origin v<version>`.
5. The workflow checks that the tag matches `appVersionName`, runs tests and
   lint, builds and signs the APK and creates the Release with
   `FuelUp-<version>.apk` and `mapping-<version>.txt` (R8's renaming map, to
   decode stack traces from crash reports with `retrace`).

### Release test checklist

Install the signed release APK (`adb install -r <apk>`) and check, with
`adb logcat -b crash` open: first start without cache (download, map, date);
restart from cache; every fuel and self/served, choice kept after restart;
station sheet (expand, Navigate, "Also at this location"); search (town,
station, brand); favourites (star, list, map star, kept after restart); near
me (permission, radius, sort, opening on the position at the next start);
Settings (theme, map style, language, Update data now); About; rotation and
side panel; compass; process death on Settings (Home, `adb shell am kill
io.github.filbeq.fuelup`, reopen: Settings is back; check the screen before
the kill, a Back too many leaves the app and the test proves nothing); offline
start.
