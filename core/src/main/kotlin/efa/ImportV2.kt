package efa

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * IX2: two modes, chosen in the app and never by the file, so a file from an AI cannot take over the plan by itself (IM5).
 * ADD lays the file's events over what is there. REPLACE makes the file the plan for its days.
 */
enum class ImportMode(val label: String) { ADD("Add to my plan"), REPLACE("Replace my plan for these days") }

/**
 * IX4: what REPLACE takes away on the days of the file: free time that is not protected, project and code blocks, the evening work blocks and planning. What always stays: sleep and
 * the shutdown, meals, the morning routine and chores, lectures and the study cycles they made (those are obligations), football, church, the gym, paid sessions, anything marked
 * hard, protected free time (G3) and days off. Blocks that came from an earlier file are not touched.
 */
val REPLACEABLE_TYPES = setOf("free", "code", "study", "planning")

val REPLACED_WORK_TYPES = setOf("code", "study", "planning")

/** The dates a file gives its own events to. A cancel and a reminder do not make a date. */
fun importDates(parsed: ParsedImport): Set<LocalDate> =
    parsed.rows.filterIsInstance<EventRow>().filter { !it.cancel && it.type != REMINDER_TYPE }.map { it.date }.toSet()

/** The blocks REPLACE cancels, one overlay item each, with a fingerprint that is stable so importing the same file twice changes nothing (IM8). */
fun replaceOverlay(dates: Set<LocalDate>, slots: List<PatternSlot>, zone: ZoneId = LONDON, keeps: List<KeepRow> = emptyList()): List<OverlayItem> {
    val prot = dates.map { weekStart(it) }.distinct().flatMap { protectedFree(slots, it, zone) }.toSet()
    return dates.sorted().filter { !isDayOff(slots, it) }.flatMap { date ->
        slotsFor(slots, date, zone).filter { d ->
            val s = d.slot
            s.date == null && !s.hardAlarm && s.type in REPLACEABLE_TYPES && !(s.type == "free" && (date to s.startMin) in prot) &&
                keeps.none { it.date == date && s.startMin < it.endMin && s.endMin > it.startMin }          // IX6: a keep row protects its time from a Replace
        }.map { d -> OverlayItem(d.slot.copy(date = date, cancelled = true, notes = "imported replace"), "span|$date|${d.slot.startMin}|${d.slot.type}") }
    }
}

/**
 * IX1 to IX5: the verification of a file in a mode. ADD is [verifyImport]. REPLACE cancels the blocks of [replaceOverlay] first, so the file's events stand in their place, and
 * says what it took: the Buffer is worked out before (the plan as it was) and after (with the replacement), so any work given up shows as a number. If any work block is taken
 * the file loosens and needs the wall (IX5, amending IM6); if only free time is taken it does not.
 */
fun verifyImportIn(
    mode: ImportMode, parsed: ParsedImport, slots: List<PatternSlot>, known: Set<String>, work: List<DueWork>, now: ZonedDateTime,
    zone: ZoneId = LONDON, hoursFor: (Int) -> Double = ::quickAddHours, reflow: Boolean = false,
): Verification {
    if (mode == ImportMode.ADD) return verifyImport(parsed, slots, known, work, now, zone, hoursFor, reflow).let { if (reflow && it.ok) reflowEvents(it, parsed, slots, known, now, zone) else it }
    val keeps = parsed.rows.filterIsInstance<KeepRow>()
    // RE1: a block the file itself cancels (a cancel row, an override) is left to that row. Taking it here first would leave the row with nothing to find, and the file would fail only in this mode.
    val explicit = parsed.rows.filterIsInstance<EventRow>().filter { it.cancel }.map { Triple(it.date, it.startMin, it.type) }.toSet()
    val cancels = replaceOverlay(importDates(parsed), slots, zone, keeps).filter { it.rowHash !in known && Triple(it.slot.date!!, it.slot.startMin, it.slot.type) !in explicit }
    val withCancels = slots + cancels.map { it.slot }
    val v0 = verifyImport(parsed, withCancels, known, work, now, zone, hoursFor, reflow)
    val v = if (reflow && v0.ok) reflowEvents(v0, parsed, withCancels, known, now, zone) else v0
    if (!v.ok) return v
    val taken = cancels.filter { it.slot.type in REPLACED_WORK_TYPES }
    val takenH = taken.sumOf { it.slot.endMin - it.slot.startMin } / 60.0
    val freed = cancels.filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin } / 60.0
    val before = bufferNow(now, studyCycles(slots, now) + work, slots, shortSleep = false)
    val loosening = v.loosening + (if (taken.isNotEmpty()) listOf("Replaces ${fmt1(takenH)}h of work blocks on ${importDates(parsed).sorted().joinToString(", ") { it.dayOfMonth.toString() + " " + it.month.name.take(3).lowercase() }}") else emptyList())
    val warnings = v.warnings + (if (freed > 0) emptyList() else emptyList())
    return v.copy(overlay = cancels + v.overlay, loosening = loosening.distinct(), warnings = warnings, before = before, replaced = v.replaced + cancels.size)
}

/** IX5: a REPLACE that takes work is a loosening; one that only moves or adds is a move; ADD that only adds is an add; ADD that loosens is a loosening. */
fun importChangeClass(v: Verification, mode: ImportMode): ChangeClass = when {
    v.needsWall -> ChangeClass.LOOSEN
    mode == ImportMode.REPLACE && v.replaced > 0 -> ChangeClass.MOVE
    v.adds == 0 && v.cancels == 0 && v.work.isEmpty() && v.periods.isEmpty() -> ChangeClass.REALITY
    else -> ChangeClass.ADD
}

/** CE9: the meter, for Setup, Modify and the import preview. It is never on the home page. */
fun economyMeter(history: List<ChangeEntry>, now: java.time.Instant, limits: ChangeLimits = ChangeLimits(), zone: ZoneId = LONDON): String {
    val from = weekStart(now, zone)
    val week = history.filter { !it.at.isBefore(from) && !it.at.isAfter(now) }
    val imports = week.count { it.import }; val moves = week.count { it.cls == ChangeClass.MOVE }; val loosens = week.count { it.cls == ChangeClass.LOOSEN }
    return "This week: imports $imports of ${limits.freeImportsPerWeek} free, moves $moves of ${limits.freeMovesPerWeek} free, loosening $loosens of ${limits.lockAtLoosen - 1}."
}

/**
 * IX1: a file's events go through the same engine as an event added in Modify Schedule. For each date with new events the day is laid out again around all of them together: a meal, the routine or a
 * chore gives way inside its window, work and free time slide, and what cannot fit is said. The event itself keeps its venue and its hard alarm. What the engine has to shorten comes back as a loosening
 * (and so the wall), and a problem it cannot solve is a warning on the preview, never silence.
 */
fun reflowEvents(v: Verification, parsed: ParsedImport, base: List<PatternSlot>, known: Set<String>, now: ZonedDateTime, zone: ZoneId = LONDON): Verification {
    val events = parsed.rows.filterIsInstance<EventRow>().filter { !it.cancel && it.type != REMINDER_TYPE && it.hash() !in known }
    if (events.isEmpty()) return v
    val overlay = v.overlay.toMutableList(); val loosening = v.loosening.toMutableList(); val warnings = v.warnings.toMutableList()
    for ((date, evs) in events.groupBy { it.date }) {
        if (isDayOff(base, date)) continue
        val prot = setOf(weekStart(date)).flatMap { protectedFree(base, it, zone) }.toSet()
        val nowMin = if (date == now.toLocalDate()) now.hour * 60 + now.minute else 0
        val b = replanDay(base, date, Disturbance.Several(evs.map { Disturbance.TimeTaken(it.startMin, it.endMin, it.title, it.type, nowMin) }), prot, zone)
        val originals = evs.associateBy { it.hash() }
        val kept = overlay.filter { item -> item.slot.date == date && !item.slot.cancelled && item.rowHash in originals.keys }
        if (kept.size != evs.size) continue                                    // an event that was not placed as a plain add is left as verifyImport made it
        overlay.removeAll(kept.toSet())
        overlay += kept
        var n = 0
        for (s in b.slots) {
            val isEvent = !s.cancelled && s.date == date && evs.any { it.title == s.title && it.startMin == s.startMin && it.endMin == s.endMin }
            if (isEvent) continue
            overlay += OverlayItem(s, "reflow|$date|${s.title}|${s.startMin}|${if (s.cancelled) "c" else "a"}|${n++}")
        }
        b.dropped.forEach { loosening += it }
        b.problems.forEach { warnings += ImportIssue(0, "", "Not fully planned: $it") }
    }
    val before = v.before
    val cur = base + overlay.map { it.slot }
    val extra = v.work.map { DueWork(it.title, it.hours, it.heaviness, it.due) }
    val after = bufferNow(now, studyCycles(cur, now) + extra, cur, shortSleep = false)
    return v.copy(overlay = overlay, loosening = loosening.distinct(), warnings = warnings, before = before, after = after)
}
