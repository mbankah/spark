package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

// ---------- VR1 to VR8: the study loop, V R N P M, as a timer ----------
// One cycle in the order V video, R read, N notes, P practice, M marking, with breaks between them. Every step has a time. A step may overrun and never stops by itself,
// but the overrun is always shown, as a count up, and kept. The times are settings (VrConfig); the N time is also tuned by timing real pages.

enum class VrStep(val letter: Char, val label: String, val minutes: Int, val todo: String) {
    V('V', "Video", 10, "Watch the lecture video or the slides."),
    R('R', "Read", 5, "Read the section of the notes or the book for this lecture."),
    N('N', "Notes", 45, "Copy the notes by hand, about four pages. Put the phone away."),
    P('P', "Practice", 30, "Do the practice questions: all the Foundation and Building ones."),
    M('M', "Marking", 15, "Mark each question and correct it now. A cross by each wrong one. Write C or U before you see the solution. Log one line per mistake: concept, method or arithmetic."),
}

/** One stretch of the cycle: a step, or the break after the step before it ([step] is null for a break). */
data class VrPhase(val step: VrStep?, val plannedMin: Int) {
    val isBreak: Boolean get() = step == null
    val title: String get() = step?.let { "${it.letter} ${it.label}" } ?: "Break"
}

private fun round5(x: Double): Int = (Math.round(x / 5.0) * 5).toInt()

// ---------- VR7: the settings ----------

/**
 * How a cycle is run. The steps are in minutes, the breaks are 5 between the steps (after V, R and P) and 10 after N unless set, and [capMin] is the longest the steps may add up to
 * (0 is no limit): over it every step is shrunk in proportion to fit. [pagesPerCycle] turns a timed page into the next N. [remedialMin] is what each finished cycle earns, and
 * [redEngine] says the Red Engine does that work now, so none is booked.
 */
data class VrConfig(
    val v: Int = 10, val r: Int = 5, val n: Int = 45, val p: Int = 30, val m: Int = 15,
    val breakBetween: Int = 5, val breakAfterN: Int = 10,
    val capMin: Int = 0, val pagesPerCycle: Int = 4, val remedialMin: Int = REMEDIAL_MIN_PER_CYCLE, val redEngine: Boolean = false,
    /** VR10: V and R are done by an override (you did them elsewhere): a cycle starts at N, and V and R are shown as done by override, never as timed. */
    val skipVR: Boolean = false,
    /** VR13: an alert when a step ends, saying what is next and what to do, and again every [overEvery] minutes while the step is over (0 is not again). */
    val remind: Boolean = true, val overEvery: Int = 5,
) {
    fun clamped(): VrConfig = copy(
        v = v.coerceIn(5, 120), r = r.coerceIn(5, 120), n = n.coerceIn(5, 120), p = p.coerceIn(5, 120), m = m.coerceIn(5, 120),
        breakBetween = breakBetween.coerceIn(0, 15), breakAfterN = breakAfterN.coerceIn(0, 20),
        capMin = if (capMin <= 0) 0 else capMin.coerceIn(45, 240), pagesPerCycle = pagesPerCycle.coerceIn(2, 10), remedialMin = remedialMin.coerceIn(0, 60),
        overEvery = overEvery.coerceIn(0, 15),
    )

    fun encode(): String = listOf(v, r, n, p, m, breakBetween, breakAfterN, capMin, pagesPerCycle, remedialMin, if (redEngine) 1 else 0, if (skipVR) 1 else 0, if (remind) 1 else 0, overEvery).joinToString(",")

    /** The preset these step and break times match, or null when they are your own. */
    fun preset(): String? = when {
        sameTimes(OPTIMAL) && !skipVR -> "Optimal"
        sameTimes(IDEAL) && !skipVR -> "Ideal"
        sameTimes(IDEAL) && skipVR -> "Session"
        else -> null
    }

    private fun sameTimes(o: VrConfig) = v == o.v && r == o.r && n == o.n && p == o.p && m == o.m && breakBetween == o.breakBetween && breakAfterN == o.breakAfterN

    companion object {
        /** The compressed cycle of the spec: V10 R5 N45 P30 M15 (105 minutes), 30 minutes of remedial a week for each cycle. */
        val OPTIMAL = VrConfig()
        /** The full cycle of the spec: V15 R15 N65 P45 M25 (165 minutes), 10 minutes of remedial a week for each cycle. */
        val IDEAL = VrConfig(v = 15, r = 15, n = 65, p = 45, m = 25, remedialMin = 10)
        /** The 2.5 hour session: the ideal N, P and M with V and R done by override. N 65, a break of 10, P 45, a break of 5 and M 25 is 150 minutes. */
        val SESSION = IDEAL.copy(skipVR = true)

        fun decode(text: String?): VrConfig {
            val p = text?.split(",")?.map { it.trim().toIntOrNull() } ?: return VrConfig()
            if (p.size < 11 || p.take(11).any { it == null }) return VrConfig()
            val x = p.map { it ?: 0 }
            return VrConfig(x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], x[8], x[9], x[10] == 1, x.getOrElse(11) { 0 } == 1, x.getOrElse(12) { 1 } == 1, x.getOrElse(13) { 5 }).clamped()
        }
    }
}

/** The five step times of [cfg] after the longest-cycle cap: over the cap every step is shrunk in proportion, to the nearest 5 and never under 5, and then trimmed from the longest until it fits. */
fun vrSteps(cfg: VrConfig): List<Int> {
    val c = cfg.clamped(); var steps = listOf(c.v, c.r, c.n, c.p, c.m)
    if (c.capMin > 0 && steps.sum() > c.capMin) {
        val f = c.capMin.toDouble() / steps.sum()
        steps = steps.map { maxOf(5, round5(it * f)) }.toMutableList().also { s ->
            while (s.sum() > c.capMin && s.any { it > 5 }) { val i = s.indices.filter { s[it] > 5 }.maxByOrNull { s[it] }!!; s[i] -= 5 }
        }
    }
    return steps
}

/** The nine phases of a cycle in minutes: V, break, R, break, N, break, P, break, M. [nMin] replaces the N time (the timed pages' answer) before the cap is applied. */
fun vrPlan(cfg: VrConfig, nMin: Int? = null): List<Int> {
    val c = if (nMin != null) cfg.copy(n = nMin) else cfg
    val s = vrSteps(c); val cc = cfg.clamped()
    val plan = listOf(s[0], cc.breakBetween, s[1], cc.breakBetween, s[2], cc.breakAfterN, s[3], cc.breakBetween, s[4])
    // VR10: V, R and the two breaks before N are not in the cycle when V and R are done by override, so the cycle runs N, P and M
    return if (cc.skipVR) plan.mapIndexed { i, m -> if (i < 4) 0 else m } else plan
}

val VR_DEFAULT_PLAN: List<Int> = vrPlan(VrConfig.OPTIMAL)

/** The phases of a cycle from its nine times. A break of 0 minutes is a phase too, and is ended at once. */
fun vrPhases(plan: List<Int> = VR_DEFAULT_PLAN): List<VrPhase> = listOf(VrStep.V, null, VrStep.R, null, VrStep.N, null, VrStep.P, null, VrStep.M).mapIndexed { i, s -> VrPhase(s, plan.getOrElse(i) { VR_DEFAULT_PLAN[i] }) }

/** The default plan with a different N time. */
fun vrPhases(nMinutes: Int): List<VrPhase> = vrPhases(vrPlan(VrConfig.OPTIMAL, nMinutes))

/** The N step from timed pages: pages times the minutes a page, to the nearest 5, kept between 20 and 90. */
fun suggestedN(pages: Int, avgPageMs: Long): Int = round5(pages * avgPageMs / 60_000.0).coerceIn(20, 90)

/**
 * One cycle as it was run: [boundaries] are the moments each phase began, then the moment the last one ended. Phase i began at boundaries[i] and ended at boundaries[i+1].
 * [pages] are the moments a page of notes was finished. [plan] is the nine times the cycle was planned with, kept so a change to the settings does not change a cycle already running.
 */
data class VrRun(val nMin: Int = VrStep.N.minutes, val boundaries: List<Long> = emptyList(), val pages: List<Long> = emptyList(), val plan: List<Int>? = null, val pauses: List<Long> = emptyList(), val overridden: String = "") {
    val planMinutes: List<Int> get() = plan ?: vrPlan(VrConfig.OPTIMAL, nMin)

    /** VR9: [pauses] are the moments of pausing and resuming, alternately, so an odd count means paused now. */
    val paused: Boolean get() = pauses.size % 2 == 1

    /** The milliseconds inside [from, to] that were paused. A pause still open counts up to [nowMs]. */
    fun pausedMs(from: Long, to: Long, nowMs: Long): Long {
        var total = 0L; var i = 0
        while (i < pauses.size) { val e = pauses.getOrNull(i + 1) ?: nowMs; total += (minOf(e, to) - maxOf(pauses[i], from)).coerceAtLeast(0L); i += 2 }
        return total
    }

    fun encode(): String = listOf(nMin.toString(), boundaries.joinToString(","), pages.joinToString(","), plan?.joinToString(",").orEmpty(), pauses.joinToString(","), overridden).joinToString("|")

    companion object {
        fun decode(text: String?): VrRun? {
            if (text.isNullOrBlank()) return null
            val p = text.split("|", limit = 6); if (p.size < 3) return null
            val n = p[0].toIntOrNull() ?: return null
            fun longs(s: String) = if (s.isBlank()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }
            val plan = p.getOrNull(3)?.takeIf { it.isNotBlank() }?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 9 }
            return VrRun(n, longs(p[1]), longs(p[2]), plan, longs(p.getOrElse(4) { "" }), p.getOrElse(5) { "" })
        }
    }
}

/** Starts a cycle now, planned with the N time given and the default breaks. */
fun vrStart(nMin: Int, nowMs: Long): VrRun = VrRun(nMin, listOf(nowMs))

/** Starts a cycle now with a plan from the settings. With [overridden] (VR10) the steps named are done by override: their phases are over at the start and the cycle begins at N. */
fun vrStart(plan: List<Int>, nowMs: Long, overridden: String = ""): VrRun =
    VrRun(plan[4], List(if (overridden.isEmpty()) 1 else 5) { nowMs }, emptyList(), plan, emptyList(), overridden)

/** Ends the phase that is running and begins the next one, or ends the cycle after the last. Does nothing once the cycle is over. A pause that is open is ended first. */
fun vrAdvance(run: VrRun, nowMs: Long): VrRun {
    val phases = vrPhases(run.planMinutes)
    if (run.boundaries.isEmpty() || run.boundaries.size > phases.size) return run
    val resumed = if (run.paused) run.copy(pauses = run.pauses + nowMs) else run
    return resumed.copy(boundaries = resumed.boundaries + nowMs)
}

/** VR9: pauses a cycle that is running. The time paused is not counted in the step, and it is kept and shown. Does nothing when not running or already paused. */
fun vrPause(run: VrRun, nowMs: Long): VrRun = if (run.boundaries.isEmpty() || run.boundaries.size > vrPhases(run.planMinutes).size || run.paused) run else run.copy(pauses = run.pauses + nowMs)

/** VR9: resumes a paused cycle. */
fun vrResume(run: VrRun, nowMs: Long): VrRun = if (run.paused) run.copy(pauses = run.pauses + nowMs) else run

/** A page of notes finished, counted only while N is the phase that is running. */
fun vrPage(run: VrRun, nowMs: Long): VrRun {
    val i = run.boundaries.size - 1; val phases = vrPhases(run.planMinutes)
    return if (i in phases.indices && phases[i].step == VrStep.N) run.copy(pages = run.pages + nowMs) else run
}

/** The average time of a page of notes from the taps in this cycle: the first page from the start of N, the rest from the page before. Null with no page timed. */
fun vrAveragePageMs(run: VrRun): Long? {
    val phases = vrPhases(run.planMinutes); val nIdx = phases.indexOfFirst { it.step == VrStep.N }
    val nStart = run.boundaries.getOrNull(nIdx) ?: return null
    val taps = run.pages.filter { it >= nStart }; if (taps.isEmpty()) return null
    return ((taps.last() - nStart) - run.pausedMs(nStart, taps.last(), taps.last())) / taps.size
}

enum class VrStatus { NOT_STARTED, RUNNING, DONE }
enum class VrChipState { UPCOMING, CURRENT, OVER_NOW, DONE, DONE_OVER, OVERRIDDEN }
data class VrChip(val letter: Char, val state: VrChipState)

/** What the timer shows. [overMs] is above 0 only when the phase running (or, once done, the cycle) has passed its time: the screen shows it as a count up and never hides it. */
data class VrView(
    val status: VrStatus, val phaseIndex: Int, val title: String, val isBreak: Boolean, val plannedMs: Long, val elapsedMs: Long, val overMs: Long, val remainingMs: Long, val fraction: Float,
    val chips: List<VrChip>, val plannedStepMin: Int, val actualMin: Int?, val summary: String,
    /** VR9: the cycle is paused now, and how many minutes have been paused in all. */
    val paused: Boolean = false, val pausedMin: Int = 0,
    /** VR13: what to do in the step in hand. */
    val todo: String = "",
) { val over: Boolean get() = overMs > 0 }

private const val TOLERANCE_MS = 30_000L        // half a minute either way is on time

/** The view of [run] now. With no run, [plan] is what a cycle started now would use. */
fun vrView(run: VrRun?, nowMs: Long, plan: List<Int>? = null): VrView {
    val phases = vrPhases(run?.planMinutes ?: plan ?: VR_DEFAULT_PLAN)
    val plannedStepMin = phases.filter { !it.isBreak }.sumOf { it.plannedMin }
    val b = run?.boundaries.orEmpty()
    val status = when { b.isEmpty() -> VrStatus.NOT_STARTED; b.size > phases.size -> VrStatus.DONE; else -> VrStatus.RUNNING }
    val cur = if (status == VrStatus.RUNNING) b.size - 1 else -1
    fun rawEnd(i: Int) = if (i + 1 < b.size) b[i + 1] else nowMs
    fun spentMs(i: Int): Long = if (i < b.size) (rawEnd(i) - b[i]) - (run?.pausedMs(b[i], rawEnd(i), nowMs) ?: 0L) else 0L
    val pausedAll = if (b.isEmpty()) 0L else run?.pausedMs(b.first(), if (status == VrStatus.DONE) b.last() else nowMs, nowMs) ?: 0L
    val pausedMin = ((pausedAll + 30_000L) / 60_000L).toInt(); val pausedNow = status == VrStatus.RUNNING && run?.paused == true
    fun overOf(i: Int) = (spentMs(i) - phases[i].plannedMin * 60_000L).coerceAtLeast(0L)
    val chips = VrStep.entries.map { s ->
        val i = phases.indexOfFirst { it.step == s }
        VrChip(s.letter, when {
            run?.overridden?.contains(s.letter) == true || phases[i].plannedMin == 0 -> VrChipState.OVERRIDDEN          // VR10: done by override, never timed
            status == VrStatus.NOT_STARTED || i > cur && status == VrStatus.RUNNING -> VrChipState.UPCOMING
            status == VrStatus.RUNNING && i == cur -> if (overOf(i) > TOLERANCE_MS) VrChipState.OVER_NOW else VrChipState.CURRENT
            else -> if (overOf(i) > TOLERANCE_MS) VrChipState.DONE_OVER else VrChipState.DONE
        })
    }
    if (status == VrStatus.NOT_STARTED) return VrView(status, -1, "Not started", false, 0, 0, 0, 0, 0f, chips, plannedStepMin, null, "$plannedStepMin min in the steps.")
    val actualMin = (((b.last() - b.first()) - pausedAll) / 60_000L).toInt().takeIf { status == VrStatus.DONE }
    val overs = phases.indices.filter { !phases[it].isBreak && it < b.size - (if (status == VrStatus.DONE) 1 else 0) && overOf(it) > TOLERANCE_MS }
        .joinToString(", ") { i -> "${phases[i].step!!.letter} +${(overOf(i) + 30_000L) / 60_000L} min" }
    if (status == VrStatus.DONE) {
        val spent = phases.indices.filter { !phases[it].isBreak }.sumOf { spentMs(it) } / 60_000L
        return VrView(status, phases.size, "Cycle done", false, 0, 0, 0, 0, 1f, chips, plannedStepMin, actualMin,
            "Done in $actualMin min ($spent min in the steps, $plannedStepMin planned)." + (if (overs.isNotEmpty()) " Over: $overs." else "") + (if (pausedMin > 0) " Paused $pausedMin min." else "") + (if (run?.overridden.isNullOrEmpty()) "" else " ${run?.overridden} done by override."), false, pausedMin)
    }
    val p = phases[cur]; val plannedMs = p.plannedMin * 60_000L; val elapsed = spentMs(cur)
    val over = (elapsed - plannedMs).coerceAtLeast(0L)
    return VrView(status, cur, p.title, p.isBreak, plannedMs, elapsed, over, (plannedMs - elapsed).coerceAtLeast(0L), (elapsed.toFloat() / plannedMs.coerceAtLeast(1)).coerceIn(0f, 1f), chips, plannedStepMin, null,
        (if (pausedNow) "Paused. " else "") + (if (over > TOLERANCE_MS) "Over by ${clockMs(over)}." else "${clockMs((plannedMs - elapsed).coerceAtLeast(0L))} left."), pausedNow, pausedMin, vrTodo(p))
}

/** m:ss for a length in milliseconds. */
fun clockMs(ms: Long): String { val s = (ms / 1000L).coerceAtLeast(0L); return "%d:%02d".format(s / 60, s % 60) }

// ---------- VR6: the weekly remedial ----------

const val REMEDIAL_MIN_PER_CYCLE = 30
const val REMEDIAL_SESSION_MAX_MIN = 90

/**
 * Each finished cycle earns remedial (new similar questions from the mistake log, then the Confident tier), due on the Sunday evening of its week. A cycle done
 * inside a catch-up span (up to [catchUpEnd]) is due after the span: the Sunday after it ends, so the catch-up weeks are not given more to do.
 */
fun remedialDue(cycleDate: LocalDate, catchUpEnd: LocalDate?, zone: ZoneId): ZonedDateTime {
    val sunday = cycleDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val day = if (catchUpEnd != null && !cycleDate.isAfter(catchUpEnd)) maxOf(sunday, catchUpEnd.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))) else sunday
    return day.atTime(21, 0).atZone(zone)
}

/** The sessions the week's remedial is split into: [cycles] times [perCycleMin], in sessions of at most 90. */
fun remedialSessions(cycles: Int, perCycleMin: Int = REMEDIAL_MIN_PER_CYCLE): List<Int> {
    var left = cycles * perCycleMin; val out = ArrayList<Int>()
    while (left > 0) { val n = minOf(left, REMEDIAL_SESSION_MAX_MIN); out += n; left -= n }
    return out
}

/** VR8: whether a finished cycle books remedial at all. The Red Engine switches it off, and so does a remedial time of 0. */
fun remedialBooked(cfg: VrConfig): Boolean = !cfg.redEngine && cfg.remedialMin > 0

const val REMEDIAL_TITLE = "Weekly remedial: new questions from your mistakes, then the Confident tier"

// ---------- VR11: the ring on the home screen ----------

/** One arc of the home screen's ring: where it starts and how wide it is (fractions of a turn), how full it is, whether it is a break, and whether its step has passed its time. */
data class VrArc(val start: Float, val sweep: Float, val fill: Float, val isBreak: Boolean, val over: Boolean, val letter: Char?)

/**
 * VR11: the cycle as a ring, each phase as wide as its planned time (a step or a break; a step done by override has no width). A finished phase is full, the one running is filled by the time
 * spent in it (the time paused left out), and the rest are empty. A running phase past its time is full and marked over.
 */
fun vrRing(run: VrRun?, nowMs: Long, plan: List<Int>? = null): List<VrArc> {
    val phases = vrPhases(run?.planMinutes ?: plan ?: VR_DEFAULT_PLAN)
    val total = phases.sumOf { it.plannedMin }.coerceAtLeast(1)
    val b = run?.boundaries.orEmpty()
    val done = b.size > phases.size
    val cur = if (b.isNotEmpty() && !done) b.size - 1 else -1
    var at = 0f; val out = ArrayList<VrArc>()
    for ((i, p) in phases.withIndex()) {
        if (p.plannedMin == 0) continue
        val sweep = p.plannedMin.toFloat() / total
        var fill = 0f; var over = false
        if (done || i + 1 < b.size) fill = 1f
        else if (i == cur) {
            val spent = (nowMs - b[i]) - (run?.pausedMs(b[i], nowMs, nowMs) ?: 0L)
            fill = (spent.toFloat() / (p.plannedMin * 60_000f)).coerceIn(0f, 1f)
            over = spent > p.plannedMin * 60_000L + 30_000L
        }
        out += VrArc(at, sweep, fill, p.isBreak, over, p.step?.letter)
        at += sweep
    }
    return out
}

// ---------- VR12: the lock screen notification ----------

enum class VrNoteMode { COUNTDOWN, OVER, PAUSED }

/**
 * VR12: what the lock screen notification says for a running cycle. [whenMs] is the moment its live clock counts to (COUNTDOWN) or from (OVER), and 0 while PAUSED, when the clock is
 * stopped. [chips] is one line of the five steps: done, now, to come, or done by override.
 */
data class VrNote(val title: String, val text: String, val chips: String, val mode: VrNoteMode, val whenMs: Long, val todo: String = "")

fun vrNote(run: VrRun, nowMs: Long): VrNote {
    val v = vrView(run, nowMs)
    val chips = v.chips.joinToString("  ") { ch ->
        ch.letter + when (ch.state) {
            VrChipState.UPCOMING -> " ○"; VrChipState.CURRENT -> " ●"; VrChipState.OVER_NOW -> " ●+"
            VrChipState.DONE -> " ✓"; VrChipState.DONE_OVER -> " ✓+"; VrChipState.OVERRIDDEN -> " *"
        }
    }
    return when {
        v.paused -> VrNote(v.title, "Paused, ${clockMs(v.remainingMs)} left", chips, VrNoteMode.PAUSED, 0L, v.todo)
        v.over -> VrNote(v.title, "OVER", chips, VrNoteMode.OVER, nowMs - v.overMs, v.todo)
        else -> VrNote(v.title, "left", chips, VrNoteMode.COUNTDOWN, nowMs + v.remainingMs, v.todo)
    }
}

// ---------- VR13: what to do, and the reminder ----------

/** What to do in a phase: the step's own instructions, or for a break the reset. */
fun vrTodo(p: VrPhase): String = p.step?.todo ?: "Break: stand up, drink water, phone away."

/** The words of a reminder: [title] says which step is up or over, [text] says what is next and what to do in it. */
data class VrAlert(val title: String, val text: String)

/**
 * VR13: the reminder for the phase running at [nowMs]: it is up (the time has come) or over by some minutes, then what is next and what to do in it. Null when no cycle is running or it is paused.
 * Phases of 0 minutes (a step done by override) are skipped when saying what is next.
 */
fun vrAlert(run: VrRun, nowMs: Long): VrAlert? {
    val v = vrView(run, nowMs)
    if (v.status != VrStatus.RUNNING || v.paused) return null
    val phases = vrPhases(run.planMinutes)
    val next = phases.drop(v.phaseIndex + 1).firstOrNull { it.plannedMin > 0 }
    val overMin = ((v.overMs + 30_000L) / 60_000L).toInt()
    val head = if (overMin >= 1) "${v.title} is over by $overMin min" else "${v.title} is up"
    val tail = if (next == null) "That was the last step. Hold to finish the cycle, then take the proof photo of your notes."
    else "Next: ${next.title}, ${next.plannedMin} min. ${vrTodo(next)}"
    return VrAlert(head, tail)
}

/** VR13: the minutes from [nowMs] to the next reminder for a step that ends in [remainingMs] or is over by [overMs]: at its end, then every [every] minutes of overrun (0: never again). Null when there is none. */
fun vrNextReminderMs(remainingMs: Long, overMs: Long, every: Int): Long? = when {
    remainingMs > 0 -> remainingMs
    every <= 0 -> null
    else -> every * 60_000L - overMs % (every * 60_000L)
}
