package efa

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * AD: adapting to a situation nobody wrote a rule for. Life is spontaneous and a menu of situations can never be complete, so the app must not wait to be told in its own words. Three
 * pieces, all built on the one reflow engine and the one preview, so nothing here is a second planner:
 *
 *  - AD1 drift: Spark notices that the day and the plan have come apart (a block passed with no start, a block not started) and offers one proposal, the rest of today from now.
 *  - AD2 something came up: whatever happened, said in a few words, is placed in the day, now or at the next free moment or tomorrow, and the numbers before and after are shown.
 *  - AD3 the ladder: when you are behind, the day steps down by itself (full, short, floor, three things) and steps back up when you catch up. What is protected does not step down.
 *
 * The wall is for wanting less, not for life happening: any of these that only moves work is free, and only one that has to give work up needs the wall, exactly as in Modify Schedule.
 */

/** Blocks that are mine to move. A lecture, a meal, the routine and sleep are not. */
val MOVABLE_WORK: Set<String> = setOf("study", "study-standard", "study-light", "code", "planning")

/** What the person is already doing: nothing is proposed over it. */
val BUSY_TYPES: Set<String> = setOf("sleep", "lecture", "tutorial", "mytutor", "training", "match", "gym", "church", "travel")

const val DRIFT_TRIGGER_MIN = 20
const val DRIFT_GRACE_MIN = 10

// ---------------------------------------------------------------- AD1: drift

/**
 * AD1: Spark can only tell that you are behind if it can see you start. A block counts as started when you hold it, and as done when it is proven. Until it has seen at least one of
 * either in the last week it does not know whether an unstarted block means you did not start or that you started without telling it, so it says nothing and the day stays a full day.
 * That keeps a new install, and anyone who works without pressing hold, from being told they are behind all day.
 */
fun startSignalsSeen(startedLastWeek: Int, provenBlocks: Int): Boolean = startedLastWeek > 0 || provenBlocks > 0

/** [behindMin] is the work that has passed unstarted plus the time the block in front has waited, and [lateMin] is the second part alone. */
data class Drift(val behindMin: Int, val lateMin: Int, val reasons: List<String>)

/**
 * AD1: whether the day and the plan have come apart. Only movable work counts. A block that was started (a hold was recorded) or proven is not behind. Nothing is proposed while you are
 * in a lecture, at training, asleep or in the night, and what you already answered ([ackedAt], when you accepted or dismissed) is not counted again: a dismissed proposal needs fresh drift.
 * Null when nothing is behind, when behind is under [DRIFT_TRIGGER_MIN], or when there is no work left today to replan.
 */
fun detectDrift(
    pattern: List<PatternSlot>, now: ZonedDateTime, started: Set<String>, verified: Set<String>, ackedAt: ZonedDateTime? = null,
): Drift? {
    val today = now.toLocalDate()
    val cur = nowAndNext(pattern, now).now
    if (cur != null && cur.slot.type in BUSY_TYPES) return null
    if (nightWindow(pattern, now) != null) return null
    val movable = slotsFor(pattern, today, now.zone).filter { it.slot.type in MOVABLE_WORK && !it.slot.cancelled }
    val reasons = ArrayList<String>(); var behind = 0; var lateNow = 0
    for (d in movable.filter { !it.end.isAfter(now) }) {
        val key = blockKey(d)
        if (key in verified || key in started) continue
        if (ackedAt != null && !d.end.isAfter(ackedAt)) continue
        val m = Duration.between(d.start, d.end).toMinutes().toInt()
        behind += m; reasons += "${d.slot.title.substringBefore(" - ")} ($m minutes) passed without a start"
    }
    if (cur != null && cur.slot.type in MOVABLE_WORK && !cur.slot.cancelled) {
        val key = blockKey(cur)
        if (key !in verified && key !in started) {
            val from = if (ackedAt != null && ackedAt.isAfter(cur.start)) ackedAt else cur.start
            val late = Duration.between(from, now).toMinutes().toInt()
            if (late >= DRIFT_GRACE_MIN) { behind += late; lateNow = late; reasons += "${cur.slot.title.substringBefore(" - ")} has not started ($late minutes)" }
        }
    }
    if (behind < DRIFT_TRIGGER_MIN) return null
    if (movable.none { it.end.isAfter(now) }) return null
    return Drift(behind, lateNow, reasons)
}

/** The modifier that answers a drift, with its preview. */
data class DriftProposal(val drift: Drift, val instance: Instance, val preview: ModPreview, val shiftMin: Int?)

/**
 * AD1: the proposal for a drift is searched for, never assumed. When the block in front is late, the rest of today slides by the time it lost, so it can still run whole; if that
 * gives something up, a smaller slide is tried, then the day started again from now. The first that gives nothing up is offered, so accepting it is free. If every one of them would
 * give something up there is no proposal: a drift never asks for the wall, because being late is not wanting less.
 */
fun adaptDrift(d: Drift, pattern: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId = now.zone, anchors: Map<java.time.LocalDate, NightAnchors> = emptyMap()): DriftProposal? {
    val today = now.toLocalDate()
    val top = d.lateMin.coerceIn(0, 180)
    for (m in listOf(top, 45, 30, 20, 10, 5).filter { it in 5..top }.distinct()) {
        val inst = Instance(Preset.RUN_OVER, today, today, mapOf("minutes" to m.toString()))
        val pv = previewOf(inst, pattern, work, now, zone, anchors)
        if (pv.canApply && !pv.wallNeeded) return DriftProposal(d, inst, pv, m)
    }
    val ls = Instance(Preset.LATE_START, today, today, mapOf("from_now" to "true"))
    val pv = previewOf(ls, pattern, work, now, zone, anchors)
    return if (pv.canApply && !pv.wallNeeded) DriftProposal(d, ls, pv, null) else null
}

fun driftLine(d: Drift): String = "You are ${d.behindMin} minutes behind. Replan the rest of today from now?"

// ---------------------------------------------------------------- AD2: something came up

enum class Flex(val label: String) { FIXED("It cannot move"), FLEXIBLE("It can move") }

enum class SituationKind { ILL, GENERIC }

/** What happened, in the person's words, how long it takes, whether it can move, and optionally when it starts (null means now). */
data class Situation(val text: String, val minutes: Int, val flex: Flex = Flex.FIXED, val at: LocalTime? = null)

data class SituationPlan(val instance: Instance, val kind: SituationKind, val placedAt: String?, val notes: List<String> = emptyList())

private val ILL_WORDS = Regex("\\b(ill|sick|unwell|migraine|headache|flu|fever|vomiting|nauseous|exhausted|burnt out|burned out|not well)\\b", RegexOption.IGNORE_CASE)

/** AD2: ill is the one thing that is not an event but a lighter day; everything else is time taken. */
fun kindOf(text: String): SituationKind = if (ILL_WORDS.containsMatchIn(text)) SituationKind.ILL else SituationKind.GENERIC

/** The shortest thing Spark plans (the smallest block). A shorter thing is planned at this length and says so. */
const val MIN_EVENT_MIN = 15

private fun roundUp5(m: Int) = ((m + 4) / 5) * 5
private fun hhmm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

/** The first free slot today with room for [minutes] from [fromMin], or null. Only free time is looked at: nothing is moved to make room. */
fun gapStart(pattern: List<PatternSlot>, date: java.time.LocalDate, fromMin: Int, minutes: Int, zone: ZoneId): Int? =
    slotsFor(pattern, date, zone).map { it.slot }.filter { it.type == "free" && !it.cancelled }
        .sortedBy { it.startMin }.firstNotNullOfOrNull { f -> val s = maxOf(f.startMin, fromMin); if (f.endMin <= 1439 && f.endMin - s >= minutes) s else null }

/** AD2: the modifier a situation becomes. It is an ordinary modifier, so the preview, the guard rails, the wall and the change count are the app's own. */
fun planSituation(s: Situation, pattern: List<PatternSlot>, now: ZonedDateTime, zone: ZoneId = now.zone, date: java.time.LocalDate = now.toLocalDate()): SituationPlan {
    val today = date
    val kind = kindOf(s.text)
    if (kind == SituationKind.ILL) return SituationPlan(Instance(Preset.RECOVERY, today, today, emptyMap()), kind, null, listOf("A lighter day: work is held to a small amount and what is set aside comes back afterwards."))
    val mins = s.minutes.coerceIn(MIN_EVENT_MIN, 600)
    val nowMin = if (date == now.toLocalDate()) now.hour * 60 + now.minute else 0
    val notes = ArrayList<String>()
    if (s.minutes < MIN_EVENT_MIN) notes += "Spark plans at least $MIN_EVENT_MIN minutes for anything."
    val start = when {
        s.at != null -> s.at.hour * 60 + s.at.minute
        s.flex == Flex.FLEXIBLE -> gapStart(pattern, today, roundUp5(nowMin), mins, zone)?.also { notes += "Placed in your next free time." } ?: roundUp5(nowMin).also { notes += "There is no free gap that long today, so it is placed now." }
        else -> roundUp5(nowMin)
    }
    var end = start + mins
    if (end > 1439) { end = 1439; notes += "Cut at midnight." }
    val title = s.text.trim().replace(Regex("\\s+"), " ").take(60).replaceFirstChar { it.uppercase() }.ifBlank { "Something came up" }
    return SituationPlan(Instance(Preset.ADD_EVENT, today, today, mapOf("title" to title, "type" to "errand", "start" to hhmm(start), "end" to hhmm(end), "return_work" to "true")), kind, hhmm(start), notes)
}

/**
 * The proposal for a situation: the plan as asked and its preview, and, when that cannot be made or has to give work up, the next best way already previewed: the first time today
 * that it fits without giving anything up, else the first such time tomorrow.
 */
data class Proposal(val primary: SituationPlan, val preview: ModPreview, val fallback: SituationPlan? = null, val fallbackPreview: ModPreview? = null) {
    /** What to offer first: the plan as asked if it can be made, else the fallback, else null. */
    val offer: Pair<SituationPlan, ModPreview>? get() = if (preview.canApply) primary to preview else if (fallback != null && fallbackPreview?.canApply == true) fallback to fallbackPreview else null
    /** The way that gives nothing up, if there is one: the plan as asked, or the fallback. */
    val freeOffer: Pair<SituationPlan, ModPreview>? get() = when {
        preview.canApply && !preview.wallNeeded -> primary to preview
        fallback != null && fallbackPreview?.canApply == true && !fallbackPreview.wallNeeded -> fallback to fallbackPreview
        else -> null
    }
    /** Why nothing can be offered, in the engine's own words. Never empty when [offer] is null. */
    val reasons: List<String> get() = if (offer != null) emptyList() else (preview.problems + (fallbackPreview?.problems.orEmpty())).distinct().ifEmpty { listOf("Nothing can be placed for that.") }
    /** True when the way offered first has to give work up, so it needs the typed wall. */
    val needsWall: Boolean get() = offer?.second?.wallNeeded == true
}

private fun scanFit(
    s: Situation, pattern: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId, anchors: Map<java.time.LocalDate, NightAnchors>,
    date: java.time.LocalDate, fromMin: Int, toMin: Int, allowLoss: Boolean = false,
): Pair<SituationPlan, ModPreview>? {
    val mins = s.minutes.coerceIn(MIN_EVENT_MIN, 600)
    var t = ((fromMin + 14) / 15) * 15
    var best: Pair<SituationPlan, ModPreview>? = null
    while (t + mins <= minOf(toMin, 1439)) {
        val plan = planSituation(s.copy(at = LocalTime.of(t / 60, t % 60), flex = Flex.FIXED), pattern, now, zone, date)
        val pv = previewOf(plan.instance, pattern, work, now, zone, anchors)
        if (pv.canApply && !pv.wallNeeded) return plan to pv
        if (allowLoss && pv.canApply && (best == null || pv.compiled.loosening.size < best.second.compiled.loosening.size)) best = plan to pv
        t += 15
    }
    return best
}

fun proposeFor(
    s: Situation, pattern: List<PatternSlot>, work: List<DueWork>, now: ZonedDateTime, zone: ZoneId = now.zone, anchors: Map<java.time.LocalDate, NightAnchors> = emptyMap(),
): Proposal {
    val p = planSituation(s, pattern, now, zone)
    val pv = previewOf(p.instance, pattern, work, now, zone, anchors)
    if (p.kind == SituationKind.ILL || (pv.canApply && !pv.wallNeeded)) return Proposal(p, pv)
    val why = if (!pv.canApply) "As asked it does not fit: ${pv.problems.firstOrNull().orEmpty()}" else "As asked it gives something up: ${pv.compiled.loosening.firstOrNull().orEmpty()}"
    val nowMin = now.hour * 60 + now.minute
    scanFit(s, pattern, work, now, zone, anchors, now.toLocalDate(), nowMin + 1, 22 * 60)?.let { (plan, apv) ->
        return Proposal(p, pv, plan.copy(notes = listOf("$why This is the next time it fits without giving anything up, ${plan.placedAt}.")), apv)
    }
    val later = (1L..3L).map { now.toLocalDate().plusDays(it) }
    fun word(d: java.time.LocalDate) = if (d == now.toLocalDate().plusDays(1)) "tomorrow" else d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.UK))
    for (d in later) scanFit(s, pattern, work, now, zone, anchors, d, 8 * 60, 21 * 60)?.let { (plan, apv) ->
        return Proposal(p, pv, plan.copy(notes = listOf("$why There is no room today. On ${word(d)} at ${plan.placedAt} it fits without giving anything up.")), apv)
    }
    // nothing fits without giving something up: the way that gives up the least is offered, and it says what it costs and that it needs the wall
    val today = scanFit(s, pattern, work, now, zone, anchors, now.toLocalDate(), nowMin + 1, 22 * 60, allowLoss = true)
    val next = later.mapNotNull { d -> scanFit(s, pattern, work, now, zone, anchors, d, 8 * 60, 21 * 60, allowLoss = true)?.let { it to word(d) } }
    val pick = (listOfNotNull(today?.let { it to "today" }) + next).minWithOrNull(compareBy({ it.first.second.compiled.loosening.size }))
    if (pick != null) {
        val (plan, apv) = pick.first
        return Proposal(p, pv, plan.copy(notes = listOf("$why Nothing fits without giving something up. The least it gives up is on ${pick.second} at ${plan.placedAt}: ${apv.compiled.loosening.firstOrNull().orEmpty()} It needs the typed wall.")), apv)
    }
    return Proposal(p, pv)
}

// ---------------------------------------------------------------- AD3: the ladder

/** How much of the day Spark asks for. A level never lowers a protected thing (sleep, the Bible floor, hard commitments): it only changes how much more is asked on top. */
enum class DayLevel(val label: String, val routineCap: Tier?, val lite: Boolean) {
    FULL("Full day", null, false),
    SHORT("Short day", Tier.SHORT, false),
    FLOOR("Floor day", Tier.FLOOR, false),
    THREE("Three things", Tier.FLOOR, true),
}

/** The work still planned today, the free time left, and how far behind you are. */
data class Squeeze(val plannedMin: Int, val freeMin: Int, val behindMin: Int) {
    val requiredMin: Int get() = plannedMin + behindMin
    val roomMin: Int get() = plannedMin + freeMin
}

fun squeezeOf(pattern: List<PatternSlot>, now: ZonedDateTime, behindMin: Int): Squeeze {
    val today = slotsFor(pattern, now.toLocalDate(), now.zone).filter { !it.slot.cancelled && it.end.isAfter(now) }
    fun left(d: DatedSlot) = Duration.between(if (d.start.isAfter(now)) d.start else now, d.end).toMinutes().toInt()
    return Squeeze(today.filter { it.slot.type in MOVABLE_WORK }.sumOf(::left), today.filter { it.slot.type == "free" }.sumOf(::left), behindMin)
}

/** AD3: the level from the squeeze. Nothing steps down while nothing is behind. Stepping back up needs a margin so the level does not flicker at a boundary. */
fun dayLevel(sq: Squeeze, prev: DayLevel? = null): DayLevel {
    if (sq.behindMin <= 0) return DayLevel.FULL
    fun at(r: Double) = when { r <= 0.75 -> DayLevel.FULL; r <= 1.0 -> DayLevel.SHORT; r <= 1.4 -> DayLevel.FLOOR; else -> DayLevel.THREE }
    val r = if (sq.roomMin <= 0) 9.0 else sq.requiredMin.toDouble() / sq.roomMin
    val raw = at(r)
    return if (prev != null && raw.ordinal < prev.ordinal && at(r + 0.1).ordinal >= prev.ordinal) prev else raw
}

/** A routine runs at its own tier or the level's, whichever is smaller. A floor is never below its floor. */
fun capTier(t: Tier, level: DayLevel): Tier = level.routineCap?.let { if (it.ordinal > t.ordinal) it else t } ?: t

/** One of the three things that matter now. */
data class Matter(val title: String, val start: ZonedDateTime, val why: String)

private val HARD_TYPES = setOf("lecture", "tutorial", "mytutor", "training", "match", "gym", "church")

/**
 * AD3: the three things that matter from now: the next commitment that does not move, the next thing that looks after you (the routine, a meal, the shutdown), and the next work block.
 * Chronological. When fewer than three of those exist the next other things fill the gap, but free time never does.
 */
fun threeThatMatter(pattern: List<PatternSlot>, now: ZonedDateTime, verified: Set<String> = emptySet()): List<Matter> {
    val upcoming = slotsFor(pattern, now.toLocalDate(), now.zone).filter { !it.slot.cancelled && it.end.isAfter(now) && it.slot.type != "free" && it.slot.type != REMINDER_TYPE && blockKey(it) !in verified }.sortedBy { it.start }
    fun m(d: DatedSlot, why: String) = Matter(d.slot.title.substringBefore(" - "), d.start, why)
    val picks = LinkedHashMap<String, Matter>()
    upcoming.firstOrNull { it.slot.hardAlarm || it.slot.type in HARD_TYPES }?.let { picks[blockKey(it)] = m(it, "It does not move") }
    upcoming.firstOrNull { it.slot.type in setOf("routine", "meal", "sleep", "chore") }?.let { picks[blockKey(it)] = m(it, "It looks after you") }
    upcoming.firstOrNull { it.slot.type in MOVABLE_WORK }?.let { picks[blockKey(it)] = m(it, "The work that matters most now") }
    for (d in upcoming) { if (picks.size >= 3) break; if (blockKey(d) !in picks) picks[blockKey(d)] = m(d, "Next") }
    return picks.values.sortedBy { it.start }.take(3)
}

/** AD3: where Lite comes from. Your own switch always wins; the ladder turns it on at the bottom step and off again when the day recovers, unless you sent it away for today. */
enum class LiteSource { NONE, MANUAL, AUTO }

fun liteAfter(current: LiteSource, level: DayLevel, dismissedToday: Boolean): LiteSource = when {
    current == LiteSource.MANUAL -> LiteSource.MANUAL
    dismissedToday -> LiteSource.NONE
    level.lite -> LiteSource.AUTO
    current == LiteSource.AUTO -> LiteSource.NONE
    else -> current
}
