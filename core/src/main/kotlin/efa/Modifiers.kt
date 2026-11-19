package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Modify Schedule (docs/WEEK_TYPES_SPEC.md). A preset adapts the schedule: it injects, transforms and replaces parts of days. Each preset is a list of settings and
 * lists of items, each with its own settings, so the screens are drawn from these definitions and a new preset needs no new screen. [compile] turns an [Instance] into
 * calendar changes and tasks without writing anything, [checkPlan] runs the guard rails (spec section 10), and [previewOf] shows the cost before anything is applied.
 *
 * Built here: Assignment (planned in anticipation), Catch-up week, Period off, Focus week and the Lecture-free lighter period. Not built: Revision, Routine, Recovery,
 * Away, Guests, Placement, Fresh start, Custom and the screen-time policy, so none of them appears in the app.
 */
enum class SettingKind { INT, DECIMAL, CHOICE, BOOL, TIME, OPT_TIME, TEXT, DATE }

data class SettingDef(
    val key: String, val label: String, val kind: SettingKind, val default: String,
    val min: Double? = null, val max: Double? = null, val choices: List<String> = emptyList(), val help: String = "",
)

data class ItemDef(val kind: String, val label: String, val settings: List<SettingDef>, val min: Int = 0, val max: Int = 20)

enum class Preset(val id: String, val label: String, val blurb: String) {
    ASSIGNMENT("assignment", "Assignment", "Plans each assignment backward from its due date so work starts early."),
    CATCH_UP("catch_up", "Catch-up week", "Gives the days to whole study blocks by module, laid around what stays fixed. Meals, football and the rest of your commitments stay."),
    PERIOD_OFF("period_off", "Period off", "Work and lectures off between two dates. Important blocks stay."),
    FOCUS("focus", "Focus week", "One project only, with the daily routine still happening."),
    LECTURE_FREE("lecture_free", "Lecture-free lighter period", "No lectures, lighter work, still working."),
    LATE_START("late_start", "Late start", "The day starts at a time you choose, or from now. Meals, work and free time follow it, and what cannot fit is said."),
    RUN_OVER("run_over", "Running over", "What you are doing takes longer. The rest of today slides later, free time first, and what cannot fit is said."),
    ADD_EVENT("add_event", "Add an event", "Time is taken by something new. Everything movable gives way around it, free time first, and what cannot fit is said."),
    CANCEL("cancel", "Something is cancelled", "A lecture or commitment on one day is not happening. Its time becomes free."),
    RECOVERY("recovery", "Recovery", "Lighter days when you are ill or drained. Routine, meals and sleep stay, work is held to a small amount, and what is set aside comes back afterwards."),
    GUESTS("guests", "Guests", "People are coming. Preparation is placed before they arrive, the day gives way around the visit, and tidying follows."),
    AWAY("away", "Away", "You are away from home. Lectures and tutorials pause, work is lighter, and the travel is placed."),
    REVISION("revision", "Revision", "Revision for exams, planned backwards from each exam in short sessions, interleaved, so it starts early."),
    NIGHT("night", "Sleep later or earlier", "Move the day's edges for one night: lights-out and wake move together, the evening's shutdown moves with them and the next morning starts when you wake. Needs 8 hours of sleep.");
    companion object { fun of(id: String) = entries.firstOrNull { it.id == id } }
}

private fun d(key: String, label: String, kind: SettingKind, default: String, min: Double? = null, max: Double? = null, choices: List<String> = emptyList(), help: String = "") =
    SettingDef(key, label, kind, default, min, max, choices, help)

object ModDefs {
    val MODULES = "Randomness and Computation|Numerical Methods and NLA|Neural Computation|Game Theory"

    private val assignmentItem = ItemDef("assignment", "Assignment", listOf(
        d("title", "Title", SettingKind.TEXT, "", help = "For example: GT assignment"),
        d("due", "Due date", SettingKind.DATE, ""),
        d("due_time", "Due time", SettingKind.TIME, "17:00"),
        d("size_h", "Hours of work", SettingKind.DECIMAL, "6.0", 0.5, 80.0, help = "Recommended fills this from heaviness and your own times"),
        d("priority", "Priority (1 most, 5 least)", SettingKind.INT, "3", 1.0, 5.0),
        d("lead_days", "Start this many days before", SettingKind.INT, "14", 0.0, 60.0),
        d("finish_early_h", "Finish this many hours before", SettingKind.INT, "24", 0.0, 168.0),
        d("sessions_per_day", "Most sessions a day", SettingKind.INT, "2", 1.0, 4.0),
        d("heaviness", "Heaviness (for the Buffer)", SettingKind.INT, "5", 1.0, 10.0),
    ), min = 1, max = 12)

    private val projectItem = ItemDef("project", "Project", listOf(
        d("title", "Project", SettingKind.TEXT, "", help = "For example: CS Project"),
        d("due", "Due date", SettingKind.DATE, ""),
        d("due_time", "Due time", SettingKind.TIME, "17:00"),
        d("size_h", "Hours of work", SettingKind.DECIMAL, "30.0", 1.0, 200.0),
        d("finish_early_h", "Finish this many hours before", SettingKind.INT, "24", 0.0, 168.0),
        d("heaviness", "Heaviness (for the Buffer)", SettingKind.INT, "8", 1.0, 10.0),
    ), min = 1, max = 1)

    private val pinItem = ItemDef("pin", "Important block", listOf(
        d("title", "Title", SettingKind.TEXT, "", help = "For example: Gym"),
        d("type", "Type", SettingKind.CHOICE, "errand", choices = listOf("errand", "chore", "church", "gym", "mytutor", "training", "match")),
        d("placement", "Placement", SettingKind.CHOICE, "window", choices = listOf("fixed", "window")),
        d("start", "Fixed start", SettingKind.TIME, "10:00"),
        d("window_from", "Window from", SettingKind.TIME, "10:00"),
        d("window_to", "Window to", SettingKind.TIME, "17:00"),
        d("length_min", "Minutes", SettingKind.INT, "45", 5.0, 480.0),
        d("days", "Days", SettingKind.TEXT, "all", help = "all, or for example sat,sun"),
    ), min = 0, max = 20)

    /** Lectures are one dimension any work preset can set (term state): none cancels lectures and tutorials in the period and nothing comes back. */
    private val lecturesAnywhere = d("lectures", "Lectures during the period", SettingKind.CHOICE, "running", choices = listOf("running", "none"),
        help = "None: lectures and tutorials in the period are cancelled, with no catch-up, so the same plan works in a hybrid or holiday week.")

    fun settings(p: Preset): List<SettingDef> = own(p) + (if (p == Preset.ASSIGNMENT || p == Preset.FOCUS || p == Preset.CATCH_UP) listOf(lecturesAnywhere) else emptyList())

    /** The presets that lay the day out again, so their span has a time as well as a date at each end (RF1). */
    fun hasSpanTimes(p: Preset) = p == Preset.CATCH_UP || p == Preset.LATE_START

    private fun own(p: Preset): List<SettingDef> = when (p) {
        Preset.ASSIGNMENT -> listOf(
            d("order", "Planning order", SettingKind.CHOICE, "auto", choices = listOf("auto", "sequential", "interleaved"), help = "Auto interleaves when two assignments overlap and fall due within 3 days"),
            d("mode", "Planning mode", SettingKind.CHOICE, "anticipate", choices = listOf("anticipate", "even", "front", "crunch")),
            d("daily_cap_h", "Most assignment hours a day", SettingKind.DECIMAL, "4.0", 0.5, 10.0),
            d("cycles", "Study cycles meanwhile", SettingKind.CHOICE, "adaptive", choices = listOf("adaptive", "keep"), help = "Adaptive lightens a long cycle to make room"),
            d("min_session", "Shortest session (minutes)", SettingKind.INT, "45", 15.0, 240.0),
            d("max_session", "Longest session (minutes)", SettingKind.INT, "120", 30.0, 300.0),
        )
        Preset.FOCUS -> listOf(
            d("hybrid", "Hybrid with a period off", SettingKind.BOOL, "false", help = "On for the holidays: no lectures, the project is the only work, the rest of the day is free"),
            d("daily_cap_h", "Most project hours a day", SettingKind.DECIMAL, "6.0", 1.0, 10.0),
            d("day_from", "Work window from", SettingKind.TIME, "09:00"),
            d("day_to", "Work window to", SettingKind.TIME, "17:30"),
            d("min_session", "Shortest session (minutes)", SettingKind.INT, "60", 15.0, 240.0),
            d("max_session", "Longest session (minutes)", SettingKind.INT, "90", 30.0, 300.0),
            d("order", "Planning mode", SettingKind.CHOICE, "anticipate", choices = listOf("anticipate", "even", "front", "crunch")),
        )
        Preset.CATCH_UP -> listOf(
            d("day_from", "Study from", SettingKind.TIME, "09:00", help = "The first study block of a day starts no earlier than this"),
            d("day_to", "Study until", SettingKind.TIME, "21:30", help = "The last study block of a day ends by this"),
            d("after_midnight", "Study until after midnight", SettingKind.BOOL, "false", help = "On for a late night: Study until is then the small hours of the next morning, for example 01:25"),
            d("wake", "The day starts at", SettingKind.OPT_TIME, "", help = "Leave it as normal, or choose a later start. Meals and the routine follow it"),
            d("slots_floor", "Study blocks a day (at least)", SettingKind.INT, "3", 1.0, 6.0, help = "Lecture cycles count toward it"),
            d("slots_stretch", "Study blocks on a good day", SettingKind.INT, "4", 1.0, 8.0, help = "Only while there is still backlog to clear"),
            d("slot_h", "Hours in a block", SettingKind.DECIMAL, "2.5", 0.5, 4.0),
            d("intensity", "Intensity", SettingKind.CHOICE, "full", choices = listOf("full", "rapid"), help = "Rapid notes: faster video, skim reading, short notes"),
            d("rapid_factor", "Rapid notes take this share of a block", SettingKind.DECIMAL, "0.65", 0.3, 1.0),
            d("modules", "Modules, most behind first", SettingKind.TEXT, MODULES, help = "Separate with |"),
            d("module_cap", "Most blocks of one module a day", SettingKind.INT, "2", 1.0, 4.0),
            d("weekend", "Weekend", SettingKind.CHOICE, "same", choices = listOf("same", "lower", "none")),
            d("replace_code", "Also replace project and code blocks", SettingKind.BOOL, "true", help = "On gives the time of CS Project, the language lesson and your own projects to study. Off keeps them"),
            d("use_free", "Study in protected free time too", SettingKind.BOOL, "false", help = "On gives up your protected free time on these days. It needs the typed wall"),
        )
        Preset.LATE_START -> listOf(
            d("from_now", "Start from now", SettingKind.BOOL, "false", help = "Today only: the day starts now, rounded up to 5 minutes. What was planned before now is already missed and comes back as work"),
            d("wake", "The day starts at", SettingKind.TIME, "10:00", help = "Wake, Bible, breakfast and then the day follow this time. A day that starts after 13:00 has no breakfast"),
        )
        Preset.CANCEL -> listOf(
            d("what", "What is cancelled", SettingKind.TEXT, "", help = "Words from its title, for example: Game Theory lecture. Everything on that day that matches is cancelled"),
        )
        Preset.RECOVERY -> listOf(
            d("work_h_max", "Most work hours a day", SettingKind.DECIMAL, "2.0", 0.0, 8.0, help = "Work beyond this is set aside and comes back as work after the last day"),
        )
        Preset.GUESTS -> listOf(
            d("title", "Who is coming", SettingKind.TEXT, "Guests"),
            d("from", "They arrive", SettingKind.TIME, "18:00", help = "On the first day"),
            d("until", "They leave", SettingKind.TIME, "21:00", help = "On the last day"),
            d("lead_days", "Prepare this many days before", SettingKind.INT, "2", 0.0, 7.0),
            d("prep", "Preparation", SettingKind.TEXT, "Tidy the room|30;Clean the bathroom|25;Kitchen and washing up|20;Fresh bedding|15;Shop and cook|45", help = "Each job and its minutes, separated by ;"),
            d("after_min", "Tidy up after (minutes)", SettingKind.INT, "30", 0.0, 120.0),
        )
        Preset.AWAY -> listOf(
            d("depart", "You leave at", SettingKind.TIME, "09:00", help = "On the first day"),
            d("back", "You are back at", SettingKind.TIME, "17:00", help = "On the last day"),
            d("travel_h", "Travel each way (hours)", SettingKind.DECIMAL, "3.0", 0.5, 12.0),
            d("work_h_max", "Most work hours a day", SettingKind.DECIMAL, "4.0", 0.0, 10.0),
            d("zone", "Time zone", SettingKind.TEXT, "", help = "Blank for London. A zone like America/New_York or Asia/Tokyo: alarms, meals and lights-out are read in it and place checks are off while you are away"),
            d("lectures", "Lectures while away", SettingKind.CHOICE, "auto", choices = listOf("auto", "running", "none"), help = "Running: they are missed and come back as work. None (a holiday): nothing comes back. Auto: none for 7 days or more"),
        )
        Preset.REVISION -> listOf(
            d("daily_cap_h", "Most revision hours a day", SettingKind.DECIMAL, "4.0", 0.5, 10.0),
            d("min_session", "Shortest session (minutes)", SettingKind.INT, "45", 15.0, 240.0),
            d("max_session", "Longest session (minutes)", SettingKind.INT, "90", 30.0, 300.0),
            d("cycles", "Study cycles meanwhile", SettingKind.CHOICE, "adaptive", choices = listOf("adaptive", "keep"), help = "Adaptive lightens a long cycle to make room"),
        )
        Preset.ADD_EVENT -> listOf(
            d("title", "What is it", SettingKind.TEXT, "", help = "For example: Birthday dinner"),
            d("type", "Type", SettingKind.CHOICE, "errand", choices = listOf("errand", "church", "gym", "mytutor", "training", "match", "study")),
            d("start", "From", SettingKind.TIME, "18:00"),
            d("end", "Until", SettingKind.TIME, "22:00"),
            d("after_midnight", "After midnight", SettingKind.BOOL, "false", help = "On: the times are after midnight and the event belongs to the night of the date, for example a 00:00 to 02:30 study session on the night of the 18th"),
            d("return_work", "Work that cannot fit comes back", SettingKind.BOOL, "false", help = "On: work that has to give way to the event comes back as work due the next working day, so nothing disappears and the Buffer shows it. That is a move, not a loosening"),
        )
        Preset.NIGHT -> listOf(
            d("lights_out", "Lights out", SettingKind.TIME, "02:40", help = "Before 12:00 means after midnight. With the wake time this must leave 8 hours of sleep and 20 minutes to fall asleep"),
            d("wake", "Wake", SettingKind.TIME, "11:00", help = "The next morning starts here. A lecture before it is refused, with the choices"),
            d("winddown_min", "Wind-down tonight (minutes)", SettingKind.INT, "0", 0.0, 120.0, help = "0 keeps the normal prayer and shutdown. A late night can use the floor, 10 minutes, so the last block can run to just before lights-out"),
        )
        Preset.RUN_OVER -> listOf(
            d("minutes", "Minutes more", SettingKind.INT, "30", 5.0, 180.0, help = "How much longer what you are doing will take. Blocks that have not started slide after it"),
        )
        Preset.PERIOD_OFF -> listOf(
            d("screen_limit_min", "Screen-time limit (minutes a day)", SettingKind.INT, "0", 0.0, 480.0, help = "0 means no limit. The apps on your block list get this many minutes a day, then close until midnight, so a weekend off can still have a limit"),
            d("lectures", "Lectures during the period", SettingKind.CHOICE, "auto", choices = listOf("auto", "running", "none"),
                help = "Running: skipped lectures and study cycles come back as work. None (a holiday): nothing comes back. Auto: none for 7 days or more."),
        )
        Preset.LECTURE_FREE -> listOf(
            d("work_h_max", "Most work hours a day", SettingKind.DECIMAL, "5.0", 1.0, 10.0, help = "Work beyond this becomes free time"),
            d("cycles_per_day", "Study slots a day", SettingKind.INT, "1", 0.0, 4.0),
            d("modules", "Modules for the slots", SettingKind.TEXT, MODULES, help = "Separate with |"),
        )
    }

    fun items(p: Preset): List<ItemDef> = when (p) {
        Preset.ASSIGNMENT -> listOf(assignmentItem)
        Preset.REVISION -> listOf(ItemDef("assignment", "Exam", assignmentItem.settings, min = 1, max = 12))
        Preset.FOCUS -> listOf(projectItem)
        Preset.PERIOD_OFF -> listOf(pinItem)
        else -> emptyList()
    }

    /** Assignment and focus plan backward from due dates, so their own dates come from the items; the others are given a start and an end. */
    fun datesFromItems(p: Preset) = p == Preset.ASSIGNMENT || p == Preset.REVISION
}

/**
 * One use of a preset: its dates, its settings and its items. [fromMin] is the minute of the first day the span starts at and [toMin] the minute of the last day it
 * ends at (RF1), so a span can begin this afternoon and end tomorrow noon. Presets that lay the day out again read them; the others work in whole days.
 */
data class Instance(val preset: Preset, val start: LocalDate, val end: LocalDate, val settings: Map<String, String> = emptyMap(), val items: List<Map<String, String>> = emptyList(),
    val fromMin: Int = 0, val toMin: Int = 1440) {
    private fun def(key: String) = ModDefs.settings(preset).first { it.key == key }
    fun str(key: String) = settings[key]?.takeIf { it.isNotBlank() } ?: def(key).default
    fun int(key: String) = str(key).toIntOrNull() ?: def(key).default.toInt()
    fun dbl(key: String) = str(key).toDoubleOrNull() ?: def(key).default.toDouble()
    fun bool(key: String) = str(key).equals("true", ignoreCase = true)
    val days: Int get() = (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() + 1).toInt()
    fun dates(): List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    /** The days the modifier touches: its dates, and for guests the days of preparation before they arrive. */
    fun coverage(): List<LocalDate> = if (preset == Preset.NIGHT) listOf(start, start.plusDays(1)) else if (preset == Preset.GUESTS) generateSequence(start.minusDays(int("lead_days").toLong())) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList() else dates()
}

fun itemStr(item: Map<String, String>, kind: String, preset: Preset, key: String): String {
    val def = ModDefs.items(preset).first { it.kind == kind }.settings.first { it.key == key }
    return item[key]?.takeIf { it.isNotBlank() } ?: def.default
}

/** What compiling a preset produces: calendar changes, work, days off, and the reasons. Nothing is written. */
data class Compiled(
    val slots: List<PatternSlot> = emptyList(), val tasks: List<DueWork> = emptyList(), val dayOffDates: List<LocalDate> = emptyList(),
    val lines: List<String> = emptyList(), val loosening: List<String> = emptyList(), val problems: List<String> = emptyList(), val lightened: Int = 0,
    val noDemand: Boolean = false,   // a holiday: the lectures are not running, so a day off has nothing to bring back
    val droppedMeals: Set<String> = emptySet(),   // "date|title" of a meal or breakfast the plan says it drops, so the guard rails allow it (RF4)
    val anchors: List<NightAnchors> = emptyList(), // SA1: the nights whose lights-out and wake this changes; the guard rail reads them (SA2)
    val backlogDue: ZonedDateTime? = null,         // BK1: inside a catch-up span the catch-up work is due here, at the span's end
)

/** Whether lectures are running during a period off. Auto reads a week or more as a holiday. */
fun lecturesRunning(inst: Instance): Boolean = when (inst.str("lectures")) { "running" -> true; "none" -> false; else -> inst.days < 7 }

/** MG8 (amended, RF1): real dates, a period of 1 to 28 days with a time at each end, nothing in the past, every setting inside its range. */
fun validate(inst: Instance, today: LocalDate): List<String> {
    if (inst.preset == Preset.REVISION) return validate(revisionAsAssignment(inst), today).map { it.replace("Assignment", "Exam").replace("assignment", "exam") }
    val out = ArrayList<String>()
    if (inst.end.isBefore(inst.start)) out += "The last day is before the first day."
    if (inst.preset == Preset.NIGHT && inst.days != 1) out += "A night is one date."
    // SA1: after midnight the night that is still running belongs to yesterday, so a night, or an event after midnight on it, may start yesterday
    val stillTonight = (inst.preset == Preset.NIGHT || (inst.preset == Preset.ADD_EVENT && inst.bool("after_midnight"))) && inst.start == today.minusDays(1)
    if (inst.start.isBefore(today) && !stillTonight) out += "The first day is in the past."
    val needsRange = inst.preset != Preset.ASSIGNMENT
    if (needsRange && inst.days > MAX_SPAN_DAYS) out += "A period is 1 to $MAX_SPAN_DAYS days."
    if (inst.fromMin !in 0..1439 || inst.toMin !in 1..1440) out += "The times of the period must be inside a day."
    else if (inst.start == inst.end && inst.toMin <= inst.fromMin) out += "The period ends before it starts."
    if (inst.preset == Preset.AWAY) inst.settings["zone"]?.takeIf { it.isNotBlank() }?.let { z -> if (runCatching { java.time.ZoneId.of(z.trim()) }.isFailure) out += "The time zone \"$z\" is not a zone, for example America/New_York." }
    for (s in ModDefs.settings(inst.preset)) {
        val raw = inst.settings[s.key] ?: continue
        when (s.kind) {
            SettingKind.TIME, SettingKind.OPT_TIME -> if (raw.isNotBlank() && runCatching { LocalTime.parse(raw) }.isFailure) out += "${s.label} must be a time like 09:30."
            SettingKind.INT -> raw.toIntOrNull()?.let { v -> if ((s.min != null && v < s.min) || (s.max != null && v > s.max)) out += "${s.label} must be ${s.min?.toInt()} to ${s.max?.toInt()}." } ?: run { out += "${s.label} must be a whole number." }
            SettingKind.DECIMAL -> raw.toDoubleOrNull()?.let { v -> if ((s.min != null && v < s.min) || (s.max != null && v > s.max)) out += "${s.label} must be ${s.min} to ${s.max}." } ?: run { out += "${s.label} must be a number." }
            SettingKind.CHOICE -> if (raw !in s.choices) out += "${s.label} must be one of ${s.choices.joinToString(", ")}."
            else -> {}
        }
    }
    if (inst.preset == Preset.ASSIGNMENT || inst.preset == Preset.FOCUS) {
        val mn = inst.int("min_session"); val mx = inst.int("max_session")
        if (mn > mx) out += "The shortest session (${mn} minutes) cannot be longer than the longest (${mx})."
        if (mn > inst.dbl("daily_cap_h") * 60) out += "The shortest session (${mn} minutes) is longer than the daily cap."
    }
    for ((n, item) in inst.items.withIndex()) {
        val kind = ModDefs.items(inst.preset).firstOrNull() ?: continue
        val label = "${kind.label} ${n + 1}"
        for (s in kind.settings) {
            val raw = item[s.key]?.takeIf { it.isNotBlank() } ?: continue
            if (s.kind == SettingKind.INT) raw.toIntOrNull()?.let { v -> if ((s.min != null && v < s.min) || (s.max != null && v > s.max)) out += "$label: ${s.label} must be ${s.min?.toInt()} to ${s.max?.toInt()}." } ?: run { out += "$label: ${s.label} must be a whole number." }
            if (s.kind == SettingKind.DECIMAL) raw.toDoubleOrNull()?.let { v -> if ((s.min != null && v < s.min) || (s.max != null && v > s.max)) out += "$label: ${s.label} must be ${s.min} to ${s.max}." } ?: run { out += "$label: ${s.label} must be a number." }
        }
        if (inst.preset == Preset.ASSIGNMENT || inst.preset == Preset.FOCUS) {
            if (itemStr(item, kind.kind, inst.preset, "title").isBlank()) out += "$label needs a title."
            val due = runCatching { LocalDate.parse(item["due"]) }.getOrNull()
            if (due == null) out += "$label needs a due date." else if (due.isBefore(today)) out += "$label is already past its due date."
            if (runCatching { LocalTime.parse(itemStr(item, kind.kind, inst.preset, "due_time")) }.isFailure) out += "$label: the due time must be like 17:00."
        }
        if (inst.preset == Preset.PERIOD_OFF && itemStr(item, "pin", inst.preset, "title").isBlank()) out += "$label needs a title."
    }
    if (inst.preset == Preset.CATCH_UP && out.none { it.contains("time like") }) {
        val from = hm(inst.str("day_from")); val to = hm(inst.str("day_to")) + (if (inst.bool("after_midnight")) 1440 else 0)
        if (to - from < MIN_STUDY_BLOCK_MIN) out += "Study from ${inst.str("day_from")} until ${inst.str("day_to")} leaves no room for a study block."
        val wake = inst.str("wake").takeIf { it.isNotBlank() }?.let { hm(it) }
        if (wake != null && wake !in 4 * 60..18 * 60) out += "The day starts between 04:00 and 18:00."
    }
    if (inst.preset == Preset.LATE_START && !inst.bool("from_now") && out.none { it.contains("time like") } && hm(inst.str("wake")) !in 4 * 60..18 * 60) out += "The day starts between 04:00 and 18:00."
    if (inst.preset == Preset.LATE_START && inst.bool("from_now") && inst.start != today) out += "Start from now is for today."
    if (inst.preset == Preset.RUN_OVER && (inst.start != today || inst.end != today)) out += "Running over is for today only."
    if (inst.preset == Preset.CANCEL) {
        if (inst.start != inst.end) out += "Something cancelled is on one day."
        if (inst.str("what").isBlank()) out += "Say what is cancelled."
    }
    if (inst.preset == Preset.GUESTS && out.none { it.contains("time like") }) {
        if (inst.start == inst.end && hm(inst.str("until")) <= hm(inst.str("from"))) out += "They leave before they arrive."
        if (inst.str("prep").isNotBlank() && inst.str("prep").split(";").any { j -> j.isNotBlank() && (j.split("|").getOrNull(1)?.trim()?.toIntOrNull() ?: 0) <= 0 }) out += "Each preparation job needs its minutes, like Tidy the room|30."
    }
    if (inst.preset == Preset.AWAY && out.none { it.contains("time like") }) {
        val travel = Math.round(inst.dbl("travel_h") * 60).toInt()
        if (inst.start == inst.end && hm(inst.str("back")) - travel < hm(inst.str("depart")) + travel) out += "There is not room for the travel there and back in one day."
    }
    if (inst.preset == Preset.ADD_EVENT) {
        if (inst.start != inst.end) out += "An event is on one day."
        if (inst.str("title").isBlank()) out += "The event needs a title."
        if (out.none { it.contains("time like") } && hm(inst.str("end")) <= hm(inst.str("start"))) out += "The event ends before it starts."
    }
    if (inst.preset == Preset.ASSIGNMENT && inst.items.isEmpty()) out += "Add at least one assignment."
    if (inst.preset == Preset.FOCUS && inst.items.size != 1) out += "A focus week needs one project."
    return out
}

// ---------- calendar helpers ----------

private fun hm(s: String) = LocalTime.parse(s).let { it.hour * 60 + it.minute }
private fun label(d: LocalDate) = "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
private const val LIGHT_KEEP_MIN = 60
private fun isCycle(s: PatternSlot) = s.title.startsWith("Notes and problems")

/** Free or empty time inside [from, to] on a date: the gaps that touch nothing but free time. */
fun freeGaps(slots: List<PatternSlot>, date: LocalDate, from: Int, to: Int, minLen: Int, zone: ZoneId = LONDON): List<Pair<Int, Int>> {
    val busy = slotsFor(slots, date, zone).filter { it.slot.type != "free" && it.slot.endMin > from && it.slot.startMin < to }.map { max(it.slot.startMin, from) to min(it.slot.endMin, to) }.sortedBy { it.first }
    val out = ArrayList<Pair<Int, Int>>(); var cur = from
    for ((s, e) in busy) { if (s - cur >= minLen) out += cur to s; cur = max(cur, e) }
    if (to - cur >= minLen) out += cur to to
    return out
}

private fun place(base: List<PatternSlot>, date: LocalDate, title: String, type: String, start: Int, end: Int, notes: String, zone: ZoneId) =
    PatternSlot(date.dayOfWeek, start, end, title, type, "", false, notes, date = date)

// ---------- Assignment and Focus ----------

private data class Cand(val start: Int, val end: Int, val cycle: Boolean)
private data class AItem(val idx: Int, val title: String, val due: ZonedDateTime, val sizeMin: Int, val priority: Int, val leadDays: Int, val finishEarlyH: Int, val perDay: Int, val heaviness: Int)

private fun readItems(inst: Instance, kind: String): List<AItem> = inst.items.mapIndexed { i, m ->
    val due = LocalDate.parse(m["due"]).atTime(LocalTime.parse(itemStr(m, kind, inst.preset, "due_time"))).atZone(LONDON)
    AItem(i, itemStr(m, kind, inst.preset, "title"), due, Math.round(itemStr(m, kind, inst.preset, "size_h").toDouble() * 60).toInt(),
        if (kind == "assignment") itemStr(m, kind, inst.preset, "priority").toInt() else 1,
        if (kind == "assignment") itemStr(m, kind, inst.preset, "lead_days").toInt() else 0,
        itemStr(m, kind, inst.preset, "finish_early_h").toInt(),
        if (kind == "assignment") itemStr(m, kind, inst.preset, "sessions_per_day").toInt() else 4,
        itemStr(m, kind, inst.preset, "heaviness").toInt())
}

private fun milestone(doneMin: Int, sizeMin: Int): String { val share = if (sizeMin == 0) 1.0 else doneMin.toDouble() / sizeMin; return if (share < 0.15) "outline" else if (share < 0.70) "draft" else "final" }

/**
 * Anticipation (spec 5): each item is planned backward from "due minus the finish-early margin", starting [AItem.leadDays] before the due date. Sessions go into the work
 * blocks that exist (a long study cycle is lightened to make room, adaptive), or, for a hybrid focus week, into free and empty daytime. Planning order is sequential,
 * interleaved or auto. Nothing is placed over a protected block, past the daily cap, or outside the session lengths.
 */
private fun planSessions(
    base: List<PatternSlot>, items: List<AItem>, from: LocalDate, until: LocalDate, capMin: Int, minSession: Int, maxSession: Int, order: String, mode: String,
    adaptive: Boolean, gapWindow: Pair<Int, Int>?, zone: ZoneId,
): Triple<List<PatternSlot>, Map<Int, Int>, Int> {
    val remaining = items.associate { it.idx to it.sizeMin }.toMutableMap()
    val doneMin = items.associate { it.idx to 0 }.toMutableMap()
    val slots = ArrayList<PatternSlot>(); var lightened = 0
    fun windowStart(i: AItem) = maxOf(from, i.due.toLocalDate().minusDays(i.leadDays.toLong()))
    fun windowEnd(i: AItem): ZonedDateTime = i.due.minusHours(i.finishEarlyH.toLong())
    fun cands(date: LocalDate): MutableList<Cand> {
        if (gapWindow != null) return freeGaps(base, date, gapWindow.first, gapWindow.second, minSession, zone).map { Cand(it.first, it.second, false) }.toMutableList()
        return slotsFor(base, date, zone).filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK }.mapNotNull { ds ->
            val s = ds.slot
            if (isCycle(s)) { if (!adaptive) null else Cand(s.startMin + LIGHT_KEEP_MIN, s.endMin, true).takeIf { it.end - it.start >= minSession } } else Cand(s.startMin, s.endMin, false)
        }.sortedWith(compareBy({ it.cycle }, { it.start })).toMutableList()
    }
    val all = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }.toList()
    val capByDay = all.associateWith { cands(it) }
    val dates = if (mode == "crunch") all.reversed() else all
    for (date in dates) {
        val active = items.filter { remaining.getValue(it.idx) > 0 && !date.isBefore(windowStart(it)) && !windowEnd(it).toLocalDate().isBefore(date) }
        if (active.isEmpty()) continue
        val pool = capByDay.getValue(date).toMutableList()
        if (pool.isEmpty()) continue
        val interleave = when (order) {
            "interleaved" -> true
            "sequential" -> false
            else -> active.size > 1 && active.any { a -> active.any { b -> a.idx < b.idx && overlapShare(windowStart(a), windowEnd(a).toLocalDate(), windowStart(b), windowEnd(b).toLocalDate()) > 0.5 && Math.abs(Duration.between(a.due, b.due).toDays()) <= 3 } }
        }
        val todayMin = active.associate { it.idx to 0 }.toMutableMap(); val sessions = active.associate { it.idx to 0 }.toMutableMap()
        var used = 0
        // the day's share of each item: spread over the days it still has with room (anticipate, even), or all of it (front, crunch)
        fun daysLeft(i: AItem) = max(1, all.count { x -> !x.isBefore(date) && !x.isAfter(windowEnd(i).toLocalDate()) && capByDay.getValue(x).isNotEmpty() })
        val quota = active.associate { i -> i.idx to (if (mode == "front" || mode == "crunch") remaining.getValue(i.idx) else ceil(remaining.getValue(i.idx).toDouble() / daysLeft(i)).toInt().coerceAtLeast(minSession)) }
        var guard = 0
        while (pool.isNotEmpty() && used < capMin && guard++ < 40) {
            val cand = pool.removeAt(0)
            val choices = active.filter { remaining.getValue(it.idx) > 0 && sessions.getValue(it.idx) < it.perDay && todayMin.getValue(it.idx) < quota.getValue(it.idx) }
            if (choices.isEmpty()) break
            val item = (if (interleave) choices.minByOrNull { todayMin.getValue(it.idx).toDouble() / (6 - it.priority) } else choices.sortedWith(compareBy({ it.due }, { it.priority })).first()) ?: continue
            var end = cand.end
            if (date == windowEnd(item).toLocalDate()) end = min(end, windowEnd(item).hour * 60 + windowEnd(item).minute)
            var len = minOf(end - cand.start, maxSession, remaining.getValue(item.idx), capMin - used, max(quota.getValue(item.idx) - todayMin.getValue(item.idx), minSession))
            if (len < minSession && remaining.getValue(item.idx) >= minSession) continue
            // never a piece under the shortest allowed (MG5), and never leave a remainder under it: take it with this session if there is room, otherwise give it back to the next one
            val rest = remaining.getValue(item.idx) - len
            if (rest in 1 until MIN_PIECE_MIN) {
                val room = minOf(end - cand.start, maxSession, capMin - used)
                len = if (len + rest <= room) len + rest else len - (MIN_PIECE_MIN - rest)
            }
            if (len < MIN_PIECE_MIN) continue
            if (len <= 0) continue
            val title = "${item.title}: ${milestone(doneMin.getValue(item.idx), item.sizeMin)}"
            slots += PatternSlot(date.dayOfWeek, cand.start, cand.start + len, title, "study", "", false, "${MOD_NOTE}Assignment session. Due ${label(item.due.toLocalDate())} ${item.due.toLocalTime()}.", date = date)
            if (cand.cycle) lightened++
            remaining[item.idx] = remaining.getValue(item.idx) - len; doneMin[item.idx] = doneMin.getValue(item.idx) + len
            used += len; todayMin[item.idx] = todayMin.getValue(item.idx) + len; sessions[item.idx] = sessions.getValue(item.idx) + 1
            if (cand.end - (cand.start + len) >= max(MIN_PIECE_MIN, minSession)) pool.add(0, Cand(cand.start + len, cand.end, cand.cycle))
        }
    }
    return Triple(slots, remaining, lightened)
}

private fun overlapShare(a1: LocalDate, a2: LocalDate, b1: LocalDate, b2: LocalDate): Double {
    val s = maxOf(a1, b1); val e = minOf(a2, b2)
    if (e.isBefore(s)) return 0.0
    val inter = Duration.between(s.atStartOfDay(), e.plusDays(1).atStartOfDay()).toDays().toDouble()
    val shortest = minOf(Duration.between(a1.atStartOfDay(), a2.plusDays(1).atStartOfDay()).toDays(), Duration.between(b1.atStartOfDay(), b2.plusDays(1).atStartOfDay()).toDays()).coerceAtLeast(1)
    return inter / shortest
}

private fun compileAssignment(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val items = readItems(inst, "assignment")
    val last = items.maxOf { it.due.toLocalDate() }
    val (slots, remaining, lightened) = planSessions(base, items, maxOf(inst.start, now.toLocalDate()), last, (inst.dbl("daily_cap_h") * 60).toInt(), inst.int("min_session"), inst.int("max_session"),
        inst.str("order"), inst.str("mode"), inst.str("cycles") == "adaptive", null, zone)
    val lines = ArrayList<String>(); val problems = ArrayList<String>()
    for (i in items) {
        val mine = slots.filter { it.title.startsWith(i.title + ":") }
        val first = mine.minByOrNull { it.date!! }?.date; val lastDay = mine.maxByOrNull { it.date!! }?.date
        lines += if (first == null) "${i.title}: no session could be placed." else "${i.title}: ${mine.size} session${if (mine.size == 1) "" else "s"}, ${label(first)} to ${label(lastDay!!)}, due ${label(i.due.toLocalDate())} ${i.due.toLocalTime()}."
        val left = remaining.getValue(i.idx)
        if (left >= MIN_PIECE_MIN) { val m = "${i.title} is ${fmt1(left / 60.0)}h short before its finish time. Start earlier, raise the daily cap, or lower the hours."; problems += m; lines += "Not fully planned: $m" }
    }
    if (lightened > 0) lines += "$lightened long study cycle${if (lightened == 1) " was" else "s were"} lightened to make room. The hours come back as catch-up afterwards."
    return Compiled(slots, items.map { DueWork(it.title, it.sizeMin / 60.0, it.heaviness, it.due) }, emptyList(), lines, emptyList(), problems, lightened)
}

private fun compileFocus(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val items = readItems(inst, "project")
    val hybrid = inst.bool("hybrid")
    val dates = inst.dates()
    val withOff = if (hybrid) base + dates.map { dayOffMarker(it) } else base
    val window = if (hybrid) hm(inst.str("day_from")) to hm(inst.str("day_to")) else null
    val (slots, remaining, lightened) = planSessions(withOff, items.map { it.copy(leadDays = 3650) }, maxOf(inst.start, now.toLocalDate()), inst.end, (inst.dbl("daily_cap_h") * 60).toInt(),
        inst.int("min_session"), inst.int("max_session"), "sequential", inst.str("order"), true, window, zone)
    val p = items.first()
    val lines = ArrayList<String>(); val problems = ArrayList<String>()
    lines += "${p.title}: ${slots.size} session${if (slots.size == 1) "" else "s"} between ${label(inst.start)} and ${label(inst.end)}, due ${label(p.due.toLocalDate())}."
    if (hybrid) lines += "Hybrid: ${dates.size} days off lectures and work blocks, the project is the only work, the rest of each day is free, and the daily routine still happens."
    else lines += "Lecture cycles keep running, lightened where the project needs the time."
    if (remaining.getValue(p.idx) >= MIN_PIECE_MIN) { val m = "${p.title} is ${fmt1(remaining.getValue(p.idx) / 60.0)}h short in these dates. Use more days, a higher daily cap, or fewer hours."; problems += m; lines += "Not fully planned: $m" }
    val loosening = if (hybrid) listOf("Lectures, work blocks and planning are paused on ${dates.joinToString(", ") { label(it) }}") else emptyList()
    return Compiled(slots, listOf(DueWork(p.title, p.sizeMin / 60.0, p.heaviness, p.due)), if (hybrid) dates else emptyList(), lines, loosening, problems, lightened)
}

// ---------- Catch-up ----------

/** The minutes of a span's day that may be changed: the span's own times on its first and last day, and never the part of today that has passed. */
private fun spanMinutes(inst: Instance, date: LocalDate, now: ZonedDateTime): Pair<Int, Int> {
    var from = if (date == inst.start) inst.fromMin else 0
    val to = if (date == inst.end) inst.toMin else 1440
    if (date == now.toLocalDate()) from = max(from, ((now.hour * 60 + now.minute + 14) / 15) * 15)
    return from to to
}

private fun protectedKeys(base: List<PatternSlot>, dates: List<LocalDate>, zone: ZoneId): Set<Pair<LocalDate, Int>> =
    dates.map { weekStart(it) }.distinct().flatMap { protectedFree(base, it, zone) }.toSet()

private fun wallClock(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)

/** What a day that starts later than planned gives up: said in the preview, and it needs the wall (loosening). */
private fun startLoosening(base: List<PatternSlot>, date: LocalDate, wake: Int, built: Built): List<String> {
    val planned = slotsFor(base, date).firstOrNull { it.slot.type == "routine" && it.slot.title.startsWith("Wake") }?.slot?.startMin
    return (if (planned != null && wake > planned) listOf("${label(date)} starts at ${wallClock(wake)} instead of ${wallClock(planned)}") else emptyList()) + built.dropped
}

// ---------- Catch-up (RF5): the days are given to whole study blocks ----------

private fun compileCatchUp(inst: Instance, base: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val mods = inst.str("modules").split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val rapid = inst.str("intensity") == "rapid"
    val slotMin = Math.round(inst.dbl("slot_h") * 60 * if (rapid) inst.dbl("rapid_factor") else 1.0).toInt().coerceAtLeast(MIN_STUDY_BLOCK_MIN)
    val dates = inst.dates()
    val prot = protectedKeys(base, dates, zone)
    val late = if (inst.bool("after_midnight")) 1440 else 0                    // LN1: a late night's study can run past midnight
    val dayFrom = hm(inst.str("day_from")); val dayTo = hm(inst.str("day_to")) + late
    val wake = inst.str("wake").takeIf { it.isNotBlank() }?.let { hm(it) }
    var backlogLeft = Math.round(work.sumOf { it.hours } * 60).toInt()
    val slots = ArrayList<PatternSlot>(); val lines = ArrayList<String>(); val problems = ArrayList<String>(); val loosening = ArrayList<String>(); val droppedMeals = HashSet<String>()
    var blocks = 0
    for ((n, date) in dates.withIndex()) {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val floor = when { weekend && inst.str("weekend") == "none" -> 0; weekend && inst.str("weekend") == "lower" -> max(1, inst.int("slots_floor") - 1); else -> inst.int("slots_floor") }
        if (floor == 0 || isDayOff(base, date)) continue
        val (from, to) = spanMinutes(inst, date, now)
        if (from >= to) continue
        val stretch = if (weekend && inst.str("weekend") == "lower") floor else inst.int("slots_stretch")
        val dv = Devote(mods, slotMin, RESET_MIN, floor, max(floor, stretch), dayFrom, dayTo, inst.int("module_cap"), backlogMin = backlogLeft, replaceCode = inst.bool("replace_code"))
        // a start that has not come yet today can still be chosen; one that has passed is ignored
        val wakeToday = wake?.takeIf { date != now.toLocalDate() || it > now.hour * 60 + now.minute }
        val useFree = inst.bool("use_free")
        val b = buildDay(base, date, Rules(wake = wakeToday, from = if (wakeToday != null) min(from, 0) else from, to = to + late, devote = dv, protectedFree = if (useFree) emptySet() else prot), n, zone)
        if (useFree && prot.any { it.first == date }) loosening += "Uses protected free time on ${label(date)}"           // LN2: it is said, and it needs the wall
        slots += b.slots; droppedMeals += b.droppedMeals; problems += b.problems
        if (wakeToday != null) loosening += startLoosening(base, date, wakeToday, b) else loosening += b.dropped
        blocks += b.blocks; backlogLeft = max(0, backlogLeft - b.slots.filter { !it.cancelled && it.type == "study" }.sumOf { it.endMin - it.startMin })
    }
    lines += "$blocks study block${if (blocks == 1) "" else "s"} of ${fmt1(slotMin / 60.0)}h${if (rapid) " (rapid notes)" else ""} by module between ${wallClock(dayFrom)} and ${wallClock(dayTo)}, around everything that stays fixed."
    lines += "Meals, the routine, lectures, football and your other commitments keep their place. Your protected free time is not touched."
    // BK1, BK2: the backlog is due at the end of the span, spread oldest first over the days, and the room against the work is said as a number
    val workH = work.sumOf { it.hours }; val roomH = slots.filter { !it.cancelled && it.type == "study" }.sumOf { (it.endMin - it.startMin) / 60.0 }
    if (workH > 0) lines += "Backlog: ${fmt1(workH)}h of work, ${fmt1(roomH)}h of room in these days" + (if (workH > roomH + 0.05) ", ${fmt1(workH - roomH)}h over. Lengthen the span or set something aside." else ". It fits.")
    problems.forEach { lines += "Not fully planned: $it" }
    return Compiled(slots, emptyList(), emptyList(), lines, loosening.distinct(), problems, droppedMeals = droppedMeals, backlogDue = endOfDayCutoff(base, inst.end, zone))
}

// ---------- Late start (RF6): the day begins when you say and the rest follows ----------

private fun compileLateStart(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val setWake = hm(inst.str("wake"))
    val nowMin = now.hour * 60 + now.minute
    val fromNow = inst.bool("from_now")
    val slots = ArrayList<PatternSlot>(); val lines = ArrayList<String>(); val problems = ArrayList<String>(); val loosening = ArrayList<String>(); val droppedMeals = HashSet<String>()
    var days = 0; var shownWake = setWake
    for (date in inst.dates()) {
        if (isDayOff(base, date)) continue
        val today = date == now.toLocalDate()
        // RN1: from now, the day starts at this minute (to 5 minutes). The time before it is already missed, so starting later than planned gives nothing up that was not already given up.
        val nowStart = fromNow && today
        val wake = if (nowStart) ((nowMin + 4) / 5) * 5 else setWake
        if (nowStart) shownWake = wake
        if (!nowStart && today && wake <= nowMin) { problems += "${label(date)}: ${wallClock(wake)} has already passed."; continue }
        val b = replanDay(base, date, Disturbance.StartsAt(wake), protectedKeys(base, listOf(date), zone), zone)
        slots += b.slots; droppedMeals += b.droppedMeals; problems += b.problems; b.notes.forEach { lines += it }
        loosening += if (nowStart) b.dropped.filter { !it.startsWith("Breakfast") } else startLoosening(base, date, wake, b)   // breakfast at 3pm was never going to happen
        if (b.slots.isNotEmpty()) days++
        b.dropped.forEach { lines += it }
    }
    lines.add(0, "The day starts at ${wallClock(shownWake)} on $days day${if (days == 1) "" else "s"}. Wake, Bible and breakfast follow it, then meals, work and free time, around your commitments.")
    problems.forEach { lines += "Not fully planned: $it" }
    return Compiled(slots, emptyList(), emptyList(), lines, loosening.distinct(), problems, droppedMeals = droppedMeals)
}

// ---------- Sleep later or earlier (SA1 to SA6): the day's edges move for one night ----------

private fun compileNight(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val night = inst.start
    val lo = hm(inst.str("lights_out")); val wk = hm(inst.str("wake"))
    val loAbs = if (lo < 12 * 60) lo + 1440 else lo
    val lightsOut = night.atStartOfDay().plusMinutes(loAbs.toLong())
    val wake = night.plusDays(1).atStartOfDay().plusMinutes(wk.toLong())
    if (!wake.isAfter(lightsOut)) return Compiled(problems = listOf("Wake ${wallClock(wk)} is not after lights-out ${wallClock(lo)}."))
    val a = NightAnchors(night, lightsOut, wake)
    val problems = ArrayList<String>(); val lines = ArrayList<String>(); val slots = ArrayList<PatternSlot>(); val loosening = ArrayList<String>(); val droppedMeals = HashSet<String>()
    checkAnchors(a, base, zone = zone).forEach { problems += it.text() }
    if (ZonedDateTime.of(lightsOut, zone).isBefore(now)) problems += "Lights-out ${wallClock(lo)} has already passed."
    // the evening's shutdown: the routine and sleep blocks in the last two hours before the planned lights-out move with it, in order and keeping their lengths
    val day = slotsFor(base, night, zone).filter { it.slot.type != REMINDER_TYPE }.sortedBy { it.slot.startMin }
    val oldLo = day.filter { it.slot.date == null && it.slot.type == "sleep" }.maxOfOrNull { it.slot.endMin }
    if (oldLo == null) problems += "${label(night)} has no shutdown block to move."
    else {
        val wd = inst.int("winddown_min")
        val tail = day.filter { it.slot.date == null && (it.slot.type == "routine" || it.slot.type == "sleep") && it.slot.startMin >= oldLo - 120 && it.slot.endMin <= oldLo }
        val tailLen = tail.sumOf { it.slot.endMin - it.slot.startMin }
        if (wd in 1 until tailLen) {
            // RT2: the wind-down has a floor. A late night can use it, so the evening's last block can run to just before lights-out.
            val shut = tail.last { it.slot.type == "sleep" }.slot
            for (ds in tail) { slots += ds.slot.copy(date = night, cancelled = true, notes = MOD_NOTE + "Wind-down at its floor."); if (ds.slot.type != "sleep") droppedMeals += "$night|${ds.slot.title}" }
            slots += shut.copy(date = night, cancelled = false, startMin = loAbs - wd, endMin = loAbs, notes = MOD_NOTE + "Wind-down at its floor: teeth, phone to the charger, shutdown photo.")
            lines += "The wind-down is ${wd} minutes tonight, its floor, instead of ${tailLen}, so lights-out is ${wallClock(loAbs)}."
        } else if (loAbs != oldLo) {
            val delta = loAbs - oldLo
            for (ds in tail) {
                slots += ds.slot.copy(date = night, cancelled = true, notes = MOD_NOTE + "Moved with lights-out.")
                slots += ds.slot.copy(date = night, cancelled = false, startMin = ds.slot.startMin + delta, endMin = ds.slot.endMin + delta, notes = MOD_NOTE + "Moved with lights-out.")
            }
            lines += "The evening's shutdown moves ${if (delta > 0) "later" else "earlier"} by ${Math.abs(delta)} minutes, so lights-out is ${wallClock(loAbs)}."
        }
    }
    // the next morning starts when you wake
    val wakeDate = night.plusDays(1)
    val planned = slotsFor(base, wakeDate, zone).firstOrNull { it.slot.type == "routine" && it.slot.title.startsWith("Wake") }?.slot?.startMin
    if (!isDayOff(base, wakeDate) && planned != wk) {
        val b = replanDay(base, wakeDate, Disturbance.StartsAt(wk), protectedKeys(base, listOf(wakeDate), zone), zone)
        slots += b.slots; droppedMeals += b.droppedMeals; problems += b.problems; b.notes.forEach { lines += it }; b.dropped.forEach { lines += it }; loosening += b.dropped
    }
    val sleepH = (a.inBedMinutes(zone) - FALL_ASLEEP_DEFAULT.toMinutes()) / 60.0
    lines.add(0, "Lights-out ${wallClock(loAbs)}, wake ${wallClock(wk)}: ${fmt1(sleepH)}h of sleep after 20 minutes to fall asleep.")
    problems.forEach { lines += "Not fully planned: $it" }
    return Compiled(slots, emptyList(), emptyList(), lines, loosening.distinct(), problems, droppedMeals = droppedMeals, anchors = listOf(a))
}

// ---------- Period off ----------

private fun dayNames(s: String): Set<DayOfWeek>? {
    if (s.isBlank() || s.trim().equals("all", true)) return null
    return s.split(",").mapNotNull { t -> DayOfWeek.entries.firstOrNull { it.name.startsWith(t.trim().uppercase().take(3)) } }.toSet()
}

private fun compilePeriodOff(inst: Instance, base: List<PatternSlot>, zone: ZoneId): Compiled {
    val dates = inst.dates()
    val withOff = base + dates.map { dayOffMarker(it) }
    val slots = ArrayList<PatternSlot>(); val problems = ArrayList<String>(); var pinned = 0
    for (item in inst.items) {
        val title = itemStr(item, "pin", Preset.PERIOD_OFF, "title"); val type = itemStr(item, "pin", Preset.PERIOD_OFF, "type"); val len = itemStr(item, "pin", Preset.PERIOD_OFF, "length_min").toInt()
        val days = dayNames(itemStr(item, "pin", Preset.PERIOD_OFF, "days"))
        for (date in dates.filter { days == null || it.dayOfWeek in days }) {
            val occupied = slots.filter { it.date == date }
            if (itemStr(item, "pin", Preset.PERIOD_OFF, "placement") == "fixed") {
                val s = hm(itemStr(item, "pin", Preset.PERIOD_OFF, "start")); val e = s + len
                val gap = freeGaps(withOff + occupied, date, s, e, len, zone).firstOrNull()
                if (gap == null) problems += "$title on ${label(date)} could not be placed at ${itemStr(item, "pin", Preset.PERIOD_OFF, "start")}: something is there." else { slots += place(base, date, title, type, s, e, MOD_NOTE + "Pinned during a period off.", zone); pinned++ }
            } else {
                val gap = freeGaps(withOff + occupied, date, hm(itemStr(item, "pin", Preset.PERIOD_OFF, "window_from")), hm(itemStr(item, "pin", Preset.PERIOD_OFF, "window_to")), len, zone).firstOrNull()
                if (gap == null) problems += "$title on ${label(date)} could not be placed in its window." else { slots += place(base, date, title, type, gap.first, gap.first + len, MOD_NOTE + "Pinned during a period off.", zone); pinned++ }
            }
        }
    }
    val loosening = listOf("Work blocks, lectures and planning are paused on ${dates.joinToString(", ") { label(it) }}")
    val lines = listOf("Period off ${label(inst.start)}${if (inst.days > 1) " to ${label(inst.end)}" else ""}: ${inst.days} day${if (inst.days == 1) "" else "s"}.",
        "Sleep, meals, the daily routine and chores stay. $pinned important block${if (pinned == 1) "" else "s"} placed.")
    return Compiled(slots, emptyList(), dates, lines + (if (lecturesRunning(inst)) emptyList() else listOf("No lectures are running, so nothing comes back as catch-up.")), loosening, problems, noDemand = !lecturesRunning(inst))
}

// ---------- Lecture-free lighter period ----------

private fun compileLectureFree(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val slots = ArrayList<PatternSlot>(); val loosening = ArrayList<String>(); val lines = ArrayList<String>(); val problems = ArrayList<String>()
    var lecturesRemoved = 0; var workEased = 0; var blocks = 0
    val maxMin = (inst.dbl("work_h_max") * 60).toInt()
    val mods = inst.str("modules").split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val slotLen = Math.round(STUDY_CYCLE_STANDARD_HOURS * 60).toInt()
    val wanted = inst.int("cycles_per_day")
    val perDay = wanted
    val prot = protectedKeys(base, inst.dates(), zone)
    for ((n, date) in inst.dates().withIndex()) {
        if (isDayOff(base, date)) continue
        val day = slotsFor(base, date, zone)
        val cancels = ArrayList<PatternSlot>()
        for (ds in day.filter { it.slot.date == null && (it.slot.type == "lecture" || it.slot.type == "tutorial") }) {
            cancels += ds.slot.copy(date = date, cancelled = true, notes = "lecture-free period"); lecturesRemoved++
        }
        slots += cancels
        // the day's work hours go to study first: the cycles that are there, then the study blocks it is asked for, then the rest in time order; what is over becomes free
        var used = 0; var cyclesLeft = 0; val eased = ArrayList<PatternSlot>()
        fun ease(s: PatternSlot) { eased += PatternSlot(date.dayOfWeek, s.startMin, s.endMin, "Free time (lighter period)", "free", "", false, MOD_NOTE + "Eased in a lighter period.", date = date); workEased += s.endMin - s.startMin }
        val work = day.map { it.slot }.filter { it.date == null && it.type in CUTTABLE_WORK }.sortedBy { it.startMin }
        for (s in work.filter { it.type == "study-standard" || it.type == "study-light" }) { val len = s.endMin - s.startMin; if (used + len <= maxMin) { used += len; cyclesLeft++ } else ease(s) }
        val add = if (mods.isEmpty()) 0 else max(0, min(perDay - cyclesLeft, (maxMin - used) / slotLen))
        used += add * slotLen
        for (s in work.filter { it.type != "study-standard" && it.type != "study-light" }) { val len = s.endMin - s.startMin; if (used + len <= maxMin) used += len else ease(s) }
        slots += eased
        // RF3: the study blocks a day are real blocks, laid in the gaps the lectures leave; the cycles still there count toward them
        val floor = cyclesLeft + add
        if (add > 0) {
            val (from, to) = spanMinutes(inst, date, now)
            if (from < to) {
                val dv = Devote(mods, slotLen, RESET_MIN, floor, floor, 9 * 60, 21 * 60 + 30, 2, replaceCode = false)
                val b = buildDay(base + cancels + eased, date, Rules(from = from, to = to, devote = dv, protectedFree = prot), n, zone)
                slots += b.slots; blocks += b.blocks; problems += b.problems
            }
        }
    }
    if (lecturesRemoved > 0) loosening += "$lecturesRemoved lecture${if (lecturesRemoved == 1) "" else "s"} and tutorial${if (lecturesRemoved == 1) "" else "s"} are removed from ${label(inst.start)} to ${label(inst.end)}"
    if (workEased > 0) loosening += "${fmt1(workEased / 60.0)}h of work is turned into free time"
    lines += "No lectures from ${label(inst.start)} to ${label(inst.end)}. Work is at most ${fmt1(maxMin / 60.0)}h a day, and the freed time is free."
    if (blocks > 0) lines += "$blocks study block${if (blocks == 1) "" else "s"} by module, $perDay a day counting the cycles that are already there."
    if (wanted * slotLen > maxMin) lines += "$wanted study blocks a day are more than ${fmt1(maxMin / 60.0)}h of work holds, so a day gets as many as fit. Raise the most work hours a day to have more."
    problems.forEach { lines += "Not fully planned: $it" }
    return Compiled(slots, emptyList(), emptyList(), lines, loosening.distinct(), problems)
}

// ---------- Running over (RN2): the rest of today slides after what is taking longer ----------

private fun compileRunOver(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val date = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val laneFrom = ((nowMin + inst.int("minutes") + 4) / 5) * 5
    val b = replanDay(base, date, Disturbance.RunsOver(nowMin, inst.int("minutes")), protectedKeys(base, listOf(date), zone), zone)
    val lines = ArrayList<String>(); val problems = ArrayList<String>()
    val stuck = slotsFor(base, date, zone).map { it.slot }.filter { it.date != null || it.type in setOf("lecture", "tutorial", "match", "church", "mytutor", "training", "gym", "errand") || it.hardAlarm }
        .filter { it.startMin in nowMin until laneFrom }
    lines += "What you are doing runs until ${wallClock(laneFrom)}. Blocks that have not started slide after it; free time is used first."
    stuck.forEach { lines += "${it.title.substringBefore(" - ")} starts at ${wallClock(it.startMin)}, before then, and is not moved." }
    b.notes.forEach { lines += it }; b.dropped.forEach { lines += it }
    b.problems.forEach { lines += "Not fully planned: $it" }
    return Compiled(b.slots, emptyList(), emptyList(), lines, b.dropped.distinct(), b.problems, droppedMeals = b.droppedMeals)
}

// ---------- Add an event (YP2): time is taken, and the day gives way around it ----------

private fun compileAddEvent(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val date = inst.start
    val shift = if (inst.bool("after_midnight")) 1440 else 0          // SA1: after midnight belongs to the night of the date
    val from = hm(inst.str("start")) + shift; val to = hm(inst.str("end")) + shift
    // after midnight the night still running belongs to yesterday, so "now" on that planned day is a day later in minutes
    val onTonight = date == now.toLocalDate().minusDays(1) && shift > 0
    val nowMin = if (date == now.toLocalDate()) now.hour * 60 + now.minute else if (onTonight) 1440 + now.hour * 60 + now.minute else 0
    if ((date == now.toLocalDate() || onTonight) && to <= nowMin) return Compiled(problems = listOf("${label(date)}: ${inst.str("title")} would already be over."))
    val b = replanDay(base, date, Disturbance.TimeTaken(from, to, inst.str("title").trim(), inst.str("type"), nowMin), emptySet(), zone)
    val lines = ArrayList<String>()
    lines += "${inst.str("title").trim()} on ${label(date)}, ${wallClock(from)} to ${wallClock(to)}. Everything movable gives way around it, free time first."
    b.notes.forEach { lines += it }; b.dropped.forEach { lines += it }; b.problems.forEach { lines += "Not fully planned: $it" }
    var tasks = emptyList<DueWork>(); var loosening = b.dropped.distinct()
    // AD2: with return_work, work that has to give way comes back as work instead of vanishing. The numbers carry it (the Buffer after counts it), so it is a move and not a loosening.
    if (inst.bool("return_work") && b.problems.isEmpty()) {
        val lost = lostWork(base, b.slots, date, zone)
        if (lost.isNotEmpty()) {
            val due = endOfDayCutoff(base, nextWorkingDay(base, date), zone)
            tasks = returnedWork(lost, due, "Came back")
            loosening = loosening.filterNot { line -> lost.any { line.startsWith(it.first) } }
            lines += "${lost.size} block${if (lost.size == 1) "" else "s"} (${fmt1(lost.sumOf { it.second } / 60.0)}h) could not fit around it. They come back as work due ${label(due.toLocalDate())}, so nothing disappears."
        }
    }
    return Compiled(b.slots, tasks, emptyList(), lines, loosening, b.problems, droppedMeals = b.droppedMeals)
}

/** AD2: the movable work a replan of [date] took away or shortened, as title and minutes lost. A block that only moved loses nothing. */
private fun lostWork(base: List<PatternSlot>, replanned: List<PatternSlot>, date: LocalDate, zone: ZoneId): List<Pair<String, Int>> {
    fun minutes(slots: List<PatternSlot>) = slotsFor(slots, date, zone).filter { it.slot.type in MOVABLE_WORK && !it.slot.cancelled }.groupBy { it.slot.title }.mapValues { (_, v) -> v.sumOf { it.slot.endMin - it.slot.startMin } }
    val before = minutes(base); val after = minutes(base + replanned)
    return before.mapNotNull { (title, m) -> val lost = m - (after[title] ?: 0); if (lost >= 5) title.substringBefore(" - ") to lost else null }
}

// ---------- Cancelled (YP4): time is freed ----------

private fun compileCancel(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val date = inst.start
    val nowMin = if (date == now.toLocalDate()) now.hour * 60 + now.minute else 0
    val b = replanDay(base, date, Disturbance.TimeFreed(inst.str("what"), nowMin), emptySet(), zone)
    val lines = b.dropped.map { "$it Its time is free." } + b.problems.map { "Not fully planned: $it" }
    return Compiled(b.slots, emptyList(), emptyList(), lines, b.dropped, b.problems)
}

// ---------- Lighter days: Recovery and Away share what is set aside and what comes back (YP4) ----------

/** Work beyond [maxMin] on a day becomes free time, in time order. Returns the slots and what was set aside, as title and minutes. */
private fun easeWork(base: List<PatternSlot>, date: LocalDate, maxMin: Int, now: ZonedDateTime, zone: ZoneId, why: String): Pair<List<PatternSlot>, List<Pair<String, Int>>> {
    val out = ArrayList<PatternSlot>(); val aside = ArrayList<Pair<String, Int>>(); var used = 0
    for (ds in slotsFor(base, date, zone).filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK }.sortedBy { it.slot.startMin }) {
        val len = ds.slot.endMin - ds.slot.startMin
        if (ds.end.isBefore(now)) { used += len; continue }
        if (used + len <= maxMin) used += len
        else { out += PatternSlot(date.dayOfWeek, ds.slot.startMin, ds.slot.endMin, "Free time ($why)", "free", "", false, MOD_NOTE + "Set aside: $why.", date = date); aside += ds.slot.title.substringBefore(" - ").removePrefix("Notes and problems: ") to len }
    }
    return out to aside
}

private fun returnedWork(aside: List<Pair<String, Int>>, due: ZonedDateTime, prefix: String) =
    aside.map { (title, min) -> DueWork("$prefix: $title", min / 60.0, 4, due) }

private fun compileRecovery(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val maxMin = (inst.dbl("work_h_max") * 60).toInt()
    val slots = ArrayList<PatternSlot>(); val aside = ArrayList<Pair<String, Int>>()
    for (date in inst.dates()) { if (isDayOff(base, date)) continue; val (s, a) = easeWork(base, date, maxMin, now, zone, "recovery"); slots += s; aside += a }
    val due = endOfDayCutoff(base, nextWorkingDay(base, inst.end), zone)
    val tasks = returnedWork(aside, due, "Recovery")
    val hours = aside.sumOf { it.second } / 60.0
    val lines = listOf("Work is at most ${fmt1(maxMin / 60.0)}h a day from ${label(inst.start)} to ${label(inst.end)}. Routine, meals, lectures and sleep stay.",
        if (aside.isEmpty()) "Nothing needs setting aside." else "${aside.size} block${if (aside.size == 1) "" else "s"} (${fmt1(hours)}h) set aside. They come back as work due ${label(due.toLocalDate())}, so nothing disappears.")
    return Compiled(slots, tasks, emptyList(), lines, if (aside.isEmpty()) emptyList() else listOf("${fmt1(hours)}h of work is set aside"))
}

// ---------- Away (YP4): away from home, lighter, with the travel placed ----------

private fun compileAway(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val maxMin = (inst.dbl("work_h_max") * 60).toInt()
    val returns = lecturesRunning(inst)
    val slots = ArrayList<PatternSlot>(); val aside = ArrayList<Pair<String, Int>>(); var cancelled = 0
    for (date in inst.dates()) {
        if (isDayOff(base, date)) continue
        for (ds in slotsFor(base, date, zone).filter { it.slot.date == null && (it.slot.type == "lecture" || it.slot.type == "tutorial") && !it.end.isBefore(now) }) {
            slots += ds.slot.copy(date = date, cancelled = true, notes = "away"); cancelled++
            if (returns) aside += ds.slot.title.substringBefore(" - ") to (ds.slot.endMin - ds.slot.startMin)
        }
    }
    val problems = ArrayList<String>(); val lines = ArrayList<String>(); val loosening = ArrayList<String>(); val droppedMeals = HashSet<String>()
    // the travel is placed first, by the same entry as any other event and together when it is one day, so the work is eased around it and not the other way round
    val travel = Math.round(inst.dbl("travel_h") * 60).toInt()
    val legs = listOf(inst.start to Triple("Travel out", hm(inst.str("depart")), travel), inst.end to Triple("Travel home", hm(inst.str("back")) - travel, travel))
    for ((date, group) in legs.groupBy({ it.first }, { it.second })) {
        if (isDayOff(base, date)) continue
        val nowMin = if (date == now.toLocalDate()) now.hour * 60 + now.minute else 0
        val events = group.filter { (_, from, len) -> from >= 0 && from + len <= 1440 && !(date == now.toLocalDate() && from + len <= nowMin) }.map { (what, from, len) -> Disturbance.TimeTaken(from, from + len, what, "travel", nowMin) }
        if (events.size < group.size) problems += "${label(date)}: the travel does not fit in the day."
        if (events.isEmpty()) continue
        val b = replanDay(base + slots, date, Disturbance.Several(events), emptySet(), zone)
        if (b.problems.isNotEmpty()) problems += b.problems else { slots += b.slots; droppedMeals += b.droppedMeals; loosening += b.dropped; b.dropped.forEach { lines += it } }
    }
    for (date in inst.dates()) { if (isDayOff(base, date)) continue; val (s, a) = easeWork(base + slots, date, maxMin, now, zone, "away"); slots += s; aside += a }
    val due = endOfDayCutoff(base, nextWorkingDay(base, inst.end), zone)
    val tasks = returnedWork(aside, due, "Away")
    val hours = aside.sumOf { it.second } / 60.0
    lines.add(0, "Away ${label(inst.start)} to ${label(inst.end)}: $cancelled lecture or tutorial block${if (cancelled == 1) "" else "s"} paused, work at most ${fmt1(maxMin / 60.0)}h a day, ${fmt1(travel / 60.0)}h of travel each way.")
    if (returns && aside.isNotEmpty()) lines += "${fmt1(hours)}h of lectures and work come back as work due ${label(due.toLocalDate())}."
    else if (!returns) lines += "No lectures are running, so nothing from them comes back."
    problems.forEach { lines += "Not fully planned: $it" }
    if (cancelled > 0) loosening += "$cancelled lecture or tutorial block${if (cancelled == 1) "" else "s"} paused from ${label(inst.start)} to ${label(inst.end)}"
    if (hours > 0) loosening += "${fmt1(hours)}h of work is set aside or paused"
    return Compiled(slots, tasks, emptyList(), lines, loosening.distinct(), problems, noDemand = !returns, droppedMeals = droppedMeals)
}

// ---------- Guests (YP4): preparation before, the visit, tidying after ----------

private fun compileGuests(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val title = inst.str("title").trim().ifEmpty { "Guests" }
    val from = hm(inst.str("from")); val until = hm(inst.str("until"))
    val arrive = inst.start; val leave = inst.end; val multi = leave != arrive
    val today = now.toLocalDate()
    fun nowMinOn(d: LocalDate) = if (d == today) now.hour * 60 + now.minute else 0
    val events = java.util.TreeMap<LocalDate, MutableList<Disturbance.TimeTaken>>()
    fun addEvent(d: LocalDate, e: Disturbance.TimeTaken) { events.getOrPut(d) { ArrayList() } += e }
    // the visit: the time spent with them, not the whole day, so you carry on with yours while they are there
    for (date in inst.dates()) {
        val a = if (date == arrive) from else 18 * 60; val z = if (date == leave) until else 21 * 60 + 30
        if (z > a && z > nowMinOn(date)) addEvent(date, Disturbance.TimeTaken(a, z, title, "errand", nowMinOn(date)))
    }
    val after = inst.int("after_min")
    if (after > 0) addEvent(leave, Disturbance.TimeTaken(until, until + after, "$title: tidy up", "chore", nowMinOn(leave)))
    // the preparation: each job in free time before they arrive, closest days first; a job that finds no free time becomes an event the day gives way for
    val prepSlots = ArrayList<PatternSlot>(); val problems = ArrayList<String>()
    val jobs = inst.str("prep").split(";").mapNotNull { j -> val p = j.split("|"); val m = p.getOrNull(1)?.trim()?.toIntOrNull(); if (p[0].isBlank() || m == null || m <= 0) null else p[0].trim() to m }
    val prepDays = (inst.int("lead_days").toLong() downTo 0L).map { arrive.minusDays(it) }.filter { !it.isBefore(today) }
    val fallbackAt = HashMap<LocalDate, MutableList<Disturbance.TimeTaken>>(); var placedJobs = 0
    for ((n, job) in jobs.withIndex()) {
        val (name, len) = job
        var placed = false
        for (date in prepDays.indices.map { prepDays[(it + n) % prepDays.size] }) {
            val limit = if (date == arrive) from else 21 * 60
            val gap = freeGaps(base + prepSlots, date, max(9 * 60, nowMinOn(date)), limit, len, zone).firstOrNull() ?: continue
            prepSlots += PatternSlot(date.dayOfWeek, gap.first, gap.first + len, "$title: $name", "chore", "", false, MOD_NOTE + "Preparation for $title.", date = date); placed = true; break
        }
        if (!placed) {
            // no free time: look for the first time, on any preparation day, where the day can give way for it
            search@ for (date in prepDays.indices.map { prepDays[(it + n) % prepDays.size] }) {
                val limit = if (date == arrive) from else 21 * 60
                var s = max(9 * 60, nowMinOn(date)).let { (it + 29) / 30 * 30 }
                while (s + len <= limit) {
                    val taken = (events[date].orEmpty() + fallbackAt.getOrDefault(date, emptyList())).any { s < it.toMin && s + len > it.fromMin }
                    val e = Disturbance.TimeTaken(s, s + len, "$title: $name", "chore", nowMinOn(date))
                    if (!taken && eventClash(base + prepSlots, date, e, zone) == null) { addEvent(date, e); fallbackAt.getOrPut(date) { ArrayList() } += e; placed = true; break@search }
                    s += 30
                }
            }
        }
        if (placed) placedJobs++ else problems += "No room for \"$name\" before ${label(arrive)}. Prepare earlier, or shorten the list."
    }
    val slots = ArrayList<PatternSlot>(prepSlots); val loosening = ArrayList<String>(); val droppedMeals = HashSet<String>(); val lines = ArrayList<String>()
    for ((date, list) in events) {
        val b = replanDay(base + prepSlots, date, Disturbance.Several(list), emptySet(), zone)
        if (b.problems.isNotEmpty()) problems += b.problems else { slots += b.slots; droppedMeals += b.droppedMeals; loosening += b.dropped; b.dropped.forEach { lines += it } }
    }
    lines.add(0, "$title ${label(arrive)} ${wallClock(from)}${if (multi) " to ${label(leave)} ${wallClock(until)}" else " to ${wallClock(until)}"}. $placedJobs of ${jobs.size} preparation job${if (jobs.size == 1) "" else "s"} placed before they arrive, tidying after.")
    problems.forEach { lines += "Not fully planned: $it" }
    return Compiled(slots, emptyList(), emptyList(), lines, loosening.distinct(), problems, droppedMeals = droppedMeals)
}
// ---------- Revision (YP4): the assignment planner on exams ----------

/** Revision plans each exam backwards like an assignment, interleaved and spread, so the same planner serves both. */
fun revisionAsAssignment(inst: Instance): Instance = inst.copy(preset = Preset.ASSIGNMENT, settings = mapOf("order" to "interleaved", "mode" to "even") + inst.settings)

private fun compileRevision(inst: Instance, base: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val c = compileAssignment(revisionAsAssignment(inst), base, now, zone)
    return c.copy(slots = c.slots.map { if (it.type == "study" && it.title.contains(":")) it.copy(title = it.title.substringBeforeLast(":") + ": revision", notes = MOD_NOTE + "Revision session.") else it },
        lines = c.lines.map { it.replace("session", "revision session") })
}

/** Compiles a preset against the calendar as it is, without writing anything. [work] is the work due now, so the Buffer can be shown. */
fun compile(inst: Instance, base: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId = LONDON): Compiled {
    val cuts = lectureCuts(inst, base, zone)                                       // term state: lectures off is laid under the preset, so its sessions can use the time
    val b = base + cuts
    val c = when (inst.preset) {
        Preset.ASSIGNMENT -> compileAssignment(inst, b, now, zone)
        Preset.FOCUS -> compileFocus(inst, b, now, zone)
        Preset.CATCH_UP -> compileCatchUp(inst, b, work, now, zone)
        Preset.PERIOD_OFF -> compilePeriodOff(inst, base, zone)
        Preset.LECTURE_FREE -> compileLectureFree(inst, base, now, zone)
        Preset.LATE_START -> compileLateStart(inst, base, now, zone)
        Preset.RUN_OVER -> compileRunOver(inst, base, now, zone)
        Preset.ADD_EVENT -> compileAddEvent(inst, base, now, zone)
        Preset.CANCEL -> compileCancel(inst, base, now, zone)
        Preset.RECOVERY -> compileRecovery(inst, base, now, zone)
        Preset.GUESTS -> compileGuests(inst, base, now, zone)
        Preset.AWAY -> compileAway(inst, base, now, zone)
        Preset.REVISION -> compileRevision(inst, base, now, zone)
        Preset.NIGHT -> compileNight(inst, base, now, zone)
    }
    if (cuts.isEmpty()) return c
    return c.copy(slots = cuts + c.slots, loosening = c.loosening + "${cuts.size} lecture${if (cuts.size == 1) "" else "s"} and tutorial${if (cuts.size == 1) "" else "s"} are cancelled from ${label(inst.start)} to ${label(inst.end)}",
        lines = c.lines + "No lectures from ${label(inst.start)} to ${label(inst.end)}, so none come back as catch-up.")
}

/** The lectures and tutorials a "lectures: none" instance cancels. Only work presets use this; period off and lecture-free handle their own. */
fun lectureCuts(inst: Instance, base: List<PatternSlot>, zone: ZoneId = LONDON): List<PatternSlot> {
    if (inst.preset != Preset.ASSIGNMENT && inst.preset != Preset.FOCUS && inst.preset != Preset.CATCH_UP) return emptyList()
    if (inst.str("lectures") != "none") return emptyList()
    return inst.dates().filter { !isDayOff(base, it) }.flatMap { date ->
        slotsFor(base, date, zone).filter { it.slot.date == null && (it.slot.type == "lecture" || it.slot.type == "tutorial") }.map { it.slot.copy(date = date, cancelled = true, notes = "lectures off") }
    }
}

// ---------- guard rails (spec section 10) ----------

private val PROTECTED_TYPES = setOf("sleep", "meal")
private val ROUTINE_TYPES = setOf("routine", "chore")

/** The calendar with a compiled preset laid over it. */
fun applied(base: List<PatternSlot>, c: Compiled): List<PatternSlot> = base + c.dayOffDates.map { dayOffMarker(it) } + c.slots

/**
 * The guard rails that can be checked on a finished plan: protected blocks untouched (MG7, MG13), the daily routine kept (MG18), no overlaps (MG4), sensible lengths
 * (MG5), nothing in the night (MG6), nothing but pinned blocks on a day off (MG12), and the daily cap held (MG10). A plan with any problem cannot be applied.
 */
fun checkPlan(base: List<PatternSlot>, c: Compiled, inst: Instance, capMin: Int? = null, zone: ZoneId = LONDON, anchors: Map<LocalDate, NightAnchors> = emptyMap()): List<String> {
    val after = applied(base, c)
    val out = ArrayList<String>()
    val nights = anchors + c.anchors.associateBy { it.night }     // SA1, SA2: the night edges that apply; a night with no anchors keeps the old 06:00 and 23:00
    fun edges(date: LocalDate): Pair<Int, Int> {
        val w = nights[date.minusDays(1)]?.takeIf { it.wakeDate == date }?.let { it.wake.hour * 60 + it.wake.minute } ?: (6 * 60)
        val l = nights[date]?.let { Duration.between(date.atStartOfDay(), it.lightsOut).toMinutes().toInt() } ?: (23 * 60)
        return w to l
    }
    val dates = (inst.dates() + c.slots.mapNotNull { it.date }).distinct()
    fun key(d: DatedSlot) = Triple(d.slot.title, d.slot.startMin, d.slot.endMin)
    // RF4 (amends MG7, MG18): sleep and hard alarms never move. A meal, the routine or a chore may move to follow the day and keeps its length; it may only go
    // altogether when the plan says so ([Compiled.droppedMeals]) and that is a loosening.
    fun same(d: DatedSlot) = Triple(d.slot.title, d.slot.endMin - d.slot.startMin, d.slot.type)
    for (date in dates) {
        val before = slotsFor(base, date, zone); val now = slotsFor(after, date, zone)
        fun declared(d: DatedSlot) = "$date|${d.slot.title}" in c.droppedMeals
        val prot0 = before.filter { (it.slot.type == "sleep" && date !in nights.keys) || it.slot.hardAlarm }.map(::key).toSet(); val prot1 = now.filter { (it.slot.type == "sleep" && date !in nights.keys) || it.slot.hardAlarm }.map(::key).toSet()
        val meal0 = before.filter { it.slot.type == "meal" && !declared(it) }.map(::same).toSet(); val meal1 = now.filter { it.slot.type == "meal" }.map(::same).toSet()
        if (!prot1.containsAll(prot0) || !meal1.containsAll(meal0)) out += "${label(date)}: a protected block (sleep, a meal or a hard alarm) would be changed."
        val rout0 = before.filter { it.slot.type in ROUTINE_TYPES && !declared(it) }.map(::same).toSet(); val rout1 = now.filter { it.slot.type in ROUTINE_TYPES }.map(::same).toSet()
        if (!rout1.containsAll(rout0)) out += "${label(date)}: the daily routine or a chore would be removed."
        val busy = now.filter { it.slot.type != "free" }.sortedBy { it.slot.startMin }
        for ((a, b) in busy.zipWithNext()) if (b.slot.startMin < a.slot.endMin && a.slot.endMin > a.slot.startMin) out += "${label(date)}: ${a.slot.title} overlaps ${b.slot.title}."
    }
    c.slots.filter { !it.cancelled && it.date in c.dayOffDates && it.type in setOf("study", "code", "lecture", "tutorial", "planning") && !it.notes.startsWith(MOD_NOTE) }
        .forEach { out += "${it.date?.let(::label)}: ${it.title} is placed on a day off." }
    // a reset is 10 minutes by design (W4), and a block that only moved keeps the length it always had (RF4)
    fun isOriginalLength(s: PatternSlot) = s.title == RESET_TITLE || s.notes.contains("Wind-down at its floor") || base.any { it.date == null && it.day == s.date?.dayOfWeek && it.title == s.title && it.endMin - it.startMin == s.endMin - s.startMin }
    // a block that was already there and only moved earlier or stayed is not newly in the night
    fun notLaterThanBefore(s: PatternSlot) = base.any { it.date == null && it.day == s.date?.dayOfWeek && it.title == s.title && it.endMin >= s.endMin && it.endMin - it.startMin == s.endMin - s.startMin }
    for (s in c.slots.filter { !it.cancelled }) {
        if ((s.endMin - s.startMin < MIN_PIECE_MIN || s.endMin - s.startMin > 600) && !isOriginalLength(s)) out += "${s.title} on ${s.date?.let(::label)} has a length that is not allowed."
        val (wEdge, lEdge) = s.date?.let(::edges) ?: (6 * 60 to 23 * 60)
        if (s.type != "free" && !withinTheDay(s.startMin, s.endMin, wEdge, lEdge) && !notLaterThanBefore(s)) out += "${s.title} on ${s.date?.let(::label)} would be in the night."
    }
    if (capMin != null) c.slots.filter { it.type == "study" && !it.cancelled }.groupBy { it.date }.forEach { (date, l) -> if (l.sumOf { it.endMin - it.startMin } > capMin) out += "${date?.let(::label)}: more than the daily cap of ${fmt1(capMin / 60.0)}h is placed." }
    return out.distinct()
}

/** What applying a preset would do, with its cost, before anything is written (IM5, C4, H2). */
data class ModPreview(val compiled: Compiled, val before: BufferState, val after: BufferState, val problems: List<String>, val wallNeeded: Boolean, val negative: Boolean, val summary: PeriodSummary? = null) {
    val canApply get() = problems.isEmpty()
}

fun previewOf(inst: Instance, base: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId = LONDON, anchors: Map<LocalDate, NightAnchors> = emptyMap()): ModPreview {
    val invalid = validate(inst, now.toLocalDate())
    if (invalid.isNotEmpty()) return ModPreview(Compiled(), bufferNow(now, studyCycles(base, now) + work, base, false), BufferState.NoDeadline, invalid, false, false)
    val c = compile(inst, base, work, now, zone)
    val cap = when (inst.preset) { Preset.ASSIGNMENT, Preset.FOCUS, Preset.REVISION -> (inst.dbl("daily_cap_h") * 60).toInt(); else -> null }
    val rails = checkPlan(base, c, inst, cap, zone, anchors)
    val after = applied(base, c)
    val demand = (if (c.noDemand) emptyList() else dayOffDemand(base, c.dayOffDates, now, zone)).map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    val before = bufferNow(now, studyCycles(base, now) + work, base, false)
    val afterBuffer = bufferNow(now, studyCycles(after, now) + work + c.tasks + demand, after, false)
    val negative = afterBuffer is BufferState.Value && afterBuffer.result.negative
    // RF8: what the span comes to, in numbers, so a preset is never judged by its settings alone
    val numberDates = when (inst.preset) {
        Preset.PERIOD_OFF -> emptyList()
        Preset.ASSIGNMENT, Preset.REVISION -> c.slots.mapNotNull { it.date }.distinct().sorted().take(MAX_SPAN_DAYS)
        else -> inst.dates()
    }
    val summary = periodSummary(base, after, numberDates, ModDefs.MODULES.split("|"), zone)
    val lines = c.lines + (if (negative && afterBuffer is BufferState.Value) listOf("This makes the Buffer NEGATIVE, so the work no longer fits:") + afterBuffer.result.working().lines() else emptyList())
    // a span that changes nothing is not offered: Late start with a time that has passed, a catch-up span of days off
    val nothing = inst.preset in setOf(Preset.LATE_START, Preset.CATCH_UP, Preset.RUN_OVER, Preset.ADD_EVENT, Preset.CANCEL, Preset.GUESTS, Preset.AWAY) && c.slots.isEmpty()
    val why = if (nothing) listOf(c.problems.firstOrNull() ?: "This changes nothing: there is no day in these dates it can change.") else emptyList()
    val own = if (inst.preset == Preset.NIGHT) c.problems else emptyList()           // SA3, SA5: a night that cannot stand is refused, with the reason
    return ModPreview(c.copy(lines = lines), before, afterBuffer, (rails + why + own).distinct(), c.loosening.isNotEmpty(), negative, summary)
}

// ---------- Recommended (button press only, spec section 11) ----------

data class Rec(val value: String, val reason: String)

/** MR1, MR5: from what is already on the phone, the same data giving the same answer. [backlogH] is the hours of missed work, [calibration] the learned estimates. */
fun recommend(preset: Preset, base: List<PatternSlot>, start: LocalDate, end: LocalDate, backlogH: Double = 0.0, calibration: Calibration = Calibration()): Map<String, Rec> {
    val out = LinkedHashMap<String, Rec>()
    val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList().ifEmpty { listOf(start) }
    fun workMin(date: LocalDate) = slotsFor(base, date).filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK && !isCycle(it.slot) }.sumOf { it.slot.endMin - it.slot.startMin }
    val avgWorkH = dates.map { workMin(it) }.average() / 60.0
    when (preset) {
        Preset.ASSIGNMENT -> {
            val cap = (Math.round(avgWorkH.coerceIn(2.0, 6.0) * 2) / 2.0)
            out["daily_cap_h"] = Rec(cap.toString(), "Your plan has about ${fmt1(avgWorkH)}h a day of work blocks that are not study cycles, so $cap h is what assignments can use.")
        }
        Preset.FOCUS -> {
            val cap = (Math.round(avgWorkH.coerceIn(3.0, 7.0) * 2) / 2.0)
            out["daily_cap_h"] = Rec(cap.toString(), "About ${fmt1(avgWorkH)}h a day of work time exists, and a focus day is kept between 3 and 7 hours.")
            out["max_session"] = Rec("90", "90 minutes is a sitting you can hold without a break.")
        }
        Preset.CATCH_UP -> {
            val slotH = 2.5; val days = dates.size.coerceAtLeast(1)
            val floor = ceil(backlogH / (days * slotH)).toInt().coerceIn(2, 4)
            out["slots_floor"] = Rec(floor.toString(), "${fmt1(backlogH)}h of missed work over $days days at ${fmt1(slotH)}h a block needs about $floor a day.")
            out["slots_stretch"] = Rec((floor + 1).coerceAtMost(5).toString(), "One more than the floor on a good day.")
            val rapid = backlogH > days * 4 * slotH
            out["intensity"] = Rec(if (rapid) "rapid" else "full", if (rapid) "The backlog is more than 4 full blocks a day can clear in $days days, so rapid notes." else "The backlog fits in full blocks.")
        }
        Preset.ADD_EVENT, Preset.CANCEL, Preset.GUESTS, Preset.AWAY, Preset.NIGHT -> {}
        Preset.RECOVERY -> out["work_h_max"] = Rec("2.0", "Two hours a day keeps the habit without the load while you recover.")
        Preset.REVISION -> out["daily_cap_h"] = Rec((Math.round(avgWorkH.coerceIn(2.0, 6.0) * 2) / 2.0).toString(), "Your plan has about ${fmt1(avgWorkH)}h a day of work blocks that are not study cycles.")
        Preset.LATE_START -> out["wake"] = Rec("10:00", "A day that starts at 10:00 still has its meals and a full working afternoon.")
        Preset.RUN_OVER -> out["minutes"] = Rec("30", "Half an hour is the usual overrun of a meal or a task.")
        Preset.PERIOD_OFF -> out["pins"] = Rec(dates.flatMap { dt -> slotsFor(base, dt).filter { it.slot.date == null && it.slot.type in setOf("church", "gym", "mytutor", "training", "match") }.map { it.slot.title.substringBefore(" - ") } }.distinct().joinToString(", ").ifEmpty { "none" },
            "These fixed commitments fall in the period. Add each as an important block to keep it.")
        Preset.LECTURE_FREE -> {
            val allWork = dates.map { dt -> slotsFor(base, dt).filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK }.sumOf { it.slot.endMin - it.slot.startMin } }.average() / 60.0
            val cap = (Math.round((allWork * 0.6).coerceIn(2.0, 6.0) * 2) / 2.0)
            out["work_h_max"] = Rec(cap.toString(), "Your plan has about ${fmt1(allWork)}h of work a day. A lighter period keeps about 60% of it.")
            out["cycles_per_day"] = Rec("1", "One study slot a day keeps the habit without the lectures.")
        }
    }
    return out
}

/** Recommended for one assignment: hours from heaviness and your own learned times (B5), lead time from the hours and the room per day. */
fun recommendAssignment(heaviness: Int, calibration: Calibration, freeHoursPerDay: Double): Map<String, Rec> {
    val hours = Math.round(calibration.hours(heaviness) * 2) / 2.0
    val perDay = freeHoursPerDay.coerceAtLeast(1.0)
    val lead = ceil(hours / perDay).toInt().coerceIn(7, 14)
    return linkedMapOf(
        "size_h" to Rec(hours.toString(), "Heaviness $heaviness is about ${fmt1(hours)}h: ${calibration.correction(heaviness).text()}."),
        "lead_days" to Rec(lead.toString(), "${fmt1(hours)}h at about ${fmt1(perDay)}h a day free needs ${ceil(hours / perDay).toInt()} days, never fewer than 7."),
    )
}

// ---------- storage text ----------

private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
private fun unesc(s: String): String { val b = StringBuilder(); var i = 0; while (i < s.length) { val c = s[i]; if (c == '\\' && i + 1 < s.length) { b.append(when (s[i + 1]) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> s[i + 1] }); i += 2 } else { b.append(c); i++ } }; return b.toString() }

fun encodeInstance(i: Instance): String = buildString {
    append("preset=${i.preset.id}\nstart=${i.start}\nend=${i.end}\n")
    if (i.fromMin != 0) append("from=${i.fromMin}\n")
    if (i.toMin != 1440) append("to=${i.toMin}\n")
    i.settings.forEach { (k, v) -> append("s.${esc(k)}=${esc(v)}\n") }
    i.items.forEachIndexed { n, m -> m.forEach { (k, v) -> append("i.$n.${esc(k)}=${esc(v)}\n") } }
}

fun decodeInstance(text: String): Instance? = runCatching {
    val lines = text.lines().filter { it.contains('=') }.associate { it.substringBefore('=') to unesc(it.substringAfter('=')) }
    val items = java.util.TreeMap<Int, MutableMap<String, String>>()
    val settings = LinkedHashMap<String, String>()
    for ((k, v) in lines) when {
        k.startsWith("s.") -> settings[k.removePrefix("s.")] = v
        k.startsWith("i.") -> { val rest = k.removePrefix("i."); items.getOrPut(rest.substringBefore('.').toInt()) { LinkedHashMap() }[rest.substringAfter('.')] = v }
    }
    Instance(Preset.of(lines.getValue("preset"))!!, LocalDate.parse(lines.getValue("start")), LocalDate.parse(lines.getValue("end")), settings, items.values.toList(),
        lines["from"]?.toIntOrNull() ?: 0, lines["to"]?.toIntOrNull() ?: 1440)
}.getOrNull()

fun encodeSlots(l: List<PatternSlot>): String = l.joinToString("\n") { listOf(it.day.name, it.startMin, it.endMin, esc(it.title), it.type, esc(it.venue), it.hardAlarm, esc(it.notes), it.date ?: "", it.cancelled).joinToString("\t") }

fun decodeSlots(text: String): List<PatternSlot> = text.lines().filter { it.isNotBlank() }.mapNotNull { line ->
    runCatching {
        val p = line.split("\t")
        PatternSlot(DayOfWeek.valueOf(p[0]), p[1].toInt(), p[2].toInt(), unesc(p[3]), p[4], unesc(p[5]), p[6].toBoolean(), unesc(p[7]), p[8].takeIf { it.isNotEmpty() }?.let(LocalDate::parse), p[9].toBoolean())
    }.getOrNull()
}

/**
 * What stops a new instance being applied on top of ones already active. The same thing twice would double the work and a second period over the same days
 * would share its days off, so removing one would take the other's away. Work presets may overlap (the guard rails catch clashes in the calendar) unless they
 * repeat an assignment or project that is already planned.
 */
fun modifierConflicts(inst: Instance, active: List<Instance>): List<String> {
    val out = ArrayList<String>()
    if (active.any { encodeInstance(it) == encodeInstance(inst) }) out += "This is already applied."
    val periodic = inst.preset == Preset.PERIOD_OFF || inst.preset == Preset.LECTURE_FREE || inst.preset == Preset.CATCH_UP || inst.preset == Preset.LATE_START || inst.preset == Preset.RUN_OVER || inst.preset == Preset.AWAY || inst.preset == Preset.RECOVERY || inst.preset == Preset.GUESTS || (inst.preset == Preset.FOCUS && inst.bool("hybrid"))
    if (periodic && active.any { it.preset == inst.preset && !it.end.isBefore(inst.start) && !it.start.isAfter(inst.end) })
        out += "${inst.preset.label} already covers some of these days. Remove it first or choose other dates."
    if (inst.preset == Preset.PERIOD_OFF && active.any { it.preset == Preset.LECTURE_FREE && !it.end.isBefore(inst.start) && !it.start.isAfter(inst.end) })
        out += "A lecture-free period already covers some of these days. Remove it first or choose other dates."
    if (inst.preset == Preset.ASSIGNMENT || inst.preset == Preset.FOCUS || inst.preset == Preset.REVISION) {
        val planned = active.filter { it.preset == inst.preset }.flatMap { a -> a.items.map { (it["title"] ?: "").trim().lowercase() to it["due"] } }.toSet()
        for (item in inst.items) if (((item["title"] ?: "").trim().lowercase() to item["due"]) in planned) out += "${item["title"]} due ${item["due"]} is already planned."
    }
    return out
}

/** What is left of a modifier's stored slots after the calendar changed under it. */
data class Reconciled(val kept: List<PatternSlot>, val dropped: Int)

/**
 * A modifier is compiled when it is applied, but the calendar can change afterwards: a day off is planned, a file is imported, a library period is added. Its stored
 * slots must never then sit on a day off (MG12) or on top of something real (MG4). Such a slot is left out when the calendar is read; its hours are still due as work, so
 * the Buffer stays true, and the person is told how many were left out. Cancellations and free time are kept. [allowOnDayOff] is for a period off (its pinned blocks) and a
 * hybrid focus week, which are meant to sit on days off.
 */
fun reconcile(core: List<PatternSlot>, stored: List<PatternSlot>, allowOnDayOff: Boolean, zone: ZoneId = LONDON): Reconciled {
    var dropped = 0
    val cancels = stored.filter { it.cancelled }                                     // this modifier's own cancellations already clear what they cancel
    val droppedOn = HashSet<LocalDate>()
    val kept0 = stored.filter { s ->
        val date = s.date
        if (s.cancelled || s.type == "free" || date == null) return@filter true
        if (!allowOnDayOff && isDayOff(core, date)) { dropped++; droppedOn += date; return@filter false }
        // work blocks and free time are meant to be cut by a session; anything else (an event, a meal, sleep, a lecture still running) is not
        val clash = slotsFor(core + cancels, date, zone).any { it.slot.type != "free" && it.slot.type !in CUTTABLE_WORK && s.startMin < it.slot.endMin && it.slot.startMin < s.endMin }
        if (clash) { dropped++; droppedOn += date; false } else true
    }
    // RF9: a day this modifier laid out again (meals or the routine moved, or study blocks added) is all or nothing. If part of it cannot stand any more, the whole
    // day goes back to the plan underneath, so a meal that was moved is never left cancelled with nothing in its place. Lectures it cancelled stay cancelled.
    val relaid = stored.filter { !it.cancelled && it.date != null && (it.type == "meal" || it.type == "routine" || it.notes.startsWith(MOD_NOTE + "Catch-up block")) }.mapNotNull { it.date }.toSet()
    val fallBack = droppedOn.intersect(relaid)
    val kept = if (fallBack.isEmpty()) kept0 else kept0.filter { it.date !in fallBack || it.type == "lecture" || it.type == "tutorial" }
    return Reconciled(kept, dropped)
}

/** Whether a modifier's own slots may sit on days off: a period off's pinned blocks, and a hybrid focus week. */
fun allowsDaysOff(inst: Instance?): Boolean = inst != null && (inst.preset == Preset.PERIOD_OFF || inst.preset == Preset.ADD_EVENT || inst.preset == Preset.GUESTS || inst.preset == Preset.NIGHT || (inst.preset == Preset.FOCUS && inst.bool("hybrid")))

/** SA1: the anchors an active night instance stands for, so the guard rail, the alarm and the blocking read the same two times. */
fun nightAnchorsOf(inst: Instance): NightAnchors? {
    if (inst.preset != Preset.NIGHT) return null
    val lo = hm(inst.str("lights_out")); val wk = hm(inst.str("wake"))
    val loAbs = if (lo < 12 * 60) lo + 1440 else lo
    val lightsOut = inst.start.atStartOfDay().plusMinutes(loAbs.toLong()); val wake = inst.start.plusDays(1).atStartOfDay().plusMinutes(wk.toLong())
    return if (wake.isAfter(lightsOut)) NightAnchors(inst.start, lightsOut, wake) else null
}

/**
 * MX1: every modifier is built from five effects. The presets are named recipes on them (MX2). Not on the list? Pick what the thing does to your day, and the effect leads to the
 * recipes that do it (MX3).
 */
enum class Effect(val label: String, val question: String) {
    DEMAND("Adds work", "Something has to get done, with a size and a deadline"),
    SUPPLY("Takes time away", "You have less time or energy: ill, away, a period off, lighter days"),
    EVENT("A fixed thing", "Something happens at a time: a visit, an appointment, a cancellation"),
    ANCHOR("Moves the day's edges", "The day starts or ends at another time: a late start, running over, a late night"),
    POLICY("Changes the rules", "Lectures on or off, a project only, lighter work"),
}

/** The effects a preset is made of, the main one first. */
fun effectsOf(p: Preset): List<Effect> = when (p) {
    Preset.ASSIGNMENT, Preset.REVISION, Preset.CATCH_UP -> listOf(Effect.DEMAND)
    Preset.FOCUS -> listOf(Effect.POLICY, Effect.DEMAND)
    Preset.LECTURE_FREE -> listOf(Effect.POLICY, Effect.SUPPLY)
    Preset.PERIOD_OFF -> listOf(Effect.SUPPLY, Effect.POLICY)
    Preset.RECOVERY -> listOf(Effect.SUPPLY)
    Preset.AWAY -> listOf(Effect.SUPPLY, Effect.EVENT)
    Preset.ADD_EVENT, Preset.CANCEL -> listOf(Effect.EVENT)
    Preset.GUESTS -> listOf(Effect.EVENT, Effect.DEMAND)
    Preset.LATE_START, Preset.RUN_OVER, Preset.NIGHT -> listOf(Effect.ANCHOR)
}

fun presetsFor(e: Effect): List<Preset> = Preset.entries.filter { effectsOf(it).first() == e }
