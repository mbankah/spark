package efa

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Work that must be done before [due]. Manual deadlines come from quick-add (D2), study cycles from lectures (D1). */
data class DueWork(val title: String, val hours: Double, val heaviness: Int, val due: ZonedDateTime)

/** Q-G1 (revised): a quick-add task has only a heaviness slider, so hours = 1.25 x heaviness. Calibration (layer 7) corrects it. */
fun quickAddHours(heaviness: Int): Double { require(heaviness in 1..10); return heaviness * 1.25 }

const val QUICK_ADD_DEFAULT_HEAVINESS = 5   // D2: defaults so it takes seconds

/** Slot types that can hold work. Free slots stay breaks until the margin rule (F3, layer 6) says otherwise. */
private val WORK_CAPABLE = setOf("study", "code", "study-standard", "study-light")

/** F1: target wake = the planned wake of that day ("Wake, shower"). */
fun targetWake(pattern: List<PatternSlot>, date: LocalDate, zone: ZoneId = LONDON): ZonedDateTime? =
    slotsFor(pattern, date, zone).firstOrNull { it.slot.title == "Wake, shower" }?.start

/** D1: a study cycle is due "by the end of that day, meaning that night's cutoff". */
fun endOfDayCutoff(pattern: List<PatternSlot>, date: LocalDate, zone: ZoneId = LONDON): ZonedDateTime =
    nightCutoff(targetWake(pattern, date.plusDays(1), zone) ?: ZonedDateTime.of(date.plusDays(1), java.time.LocalTime.of(7, 30), zone))

/** Hours of work-capable blocks between two instants. Partly elapsed blocks count only their remaining part. */
fun workCapableHours(pattern: List<PatternSlot>, from: ZonedDateTime, to: ZonedDateTime): Double {
    if (!to.isAfter(from)) return 0.0
    var total = Duration.ZERO
    var d = from.toLocalDate().minusDays(1)
    while (!d.isAfter(to.toLocalDate())) {
        for (s in slotsFor(pattern, d, from.zone)) {
            if (s.slot.type !in WORK_CAPABLE) continue
            val a = maxOf(s.start, from); val b = minOf(s.end, to)
            if (b.isAfter(a)) total += Duration.between(a, b)
        }
        d = d.plusDays(1)
    }
    return total.toMinutes() / 60.0
}

/** D1: one study cycle per lecture still ahead, standard 2.5 h or light 1 h, due that night's cutoff. Verified or missed cycles arrive with work blocks. */
fun studyCycles(pattern: List<PatternSlot>, now: ZonedDateTime, days: Int = 14): List<DueWork> =
    (0 until days).flatMap { i ->
        val d = now.toLocalDate().plusDays(i.toLong())
        val due = endOfDayCutoff(pattern, d, now.zone)
        slotsFor(pattern, d, now.zone).filter { it.slot.type == "study-standard" || it.slot.type == "study-light" }
            .filter { it.end.isAfter(now) }
            .map {
                val std = it.slot.type == "study-standard"
                DueWork(it.slot.title, if (std) STUDY_CYCLE_STANDARD_HOURS else STUDY_CYCLE_LIGHT_HOURS, if (std) 6 else 3, due)
            }
    }

private val DEADLINE_FMT = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")

/**
 * Buffer now (B1, B9). For each deadline the slack is work-capable time before it minus all work due by it; the Buffer is
 * the smallest slack, so the tightest deadline drives the number and the working names it. No deadlines at all gives
 * NoDeadline (D6). Q-G4/Q-G11 in docs/QUESTIONS.md.
 */
fun bufferNow(now: ZonedDateTime, work: List<DueWork>, pattern: List<PatternSlot>, shortSleep: Boolean,
              weights: ShortSleepWeights = ShortSleepWeights()): BufferState {
    if (work.isEmpty()) return BufferState.NoDeadline
    val results = work.map { it.due }.distinct().sorted().map { d ->
        val tasks = work.filter { !it.due.isAfter(d) }.map { Task(it.title, it.hours, it.heaviness) }
        val avail = workCapableHours(pattern, now, d)
        val r = (computeBuffer(avail, tasks, shortSleep, weights) as BufferState.Value).result
        r.copy(deadlineLabel = DEADLINE_FMT.format(d))
    }
    return BufferState.Value(results.minBy { it.hours })
}
