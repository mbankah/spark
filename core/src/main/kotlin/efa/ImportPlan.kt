package efa

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Work a file adds. It is stored as a task, so it reaches the Buffer and its working the same way quick-add does (IM7). */
data class NewWork(val key: String, val title: String, val hours: Double, val heaviness: Int, val due: ZonedDateTime, val source: String)

/** One overlay slot to store with the fingerprint of the row that made it. */
data class OverlayItem(val slot: PatternSlot, val rowHash: String)

/** IX6: a modifier a file carries, checked against the calendar and ready to apply, with the fingerprint of its row (IM8). */
data class ImportedModifier(val instance: Instance, val hash: String, val line: Int)

/** Types the engine can move around an event (IX1): a meal, the routine, a chore. Study cycles, lectures, sleep and anything hard are never moved by a file. */
val REFLOWABLE_TYPES = setOf("meal", "routine", "chore")

/**
 * What a file would do, worked out without changing anything (IM5). [loosening] lists the reasons it needs the typed wall (IM6);
 * it is empty for a file that only adds. [before] and [after] are the Buffer now and with the file applied.
 */
data class Verification(
    val errors: List<ImportIssue>, val warnings: List<ImportIssue>,
    val overlay: List<OverlayItem>, val work: List<NewWork>, val periods: List<PeriodRow>, val periodHashes: List<String>,
    val alreadyImported: Int, val skippedPast: Int,
    val loosening: List<String>,
    val before: BufferState, val after: BufferState,
    val adds: Int, val cancels: Int, val replaced: Int = 0, val modifiers: List<ImportedModifier> = emptyList(),
    val overrides: List<String> = emptyList(),
) {
    val ok: Boolean get() = errors.isEmpty()
    val needsWall: Boolean get() = loosening.isNotEmpty()
    val changesNothing: Boolean get() = overlay.isEmpty() && work.isEmpty() && periods.isEmpty() && modifiers.isEmpty()
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Checks [parsed] against the calendar as it is now (IM4, IM5, IM6). [known] holds the fingerprints of rows already imported (IM8).
 * [work] is the work due now, so the Buffer before and after can be shown. Nothing here writes anything.
 */
fun verifyImport(
    parsed: ParsedImport, slots: List<PatternSlot>, known: Set<String>, work: List<DueWork>,
    now: ZonedDateTime, zone: ZoneId = LONDON, hoursFor: (Int) -> Double = ::quickAddHours, reflow: Boolean = false,
): Verification {
    val errors = ArrayList(parsed.errors); val warnings = ArrayList(parsed.warnings)
    val overlay = ArrayList<OverlayItem>(); val newWork = ArrayList<NewWork>()
    val periods = ArrayList<PeriodRow>(); val periodHashes = ArrayList<String>()
    val loosening = ArrayList<String>()
    var already = 0; var adds = 0; var cancels = 0; var replaced = 0
    val overrides = ArrayList<String>()
    var cur = slots

    fun slotText(s: PatternSlot) = "${s.title} ${minutesText(s.startMin)}-${minutesText(s.endMin)}"

    val events = parsed.rows.filterIsInstance<EventRow>()
    // RP2: a replace row takes away everything the calendar holds in its window, so the events of the file can stand in its place. It is a loosening and needs the wall.
    for (r in parsed.rows.filterIsInstance<ReplaceRow>()) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val hit = slotsFor(cur, r.date, zone).filter { it.slot.startMin < r.endMin && it.slot.endMin > r.startMin && it.slot.type != REMINDER_TYPE }
        if (hit.any { it.slot.title == "Wake, shower" }) { errors += ImportIssue(r.line, "end", "The window takes in the wake. The morning alarm is planned from it, so end the window before it."); continue }
        if (hit.any { it.slot.date != null }) { errors += ImportIssue(r.line, "start", "Part of that time came from an earlier file. Remove that import first, then replace."); continue }
        // Each stored row needs its own fingerprint (the store keeps one row per fingerprint); the first carries the row's own, so importing it again is recognised (IM8).
        for ((n, o) in hit.withIndex()) {
            val cancel = o.slot.copy(date = r.date, cancelled = true, notes = "imported replace")
            overlay += OverlayItem(cancel, if (n == 0) h else "$h#${n + 1}"); cur = cur + cancel; replaced++
        }
        if (hit.isNotEmpty()) loosening += "Replaces ${hit.joinToString(", ") { it.slot.title.substringBefore(" - ") }} on ${DAY.format(r.date)}, ${minutesText(r.startMin)} to ${minutesText(r.endMin)}"
        else warnings += ImportIssue(r.line, "start", "Nothing is planned in that time, so nothing is replaced.")
    }
    // Cancels first, so a moved lecture can be one file: cancel the old time, add the new one (IM6).
    for (r in events.filter { it.cancel }) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val hit = slotsFor(cur, r.date, zone).firstOrNull { it.slot.startMin == r.startMin && it.slot.type == r.type }
        if (hit == null) { errors += ImportIssue(r.line, "start", "No ${r.type} starts at ${minutesText(r.startMin)} on ${r.date} to cancel."); continue }
        if (hit.slot.date != null) { errors += ImportIssue(r.line, "start", "That ${r.type} came from an earlier file. Remove that import instead of cancelling it."); continue }
        if (r.override && neverOverridden(hit.slot.title)) { errors += ImportIssue(r.line, "override", "${hit.slot.title} is never overridden: the alarm is planned from the wake, and the Bible study comes first."); continue }
        if (!hit.slot.title.contains(r.title, ignoreCase = true) && !r.title.contains(hit.slot.title, ignoreCase = true)) warnings += ImportIssue(r.line, "title", "The ${r.type} at that time is \"${hit.slot.title}\". It is the one cancelled.")
        val cancel = hit.slot.copy(date = r.date, cancelled = true, notes = if (r.override) "imported override" else "imported cancel")
        overlay += OverlayItem(cancel, h); cur = cur + cancel; cancels++
        loosening += "Cancels ${hit.slot.title} on ${DAY.format(r.date)}"
        if (r.override) {
            // OV1: it is named, it needs the wall, and a study cycle it takes away comes back as work due the next working day, so the Buffer still carries it (the same rule as a move, AD2).
            overrides += "${hit.slot.title} (${hit.slot.type}) on ${DAY.format(r.date)}"
            loosening += "Overrides ${hit.slot.title} on ${DAY.format(r.date)}"
            val std = hit.slot.type == "study-standard"
            if (std || hit.slot.type == "study-light") {
                val due = endOfDayCutoff(cur, nextWorkingDay(cur, r.date), zone)
                if (due.isAfter(now)) newWork += NewWork("override:$h", "Study: ${hit.slot.title.substringBefore(" - ")} (came back)", if (std) STUDY_CYCLE_STANDARD_HOURS else STUDY_CYCLE_LIGHT_HOURS, if (std) 6 else 3, due, "import-override")
            }
        }
    }
    for (r in events.filter { !it.cancel }) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val here = slotsFor(cur, r.date, zone)
        if (here.any { it.slot.date == null && it.slot.type == r.type && it.slot.startMin == r.startMin && it.slot.endMin == r.endMin }) {
            warnings += ImportIssue(r.line, "start", "${r.title} is already in your weekly pattern at that time, skipped."); continue
        }
        var bad = false
        for (o in here.filter { r.type != REMINDER_TYPE && it.slot.type != REMINDER_TYPE && it.slot.startMin < r.endMin && it.slot.endMin > r.startMin }) {
            when {
                o.slot.type == "free" -> {}
                reflow && o.slot.type in REFLOWABLE_TYPES -> {}                       // IX1: the engine lays the day out around the event, so a meal or the routine gives way
                o.slot.type in CUTTABLE_WORK -> loosening += "${r.title} on ${DAY.format(r.date)} takes time from ${o.slot.title}"
                else -> { errors += ImportIssue(r.line, "start", "Overlaps ${slotText(o.slot)} on ${r.date}. Change the time, or cancel that one in the same file if it has moved."); bad = true }
            }
        }
        if (bad) continue
        val slot = PatternSlot(r.date.dayOfWeek, r.startMin, r.endMin, r.title, r.type, r.venue, r.type == "mytutor", r.notes.ifBlank { "Imported." }, date = r.date)
        overlay += OverlayItem(slot, h); cur = cur + slot; adds++
        if (r.study != Study.NONE) {
            val std = r.study == Study.STANDARD
            val due = endOfDayCutoff(cur, r.date, zone)
            if (due.isAfter(now)) newWork += NewWork("cycle:$h", "Study: ${r.title}", if (std) STUDY_CYCLE_STANDARD_HOURS else STUDY_CYCLE_LIGHT_HOURS, if (std) 6 else 3, due, "import-cycle")
        }
    }
    for (r in parsed.rows.filterIsInstance<DeadlineRow>()) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val due = r.date.atTime(r.dueMin / 60, r.dueMin % 60).atZone(zone)
        if (!due.isAfter(now)) { warnings += ImportIssue(r.line, "start", "That time has already passed, skipped."); continue }
        newWork += NewWork("deadline:$h", r.title, hoursFor(r.heaviness), r.heaviness, due, "import")
    }
    for (r in parsed.rows.filterIsInstance<PeriodRow>()) {
        val h = r.hash()
        if (h in known) { already++; continue }
        periods += r; periodHashes += h
    }

    // IX6: modifiers the file carries are checked like any modifier in the app: the same preview, the same guard rails, the same loosening. They are checked in the order
    // the file gives them, each on top of the ones before it and of the file's own events, and so is the app's apply: a catch-up row that cancels the lectures comes before a
    // night row that would otherwise be refused for waking after a lecture. Work the file itself adds (its deadlines) counts as backlog for the rows after it.
    val mods = ArrayList<ImportedModifier>()
    var modBase = cur
    var modWork = work + newWork.map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    for (r in parsed.rows.filterIsInstance<ModifierImport>()) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val clash = modifierConflicts(r.instance, mods.map { it.instance })
        if (clash.isNotEmpty()) { errors += ImportIssue(r.line, "", clash.joinToString(" ")); continue }
        val pv = previewOf(r.instance, modBase, modWork, now, zone)
        if (!pv.canApply) { errors += ImportIssue(r.line, "", pv.problems.joinToString(" ")); continue }
        loosening += pv.compiled.loosening
        mods += ImportedModifier(r.instance, h, r.line)
        modBase = applied(modBase, pv.compiled); modWork = modWork + pv.compiled.tasks
    }
    cur = modBase

    val extra = newWork.map { DueWork(it.title, it.hours, it.heaviness, it.due) } + modWork.drop(work.size + newWork.size)          // what a modifier sets aside comes back as work, and counts
    val before = bufferNow(now, studyCycles(slots, now) + work, slots, shortSleep = false)
    val after = if (errors.isNotEmpty()) before else bufferNow(now, studyCycles(cur, now) + work + extra, cur, shortSleep = false)
    return Verification(
        errors, warnings, overlay, newWork, periods, periodHashes, already, parsed.skippedPast, loosening.distinct(),
        before, after, adds, cancels, replaced, mods, overrides,
    )
}

/** The plain lines the preview shows (IM5). */
fun Verification.summaryLines(): List<String> = buildList {
    if (adds > 0) add("$adds event${if (adds == 1) "" else "s"} added")
    if (cancels > 0) add("$cancels cancelled")
    if (replaced > 0) add("$replaced planned block${if (replaced == 1) "" else "s"} replaced")
    val deadlines = work.count { it.source == "import" }
    val cycles = work.count { it.source == "import-cycle" }
    val back = work.count { it.source == "import-override" }
    if (deadlines > 0) add("$deadlines deadline${if (deadlines == 1) "" else "s"} added")
    if (cycles > 0) add("$cycles study block${if (cycles == 1) "" else "s"} due for new lectures")
    if (overrides.isNotEmpty()) add("${overrides.size} protected block${if (overrides.size == 1) "" else "s"} overridden: ${overrides.joinToString(", ")}")
    if (back > 0) add("$back study block${if (back == 1) "" else "s"} come back as work, so nothing disappears")
    if (modifiers.isNotEmpty()) add(modifiers.joinToString(", ") { "${it.instance.preset.label} ${DAY.format(it.instance.start)}${if (it.instance.end != it.instance.start) " to ${DAY.format(it.instance.end)}" else ""}" })
    if (periods.isNotEmpty()) add(periods.joinToString(", ") { "${it.kind.label} ${DAY.format(it.start)} to ${DAY.format(it.end)}" })
    if (alreadyImported > 0) add("$alreadyImported row${if (alreadyImported == 1) "" else "s"} already imported, skipped")
    if (skippedPast > 0) add("$skippedPast row${if (skippedPast == 1) "" else "s"} in the past, skipped")
    if (changesNothing) add("Nothing in this file changes anything")
    add("Buffer now: ${bufferText(before)}")
    add("Buffer with this file: ${bufferText(after)}")
}

fun bufferText(b: BufferState): String = when (b) {
    BufferState.NoDeadline -> "no deadline"
    is BufferState.Value -> b.result.label().removePrefix("Buffer ") + (b.result.deadlineLabel?.let { " to $it" } ?: "")
}

