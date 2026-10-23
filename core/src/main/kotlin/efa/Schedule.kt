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
