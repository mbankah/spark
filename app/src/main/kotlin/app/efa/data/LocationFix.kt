package app.efa.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import efa.Fix
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One location read, taken when a proof photo is taken (A8). Never continuous, never stored.
 * Failure behaviour (X7): no permission, no provider or no answer within 12 seconds returns null, which the proof rules treat as
 * "no precise location" and ask for a retake. It never guesses a position.
 */
object LocationFix {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(ctx: Context, timeoutMs: Long = 12_000): Fix? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER); add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null
        // Each provider gets a share of the time. A provider that answers "no location" hands over to the next one,
        // so a phone whose fused provider has nothing still gets a GPS or network read.
        val share = timeoutMs / providers.size
        for (provider in providers) {
            val fix = withTimeoutOrNull(share) { readOnce(ctx, lm, provider) }
            if (fix != null) return fix
        }
        return null
    }

    private suspend fun readOnce(ctx: Context, lm: LocationManager, provider: String): Fix? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc?.toFix()) }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, object : LocationListener {
                        override fun onLocationChanged(l: Location) { if (cont.isActive) cont.resume(l.toFix()) }
                    }, Looper.getMainLooper())
                }
            } catch (e: SecurityException) { if (cont.isActive) cont.resume(null) }
        }

    private fun Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE)
}
