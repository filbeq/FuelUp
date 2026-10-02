package io.github.filbeq.fuelup.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
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
 * Asks Android for the user's **approximate** position once (no tracking).
 * Uses the platform's LocationManager, not Google Play services, so it works
 * on any phone. FuelUp only holds ACCESS_COARSE_LOCATION: Android then gives a
 * position accurate to within about 3 km² (see CLAUDE.md, step 7).
 */
class UserLocator(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)

    suspend fun locate(): LocateResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return LocateResult.NoPermission
        }
        if (manager == null) return LocateResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocateResult.LocationOff
        val provider = provider(manager) ?: return LocateResult.Unavailable

        return try {
            val last = manager.getLastKnownLocation(provider)
            val location = last?.takeIf { it.ageMillis() <= RECENT_MS }
                ?: withTimeoutOrNull(TIMEOUT_MS) { currentLocation(manager, provider) }
                // Nothing fresh in time: an older fix is still better than nothing.
                ?: last?.takeIf { it.ageMillis() <= STALE_MS }
            location?.let { LocateResult.Found(UserPosition(it.latitude, it.longitude, it.accuracy)) }
                ?: LocateResult.Unavailable
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            LocateResult.NoPermission
        }
    }

    /** Fused (Android 12+, combines Wi-Fi, cell and GPS) if present, else the network provider. */
    private fun provider(manager: LocationManager): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER) ->
            LocationManager.FUSED_PROVIDER
        LocationManager.NETWORK_PROVIDER in manager.allProviders -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    @Suppress("MissingPermission") // checked in locate()
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancel = CancellationSignal()
            continuation.invokeOnCancellation { cancel.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancel,
                ContextCompat.getMainExecutor(context),
            ) { location -> if (continuation.isActive) continuation.resume(location) }
        }

    private fun Location.ageMillis() =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

    private companion object {
        /** A last known position this recent is used directly (instant answer). */
        const val RECENT_MS = 2 * 60 * 1000L
        /** Fallback when no fresh position arrives in time. */
        const val STALE_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 15_000L
    }
}
