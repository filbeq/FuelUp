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
    data class Found(val position: UserPosition) : LocateResult
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

    suspend fun locate(): LocateResult {
        val precise = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!precise && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return LocateResult.NoPermission
        if (manager == null) return LocateResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocateResult.LocationOff
        val providers = providers(manager, precise)
        if (providers.isEmpty()) return LocateResult.Unavailable

        return try {
            val last = providers.mapNotNull { manager.getLastKnownLocation(it) }.minByOrNull { it.ageMillis() }
            val location = last?.takeIf { it.ageMillis() <= RECENT_MS }
                ?: withTimeoutOrNull(TIMEOUT_MS) { firstLocation(manager, providers) }
                // Nothing fresh in time: an older fix is still better than nothing.
                ?: last?.takeIf { it.ageMillis() <= STALE_MS }
            location?.let { LocateResult.Found(UserPosition(it.latitude, it.longitude, it.accuracy)) }
                ?: LocateResult.Unavailable
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            LocateResult.NoPermission
        }
    }

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
    @Suppress("MissingPermission") // checked in locate()
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

    private companion object {
        /** A last known position this recent is used directly (instant answer). */
        const val RECENT_MS = 2 * 60 * 1000L
        /** Fallback when no fresh position arrives in time. */
        const val STALE_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 20_000L
        /** LocationManager.FUSED_PROVIDER, whose constant only exists from Android 12. */
        const val FUSED = "fused"
    }
}
