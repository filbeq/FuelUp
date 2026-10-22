package io.github.filbeq.fuelup.data

/** Where the station data comes from. */
object DataSource {
    /** The copy published by the FuelUp data workflow (GitHub Pages). */
    const val BASE_URL = "https://filbeq.github.io/FuelUp/"
    const val META_FILE = "meta.json"
    const val STATIONS_FILE = "stations.json"

    // MIMIT's own files, downloaded and cleaned on the phone. Since 16 Oct 2026
    // MIMIT answers only Italian IP addresses (issue #4).
    const val MIMIT_PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
    const val MIMIT_STATIONS_URL = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
}

/** Where the data on screen came from. */
enum class DataOrigin {
    /** Downloaded from MIMIT and cleaned on the phone. */
    MIMIT,
    /** The copy published by FuelUp on GitHub Pages. */
    PAGES,
}
