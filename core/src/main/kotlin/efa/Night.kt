package efa

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val LONDON: ZoneId = ZoneId.of("Europe/London")
val FALL_ASLEEP_DEFAULT: Duration = Duration.ofMinutes(20)   // N1 default

/** N1: cutoff = tomorrow's target wake - 8 h - time to fall asleep. Real elapsed time, so it is right across a clock change (X4). */
fun nightCutoff(targetWake: ZonedDateTime, fallAsleep: Duration = FALL_ASLEEP_DEFAULT): ZonedDateTime =
    targetWake.minusHours(8).minus(fallAsleep)

/** N7: no override is possible from 6 h before target wake. */
fun hardStop(targetWake: ZonedDateTime): ZonedDateTime = targetWake.minusHours(6)

/** An override can be bought from the cutoff until the hard stop. */
fun overrideAllowed(now: ZonedDateTime, targetWake: ZonedDateTime) =
    now.isBefore(hardStop(targetWake)) && !now.isBefore(nightCutoff(targetWake))

/** N3 / N5: 30 minutes, 45 after a fully verified day. */
fun overrideMinutes(fullyVerifiedDay: Boolean) = if (fullyVerifiedDay) 45 else 30

/** N6: "Wake 09:00, so sleeping after 01:00 means under 8h". */
fun eightHourSum(targetWake: ZonedDateTime): String {
    val f = DateTimeFormatter.ofPattern("HH:mm")
    return "Wake ${f.format(targetWake)}, so sleeping after ${f.format(targetWake.minusHours(8))} means under 8h"
}

fun at(date: LocalDate, t: LocalTime, zone: ZoneId = LONDON): ZonedDateTime = ZonedDateTime.of(date, t, zone)

// ---- Z3: the wake time can drift later, then is pulled back ----

/** Z3: tomorrow's target wake can be later by what this morning was late, then it comes back by this much a day. */
const val DRIFT_PULL_BACK_MIN = 15
/** The most one late morning can move the target (Q-G63), so oversleeping cannot push the night cutoff later without limit. */
const val DRIFT_CAP_MIN = 120

/** How many minutes later than planned the wake was on each date (only dates where it was later). */
typealias Lateness = Map<LocalDate, Int>

/**
 * Z3: the offset in minutes added to the target wake of [date]. A morning that was late by L minutes moves the next day's target later by L
 * (at most [DRIFT_CAP_MIN]), and each day after that the offset comes back by 15 minutes, until it is gone. A later morning can set it again.
 * Worked out from the lateness of the last 14 days, oldest first.
 */
fun wakeDrift(lateness: Lateness, date: LocalDate): Int {
    var offset = 0
    var d = date.minusDays(14)
    while (!d.isAfter(date)) {
        val fresh = (lateness[d.minusDays(1)] ?: 0).coerceIn(0, DRIFT_CAP_MIN)
        offset = maxOf(fresh, offset - DRIFT_PULL_BACK_MIN, 0)
        d = d.plusDays(1)
    }
    return offset
}

/** The planned wake after [after], moved later by the drift for its date (Z3). */
fun driftedWake(pattern: List<PatternSlot>, after: ZonedDateTime, lateness: Lateness): ZonedDateTime? =
    nextWake(pattern, after)?.let { it.plusMinutes(wakeDrift(lateness, it.toLocalDate()).toLong()) }

// ---- N5: a fully verified day ----

/**
 * N5: every block of [date] that needs a proof (a lecture or tutorial's photo, a work block's photo or commit, a commitment's photo) has it, and
 * there is at least one such block. A block still to come counts as not verified. [now] decides which blocks have ended.
 */
fun fullyVerifiedDay(pattern: List<PatternSlot>, date: LocalDate, verifiedKeys: Set<String>, now: ZonedDateTime): Boolean {
    val needing = slotsFor(pattern, date, now.zone).filter { proofFor(it.slot) != Proof.NONE }
    return needing.isNotEmpty() && needing.all { it.end <= now && blockKey(it) in verifiedKeys }
}

/** The planned day a night belongs to: the day before the wake it ends at. */
fun dayOfNight(wake: ZonedDateTime): LocalDate = wake.toLocalDate().minusDays(1)
