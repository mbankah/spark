package app.efa

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import app.efa.data.EfaDb
import app.efa.data.TamperRow
import efa.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * G10, E14: the trusted time source. Every minute the wall clock is compared with the phone's elapsed counter, which cannot be set by the user; every 15 minutes it
 * is compared with network time. A clock changed by hand is logged as tampering, and while the network says the phone is more than two minutes out, [BlockingState]
 * uses corrected time, so setting the clock back does not stretch a timer or move a cutoff. Failure behaviour (X7): with no network and no jump nothing changes; a
 * failed lookup is never treated as tampering.
 *
 * Limits, stated plainly: the correction applies to blocking, the Buffer notice and the widgets. Alarms are set with the system's own clock, so an alarm can still be
 * moved by someone who changes the system time, and the log is the record of it. Network time is a plain UDP lookup, so someone who controls your network could
 * answer it falsely.
 */
object TimeTrust {
    @Volatile var networkOffsetMs: Long? = null; private set
    @Volatile private var offsetAtElapsed = 0L
    private val watch = ClockWatch()
    @Volatile private var lastLookup = 0L

    /** The correction in use now. An answer older than a day is not used. */
    fun correction(): Long = if (networkOffsetMs != null && SystemClock.elapsedRealtime() - offsetAtElapsed < 24L * 3_600_000) correctionMs(networkOffsetMs) else 0L

    /** For tests: stands in for a network answer. */
    fun setNetworkOffsetForTest(ms: Long?) { networkOffsetMs = ms; offsetAtElapsed = SystemClock.elapsedRealtime() }

    private fun bootId(ctx: Context): Long = try { Settings.Global.getInt(ctx.contentResolver, "boot_count").toLong() } catch (e: Exception) { 0L }

    /** Compares the clocks. Returns the jump found, logging it. [wallMs] and [elapsedMs] are given in so a test can move them. */
    suspend fun observe(ctx: Context, wallMs: Long = System.currentTimeMillis(), elapsedMs: Long = SystemClock.elapsedRealtime(), boot: Long = bootId(ctx)): ClockJump? {
        val jump = watch.observe(wallMs, elapsedMs, boot) ?: return null
        try { EfaDb.get(ctx).tamper().insert(TamperRow(atMs = wallMs, kind = "clock_changed", detail = "${jump.kind} by ${kotlin.math.abs(jump.driftMs) / 60_000} minutes")) } catch (e: Exception) { /* a log that cannot be written must not stop the check */ }
        return jump
    }

    /** One SNTP lookup, or null if the network does not answer. */
    fun lookup(host: String = "time.google.com", timeoutMs: Int = 3_000): Long? = try {
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            val req = ntpRequest(); val t1 = System.currentTimeMillis()
            s.send(DatagramPacket(req, req.size, InetAddress.getByName(host), 123))
            val buf = ByteArray(48); s.receive(DatagramPacket(buf, buf.size))
            ntpOffsetMs(buf, t1, System.currentTimeMillis())
        }
    } catch (e: Exception) { null }

    /** Called by the blocking service every minute. */
    suspend fun tick(ctx: Context) {
        try {
            observe(ctx)
            if (SystemClock.elapsedRealtime() - lastLookup > 15 * 60_000L) {
                lastLookup = SystemClock.elapsedRealtime()
                val off = lookup()
                if (off != null) {
                    val before = correction()
                    networkOffsetMs = off; offsetAtElapsed = SystemClock.elapsedRealtime()
                    if (correctionMs(off) != 0L && before == 0L) try { EfaDb.get(ctx).tamper().insert(TamperRow(atMs = System.currentTimeMillis(), kind = "clock_differs", detail = "the phone's clock is ${kotlin.math.abs(off) / 60_000} minutes ${if (off > 0) "behind" else "ahead of"} network time")) } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { /* X7: never breaks blocking */ }
    }
}
