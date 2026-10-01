package io.github.filbeq.fuelup.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Fuel families, matching the pipeline's `type` values. */
enum class FuelKind {
    PETROL, DIESEL, LPG, CNG, LNG, OTHER;

    companion object {
        /** Unknown future types are shown as OTHER instead of failing. */
        fun of(type: String): FuelKind = entries.firstOrNull { it.name == type } ?: OTHER
    }
}

enum class ServiceMode { SELF, SERVED }

/**
 * The fuel the map shows and compares. Only the standard product of each type
 * counts (e.g. "Benzina", not "Blue Super"); methane covers Metano and L-GNC.
 */
data class FuelChoice(val fuel: FuelKind, val mode: ServiceMode) {
    /**
     * Self/served only makes sense for petrol and diesel: LPG, methane and LNG
     * are ~90–97% served-only, so for them the station's price is used
     * whatever the mode (the cheapest if it has both).
     */
    val modeApplies: Boolean get() = fuel == FuelKind.PETROL || fuel == FuelKind.DIESEL

    companion object {
        val Default = FuelChoice(FuelKind.PETROL, ServiceMode.SELF)

        /** Fuels offered in the selector, in display order. */
        val SELECTABLE = listOf(FuelKind.PETROL, FuelKind.DIESEL, FuelKind.LPG, FuelKind.CNG, FuelKind.LNG)

        /** Rebuilds a saved choice; anything unknown falls back to [Default]. */
        fun decode(fuel: String?, mode: String?): FuelChoice = FuelChoice(
            fuel = SELECTABLE.firstOrNull { it.name == fuel } ?: Default.fuel,
            mode = ServiceMode.entries.firstOrNull { it.name == mode } ?: Default.mode,
        )
    }
}

/** A station's price for a [FuelChoice]. */
class ChosenPrice(val priceMilli: Long, val updatedEpochSeconds: Long)

/** Which fuel entries of a [StationsFile] count for each [FuelChoice]: its standard products. */
fun StationsFile.standardFuelIndices(kind: FuelKind): Set<Int> =
    fuels.indices.filter { fuels[it].std && FuelKind.of(fuels[it].type) == kind }.toSet()

/** The station's price for [choice], or null if it doesn't sell it. [fuelIndices] from [standardFuelIndices]. */
fun Station.priceFor(choice: FuelChoice, fuelIndices: Set<Int>): ChosenPrice? {
    var best: LongArray? = null
    for (entry in prices) {
        if (entry[PriceEntry.FUEL].toInt() !in fuelIndices) continue
        if (choice.modeApplies) {
            val isSelf = entry[PriceEntry.SELF] == 1L
            if (isSelf != (choice.mode == ServiceMode.SELF)) continue
        }
        if (best == null || entry[PriceEntry.PRICE_MILLI] < best[PriceEntry.PRICE_MILLI]) best = entry
    }
    return best?.let { ChosenPrice(it[PriceEntry.PRICE_MILLI], it[PriceEntry.UPDATED]) }
}

/** Remembers the choice across launches (two small values in SharedPreferences). */
class FuelChoiceStore(private val prefs: SharedPreferences) {
    fun load(): FuelChoice = FuelChoice.decode(prefs.getString(KEY_FUEL, null), prefs.getString(KEY_MODE, null))

    fun save(choice: FuelChoice) {
        prefs.edit {
            putString(KEY_FUEL, choice.fuel.name)
            putString(KEY_MODE, choice.mode.name)
        }
    }

    private companion object {
        const val KEY_FUEL = "fuel"
        const val KEY_MODE = "mode"
    }
}
