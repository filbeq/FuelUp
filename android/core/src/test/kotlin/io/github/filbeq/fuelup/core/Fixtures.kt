package io.github.filbeq.fuelup.core

/** Test fixtures: real MIMIT rows picked for their quirks, plus synthetic ones. */
object Fixtures {
    fun stations(): StationFile = open("stations.csv").useMimitLines(::parseStations)
    fun prices(): PriceFile = open("prices.csv").useMimitLines(::parsePrices)

    /** The fixtures are tiny and full of bad rows on purpose: no size checks. */
    val NO_LIMITS = Thresholds(minStations = 0, minPrices = 0, maxDropRatio = 1.0)

    private fun open(name: String) =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$name")) { name }
}
