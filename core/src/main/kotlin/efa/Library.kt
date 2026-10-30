package efa

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Library periods (layer 8, section 14: C1, C2, C3, O11, O13, Y9, PW1). A library period moves work blocks to the library for 1 to 14 days, puts a walk
 * at each end of a stretch of work, and changes nothing else: meals, football, lectures and routines stay where they are (O13). Blocking is the same as
 * in any work block, because a moved block keeps its type (C2). The library's opening hours are asked for once and kept (O11).
 */

const val LIBRARY_KIND = "library"
const val LIBRARY_VENUE = "University Library"
const val LIBRARY_WALK_MIN = 15
const val WALK_TO_LIBRARY = "Walk to the library"
const val WALK_FROM_LIBRARY = "Walk home from the library"
const val LIBRARY_MAX_DAYS = 14

/** A work block shorter than this stays at home: a walk at each end would leave it nothing (the walks take 30 of its minutes at worst). */
const val LIBRARY_MIN_BLOCK_MIN = 45

/** Within a gap this short only a reset fits, so two work blocks either side of it are one stretch with one walk each way. */
const val LIBRARY_RUN_GAP_MIN = 10

/** O11: when the library is open, in minutes after midnight. Asked for once. */
data class LibraryHours(val openMin: Int, val closeMin: Int) {
    init { require(openMin in 0 until closeMin && closeMin <= 24 * 60) { "The library must open before it closes." } }
    fun text() = "${minutesText(openMin)}-${minutesText(closeMin)}"
}

/** Reads "08:00-22:00". Anything else is no answer, and the app asks again. */
fun parseLibraryHours(text: String?): LibraryHours? {
    val m = Regex("^(\\d{2}):(\\d{2})-(\\d{2}):(\\d{2})$").matchEntire(text?.trim() ?: return null) ?: return null
    val (h1, m1, h2, m2) = m.destructured.toList().map { it.toInt() }
    if (h1 > 23 || h2 > 24 || m1 > 59 || m2 > 59) return null
    return runCatching { LibraryHours(h1 * 60 + m1, h2 * 60 + m2) }.getOrNull()
}

/** C1, PW1: a period of 1 to 14 days, starting today or later. The error text is shown as it is. */
fun checkLibraryPeriod(start: LocalDate, end: LocalDate, today: LocalDate): String? = when {
    end.isBefore(start) -> "The last day is before the first day."
    start.isBefore(today) -> "A library period starts today or later."
    java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 > LIBRARY_MAX_DAYS -> "A library period is 1 to $LIBRARY_MAX_DAYS days."
    else -> null
}

/** What one day looks like at the library: the calendar items to add, the work minutes the walks take, and the blocks that stayed at home. */
data class LibraryDay(val date: LocalDate, val items: List<PatternSlot>, val lostMin: Int, val stayedHome: List<String>, val walks: Int)

private fun isLibraryWalk(s: PatternSlot) = s.type == "travel" && (s.title == WALK_TO_LIBRARY || s.title == WALK_FROM_LIBRARY)

fun isLibraryWalkSlot(s: PatternSlot) = isLibraryWalk(s)

/**
 * C2: [slots] is the calendar as it stands (with any day off markers). Every weekly work block that fits inside [hours] moves to the library. The first
 * stretch of work gets a walk to the library at its start and the last gets a walk home at its end, each [LIBRARY_WALK_MIN] minutes taken from the work
 * block next to it, so the time available falls by exactly that and the Buffer says so. A block cut by an import keeps its cut. A dated block (an
 * import, a football session, a conversion) is left where it is.
 */
fun libraryDay(slots: List<PatternSlot>, date: LocalDate, hours: LibraryHours, zone: ZoneId = LONDON): LibraryDay {
    if (isDayOff(slots, date)) return LibraryDay(date, emptyList(), 0, emptyList(), 0)
    val day = slotsFor(slots, date, zone)
    val pieces = day.filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK }
    val stayed = ArrayList<String>()
    val inside = pieces.filter { p ->
        val ok = p.slot.startMin >= hours.openMin && p.slot.endMin <= hours.closeMin && p.slot.endMin - p.slot.startMin >= LIBRARY_MIN_BLOCK_MIN
        if (!ok) stayed += p.slot.title
        ok
    }
    if (inside.isEmpty()) return LibraryDay(date, emptyList(), 0, stayed, 0)
    // Stretches: consecutive library blocks with no more than a reset between them.
    val runs = ArrayList<MutableList<DatedSlot>>()
    for (p in inside.sortedBy { it.slot.startMin }) {
        val last = runs.lastOrNull()?.last()
        if (last != null && p.slot.startMin - last.slot.endMin <= LIBRARY_RUN_GAP_MIN) runs.last() += p else runs += mutableListOf(p)
    }
    val firsts = runs.map { it.first() }.toSet(); val lasts = runs.map { it.last() }.toSet()
    val out = ArrayList<PatternSlot>()
    var lost = 0
    val originals = slots.filter { it.date == null && it.day == date.dayOfWeek && it.type in CUTTABLE_WORK }
    val touched = originals.filter { w -> inside.any { it.slot.startMin >= w.startMin && it.slot.endMin <= w.endMin && it.slot.type == w.type } }
    for (w in touched) out += w.copy(date = date, cancelled = true, notes = "library period")
    for (w in touched) for (p in pieces.filter { it.slot.startMin >= w.startMin && it.slot.endMin <= w.endMin && it.slot.type == w.type }) {
        val s = p.slot
        if (p !in inside) { out += s.copy(date = date); continue }
        var a = s.startMin; var b = s.endMin
        if (p in firsts) { out += PatternSlot(date.dayOfWeek, a, a + LIBRARY_WALK_MIN, WALK_TO_LIBRARY, "travel", "", false, "About $LIBRARY_WALK_MIN minutes. Phone away.", date = date); a += LIBRARY_WALK_MIN; lost += LIBRARY_WALK_MIN }
        if (p in lasts) { out += PatternSlot(date.dayOfWeek, b - LIBRARY_WALK_MIN, b, WALK_FROM_LIBRARY, "travel", "", false, "About $LIBRARY_WALK_MIN minutes. Phone away.", date = date); b -= LIBRARY_WALK_MIN; lost += LIBRARY_WALK_MIN }
        out += s.copy(startMin = a, endMin = b, venue = LIBRARY_VENUE, date = date, notes = "At the library. ${s.notes}".trim())
    }
    return LibraryDay(date, out, lost, stayed, runs.size * 2)
}

/** The whole period, one day at a time. */
fun libraryItems(slots: List<PatternSlot>, start: LocalDate, end: LocalDate, hours: LibraryHours, zone: ZoneId = LONDON): List<PatternSlot> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.flatMap { libraryDay(slots, it, hours, zone).items }.toList()

/**
 * C2: the work minutes the walks took come back as catch-up work due by the end of the next working day after the period, so a library week cannot make
 * work disappear. One piece per day.
 */
fun libraryDemand(slots: List<PatternSlot>, start: LocalDate, end: LocalDate, hours: LibraryHours, now: ZonedDateTime, zone: ZoneId = now.zone): List<NewDayOffWork> {
    val due = endOfDayCutoff(slots, nextWorkingDay(slots, end), zone)
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.mapNotNull { d ->
        val day = libraryDay(slots, d, hours, zone)
        if (day.lostMin == 0 || d.plusDays(1).atStartOfDay(zone).isBefore(now)) null
        else NewDayOffWork("library:$d", "Catch-up: library walks on ${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}", day.lostMin / 60.0, 5, due, "Library period: the walks took ${day.lostMin} minutes of work")
    }.toList()
}

/** What a library period would do, worked out before it is planned (C1, C2). */
data class LibraryPreview(val lines: List<String>, val lostMin: Int, val moved: Int, val stayedHome: Int, val before: BufferState, val after: BufferState)

fun libraryPreview(slots: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, start: LocalDate, end: LocalDate, hours: LibraryHours, shortSleep: Boolean = false): LibraryPreview {
    val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { libraryDay(slots, it, hours, now.zone) }.toList()
    val lost = days.sumOf { it.lostMin }
    val moved = days.sumOf { d -> d.items.count { it.venue == LIBRARY_VENUE } }
    val home = days.sumOf { it.stayedHome.size }
    val demand = libraryDemand(slots, start, end, hours, now).map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    val newSlots = slots + libraryItems(slots, start, end, hours, now.zone)
    val before = bufferNow(now, studyCycles(slots, now) + work, slots, shortSleep)
    val after = bufferNow(now, studyCycles(newSlots, now) + work + demand, newSlots, shortSleep)
    val range = if (start == end) dayLabel(start) else "${dayLabel(start)} to ${dayLabel(end)}"
    val lines = buildList {
        add("Library $range: $moved work block${if (moved == 1) "" else "s"} move to the library.")
        add("The walks take ${fmt1(lost / 60.0)}h of work, which comes back as catch-up work after the period.")
        if (home > 0) add("$home block${if (home == 1) "" else "s"} stay at home because they fall outside ${hours.text()} or are too short.")
        if (before is BufferState.Value && after is BufferState.Value) add("Buffer ${fmt1(before.result.hours)}h to ${fmt1(after.result.hours)}h.")
        if (after is BufferState.Value && after.result.negative) { add("This makes the Buffer NEGATIVE, so the work no longer fits:"); addAll(after.result.working().lines()) }
    }
    return LibraryPreview(lines, lost, moved, home, before, after)
}

private fun dayLabel(d: LocalDate) = "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
