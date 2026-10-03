package io.github.filbeq.fuelup.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Where the user is, as reported by the phone. [accuracyMeters] is the reported uncertainty radius. */
data class UserPosition(val lat: Double, val lon: Double, val accuracyMeters: Float)

/** Outcome of [UserLocator.locate]. */
sealed interface LocateResult {
    /** [lastKnown]: an earlier fix the phone already had (instant, possibly a bit old). */
    data class Found(val position: UserPosition, val lastKnown: Boolean = false) : LocateResult
    /** Location is switched off in the phone's settings. */
    data object LocationOff : LocateResult
    /** No position in time, or no location provider on this phone. */
    data object Unavailable : LocateResult
    /** The app doesn't have the location permission (anymore). */
    data object NoPermission : LocateResult
}

/**
 * Asks Android for the user's position once (no tracking). Uses the platform's
 * LocationManager, not Google Play services, so it works on any phone.
 * FuelUp asks for approximate and precise location together; on Android 12+
 * the user may grant approximate only (accurate to within about 3 km²), which
 * is enough. On Android 11 and older approximate-only apps can't use GPS, and
 * the network source alone can stay silent, hence precise too (CLAUDE.md, step 7).
 */
class UserLocator(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)

    /**
     * A position, fast: a last known fix at most [recentMs] old if there is one,
     * else the first fresh answer within [TIMEOUT_MS], else an older last known fix.
     */
    @Suppress("MissingPermission") // checked in withProviders()
    suspend fun locate(recentMs: Long = RECENT_MS): LocateResult = withProviders { manager, providers ->
        val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.minByOrNull { it.ageMillis() }
        last?.takeIf { it.ageMillis() <= recentMs }?.let { return@withProviders found(it, lastKnown = true) }
        val fresh = withTimeoutOrNull(TIMEOUT_MS) { firstLocation(manager, providers) }
        // Nothing fresh in time: an older fix is still better than nothing.
        fresh?.let { found(it) }
            ?: last?.takeIf { it.ageMillis() <= STALE_MS }?.let { found(it, lastKnown = true) }
            ?: LocateResult.Unavailable
    }

    /** A fresh fix only (no last known one), within [TIMEOUT_MS]: refines a [locate] answered from the last known fix. */
    suspend fun freshLocation(): LocateResult = withProviders { manager, providers ->
        withTimeoutOrNull(TIMEOUT_MS) { firstLocation(manager, providers) }?.let { found(it) } ?: LocateResult.Unavailable
    }

    /** Checks permission, location switch and providers, then runs [block]. */
    private suspend fun withProviders(block: suspend (LocationManager, List<String>) -> LocateResult): LocateResult {
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precise && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return LocateResult.NoPermission
        if (manager == null) return LocateResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocateResult.LocationOff
        val providers = providers(manager, precise)
        if (providers.isEmpty()) return LocateResult.Unavailable
        return try {
            block(manager, providers)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            LocateResult.NoPermission
        }
    }

    private fun found(location: Location, lastKnown: Boolean = false) =
        LocateResult.Found(UserPosition(location.latitude, location.longitude, location.accuracy), lastKnown)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * "fused" (Wi-Fi, cell and GPS combined; public from Android 12, present on
     * some older phones), "network", and GPS when precise location is allowed
     * (needed for GPS before Android 12). Only the ones this app may use are listed.
     */
    private fun providers(manager: LocationManager, precise: Boolean): List<String> {
        val available = manager.getProviders(true)
        return listOfNotNull(FUSED, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER.takeIf { precise })
            .filter { it in available }
    }

    /** Asks every provider at once; the first position wins (one may be much slower than the other). */
    @Suppress("MissingPermission") // checked in withProviders()
    private suspend fun firstLocation(manager: LocationManager, providers: List<String>): Location? =
        suspendCancellableCoroutine { continuation ->
            val signals = providers.map { CancellationSignal() }
            fun cancelAll() = signals.forEach { it.cancel() }
            continuation.invokeOnCancellation { cancelAll() }
            var pending = providers.size
            providers.forEachIndexed { i, provider ->
                LocationManagerCompat.getCurrentLocation(manager, provider, signals[i], ContextCompat.getMainExecutor(context)) { location ->
                    // Runs on the main thread: no locking needed.
                    pending--
                    if (!continuation.isActive) return@getCurrentLocation
                    if (location != null) {
                        cancelAll()
                        continuation.resume(location)
                    } else if (pending == 0) {
                        continuation.resume(null)
                    }
                }
            }
        }

    private fun Location.ageMillis() =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

    companion object {
        /** A last known position this recent is used directly (instant answer). */
        const val RECENT_MS = 2 * 60 * 1000L
        /** At launch a slightly older one is fine: a fresh fix follows in the background. */
        const val LAUNCH_RECENT_MS = 10 * 60 * 1000L
        /** Fallback when no fresh position arrives in time. */
        private const val STALE_MS = 30 * 60 * 1000L
        private const val TIMEOUT_MS = 20_000L
        /** LocationManager.FUSED_PROVIDER, whose constant only exists from Android 12. */
        private const val FUSED = "fused"
    }
}
