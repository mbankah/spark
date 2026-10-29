package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** G3: at least 6 hours of free time a week that the Buffer rule can never turn into work. */
const val PROTECTED_FREE_MIN_PER_WEEK = 360

/** F3: a free slot stays a break while the Buffer is at least 17.5% of the work due before the nearest deadline, with a 2-hour floor. */
const val MARGIN_SHARE = 0.175
const val MARGIN_FLOOR_HOURS = 2.0

const val CONVERTED_NOTE = "Free time turned into work."
const val CONVERTED_TITLE = "Work (was free time)"

fun isConversion(s: PatternSlot) = s.date != null && !s.cancelled && s.notes == CONVERTED_NOTE

fun marginNeededHours(workHours: Double) = maxOf(MARGIN_FLOOR_HOURS, MARGIN_SHARE * workHours)

/**
 * The free windows of one ISO week as the pattern and any imported events leave them, counting a window already turned into work as
 * the window it was. Protection is worked out from these, so turning a slot into work never changes which slots are protected.
 */
private fun originalFree(slots: List<PatternSlot>, monday: LocalDate, zone: ZoneId): List<Triple<LocalDate, Int, Int>> {
    val base = slots.filter { !isConversion(it) }
    val converted = slots.filter { isConversion(it) }
    return (0L..6L).flatMap { i ->
        val d = monday.plusDays(i)
        slotsFor(base, d, zone).filter { it.slot.type == "free" }.map { Triple(d, it.slot.startMin, it.slot.endMin) } +
            converted.filter { it.date == d }.map { Triple(d, it.startMin, it.endMin) }
    }
}

/** G3: per week the longest free windows are protected, longest first, until 6 hours are covered (Q-G42). Keys are date and start minute. */
fun protectedFree(slots: List<PatternSlot>, anyDayInWeek: LocalDate, zone: ZoneId = LONDON): Set<Pair<LocalDate, Int>> {
    val monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var covered = 0
    val out = HashSet<Pair<LocalDate, Int>>()
    for ((d, s, e) in originalFree(slots, monday, zone).sortedWith(compareByDescending<Triple<LocalDate, Int, Int>> { it.third - it.second }.thenBy { it.first }.thenBy { it.second })) {
        if (covered >= PROTECTED_FREE_MIN_PER_WEEK) break
        out += d to s; covered += e - s
    }
    return out
}

data class Conversion(val slot: PatternSlot, val window: DatedSlot, val reason: String)

/** What the free-slot rule did. [stillShortHours] is how far the work still does not fit after every allowed slot was used, or null when it fits (AP6, H2). */
data class ConversionPlan(val conversions: List<Conversion>, val stillShortHours: Double?)

/**
 * F2, F3: while the Buffer is below its margin, the next free slot that starts tomorrow or later, ends before the driving deadline and
 * is not protected is turned into a work block. Slots turn into work whole and only from tomorrow (assigned the night before, F2).
 * Nothing else is touched: sleep, meals, hard commitments, resets and transitions are never squeezed (section 10).
 * [slots] already holds earlier conversions, so running this again adds nothing that is not needed.
 */
fun planConversions(slots: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, horizonDays: Int = 14, zone: ZoneId = now.zone): ConversionPlan {
    var cur = slots
    val out = ArrayList<Conversion>()
    val firstDay = now.toLocalDate().plusDays(1)
    val from = firstDay.atStartOfDay(zone)
    while (true) {
        val (res, deadline) = bufferDetail(now, work, cur, shortSleep = false) ?: return ConversionPlan(out, null)
        if (res.hours >= marginNeededHours(res.workHours)) return ConversionPlan(out, null)
        val protected = (0L..horizonDays.toLong()).map { firstDay.plusDays(it) }.map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct()
            .flatMap { protectedFree(cur, it, zone) }.toSet()
        val pick = (0L..horizonDays.toLong()).flatMap { slotsFor(cur, firstDay.plusDays(it), zone) }
            .filter { it.slot.type == "free" && !isConversion(it.slot) && !it.start.isBefore(from) && !it.end.isAfter(deadline) && (it.plannedDay to it.slot.startMin) !in protected }
            .minByOrNull { it.start }
        if (pick == null) return ConversionPlan(out, res.hours.takeIf { it < 0 }?.let { -it })
        val conv = pick.slot.copy(type = "study", title = CONVERTED_TITLE, notes = CONVERTED_NOTE, venue = "", hardAlarm = false, date = pick.plannedDay, cancelled = false)
        cur = cur + conv
        out += Conversion(conv, pick, "Buffer ${bufferText(BufferState.Value(res))} is under its margin, so free time on ${pick.plannedDay} became work")
    }
}
