package efa

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The second home page: the whole calendar, day by day, as the rest of the app sees it (SC1). It is built from the same slots the
 * Buffer, the alarms and the blocker read, so it cannot disagree with them. Nothing here is stored or sample data.
 */

/** How a block is drawn: work you do, a fixed commitment, free time, or the small stuff around the day (travel, getting ready, planning). */
enum class RowKind { WORK, FIXED, FREE, MINOR }

enum class RowState { PAST, NOW, LATER }

data class ScheduleRow(
    val start: String, val end: String, val minutes: Int, val title: String, val detail: String,
    val kind: RowKind, val state: RowState,
    /** Starts after midnight, on the next calendar day, though it belongs to this planned day (shown as +1). */
    val nextDay: Boolean = false,
    /** The slot type (lecture, study-standard, travel ...), so a day can count its lectures. */
    val type: String = "",
    /** What photo or commit this block needs and when, or null (PH1). */
    val photo: PhotoDue? = null,
)

/** A deadline falling on a day, shown above its blocks. */
data class DueRow(val time: String, val title: String)

data class ScheduleDay(
    val date: LocalDate, val heading: String, val tag: String?, val today: Boolean, val dayOff: Boolean,
    val rows: List<ScheduleRow>, val due: List<DueRow>,
    val workMin: Int, val fixedMin: Int, val freeMin: Int,
) {
    val lectures: Int get() = rows.count { it.type == "lecture" }

    /** "5.0h work, 2 fixed" for the day's header; empty when there is nothing to count. */
    fun summary(): String = listOfNotNull(
        if (workMin > 0) "${fmt1(workMin / 60.0)}h work" else null,
        if (fixedMin > 0) "${fmt1(fixedMin / 60.0)}h fixed" else null,
    ).joinToString("  ")
}

fun rowKindOf(type: String): RowKind = when (type) {
    "study", "code", "study-standard", "study-light" -> RowKind.WORK
    "free" -> RowKind.FREE
    "travel", "routine", "planning", "meal" -> RowKind.MINOR
    else -> RowKind.FIXED          // lectures, tutorials, events, training, church, chores, errands, imports
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val HEADING = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

/**
 * [days] days from [from] (today by default). Each day lists its blocks in time order, with the work placed in each work block (RS2) and the
 * deadlines that fall on it. A day off is marked and shows only what a day off keeps. A block that has finished is PAST and the one running is NOW.
 */
fun scheduleView(
    slots: List<PatternSlot>, tasks: List<OpenTask>, alloc: Allocation?, now: ZonedDateTime, days: Int = 28, from: LocalDate = now.toLocalDate(), verified: Set<String> = emptySet(),
): List<ScheduleDay> = (0L until days.toLong()).map { i ->
    val date = from.plusDays(i)
    val rows = slotsFor(slots, date, now.zone).map { ds ->
        val kind = rowKindOf(ds.slot.type)
        val placed = if (kind == RowKind.WORK) alloc?.inSlot(ds).orEmpty().joinToString(", ") { "${it.task.title} ${it.minutes} min" } else ""
        val minutes = Duration.between(ds.start, ds.end).toMinutes().toInt()
        // An imported lecture is titled "Module lecture - Room"; the room is the detail, so the title stays short.
        val cut = ds.slot.title.indexOf(" - ").takeIf { it > 0 && ds.slot.venue.isNotBlank() && ds.slot.title.substring(it + 3) == ds.slot.venue }
        val title = if (cut != null) ds.slot.title.substring(0, cut) else ds.slot.title
        ScheduleRow(
            HHMM.format(ds.start), HHMM.format(ds.end), minutes, title,
            listOf(ds.slot.venue.takeUnless { v -> v.isBlank() || title.contains(v) }.orEmpty(), placed).filter { it.isNotBlank() }.joinToString(". "), kind,
            when { !ds.end.isAfter(now) -> RowState.PAST; !ds.start.isAfter(now) -> RowState.NOW; else -> RowState.LATER },
            ds.start.toLocalDate() != date, ds.slot.type, photoForSlot(ds, slots, now, verified),
        )
    }
    val due = tasks.filter { it.due.withZoneSameInstant(now.zone).toLocalDate() == date }.sortedBy { it.due }
        .map { DueRow(HHMM.format(it.due.withZoneSameInstant(now.zone)), it.title) }
    ScheduleDay(
        date, HEADING.format(date), when (date) { now.toLocalDate() -> "Today"; now.toLocalDate().plusDays(1) -> "Tomorrow"; else -> null }, date == now.toLocalDate(), isDayOff(slots, date), rows, due,
        rows.filter { it.kind == RowKind.WORK }.sumOf { it.minutes }, rows.filter { it.kind == RowKind.FIXED }.sumOf { it.minutes }, rows.filter { it.kind == RowKind.FREE }.sumOf { it.minutes },
    )
}

/** One Monday-to-Sunday week of the page, for the carousel. A week at the edge holds only the days that were asked for. */
data class ScheduleWeek(val monday: LocalDate, val label: String, val range: String, val days: List<ScheduleDay>) {
    val workMin: Int get() = days.sumOf { it.workMin }
    val lectures: Int get() = days.sumOf { it.lectures }
    val daysOff: Int get() = days.count { it.dayOff }
    /** "21.5h work, 12 lectures", leaving out what is zero. */
    fun summary(): String = listOfNotNull(
        if (workMin > 0) "${fmt1(workMin / 60.0)}h work" else null,
        if (lectures > 0) "$lectures lecture${if (lectures == 1) "" else "s"}" else null,
        if (daysOff > 0) "$daysOff day${if (daysOff == 1) "" else "s"} off" else null,
    ).joinToString(", ")
}

private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** Groups [days] into weeks that start on a Monday. The week holding [today] is "This week", the next "Next week", then "Week of 30 Nov". */
fun scheduleWeeks(days: List<ScheduleDay>, today: LocalDate? = days.firstOrNull { it.today }?.date): List<ScheduleWeek> {
    val thisMonday = today?.with(java.time.DayOfWeek.MONDAY)
    return days.groupBy { it.date.with(java.time.DayOfWeek.MONDAY) }.toSortedMap().map { (monday, ds) ->
        val sunday = monday.plusDays(6)
        val range = if (monday.month == sunday.month) "${monday.dayOfMonth} to ${SHORT.format(sunday)}" else "${SHORT.format(monday)} to ${SHORT.format(sunday)}"
        val label = when (monday) { thisMonday -> "This week"; thisMonday?.plusDays(7) -> "Next week"; thisMonday?.minusDays(7) -> "Last week"; else -> "Week of ${SHORT.format(monday)}" }
        ScheduleWeek(monday, label, range, ds.sortedBy { it.date })
    }
}
