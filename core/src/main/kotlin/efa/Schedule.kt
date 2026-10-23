package efa

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A pattern slot placed on a real date. Times are wall-clock in the zone, so planned times hold across a clock change (X4). */
data class DatedSlot(val slot: PatternSlot, val plannedDay: LocalDate, val start: ZonedDateTime, val end: ZonedDateTime)

/** Slots whose planned day is [date]. Slots after midnight (start >= 1440 min) land on the next calendar day. */
fun slotsFor(pattern: List<PatternSlot>, date: LocalDate, zone: ZoneId = LONDON): List<DatedSlot> =
    pattern.filter { it.day == date.dayOfWeek }.sortedBy { it.startMin }.map {
        val midnight = LocalDateTime.of(date, LocalTime.MIDNIGHT)
        DatedSlot(it, date, ZonedDateTime.of(midnight.plusMinutes(it.startMin.toLong()), zone), ZonedDateTime.of(midnight.plusMinutes(it.endMin.toLong()), zone))
    }

data class NowNext(val now: DatedSlot?, val next: DatedSlot?)

/** The slot running at [at] and the one after it. Looks at yesterday's planned day too, for slots after midnight. */
fun nowAndNext(pattern: List<PatternSlot>, at: ZonedDateTime): NowNext {
    val d = at.toLocalDate()
    val all = (-1L..1L).flatMap { slotsFor(pattern, d.plusDays(it), at.zone) }.sortedBy { it.start }
    val now = all.lastOrNull { !it.start.isAfter(at) && at.isBefore(it.end) }
    val next = all.firstOrNull { it.start.isAfter(at) }
    return NowNext(now, next)
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")
fun DatedSlot.label(): String = "${HM.format(start)} ${slot.title}"
fun DatedSlot.window(): String = "${HM.format(start)} to ${HM.format(end)}"

/** The next [n] blocks that start after [at], in order. The block running now is not among them. */
fun upcoming(pattern: List<PatternSlot>, at: ZonedDateTime, n: Int): List<DatedSlot> =
    (0L..2L).flatMap { slotsFor(pattern, at.toLocalDate().plusDays(it - 1), at.zone) }.filter { it.start.isAfter(at) }.sortedBy { it.start }.take(n)

/** The place a block happens, for the second line of a card. */
fun DatedSlot.detail(): String = slot.venue

/** What the block will ask for at its end, in plain words (G1, Y1), for the Now card. */
fun proofHint(p: Proof): String = when (p) {
    Proof.NONE -> ""
    Proof.WORK_PHOTO -> "Photo of your notes when the timer ends."
    Proof.WINDOW_PHOTO -> "One photo inside the time window."
    Proof.ATTENDANCE -> "Attendance photo due."
    Proof.GIT_COMMIT -> "A commit proves it when the timer ends."
}
