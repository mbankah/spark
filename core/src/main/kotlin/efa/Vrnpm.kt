package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

// ---------- VR1 to VR5: the study loop, V R N P M, as a timer ----------
// One cycle in the order V video, R read, N notes, P practice, M marking, with the spec's breaks between them. Every step has a time. A step may overrun and never stops by itself,
// but the overrun is always shown, as a count up, and kept. The N time is a plan (pages times minutes a page) and is tuned by timing real pages.

enum class VrStep(val letter: Char, val label: String, val minutes: Int) {
    V('V', "Video", 10), R('R', "Read", 5), N('N', "Notes", 45), P('P', "Practice", 30), M('M', "Marking", 15),
}

/** One stretch of the cycle: a step, or the break after the step before it ([step] is null for a break). */
data class VrPhase(val step: VrStep?, val plannedMin: Int) {
    val isBreak: Boolean get() = step == null
    val title: String get() = step?.let { "${it.letter} ${it.label}" } ?: "Break"
}

private fun round5(x: Double): Int = (Math.round(x / 5.0) * 5).toInt()

/** The optimal cycle is 105 minutes in the steps (V10 R5 N45 P30 M15). The breaks are 5 minutes between the steps and 10 after N, the longest. */
fun vrPhases(nMinutes: Int = VrStep.N.minutes): List<VrPhase> = listOf(
    VrPhase(VrStep.V, VrStep.V.minutes), VrPhase(null, 5),
    VrPhase(VrStep.R, VrStep.R.minutes), VrPhase(null, 5),
    VrPhase(VrStep.N, nMinutes), VrPhase(null, 10),
    VrPhase(VrStep.P, VrStep.P.minutes), VrPhase(null, 5),
    VrPhase(VrStep.M, VrStep.M.minutes),
)

/** The N step from timed pages: pages times the minutes a page, to the nearest 5, kept between 20 and 90. */
fun suggestedN(pages: Int, avgPageMs: Long): Int = round5(pages * avgPageMs / 60_000.0).coerceIn(20, 90)

/**
 * One cycle as it was run: [boundaries] are the moments each phase began, then the moment the last one ended. Phase i began at boundaries[i] and ended at boundaries[i+1].
 * [pages] are the moments a page of notes was finished, and [nMin] is the N time the cycle was planned with.
 */
data class VrRun(val nMin: Int = VrStep.N.minutes, val boundaries: List<Long> = emptyList(), val pages: List<Long> = emptyList()) {
    fun encode(): String = listOf(nMin.toString(), boundaries.joinToString(","), pages.joinToString(",")).joinToString("|")

    companion object {
        fun decode(text: String?): VrRun? {
            if (text.isNullOrBlank()) return null
            val p = text.split("|", limit = 3); if (p.size < 3) return null
            val n = p[0].toIntOrNull() ?: return null
            fun longs(s: String) = if (s.isBlank()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }
            return VrRun(n, longs(p[1]), longs(p[2]))
        }
    }
}

/** Starts a cycle now. */
fun vrStart(nMin: Int, nowMs: Long): VrRun = VrRun(nMin, listOf(nowMs))

/** Ends the phase that is running and begins the next one, or ends the cycle after the last. Does nothing once the cycle is over. */
fun vrAdvance(run: VrRun, nowMs: Long): VrRun {
    val phases = vrPhases(run.nMin)
    return if (run.boundaries.isEmpty() || run.boundaries.size > phases.size) run else run.copy(boundaries = run.boundaries + nowMs)
}

/** A page of notes finished, counted only while N is the phase that is running. */
fun vrPage(run: VrRun, nowMs: Long): VrRun {
    val i = run.boundaries.size - 1; val phases = vrPhases(run.nMin)
    return if (i in phases.indices && phases[i].step == VrStep.N) run.copy(pages = run.pages + nowMs) else run
}

/** The average time of a page of notes from the taps in this cycle: the first page from the start of N, the rest from the page before. Null with no page timed. */
fun vrAveragePageMs(run: VrRun): Long? {
    val phases = vrPhases(run.nMin); val nIdx = phases.indexOfFirst { it.step == VrStep.N }
    val nStart = run.boundaries.getOrNull(nIdx) ?: return null
    val taps = run.pages.filter { it >= nStart }; if (taps.isEmpty()) return null
    return (taps.last() - nStart) / taps.size
}

enum class VrStatus { NOT_STARTED, RUNNING, DONE }
enum class VrChipState { UPCOMING, CURRENT, OVER_NOW, DONE, DONE_OVER }
data class VrChip(val letter: Char, val state: VrChipState)

/** What the timer shows. [overMs] is above 0 only when the phase running (or, once done, the cycle) has passed its time: the screen shows it as a count up and never hides it. */
data class VrView(
    val status: VrStatus, val phaseIndex: Int, val title: String, val isBreak: Boolean, val plannedMs: Long, val elapsedMs: Long, val overMs: Long, val remainingMs: Long, val fraction: Float,
    val chips: List<VrChip>, val plannedStepMin: Int, val actualMin: Int?, val summary: String,
) { val over: Boolean get() = overMs > 0 }

private const val TOLERANCE_MS = 30_000L        // half a minute either way is on time

fun vrView(run: VrRun?, nowMs: Long): VrView {
    val nMin = run?.nMin ?: VrStep.N.minutes; val phases = vrPhases(nMin)
    val plannedStepMin = phases.filter { !it.isBreak }.sumOf { it.plannedMin }
    val b = run?.boundaries.orEmpty()
    val status = when { b.isEmpty() -> VrStatus.NOT_STARTED; b.size > phases.size -> VrStatus.DONE; else -> VrStatus.RUNNING }
    val cur = if (status == VrStatus.RUNNING) b.size - 1 else -1
    fun spentMs(i: Int): Long = if (i + 1 < b.size) b[i + 1] - b[i] else if (i < b.size) nowMs - b[i] else 0L
    fun overOf(i: Int) = (spentMs(i) - phases[i].plannedMin * 60_000L).coerceAtLeast(0L)
    val chips = VrStep.entries.map { s ->
        val i = phases.indexOfFirst { it.step == s }
        VrChip(s.letter, when {
            status == VrStatus.NOT_STARTED || i > cur && status == VrStatus.RUNNING -> VrChipState.UPCOMING
            status == VrStatus.RUNNING && i == cur -> if (overOf(i) > TOLERANCE_MS) VrChipState.OVER_NOW else VrChipState.CURRENT
            else -> if (overOf(i) > TOLERANCE_MS) VrChipState.DONE_OVER else VrChipState.DONE
        })
    }
    if (status == VrStatus.NOT_STARTED) return VrView(status, -1, "Not started", false, 0, 0, 0, 0, 0f, chips, plannedStepMin, null, "$plannedStepMin min in the steps.")
    val actualMin = ((b.last() - b.first()) / 60_000L).toInt().takeIf { status == VrStatus.DONE }
    val overs = phases.indices.filter { !phases[it].isBreak && it < b.size - (if (status == VrStatus.DONE) 1 else 0) && overOf(it) > TOLERANCE_MS }
        .joinToString(", ") { i -> "${phases[i].step!!.letter} +${(overOf(i) + 30_000L) / 60_000L} min" }
    if (status == VrStatus.DONE) {
        val spent = phases.indices.filter { !phases[it].isBreak }.sumOf { spentMs(it) } / 60_000L
        return VrView(status, phases.size, "Cycle done", false, 0, 0, 0, 0, 1f, chips, plannedStepMin, actualMin,
            "Done in $actualMin min ($spent min in the steps, $plannedStepMin planned)." + if (overs.isNotEmpty()) " Over: $overs." else "")
    }
    val p = phases[cur]; val plannedMs = p.plannedMin * 60_000L; val elapsed = spentMs(cur)
    val over = (elapsed - plannedMs).coerceAtLeast(0L)
    return VrView(status, cur, p.title, p.isBreak, plannedMs, elapsed, over, (plannedMs - elapsed).coerceAtLeast(0L), (elapsed.toFloat() / plannedMs.coerceAtLeast(1)).coerceIn(0f, 1f), chips, plannedStepMin, null,
        if (over > TOLERANCE_MS) "Over by ${clockMs(over)}." else "${clockMs((plannedMs - elapsed).coerceAtLeast(0L))} left.")
}

/** m:ss for a length in milliseconds. */
fun clockMs(ms: Long): String { val s = (ms / 1000L).coerceAtLeast(0L); return "%d:%02d".format(s / 60, s % 60) }

// ---------- VR6: the weekly remedial ----------

const val REMEDIAL_MIN_PER_CYCLE = 30
const val REMEDIAL_SESSION_MAX_MIN = 90

/**
 * Each finished cycle earns 30 minutes of remedial (new similar questions from the mistake log, then the Confident tier), due on the Sunday evening of its week. A cycle done
 * inside a catch-up span (up to [catchUpEnd]) is due after the span: the Sunday after it ends, so the catch-up weeks are not given more to do.
 */
fun remedialDue(cycleDate: LocalDate, catchUpEnd: LocalDate?, zone: ZoneId): ZonedDateTime {
    val sunday = cycleDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val day = if (catchUpEnd != null && !cycleDate.isAfter(catchUpEnd)) maxOf(sunday, catchUpEnd.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))) else sunday
    return day.atTime(21, 0).atZone(zone)
}

/** The sessions the week's remedial is split into: [cycles] times 30 minutes, in sessions of at most 90. */
fun remedialSessions(cycles: Int): List<Int> {
    var left = cycles * REMEDIAL_MIN_PER_CYCLE; val out = ArrayList<Int>()
    while (left > 0) { val n = minOf(left, REMEDIAL_SESSION_MAX_MIN); out += n; left -= n }
    return out
}

const val REMEDIAL_TITLE = "Weekly remedial: new questions from your mistakes, then the Confident tier"
