package efa

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Work a file adds. It is stored as a task, so it reaches the Buffer and its working the same way quick-add does (IM7). */
data class NewWork(val key: String, val title: String, val hours: Double, val heaviness: Int, val due: ZonedDateTime, val source: String)

/** One overlay slot to store with the fingerprint of the row that made it. */
data class OverlayItem(val slot: PatternSlot, val rowHash: String)

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
    val adds: Int, val cancels: Int,
) {
    val ok: Boolean get() = errors.isEmpty()
    val needsWall: Boolean get() = loosening.isNotEmpty()
    val changesNothing: Boolean get() = overlay.isEmpty() && work.isEmpty() && periods.isEmpty()
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Checks [parsed] against the calendar as it is now (IM4, IM5, IM6). [known] holds the fingerprints of rows already imported (IM8).
 * [work] is the work due now, so the Buffer before and after can be shown. Nothing here writes anything.
 */
fun verifyImport(
    parsed: ParsedImport, slots: List<PatternSlot>, known: Set<String>, work: List<DueWork>,
    now: ZonedDateTime, zone: ZoneId = LONDON,
): Verification {
    val errors = ArrayList(parsed.errors); val warnings = ArrayList(parsed.warnings)
    val overlay = ArrayList<OverlayItem>(); val newWork = ArrayList<NewWork>()
    val periods = ArrayList<PeriodRow>(); val periodHashes = ArrayList<String>()
    val loosening = ArrayList<String>()
    var already = 0; var adds = 0; var cancels = 0
    var cur = slots

    fun slotText(s: PatternSlot) = "${s.title} ${minutesText(s.startMin)}-${minutesText(s.endMin)}"

    val events = parsed.rows.filterIsInstance<EventRow>()
    // Cancels first, so a moved lecture can be one file: cancel the old time, add the new one (IM6).
    for (r in events.filter { it.cancel }) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val hit = slotsFor(cur, r.date, zone).firstOrNull { it.slot.startMin == r.startMin && it.slot.type == r.type }
        if (hit == null) { errors += ImportIssue(r.line, "start", "No ${r.type} starts at ${minutesText(r.startMin)} on ${r.date} to cancel."); continue }
        if (hit.slot.date != null) { errors += ImportIssue(r.line, "start", "That ${r.type} came from an earlier file. Remove that import instead of cancelling it."); continue }
        if (!hit.slot.title.contains(r.title, ignoreCase = true) && !r.title.contains(hit.slot.title, ignoreCase = true)) warnings += ImportIssue(r.line, "title", "The ${r.type} at that time is \"${hit.slot.title}\". It is the one cancelled.")
        val cancel = hit.slot.copy(date = r.date, cancelled = true, notes = "imported cancel")
        overlay += OverlayItem(cancel, h); cur = cur + cancel; cancels++
        loosening += "Cancels ${hit.slot.title} on ${DAY.format(r.date)}"
    }
    for (r in events.filter { !it.cancel }) {
        val h = r.hash()
        if (h in known) { already++; continue }
        val here = slotsFor(cur, r.date, zone)
        if (here.any { it.slot.date == null && it.slot.type == r.type && it.slot.startMin == r.startMin && it.slot.endMin == r.endMin }) {
            warnings += ImportIssue(r.line, "start", "${r.title} is already in your weekly pattern at that time, skipped."); continue
        }
        var bad = false
        for (o in here.filter { it.slot.startMin < r.endMin && it.slot.endMin > r.startMin }) {
            when {
                o.slot.type == "free" -> {}
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
        newWork += NewWork("deadline:$h", r.title, quickAddHours(r.heaviness), r.heaviness, due, "import")
    }
    for (r in parsed.rows.filterIsInstance<PeriodRow>()) {
        val h = r.hash()
        if (h in known) { already++; continue }
        periods += r; periodHashes += h
    }

    val extra = newWork.map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    val before = bufferNow(now, studyCycles(slots, now) + work, slots, shortSleep = false)
    val after = if (errors.isNotEmpty()) before else bufferNow(now, studyCycles(cur, now) + work + extra, cur, shortSleep = false)
    return Verification(
        errors, warnings, overlay, newWork, periods, periodHashes, already, parsed.skippedPast, loosening.distinct(),
        before, after, adds, cancels,
    )
}

/** The plain lines the preview shows (IM5). */
fun Verification.summaryLines(): List<String> = buildList {
    if (adds > 0) add("$adds event${if (adds == 1) "" else "s"} added")
    if (cancels > 0) add("$cancels cancelled")
    val deadlines = work.count { it.source == "import" }
    val cycles = work.count { it.source == "import-cycle" }
    if (deadlines > 0) add("$deadlines deadline${if (deadlines == 1) "" else "s"} added")
    if (cycles > 0) add("$cycles study block${if (cycles == 1) "" else "s"} due for new lectures")
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

