package io.github.filbeq.fuelup.core

/*
 * Map MIMIT fuel names (descCarburante) to a small fixed set of fuel types.
 *
 * MIMIT publishes the standard fuels (Benzina, Gasolio, GPL, Metano, plus the
 * LNG variants GNL and L-GNC) and many branded or "speciale" products whose
 * names are chosen by each brand. Every name is mapped to a FuelType so the app
 * can filter by type; the original name is kept in the output for display.
 */

/** Declaration order is the order of the fuel table in stations.json. */
enum class FuelType { PETROL, DIESEL, LPG, CNG, LNG, OTHER }

enum class FuelUnit(val code: String) { LITRE("L"), KILOGRAM("KG") }

data class FuelInfo(
    val type: FuelType,
    val unit: FuelUnit,
    /** True for the official standard fuels, false for branded/special products. */
    val std: Boolean,
    /** False when the name was not in the known table (worth a look in the logs). */
    val known: Boolean,
)

private val STANDARD = mapOf(
    "benzina" to FuelType.PETROL,
    "gasolio" to FuelType.DIESEL,
    "gpl" to FuelType.LPG,
    "metano" to FuelType.CNG,
    "l-gnc" to FuelType.CNG, // LNG stored on site, dispensed as compressed gas to cars
    "gnl" to FuelType.LNG,
)

private val SPECIAL = mapOf(
    // Petrol
    "benzina speciale" to FuelType.PETROL,
    "benzina speciale 98 ottani" to FuelType.PETROL,
    "benzina 100 ottani" to FuelType.PETROL,
    "benzina 102 ottani" to FuelType.PETROL,
    "benzina plus 98" to FuelType.PETROL,
    "benzina wr 100" to FuelType.PETROL,
    "benzina shell v power" to FuelType.PETROL,
    "benzina energy 98 ottani" to FuelType.PETROL,
    "blue super" to FuelType.PETROL,
    "hiq perform+" to FuelType.PETROL,
    "hiq perform b100 ottani" to FuelType.PETROL,
    "v-power" to FuelType.PETROL,
    "verde speciale" to FuelType.PETROL,
    // Diesel, including HVO (renewable diesel)
    "gasolio speciale" to FuelType.DIESEL,
    "gasolio premium" to FuelType.DIESEL,
    "gasolio artico" to FuelType.DIESEL,
    "gasolio artico igloo" to FuelType.DIESEL,
    "gasolio alpino" to FuelType.DIESEL,
    "gasolio gelo" to FuelType.DIESEL,
    "gasolio oro diesel" to FuelType.DIESEL,
    "gasolio energy d" to FuelType.DIESEL,
    "gasolio ecoplus" to FuelType.DIESEL,
    "gasolio prestazionale" to FuelType.DIESEL,
    "gasolio plus" to FuelType.DIESEL,
    "gasolio bio hvo" to FuelType.DIESEL,
    "gasolio hvo" to FuelType.DIESEL,
    "blue diesel" to FuelType.DIESEL,
    "blu diesel alpino" to FuelType.DIESEL,
    "supreme diesel" to FuelType.DIESEL,
    "hi-q diesel" to FuelType.DIESEL,
    "diesel shell v power" to FuelType.DIESEL,
    "v-power diesel" to FuelType.DIESEL,
    "dieselmax" to FuelType.DIESEL,
    "s-diesel" to FuelType.DIESEL,
    "e-diesel" to FuelType.DIESEL,
    "gp diesel" to FuelType.DIESEL,
    "excellium diesel" to FuelType.DIESEL,
    "hvo" to FuelType.DIESEL,
    "hvo100" to FuelType.DIESEL,
    "hvolution" to FuelType.DIESEL,
    "hvovolution" to FuelType.DIESEL,
    "hvo future" to FuelType.DIESEL,
    "hvo eco diesel" to FuelType.DIESEL,
    "hvo energy diesel" to FuelType.DIESEL,
    "diesel hvo" to FuelType.DIESEL,
    "diesel hvo energy" to FuelType.DIESEL,
    "rehvo" to FuelType.DIESEL,
    "bchvo" to FuelType.DIESEL,
    // Fuel kind not confirmed yet (San Marco Petroli product): OTHER until it is.
    "f101" to FuelType.OTHER,
    "f-101" to FuelType.OTHER,
)

// Fallback for names never seen before. Order matters: "V-Power Diesel" must be
// caught by the diesel rule before the petrol one.
private val KEYWORDS = listOf(
    FuelType.LNG to listOf("gnl", "lng"),
    FuelType.CNG to listOf("gnc", "cng", "metano"),
    FuelType.LPG to listOf("gpl", "lpg"),
    FuelType.DIESEL to listOf("diesel", "gasolio", "hvo"),
    FuelType.PETROL to listOf("benzina", "super", "verde", "ottani", "v-power", "perform"),
)

private fun key(name: String): String = casefold(cleanText(name))

// MIMIT prices methane per kg; LNG is sold per kg as well.
private fun unitOf(type: FuelType): FuelUnit =
    if (type == FuelType.CNG || type == FuelType.LNG) FuelUnit.KILOGRAM else FuelUnit.LITRE

/** Type, unit and flags for a MIMIT fuel name. */
fun classifyFuel(name: String): FuelInfo {
    val key = key(name)
    STANDARD[key]?.let { return FuelInfo(it, unitOf(it), std = true, known = true) }
    SPECIAL[key]?.let { return FuelInfo(it, unitOf(it), std = false, known = true) }
    for ((type, words) in KEYWORDS) {
        if (words.any { it in key }) return FuelInfo(type, unitOf(type), std = false, known = false)
    }
    return FuelInfo(FuelType.OTHER, FuelUnit.LITRE, std = false, known = false)
}
