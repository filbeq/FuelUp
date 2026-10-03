package io.github.filbeq.fuelup

import android.annotation.SuppressLint
import android.util.Log

/**
 * Performance measurements for debug builds (`adb logcat -s FuelUpPerf`).
 * Release builds log nothing.
 */
object PerfLog {
    private const val TAG = "FuelUpPerf"
    @PublishedApi internal const val MB = 1024 * 1024

    // Plain Log on purpose: "use Timber" comes from MapLibre's lint rules.
    @SuppressLint("LogNotTimber")
    fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    // Start of a measurement that ends somewhere else (see [mark] and [endMark]).
    private var markLabel: String? = null
    private var markStart = 0L

    /** Starts a measurement ended later by [endMark], e.g. "back to map" → first map frame. */
    fun mark(label: String) {
        if (!BuildConfig.DEBUG) return
        markLabel = label
        markStart = System.nanoTime()
    }

    /** Logs the time since [mark], if one is pending, and clears it. */
    fun endMark(what: String) {
        val label = markLabel ?: return
        markLabel = null
        log("$label → $what: ${(System.nanoTime() - markStart) / 1_000_000} ms")
    }

    inline fun <T> time(label: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        val start = System.nanoTime()
        val result = block()
        log("$label: ${(System.nanoTime() - start) / 1_000_000} ms")
        return result
    }

    /** Like [time], plus the Java heap kept before/after (forces a GC: debug only). */
    inline fun <T> timeWithHeap(label: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        val runtime = Runtime.getRuntime()
        runtime.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        runtime.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()
        log(
            "$label: $elapsed ms total, Java heap kept ${before / MB} → ${after / MB} MB " +
                "(max ${runtime.maxMemory() / MB} MB)",
        )
        return result
    }
}
