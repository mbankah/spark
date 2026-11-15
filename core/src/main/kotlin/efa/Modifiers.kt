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
enum class SettingKind { INT, DECIMAL, CHOICE, BOOL, TIME, TEXT, DATE }

data class SettingDef(
    val key: String, val label: String, val kind: SettingKind, val default: String,
    val min: Double? = null, val max: Double? = null, val choices: List<String> = emptyList(), val help: String = "",
)

data class ItemDef(val kind: String, val label: String, val settings: List<SettingDef>, val min: Int = 0, val max: Int = 20)

enum class Preset(val id: String, val label: String, val blurb: String) {
    ASSIGNMENT("assignment", "Assignment", "Plans each assignment backward from its due date so work starts early."),
    CATCH_UP("catch_up", "Catch-up week", "A number of study slots a day, by module, to clear a backlog."),
    PERIOD_OFF("period_off", "Period off", "Work and lectures off between two dates. Important blocks stay."),
    FOCUS("focus", "Focus week", "One project only, with the daily routine still happening."),
    LECTURE_FREE("lecture_free", "Lecture-free lighter period", "No lectures, lighter work, still working.");
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
            d("slots_floor", "Study slots a day (at least)", SettingKind.INT, "3", 1.0, 6.0, help = "Lecture cycles count toward it"),
            d("slots_stretch", "Study slots on a good day", SettingKind.INT, "4", 1.0, 8.0),
            d("intensity", "Intensity", SettingKind.CHOICE, "full", choices = listOf("full", "rapid"), help = "Rapid notes: faster video, skim reading, short notes"),
            d("rapid_factor", "Rapid notes take this share of a slot", SettingKind.DECIMAL, "0.65", 0.3, 1.0),
            d("slot_h", "Hours in a slot", SettingKind.DECIMAL, "2.5", 0.5, 4.0),
            d("modules", "Modules, most behind first", SettingKind.TEXT, MODULES, help = "Separate with |"),
            d("module_cap", "Most slots of one module a day", SettingKind.INT, "2", 1.0, 4.0),
            d("weekend", "Weekend", SettingKind.CHOICE, "same", choices = listOf("same", "lower", "none")),
            d("stretch_if_negative", "If the stretch does not fit", SettingKind.CHOICE, "drop", choices = listOf("drop", "show")),
        )
        Preset.PERIOD_OFF -> listOf(
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
        Preset.FOCUS -> listOf(projectItem)
        Preset.PERIOD_OFF -> listOf(pinItem)
        else -> emptyList()
    }

    /** Assignment and focus plan backward from due dates, so their own dates come from the items; the others are given a start and an end. */
    fun datesFromItems(p: Preset) = p == Preset.ASSIGNMENT
}

/** One use of a preset: its dates, its settings and its items. */
data class Instance(val preset: Preset, val start: LocalDate, val end: LocalDate, val settings: Map<String, String> = emptyMap(), val items: List<Map<String, String>> = emptyList()) {
    private fun def(key: String) = ModDefs.settings(preset).first { it.key == key }
    fun str(key: String) = settings[key]?.takeIf { it.isNotBlank() } ?: def(key).default
    fun int(key: String) = str(key).toIntOrNull() ?: def(key).default.toInt()
    fun dbl(key: String) = str(key).toDoubleOrNull() ?: def(key).default.toDouble()
    fun bool(key: String) = str(key).equals("true", ignoreCase = true)
    val days: Int get() = (Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays() + 1).toInt()
    fun dates(): List<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
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
)

/** Whether lectures are running during a period off. Auto reads a week or more as a holiday. */
fun lecturesRunning(inst: Instance): Boolean = when (inst.str("lectures")) { "running" -> true; "none" -> false; else -> inst.days < 7 }

/** MG8: real dates, a period of 1 to 14 days, nothing in the past, every setting inside its range. */
fun validate(inst: Instance, today: LocalDate): List<String> {
    val out = ArrayList<String>()
    if (inst.end.isBefore(inst.start)) out += "The last day is before the first day."
    if (inst.start.isBefore(today)) out += "The first day is in the past."
    val needsRange = inst.preset != Preset.ASSIGNMENT
    if (needsRange && inst.days > 14) out += "A period is 1 to 14 days."
    for (s in ModDefs.settings(inst.preset)) {
        val raw = inst.settings[s.key] ?: continue
        when (s.kind) {
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

private fun compileCatchUp(inst: Instance, base: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId): Compiled {
    val mods = inst.str("modules").split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val hoursEach = inst.dbl("slot_h") * if (inst.str("intensity") == "rapid") inst.dbl("rapid_factor") else 1.0
    val auto = studyCycles(base, now)
    val tasks = ArrayList<DueWork>(); val lines = ArrayList<String>(); val problems = ArrayList<String>()
    var dropStretch = false; var dropped = 0
    for ((n, date) in inst.dates().withIndex()) {
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val floor = when { weekend && inst.str("weekend") == "none" -> 0; weekend && inst.str("weekend") == "lower" -> max(1, inst.int("slots_floor") - 1); else -> inst.int("slots_floor") }
        if (floor == 0 || isDayOff(base, date)) continue
        val dueTime = if (date.dayOfWeek == DayOfWeek.SUNDAY) 20 * 60 + 30 else 22 * 60
        val due = date.atTime(dueTime / 60, dueTime % 60).atZone(zone)
        if (!due.isAfter(now)) continue
        val lectureCycles = auto.filter { it.due.toLocalDate() == date }
        val coveredMods = lectureCycles.map { c -> mods.firstOrNull { c.title.contains(it.take(6), ignoreCase = true) } }.filterNotNull()
        var count = lectureCycles.size
        val perMod = HashMap<String, Int>().also { m -> coveredMods.forEach { m[it] = (m[it] ?: 0) + 1 } }
        val order = mods.indices.map { mods[(it + n) % mods.size] }
        fun add(titleMod: String) {
            tasks += DueWork("$titleMod: notes and problems", hoursEach, max(1, Math.round(hoursEach / 1.25).toInt()), due); perMod[titleMod] = (perMod[titleMod] ?: 0) + 1; count++
        }
        while (count < floor) {
            val m = order.firstOrNull { (perMod[it] ?: 0) < inst.int("module_cap") } ?: break
            add(m)
        }
        val stretchTo = if (weekend && inst.str("weekend") == "lower") floor else inst.int("slots_stretch")
        while (count < stretchTo && !dropStretch) {
            val m = order.firstOrNull { (perMod[it] ?: 0) < inst.int("module_cap") } ?: break
            add(m)
            if (inst.str("stretch_if_negative") == "drop") {
                val b = bufferNow(now, studyCycles(base, now) + work + tasks, base, false)
                if (b is BufferState.Value && b.result.negative) { tasks.removeLast(); count--; perMod[m] = (perMod[m] ?: 1) - 1; dropStretch = true; dropped++ }
            }
        }
    }
    lines += "${tasks.size} study slot${if (tasks.size == 1) "" else "s"} added by module, at least ${inst.int("slots_floor")} a day counting lecture cycles, each ${fmt1(hoursEach)}h${if (inst.str("intensity") == "rapid") " (rapid notes)" else ""}."
    if (dropped > 0) problems += "The stretch (${inst.int("slots_stretch")} a day) did not fit, so it was dropped from the day it first went over. The floor stands."
    return Compiled(emptyList(), tasks, emptyList(), lines, emptyList(), emptyList())
        .let { c -> if (problems.isEmpty()) c else c.copy(lines = c.lines + problems) }
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
    val slots = ArrayList<PatternSlot>(); val loosening = ArrayList<String>(); val lines = ArrayList<String>()
    var lecturesRemoved = 0; var workEased = 0
    val maxMin = (inst.dbl("work_h_max") * 60).toInt()
    for (date in inst.dates()) {
        if (isDayOff(base, date)) continue
        val day = slotsFor(base, date, zone)
        for (ds in day.filter { it.slot.date == null && (it.slot.type == "lecture" || it.slot.type == "tutorial") }) {
            slots += ds.slot.copy(date = date, cancelled = true, notes = "lecture-free period"); lecturesRemoved++
        }
        var used = 0
        for (ds in day.filter { it.slot.date == null && it.slot.type in CUTTABLE_WORK }.sortedBy { it.slot.startMin }) {
            val len = ds.slot.endMin - ds.slot.startMin
            if (used + len <= maxMin) used += len
            else { slots += PatternSlot(date.dayOfWeek, ds.slot.startMin, ds.slot.endMin, "Free time (lighter period)", "free", "", false, MOD_NOTE + "Eased in a lighter period.", date = date); workEased += len }
        }
    }
    val mods = inst.str("modules").split("|").map { it.trim() }.filter { it.isNotEmpty() }
    val tasks = ArrayList<DueWork>()
    for ((n, date) in inst.dates().withIndex()) {
        if (isDayOff(base, date) || mods.isEmpty()) continue
        val due = date.atTime(22, 0).atZone(zone); if (!due.isAfter(now)) continue
        repeat(inst.int("cycles_per_day")) { k -> tasks += DueWork("${mods[(n + k) % mods.size]}: notes and problems", STUDY_CYCLE_STANDARD_HOURS, 2, due) }
    }
    if (lecturesRemoved > 0) loosening += "$lecturesRemoved lecture${if (lecturesRemoved == 1) "" else "s"} and tutorial${if (lecturesRemoved == 1) "" else "s"} are removed from ${label(inst.start)} to ${label(inst.end)}"
    if (workEased > 0) loosening += "${fmt1(workEased / 60.0)}h of work is turned into free time"
    lines += "No lectures from ${label(inst.start)} to ${label(inst.end)}. Work is at most ${fmt1(maxMin / 60.0)}h a day, and the freed time is free."
    if (tasks.isNotEmpty()) lines += "${tasks.size} study slot${if (tasks.size == 1) "" else "s"} by module, ${inst.int("cycles_per_day")} a day."
    return Compiled(slots, tasks, emptyList(), lines, loosening, emptyList())
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
fun checkPlan(base: List<PatternSlot>, c: Compiled, inst: Instance, capMin: Int? = null, zone: ZoneId = LONDON): List<String> {
    val after = applied(base, c)
    val out = ArrayList<String>()
    val dates = (inst.dates() + c.slots.mapNotNull { it.date }).distinct()
    fun key(d: DatedSlot) = Triple(d.slot.title, d.slot.startMin, d.slot.endMin)
    for (date in dates) {
        val before = slotsFor(base, date, zone); val now = slotsFor(after, date, zone)
        val prot0 = before.filter { it.slot.type in PROTECTED_TYPES || it.slot.hardAlarm }.map(::key).toSet(); val prot1 = now.filter { it.slot.type in PROTECTED_TYPES || it.slot.hardAlarm }.map(::key).toSet()
        if (!prot1.containsAll(prot0)) out += "${label(date)}: a protected block (sleep, a meal or a hard alarm) would be changed."
        val rout0 = before.filter { it.slot.type in ROUTINE_TYPES }.map(::key).toSet(); val rout1 = now.filter { it.slot.type in ROUTINE_TYPES }.map(::key).toSet()
        if (!rout1.containsAll(rout0)) out += "${label(date)}: the daily routine or a chore would be removed."
        val busy = now.filter { it.slot.type != "free" }.sortedBy { it.slot.startMin }
        for ((a, b) in busy.zipWithNext()) if (b.slot.startMin < a.slot.endMin && a.slot.endMin > a.slot.startMin) out += "${label(date)}: ${a.slot.title} overlaps ${b.slot.title}."
    }
    c.slots.filter { !it.cancelled && it.date in c.dayOffDates && it.type in setOf("study", "code", "lecture", "tutorial", "planning") && !it.notes.startsWith(MOD_NOTE) }
        .forEach { out += "${it.date?.let(::label)}: ${it.title} is placed on a day off." }
    for (s in c.slots.filter { !it.cancelled }) {
        if (s.endMin - s.startMin < MIN_PIECE_MIN || s.endMin - s.startMin > 600) out += "${s.title} on ${s.date?.let(::label)} has a length that is not allowed."
        if (s.type != "free" && (s.startMin < 6 * 60 || s.endMin > 23 * 60)) out += "${s.title} on ${s.date?.let(::label)} would be in the night."
    }
    if (capMin != null) c.slots.filter { it.type == "study" && !it.cancelled }.groupBy { it.date }.forEach { (date, l) -> if (l.sumOf { it.endMin - it.startMin } > capMin) out += "${date?.let(::label)}: more than the daily cap of ${fmt1(capMin / 60.0)}h is placed." }
    return out.distinct()
}

/** What applying a preset would do, with its cost, before anything is written (IM5, C4, H2). */
data class ModPreview(val compiled: Compiled, val before: BufferState, val after: BufferState, val problems: List<String>, val wallNeeded: Boolean, val negative: Boolean) {
    val canApply get() = problems.isEmpty()
}

fun previewOf(inst: Instance, base: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId = LONDON): ModPreview {
    val invalid = validate(inst, now.toLocalDate())
    if (invalid.isNotEmpty()) return ModPreview(Compiled(), bufferNow(now, studyCycles(base, now) + work, base, false), BufferState.NoDeadline, invalid, false, false)
    val c = compile(inst, base, work, now, zone)
    val cap = when (inst.preset) { Preset.ASSIGNMENT, Preset.FOCUS -> (inst.dbl("daily_cap_h") * 60).toInt(); else -> null }
    val rails = checkPlan(base, c, inst, cap, zone)
    val after = applied(base, c)
    val demand = (if (c.noDemand) emptyList() else dayOffDemand(base, c.dayOffDates, now, zone)).map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    val before = bufferNow(now, studyCycles(base, now) + work, base, false)
    val afterBuffer = bufferNow(now, studyCycles(after, now) + work + c.tasks + demand, after, false)
    val negative = afterBuffer is BufferState.Value && afterBuffer.result.negative
    val lines = c.lines + (if (negative && afterBuffer is BufferState.Value) listOf("This makes the Buffer NEGATIVE, so the work no longer fits:") + afterBuffer.result.working().lines() else emptyList())
    return ModPreview(c.copy(lines = lines), before, afterBuffer, rails + c.problems.filter { false }, c.loosening.isNotEmpty(), negative)
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
            out["slots_floor"] = Rec(floor.toString(), "${fmt1(backlogH)}h of missed work over $days days at ${fmt1(slotH)}h a slot needs about $floor a day.")
            out["slots_stretch"] = Rec((floor + 1).coerceAtMost(5).toString(), "One more than the floor on a good day.")
            val rapid = backlogH > days * 4 * slotH
            out["intensity"] = Rec(if (rapid) "rapid" else "full", if (rapid) "The backlog is more than 4 full slots a day can clear in $days days, so rapid notes." else "The backlog fits in full slots.")
        }
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
    Instance(Preset.of(lines.getValue("preset"))!!, LocalDate.parse(lines.getValue("start")), LocalDate.parse(lines.getValue("end")), settings, items.values.toList())
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
    val periodic = inst.preset == Preset.PERIOD_OFF || inst.preset == Preset.LECTURE_FREE || inst.preset == Preset.CATCH_UP || (inst.preset == Preset.FOCUS && inst.bool("hybrid"))
    if (periodic && active.any { it.preset == inst.preset && !it.end.isBefore(inst.start) && !it.start.isAfter(inst.end) })
        out += "${inst.preset.label} already covers some of these days. Remove it first or choose other dates."
    if (inst.preset == Preset.PERIOD_OFF && active.any { it.preset == Preset.LECTURE_FREE && !it.end.isBefore(inst.start) && !it.start.isAfter(inst.end) })
        out += "A lecture-free period already covers some of these days. Remove it first or choose other dates."
    if (inst.preset == Preset.ASSIGNMENT || inst.preset == Preset.FOCUS) {
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
    val kept = stored.filter { s ->
        val date = s.date
        if (s.cancelled || s.type == "free" || date == null) return@filter true
        if (!allowOnDayOff && isDayOff(core, date)) { dropped++; return@filter false }
        // work blocks and free time are meant to be cut by a session; anything else (an event, a meal, sleep, a lecture still running) is not
        val clash = slotsFor(core + cancels, date, zone).any { it.slot.type != "free" && it.slot.type !in CUTTABLE_WORK && s.startMin < it.slot.endMin && it.slot.startMin < s.endMin }
        if (clash) { dropped++; false } else true
    }
    return Reconciled(kept, dropped)
}

/** Whether a modifier's own slots may sit on days off: a period off's pinned blocks, and a hybrid focus week. */
fun allowsDaysOff(inst: Instance?): Boolean = inst != null && (inst.preset == Preset.PERIOD_OFF || (inst.preset == Preset.FOCUS && inst.bool("hybrid")))
