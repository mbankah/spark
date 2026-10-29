package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * One slot of the weekly pattern. A slot with [date] set belongs to that one date only: an imported event (IM1), or free time
 * turned into work (F3). A [cancelled] dated slot removes the weekly slot that starts at [startMin] with the same [type] on [date].
 */
data class PatternSlot(
    val day: DayOfWeek, val startMin: Int, val endMin: Int, val title: String, val type: String,
    val venue: String, val hardAlarm: Boolean, val notes: String,
    val date: LocalDate? = null, val cancelled: Boolean = false,
)

/** A pattern slot placed on a real date. Times are wall-clock in the zone, so planned times hold across a clock change (X4). */
data class DatedSlot(val slot: PatternSlot, val plannedDay: LocalDate, val start: ZonedDateTime, val end: ZonedDateTime)

/** The shortest piece of free time worth keeping when an event cuts into it (the smallest block, as in IM). */
const val MIN_PIECE_MIN = 15

/** Slots whose planned day is [date]. Slots after midnight (start >= 1440 min) land on the next calendar day. */
fun slotsFor(pattern: List<PatternSlot>, date: LocalDate, zone: ZoneId = LONDON): List<DatedSlot> {
    val dated = pattern.filter { it.date == date }
    val cancels = dated.filter { it.cancelled }
    val added = dated.filter { !it.cancelled }
    val weekly = pattern.filter { it.date == null && it.day == date.dayOfWeek }
        .filter { w -> cancels.none { it.startMin == w.startMin && it.type == w.type } }
    val kept = weekly.flatMap { w -> if (added.isEmpty()) listOf(w) else cutBy(w, added) }
    val midnight = LocalDateTime.of(date, LocalTime.MIDNIGHT)
    return (kept + added).sortedBy { it.startMin }.map {
        DatedSlot(it, date, ZonedDateTime.of(midnight.plusMinutes(it.startMin.toLong()), zone), ZonedDateTime.of(midnight.plusMinutes(it.endMin.toLong()), zone))
    }
}

/** An imported event takes the time it covers from free time and from work blocks (IM6). Pieces under [MIN_PIECE_MIN] disappear. */
private fun cutBy(w: PatternSlot, added: List<PatternSlot>): List<PatternSlot> {
    if (w.type != "free" && w.type !in CUTTABLE_WORK) return listOf(w)
    var pieces = listOf(w.startMin to w.endMin)
    for (a in added) pieces = pieces.flatMap { (s, e) ->
        if (a.endMin <= s || a.startMin >= e) listOf(s to e)
        else listOfNotNull(if (a.startMin - s >= MIN_PIECE_MIN) s to a.startMin else null, if (e - a.endMin >= MIN_PIECE_MIN) a.endMin to e else null)
    }
    return pieces.map { (s, e) -> w.copy(startMin = s, endMin = e) }
}

/** Work blocks a dated event may cut into. Cutting one is a loosening (IM6). */
val CUTTABLE_WORK = setOf("study", "code", "study-standard", "study-light")

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
