# FuelUp

**Italian fuel prices on a map.** FuelUp is a free Android app that shows the
fuel prices of petrol stations across Italy, using the open data published by
the Italian Ministry of Enterprises and Made in Italy (MIMIT).

> **Unofficial app.** FuelUp is an independent project. It is not affiliated with
> or endorsed by MIMIT.

## Status

Early development, not yet on the Play Store. Today the app shows every
station in Italy on the map (grouped when zoomed out) and works offline with
the last downloaded data. Tap a station to see its prices per fuel, self and
served, when each price was last reported, and to start navigation in your
favourite maps app. Price colours and a fuel filter are coming next.

Planned features:

- every station in Italy on the map, with its prices
- prices per fuel (petrol, diesel, LPG, methane, …), self-service and served
- colours from cheapest to most expensive for the fuel you choose
- stations near you (location is used only when you ask)
- works offline with the last downloaded prices
- English and Italian

## About the prices

Prices are those **in force at 08:00 on the data date** shown in the app, as
reported by the stations to the Ministry. The data is updated once a day and is
**not real time**: always check the price at the pump.

## Privacy

FuelUp has no accounts, no ads, no analytics and no tracking. It collects no
personal data. It downloads the daily price file and the map tiles; nothing
else leaves your phone.

## Credits

- **Price data:** Fonte dati: Ministero delle Imprese e del Made in Italy —
  Osservaprezzi Carburanti
  ([dataset](https://www.mimit.gov.it/it/open-data/elenco-dataset/carburanti-prezzi-praticati-e-anagrafica-degli-impianti)),
  [Italian Open Data License 2.0 (IODL 2.0)](https://www.dati.gov.it/content/italian-open-data-license-v20).
- **Map:** [OpenFreeMap](https://openfreemap.org),
  [© OpenMapTiles](https://www.openmaptiles.org),
  [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright).
- **Map engine:** [MapLibre Native](https://maplibre.org).

## Development

Building the app, running the data pipeline and publishing the data are
described in [DEVELOPMENT.md](DEVELOPMENT.md).
