package efa

import java.time.Duration
import java.time.Instant

/**
 * NA2: Reset the day, the tool for a night that went wrong (no sleep, a sleep far from the plan). It is a loosening that is limited, costed and visible:
 * one a week, a wall that gets longer with each use in the last 28 days, and a log. It switches off the next alarm (NA1) and offers to start the day from when you are up.
 * It never touches blocking, the block list or the night rules.
 */
const val RESET_ALLOWANCE_DAYS = 7
const val RESET_WALL_WINDOW_DAYS = 28
const val RESET_WALL_STEP = 25

/** How many resets are in the last [days] days. [log] is the epoch milliseconds of each one. */
fun resetUses(log: List<Long>, now: Instant, days: Int): Int {
    val from = now.minus(Duration.ofDays(days.toLong())).toEpochMilli()
    return log.count { it > from && it <= now.toEpochMilli() }
}

/** One reset a week: it is available when there has been none in the last 7 days. */
fun resetAvailable(log: List<Long>, now: Instant): Boolean = resetUses(log, now, RESET_ALLOWANCE_DAYS) == 0

/** When the next reset becomes available, or null if it is available now. */
fun resetNextAt(log: List<Long>, now: Instant): Instant? {
    val from = now.minus(Duration.ofDays(RESET_ALLOWANCE_DAYS.toLong())).toEpochMilli()
    return log.filter { it > from }.maxOrNull()?.let { Instant.ofEpochMilli(it).plus(Duration.ofDays(RESET_ALLOWANCE_DAYS.toLong())) }
}

/** The wall for the next reset: the base wall, and 25 more for each reset in the last 28 days. */
fun resetWallLength(log: List<Long>, now: Instant): Int = WALL_BASE + RESET_WALL_STEP * resetUses(log, now, RESET_WALL_WINDOW_DAYS)

fun decodeResetLog(text: String?): List<Long> = text.orEmpty().lines().mapNotNull { it.trim().toLongOrNull() }
fun encodeResetLog(log: List<Long>): String = log.takeLast(60).joinToString("\n")
