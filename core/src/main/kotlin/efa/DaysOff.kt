package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Planned days off (layer 8, section 14: C1, C4, C5, H1 to H6, X3, G2). A day off takes the day's work out of the time available, keeps sleep,
 * meals, the food money and the safety floor, loses nothing, and leaves the day out of the money ratio. It costs a typed wall that gets longer
 * with each use in the last 30 days, so it cannot become a loophole.
 */

/** Marks a date as a day off inside the calendar. It is never shown as a block: `slotsFor` reads it and removes the day's work (C5). */
const val DAY_OFF_TYPE = "dayoff"

fun dayOffMarker(date: LocalDate) = PatternSlot(date.dayOfWeek, 0, 1, "Day off", DAY_OFF_TYPE, "", false, "Day off.", date = date)

fun isDayOff(slots: List<PatternSlot>, date: LocalDate): Boolean = slots.any { it.date == date && it.type == DAY_OFF_TYPE && !it.cancelled }

/** What a day off takes away: work blocks, lectures, tutorials, planning blocks and the resets that follow them. Everything else stays (C5, Q-G75). */
val DAY_OFF_REMOVES = setOf("study", "code", "study-standard", "study-light", "lecture", "tutorial", "planning")

fun removedByDayOff(s: PatternSlot): Boolean = s.type in DAY_OFF_REMOVES || (s.type == "travel" && (s.title == RESET_TITLE || s.title == WALK_TO_LIBRARY || s.title == WALK_FROM_LIBRARY))

/** H1, H5: the number of walls spent in the last 30 days. A weekend is one wall, so its rows share a [DayOffUse.group]. */
data class DayOffUse(val group: Long, val atMs: Long)

val TALLY_WINDOW: Duration = Duration.ofDays(30)

fun usesInLast30Days(uses: List<DayOffUse>, now: ZonedDateTime): Int =
    uses.filter { Duration.between(java.time.Instant.ofEpochMilli(it.atMs), now.toInstant()) < TALLY_WINDOW && it.atMs <= now.toInstant().toEpochMilli() }.map { it.group }.distinct().size

/** H1, H3, H5: the string for a request. A same-day request or a claim adds 40. A weekend is requested once, so it is one string. */
fun dayOffWall(uses: List<DayOffUse>, now: ZonedDateTime, sameDay: Boolean): Int = WallLength.dayOff(usesInLast30Days(uses, now), sameDay)

/** The next date after [after] that is not itself a day off. The work a day off moves is due by the end of it (C1). */
fun nextWorkingDay(slots: List<PatternSlot>, after: LocalDate): LocalDate {
    var d = after.plusDays(1)
    while (isDayOff(slots, d)) d = d.plusDays(1)
    return d
}

/**
 * C4: the work a day off does not remove. The study cycles of its lectures and the lectures themselves still have to be learned, so they become
 * work due by the end of the next working day. This is why the cost shown is the hours lost from time available, and why a day off cannot make
 * work disappear.
 */
fun dayOffDemand(slots: List<PatternSlot>, dates: List<LocalDate>, now: ZonedDateTime, zone: ZoneId = now.zone): List<NewDayOffWork> {
    val withOff = slots + dates.map { dayOffMarker(it) }
    val out = ArrayList<NewDayOffWork>()
    for (d in dates) {
        val due = endOfDayCutoff(withOff, nextWorkingDay(withOff, dates.max()), zone)
        for (ds in slotsFor(slots, d, zone)) {
            val (hours, heaviness) = when (ds.slot.type) {
                "study-standard" -> STUDY_CYCLE_STANDARD_HOURS to 6
                "study-light" -> STUDY_CYCLE_LIGHT_HOURS to 3
                "lecture", "tutorial" -> Duration.between(ds.start, ds.end).toMinutes() / 60.0 to 4
                else -> continue
            }
            if (ds.end.isBefore(now)) continue                                    // already past: it is missed or proven, not planned away
            val name = ds.slot.title.substringBefore(" - ").removePrefix("Notes and problems: ")
            out += NewDayOffWork("dayoff:${blockKey(ds)}", if (ds.slot.type.startsWith("study")) "Study: $name" else "Catch-up: $name", hours, heaviness, due, "Day off ${d}")
        }
    }
    return out
}

data class NewDayOffWork(val key: String, val title: String, val hours: Double, val heaviness: Int, val due: ZonedDateTime, val reason: String)

/** What a request would do, worked out before anything is typed (C4, H2). */
data class DayOffPreview(
    val dates: List<LocalDate>, val before: BufferState, val after: BufferState, val costHours: Double?, val removedHours: Double, val negative: Boolean,
    val work: List<NewDayOffWork>, val lines: List<String>, val wall: Int,
)

/**
 * C4, H2: "Day off Sat: Buffer -6.0h", and if the Buffer would go negative it says so with the working. [work] is the work due now. The cost is the
 * fall in the Buffer, which is the hours taken out of time available with the day's lectures and cycles still to do.
 */
fun dayOffPreview(slots: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, dates: List<LocalDate>, uses: List<DayOffUse>, sameDay: Boolean, shortSleep: Boolean = false): DayOffPreview {
    val sorted = dates.sorted()
    val demand = dayOffDemand(slots, sorted, now)
    val newSlots = slots + sorted.map { dayOffMarker(it) }
    val baseWork = studyCycles(slots, now) + work
    val before = bufferNow(now, baseWork, slots, shortSleep)
    val afterWork = studyCycles(newSlots, now) + work + demand.map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    val after = bufferNow(now, afterWork, newSlots, shortSleep)
    val cost = if (before is BufferState.Value && after is BufferState.Value) before.result.hours - after.result.hours else null
    val negative = after is BufferState.Value && after.result.negative
    // The work-capable hours the day takes out of time available, from now on. The Buffer is measured to the tightest deadline, which may be earlier than the day.
    val removed = sorted.sumOf { date ->
        slotsFor(slots, date, now.zone).filter { it.slot.type in CUTTABLE_WORK && it.end.isAfter(now) }
            .sumOf { Duration.between(maxOf(it.start, now), it.end).toMinutes() } } / 60.0
    val label = when {
        sorted.size == 1 -> "Day off ${dayName(sorted[0])}"
        sorted.size == 2 && sorted[1] == sorted[0].plusDays(1) && sorted[0].dayOfWeek == DayOfWeek.SATURDAY -> "Weekend ${dayName(sorted[0])} and ${dayName(sorted[1])}"
        else -> "Days off ${sorted.joinToString(", ") { dayName(it) }}"
    }
    val lines = buildList {
        add(label + when {
            cost == null -> ": no deadline, so no Buffer to cost"
            cost > 0.05 || removed <= 0.0 -> ": Buffer ${if (cost > 0) "-" else "+"}${fmt1(kotlin.math.abs(cost))}h"
            else -> ": takes ${fmt1(removed)}h out of time available. The Buffer does not change because an earlier deadline is the tight one."
        })
        if (negative && after is BufferState.Value) { add("This makes the Buffer NEGATIVE, so the work no longer fits:"); addAll(after.result.working().lines()) }
    }
    return DayOffPreview(sorted, before, after, cost, removed, negative, demand, lines, dayOffWall(uses, now, sameDay))
}

private fun dayName(d: LocalDate) = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }

// ---- H4, X3: the automatic day off after a night of 3 hours or less ----

const val AUTO_OFF_MAX_SLEEP_MIN = 180

/**
 * H4: a night of 3 hours of sleep or less, with the logger alive all night (no gap), makes today a day off with no typing. It counts toward the
 * same 30-day tally. Without a night on record, or if the logger went quiet, it does not apply, so a killed service cannot grant a day off.
 */
fun autoDayOff(sleepMin: Int, gapCount: Int): Boolean = sleepMin <= AUTO_OFF_MAX_SLEEP_MIN && gapCount == 0

// ---- H6: claiming a day off afterwards ----

/** A claim for a date that has passed. It holds only if the work that came back from that day is done by its due times. */
enum class ClaimState { PENDING, HELD, FAILED }

/** One piece of work that covers the content a claimed day missed: when it was due and when it was completed, if it was. */
data class ClaimTask(val dueMs: Long, val doneAtMs: Long?)

/**
 * H6: the claim holds when every task that covers the day's missed content was done by its due time, fails as soon as one is overdue or was done late,
 * and waits otherwise. A day with nothing missed holds at once. Until it holds the time stays lost; if it fails it stays lost for good.
 */
fun resolveClaim(tasks: List<ClaimTask>, nowMs: Long): ClaimState = when {
    tasks.any { (it.doneAtMs != null && it.doneAtMs > it.dueMs) || (it.doneAtMs == null && nowMs > it.dueMs) } -> ClaimState.FAILED
    tasks.all { it.doneAtMs != null } -> ClaimState.HELD
    else -> ClaimState.PENDING
}

/** A claim can be made for a day in the last 7, never for today (that is a same-day request) or the future (that is planned ahead). */
fun claimable(date: LocalDate, today: LocalDate): Boolean = date.isBefore(today) && !date.isBefore(today.minusDays(7))

/** The weekend of a Saturday is one unit (H5): this and the day after. */
fun weekendOf(saturday: LocalDate): List<LocalDate>? = if (saturday.dayOfWeek == DayOfWeek.SATURDAY) listOf(saturday, saturday.plusDays(1)) else null

/** G2: no alarm rings on a planned day off, not even the 12:00 backstop. The next one is for the next day that is not off. */
fun planAlarmWithDaysOff(now: ZonedDateTime, onset: ZonedDateTime?, slots: List<PatternSlot>): AlarmPlan? =
    planAlarm(now, onset, slots)?.takeUnless { isDayOff(slots, it.at.toLocalDate()) }
