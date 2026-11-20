package efa

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// ---------- ST2: start, end, say ----------
// A block with no proof (a routine, a meal, an errand, planning) is engaged with by a hold at its start and an answer at its end. What happened is recorded as it was:
// started on time or late, finished on time or over, or did not happen. Late and over are the same events as "Started late" and "Running over", so the day is never silently wrong.

/** Lateness under this many minutes counts as on time. */
const val RUN_LATE_MIN = 5

/** The start hold grows with the block, between these two. The end hold is short and fixed. */
const val HOLD_FLOOR_MS = 2_000L
const val HOLD_CEIL_MS = 6_000L
const val HOLD_END_MS = 1_000L

/** ST2: the start hold in milliseconds for a block of [minutes]: 2 seconds up to 15 minutes, 6 seconds from 2 hours, in a straight line between. Friction on starting is capped on purpose. */
fun holdMillis(minutes: Int): Long {
    val m = minutes.coerceIn(15, 120)
    return HOLD_FLOOR_MS + (HOLD_CEIL_MS - HOLD_FLOOR_MS) * (m - 15) / (120 - 15)
}

/** The blocks this is for. Free time, sleep and travel are left alone, and blocks that already have a proof keep it. */
val RUN_TYPES = setOf("routine", "meal", "errand", "planning")

fun isRunnable(s: PatternSlot): Boolean = !s.cancelled && s.type in RUN_TYPES

/** What was recorded for one block: when it was started, when it was finished, or why it did not happen. */
data class RunRecord(val startMs: Long? = null, val endMs: Long? = null, val skippedReason: String? = null) {
    fun encode(): String = listOf(startMs?.toString().orEmpty(), endMs?.toString().orEmpty(), skippedReason.orEmpty().replace("|", "/")).joinToString("|")

    companion object {
        fun decode(text: String?): RunRecord? {
            if (text.isNullOrBlank()) return null
            val p = text.split("|", limit = 3)
            if (p.size < 3) return null
            return RunRecord(p[0].toLongOrNull(), p[1].toLongOrNull(), p[2].ifBlank { null })
        }
    }
}

enum class RunStatus { NOT_STARTED, RUNNING, DONE, SKIPPED }

/** What the block screen shows: the status, how late it started, how far over it went, one line saying so, and how much of the planned time has passed. */
data class RunView(val status: RunStatus, val lateMin: Int, val overMin: Int, val line: String, val fraction: Float) {
    /** Started or finished 5 minutes or more off its plan, so the rest of the day has moved. */
    val shifted: Boolean get() = lateMin >= RUN_LATE_MIN || overMin >= RUN_LATE_MIN
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private fun clock(ms: Long, zone: ZoneId) = HM.format(Instant.ofEpochMilli(ms).atZone(zone))
private fun minutesBetween(fromMs: Long, toMs: Long) = ((toMs - fromMs) / 60_000L).toInt()

/** ST2: reads a block's record against its plan and the clock. Never says a block was on time when it was not. */
fun runView(rec: RunRecord?, plannedStartMs: Long, plannedEndMs: Long, nowMs: Long, zone: ZoneId): RunView {
    val span = (plannedEndMs - plannedStartMs).coerceAtLeast(1L)
    if (rec?.skippedReason != null) return RunView(RunStatus.SKIPPED, 0, 0, "Did not happen: ${rec.skippedReason}.", 0f)
    val start = rec?.startMs
        ?: return if (nowMs < plannedStartMs) RunView(RunStatus.NOT_STARTED, 0, 0, "Starts at ${clock(plannedStartMs, zone)}.", 0f)
        else minutesBetween(plannedStartMs, nowMs).let { late -> RunView(RunStatus.NOT_STARTED, late, 0, "Not started. ${late} min past its time.", 0f) }
    val late = maxOf(0, minutesBetween(plannedStartMs, start))
    val lateText = if (late >= RUN_LATE_MIN) ", $late min late" else ""
    val end = rec.endMs
    if (end == null) {
        val over = maxOf(0, minutesBetween(plannedEndMs, nowMs))
        val line = "Started ${clock(start, zone)}$lateText." + if (over >= RUN_LATE_MIN) " Over by $over min." else ""
        return RunView(RunStatus.RUNNING, late, over, line, ((nowMs - start).toFloat() / span).coerceIn(0f, 1f))
    }
    val over = maxOf(0, minutesBetween(plannedEndMs, end))
    val overText = if (over >= RUN_LATE_MIN) ", $over min over" else ""
    return RunView(RunStatus.DONE, late, over, "Done ${clock(start, zone)} to ${clock(end, zone)}$lateText$overText.", 1f)
}

/** The minutes the rest of today should slide by for a lateness or an overrun, in the steps Running over takes (5 to 180). */
fun shiftMinutes(minutes: Int): Int = (((minutes + 4) / 5) * 5).coerceIn(5, 180)

// ---------- the day strip ----------

enum class StripState { PLANNED, ON_TIME, SHIFTED, RUNNING, NOT_STARTED, SKIPPED }

/** One block on the day strip: its plan in minutes of the planned day, what happened (the same unit, or null), and how to draw it. */
data class StripBlock(val title: String, val plannedStart: Int, val plannedEnd: Int, val actualStart: Int?, val actualEnd: Int?, val state: StripState, val runnable: Boolean)

/**
 * ST3: the day as a strip, planned against what happened. Only the blocks of [RUN_TYPES] have a record; the rest are drawn as planned. A runnable block whose time has come with no start is
 * NOT_STARTED, one that is running is RUNNING, one finished within 5 minutes of its plan is ON_TIME, and one started or finished further off is SHIFTED. Free time is left out.
 */
fun dayStrip(day: List<DatedSlot>, records: Map<String, RunRecord>, now: ZonedDateTime): List<StripBlock> {
    val zone = now.zone; val nowMs = now.toInstant().toEpochMilli()
    return day.filter { it.slot.type != "free" && it.slot.type != REMINDER_TYPE }.sortedBy { it.start }.map { ds ->
        val s = ds.slot; val dayStartMs = ds.plannedDay.atStartOfDay(zone).toInstant().toEpochMilli()
        fun rel(ms: Long?) = ms?.let { ((it - dayStartMs) / 60_000L).toInt() }
        if (!isRunnable(s)) return@map StripBlock(s.title.substringBefore(" - "), s.startMin, s.endMin, null, null, StripState.PLANNED, false)
        val rec = records[blockKey(ds)]
        val v = runView(rec, ds.start.toInstant().toEpochMilli(), ds.end.toInstant().toEpochMilli(), nowMs, zone)
        val state = when (v.status) {
            RunStatus.SKIPPED -> StripState.SKIPPED
            RunStatus.RUNNING -> StripState.RUNNING
            RunStatus.DONE -> if (v.shifted) StripState.SHIFTED else StripState.ON_TIME
            RunStatus.NOT_STARTED -> if (nowMs >= ds.start.toInstant().toEpochMilli()) StripState.NOT_STARTED else StripState.PLANNED
        }
        StripBlock(s.title.substringBefore(" - "), s.startMin, s.endMin, rel(rec?.startMs), rel(rec?.endMs), state, true)
    }
}
