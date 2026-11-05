package efa

/**
 * A trusted time source (G10, E14). Changing the phone's clock to beat a cutoff or a timer is caught two ways: the wall clock is compared with the phone's own
 * elapsed-time counter (which the user cannot set), and with network time (SNTP). Either one finding the clock more than [CLOCK_TOLERANCE_MS] out is logged as
 * tampering, and the Buffer, cutoffs and blocks then use the corrected time. Without a network answer and without a jump nothing is changed (X7).
 */

const val CLOCK_TOLERANCE_MS = 2 * 60_000L

/** What a comparison found. [driftMs] is positive when the wall clock ran ahead. */
data class ClockJump(val driftMs: Long, val kind: String)

/**
 * Compares how far the wall clock moved with how far the elapsed counter moved since the last look. The counter restarts at boot, so a new [bootId] starts a fresh
 * comparison and a reboot is never mistaken for tampering.
 */
class ClockWatch {
    private var bootId: Long? = null; private var wall = 0L; private var elapsed = 0L
    fun observe(wallMs: Long, elapsedMs: Long, bootId: Long): ClockJump? {
        val jump = if (this.bootId == bootId) {
            val drift = (wallMs - wall) - (elapsedMs - elapsed)
            if (kotlin.math.abs(drift) > CLOCK_TOLERANCE_MS) ClockJump(drift, if (drift > 0) "clock set forward" else "clock set back") else null
        } else null
        this.bootId = bootId; wall = wallMs; elapsed = elapsedMs
        return jump
    }
}

private const val NTP_UNIX_OFFSET_SECONDS = 2_208_988_800L

fun ntpRequest(): ByteArray = ByteArray(48).also { it[0] = 0x1B }             // version 3, client mode

/**
 * The offset of the phone's clock from the server's, in milliseconds (positive when the phone is behind), from an SNTP reply. [t1] is when the request left and [t4]
 * when the reply arrived, both on the phone. Null for a reply that is not a usable answer.
 */
fun ntpOffsetMs(reply: ByteArray, t1: Long, t4: Long): Long? {
    if (reply.size < 48) return null
    val mode = reply[0].toInt() and 0x07; val stratum = reply[1].toInt() and 0xFF
    if (mode != 4 || stratum == 0 || stratum > 15) return null
    fun ts(at: Int): Long {
        var sec = 0L; for (i in 0 until 4) sec = (sec shl 8) or (reply[at + i].toLong() and 0xFF)
        var frac = 0L; for (i in 4 until 8) frac = (frac shl 8) or (reply[at + i].toLong() and 0xFF)
        if (sec == 0L) return -1
        return (sec - NTP_UNIX_OFFSET_SECONDS) * 1000 + (frac * 1000 shr 32)
    }
    val t2 = ts(32); val t3 = ts(40)
    if (t2 < 0 || t3 < 0) return null
    return ((t2 - t1) + (t3 - t4)) / 2
}

/** The correction the app applies to the wall clock: the network offset once it is more than the tolerance away, otherwise none. */
fun correctionMs(networkOffsetMs: Long?): Long = if (networkOffsetMs != null && kotlin.math.abs(networkOffsetMs) > CLOCK_TOLERANCE_MS) networkOffsetMs else 0L
