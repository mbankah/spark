package efa

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/** A task still to do. heaviness is the single slider, 1..10 (B4). */
data class Task(val title: String, val remainingHours: Double, val heaviness: Int) {
    init { require(heaviness in 1..10) { "heaviness must be 1..10" } }
}

/** B8: after 6.5 h sleep or less, hard tasks x1.5 and easy tasks x1.15. Number and threshold are settings (Q-G2). */
data class ShortSleepWeights(val hard: Double = 1.5, val easy: Double = 1.15, val hardFromHeaviness: Int = 6)

const val SHORT_SLEEP_MAX_HOURS = 6.5

fun isShortSleep(sleptHours: Double) = sleptHours <= SHORT_SLEEP_MAX_HOURS

/** Q-G1: a manual task's estimate in hours = base * (0.5 + heaviness/10). Calibration (layer 7) replaces this. */
fun estimateHours(baseHours: Double, heaviness: Int): Double = baseHours * (0.5 + heaviness / 10.0)

/** D1 study cycles are fixed sizes. */
const val STUDY_CYCLE_STANDARD_HOURS = 2.5
const val STUDY_CYCLE_LIGHT_HOURS = 1.0

/** One row of the tap-for-working screen (B3). Nothing is hidden. */
data class WorkingLine(val title: String, val heaviness: Int, val rawHours: Double, val factor: Double, val hours: Double)

data class Interval(val start: Instant, val end: Instant) {
    init { require(!end.isBefore(start)) { "interval ends before it starts" } }
}

/**
 * Time available between two instants minus the time already spoken for (sleep, meals, fixed commitments,
 * transitions, resets, protected rest; Q-G4). Works on instants, so a 25-hour day (X4) is 25 hours.
 */
fun availableHours(from: Instant, to: Instant, spokenFor: List<Interval>): Double {
    if (!to.isAfter(from)) return 0.0
    val clipped = spokenFor.mapNotNull {
        val s = maxOf(it.start, from); val e = minOf(it.end, to)
        if (e.isAfter(s)) Interval(s, e) else null
    }.sortedBy { it.start }
    var busy = Duration.ZERO
    var curS: Instant? = null; var curE: Instant? = null
    for (i in clipped) {
        if (curE == null || i.start.isAfter(curE)) {
            if (curS != null) busy += Duration.between(curS, curE)
            curS = i.start; curE = i.end
        } else if (i.end.isAfter(curE)) curE = i.end
    }
    if (curS != null) busy += Duration.between(curS, curE)
    return (Duration.between(from, to) - busy).toMinutes() / 60.0
}

data class BufferResult(val availableHours: Double, val lines: List<WorkingLine>, val shortSleep: Boolean, val deadlineLabel: String? = null) {
    val workHours: Double get() = lines.sumOf { it.hours }
    val hours: Double get() = availableHours - workHours
    val negative: Boolean get() = round1(hours) < 0.0

    /** Dial fill (Q-G12): how much of the work due fits in the time available, 0..1. Full when the work fits. */
    val coverage: Double get() = if (workHours <= 0.0) 1.0 else (availableHours / workHours).coerceIn(0.0, 1.0)

    /** B1 wording. A negative Buffer always says NEGATIVE (AP6). */
    fun label(): String = if (negative) "Buffer NEGATIVE ${fmt(hours)}h" else "Buffer ${fmt(hours)}h"
    fun working(): String = buildString {
        deadlineLabel?.let { appendLine("To $it") }
        appendLine("Available ${fmt1(availableHours)}h")
        lines.forEach { appendLine("- ${it.title} (heaviness ${it.heaviness}): ${fmt1(it.rawHours)}h x ${it.factor} = ${fmt1(it.hours)}h") }
        appendLine("Work ${fmt1(workHours)}h")
        append(label())
    }
}

/** D6: with no deadline there is no number to show. */
sealed interface BufferState {
    data object NoDeadline : BufferState
    data class Value(val result: BufferResult) : BufferState
}

fun computeBuffer(availableHours: Double?, tasks: List<Task>, shortSleep: Boolean, w: ShortSleepWeights = ShortSleepWeights()): BufferState {
    if (availableHours == null) return BufferState.NoDeadline
    val lines = tasks.map {
        val f = if (!shortSleep) 1.0 else if (it.heaviness >= w.hardFromHeaviness) w.hard else w.easy
        WorkingLine(it.title, it.heaviness, it.remainingHours, f, it.remainingHours * f)
    }
    return BufferState.Value(BufferResult(availableHours, lines, shortSleep))
}

fun round1(x: Double): Double = BigDecimal(x).setScale(1, RoundingMode.HALF_UP).toDouble()
fun fmt1(x: Double): String = BigDecimal(x).setScale(1, RoundingMode.HALF_UP).toPlainString()
fun fmt(x: Double): String = (if (round1(x) >= 0) "+" else "") + fmt1(x)
