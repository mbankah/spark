package efa

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * SA1: every night has two anchors, lights-out and wake. [night] is the evening the night starts on. Everything that depends on the day's edges
 * (the night cutoff, the alarm, meal windows, the guard rail, the end of the planned day) reads these and nothing else.
 */
data class NightAnchors(val night: LocalDate, val lightsOut: LocalDateTime, val wake: LocalDateTime) {
    init { require(wake.isAfter(lightsOut)) { "wake must be after lights-out" } }
    fun lightsOutAt(zone: ZoneId = LONDON): ZonedDateTime = ZonedDateTime.of(lightsOut, zone)
    fun wakeAt(zone: ZoneId = LONDON): ZonedDateTime = ZonedDateTime.of(wake, zone)
    /** Real elapsed minutes between lights-out and wake (right across a clock change, SA6). */
    fun inBedMinutes(zone: ZoneId = LONDON): Long = Duration.between(lightsOutAt(zone), wakeAt(zone)).toMinutes()
    /** The planned day the wake belongs to. */
    val wakeDate: LocalDate get() = wake.toLocalDate()
}

/** Why a pair of anchors is refused. Each has the text that says it and the choices (P2). */
sealed interface AnchorProblem {
    fun text(): String
    /** SA3: lights-out is after the cutoff for this wake time, so sleep would be under 8 hours. */
    data class UnderEightHours(val cutoff: LocalDateTime, val earliestWake: LocalDateTime) : AnchorProblem {
        override fun text() = "Lights-out ${clock(cutoff.toLocalTime())} is after the cutoff for this wake time (${clock(cutoff.toLocalTime())}), so sleep is under 8 hours. Wake at ${clock(earliestWake.toLocalTime())} or later, or go to sleep by ${clock(cutoff.toLocalTime())}."
    }
    /** SA5: a commitment starts before the person would be up and ready. */
    data class HidesCommitment(val title: String, val startsAt: LocalDateTime, val wake: LocalDateTime) : AnchorProblem {
        override fun text() = "${title.substringBefore(" - ")} starts at ${clock(startsAt.toLocalTime())} on ${label(startsAt.toLocalDate())}, before you would be up (wake ${clock(wake.toLocalTime())}). Wake earlier, or use the recording."
    }
}

private fun clock(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
private fun label(d: LocalDate): String = "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"

/** The part of the morning that has to fit between waking and a commitment: the Short tier of the getting-ready routines (water, light, wash, dress, Bible floor, pocket check). */
const val MORNING_FLOOR_MIN = 30

/** Types of commitment that a late wake would hide (RF2). Study cycles and the morning routine are not commitments: they move. */
val ANCHOR_COMMITMENT_TYPES = setOf("lecture", "tutorial", "mytutor", "training", "gym", "church", "match", "errand")

/** Default anchors for a night: wake from the pattern, lights-out at the N1 cutoff for it. */
fun defaultAnchors(night: LocalDate, wake: LocalDateTime, fallAsleep: Duration = FALL_ASLEEP_DEFAULT, zone: ZoneId = LONDON): NightAnchors {
    val cutoff = nightCutoff(ZonedDateTime.of(wake, zone), fallAsleep).toLocalDateTime()
    return NightAnchors(night, cutoff, wake)
}

/**
 * SA3, SA5: whether [a] can stand. It is refused when sleep would be under 8 hours (N1: asleep by wake minus 8 hours, lights-out is the cutoff) or when a
 * commitment on the wake date starts before the morning floor fits after waking. A refusal always says what would work.
 */
fun checkAnchors(a: NightAnchors, pattern: List<PatternSlot>, fallAsleep: Duration = FALL_ASLEEP_DEFAULT, zone: ZoneId = LONDON): List<AnchorProblem> {
    val out = ArrayList<AnchorProblem>()
    val cutoff = nightCutoff(a.wakeAt(zone), fallAsleep).toLocalDateTime()
    if (a.lightsOut.isAfter(cutoff)) out += AnchorProblem.UnderEightHours(cutoff, a.lightsOut.plus(fallAsleep).plusHours(8))
    val midnight = a.wakeDate.atStartOfDay()
    val needUp = a.wake.plusMinutes(MORNING_FLOOR_MIN.toLong())
    for (d in slotsFor(pattern, a.wakeDate, zone)) {
        val s = d.slot
        if (s.cancelled || (s.type !in ANCHOR_COMMITMENT_TYPES && !s.hardAlarm)) continue
        val startsAt = midnight.plusMinutes(s.startMin.toLong())
        if (startsAt.isBefore(needUp) && startsAt.isAfter(a.lightsOut)) out += AnchorProblem.HidesCommitment(s.title, startsAt, a.wake)
    }
    return out.distinctBy { it.text() }
}

/**
 * SA2: the guard rail "in the night". A block that is not free time may not start before the wake of its day or end after that day's lights-out.
 * [wakeMin] is minutes after midnight of the block's date; [lightsOutMin] may be over 1440 (after midnight).
 * Slots after midnight that belong to the previous planned day are checked against that day's lights-out by the caller passing its anchors.
 */
fun withinTheDay(startMin: Int, endMin: Int, wakeMin: Int, lightsOutMin: Int): Boolean = startMin >= wakeMin && endMin <= lightsOutMin

/** SA6: minutes in a calendar day. 1380 on the day the clocks go forward, 1500 when they go back, 1440 otherwise. */
fun dayLengthMinutes(date: LocalDate, zone: ZoneId = LONDON): Long =
    Duration.between(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone)).toMinutes()

/**
 * SA4: whether moving lights-out and wake together is a reality change (free) or a loosening. Moving both by the same amount keeps the hours, so it is free.
 * Moving lights-out later alone, so that fewer than 8 hours remain, is not a door at all: it is refused (SA3).
 */
enum class AnchorChange { FREE, LOOSENING, REFUSED }

fun classifyAnchorChange(old: NightAnchors, new: NightAnchors, fallAsleep: Duration = FALL_ASLEEP_DEFAULT, zone: ZoneId = LONDON): AnchorChange {
    val cutoffNew = nightCutoff(new.wakeAt(zone), fallAsleep).toLocalDateTime()
    if (new.lightsOut.isAfter(cutoffNew)) return AnchorChange.REFUSED
    return if (new.inBedMinutes(zone) >= old.inBedMinutes(zone) || new.inBedMinutes(zone) >= 8 * 60 + fallAsleep.toMinutes()) AnchorChange.FREE else AnchorChange.LOOSENING
}

/** SA1: the date of the night that is running or about to start. Before 06:00 it is still yesterday's night, so tonight's plan can be made at 01:45. */
fun nightOf(now: ZonedDateTime): LocalDate = if (now.hour < 6) now.toLocalDate().minusDays(1) else now.toLocalDate()
