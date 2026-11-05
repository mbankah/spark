package efa

import java.time.Duration
import java.time.Instant

/**
 * Health Connect as an optional sleep input (S6). A watch or band that writes sleep to Health Connect gives a second opinion on the night. It is never needed and never
 * changes what the phone worked out (S1 to S5): it is shown beside it, and a big difference is said plainly, because the phone's own sensors can be fooled by a phone
 * left on a bed and a watch can be fooled by a night spent awake and still. Without permission, without Health Connect, or without a watch, nothing changes (X7).
 */
data class HealthSession(val start: Instant, val end: Instant, val asleepMin: Int)

/** The night this session belongs to, or null if none overlaps the phone's window by at least half of the session. */
fun sessionForNight(sessions: List<HealthSession>, nightStart: Instant, nightEnd: Instant): HealthSession? {
    fun overlapMin(s: HealthSession): Long {
        val a = maxOf(s.start, nightStart); val b = minOf(s.end, nightEnd)
        return if (b.isAfter(a)) Duration.between(a, b).toMinutes() else 0
    }
    return sessions.filter { overlapMin(it) * 2 >= Duration.between(it.start, it.end).toMinutes().coerceAtLeast(1) }.maxByOrNull { overlapMin(it) }
}

/** A line for the sleep card: the watch's night beside the phone's, with a note when they are far apart. */
fun healthLine(phoneSleepMin: Int, watch: HealthSession?): String? {
    if (watch == null) return null
    val diff = watch.asleepMin - phoneSleepMin
    fun h(m: Int) = "${m / 60}h ${m % 60}m"
    val base = "Your watch says ${h(watch.asleepMin)}. The phone says ${h(phoneSleepMin)}."
    return if (kotlin.math.abs(diff) >= HEALTH_DIFFERENCE_MIN) base + " They differ by ${h(kotlin.math.abs(diff))}, so treat both with care." else base
}

const val HEALTH_DIFFERENCE_MIN = 45
