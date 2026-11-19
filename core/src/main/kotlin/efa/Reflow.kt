package efa

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * RF1 to RF9: the general reflow. A modifier is a span of time (a start and an end, each with a date and a time) with a goal, and the day is built again from what
 * cannot move. [buildDay] takes one day of the calendar and returns the changes that make it fit: commitments (lectures, matches, church, errands, anything dated,
 * the lecture study cycles and the protected free time) stay where they are; the start of the day, the meals, the chores and the work and free time between them
 * are laid out again around them. The goal is one of two things:
 *  - slide: nothing is added; the movable blocks follow a new start of the day (a late wake, or a day that starts from a given time);
 *  - devote: the window is given over to a number of whole study blocks by module, laid in the gaps the commitments leave.
 * Nothing here knows about wake time or about catch-up weeks: those are only the rules a preset passes in.
 */
enum class Role { FIXED, CHAIN, NIGHT, MEAL, CHORE, WORK, FREE, RESET }

const val MEAL_BACK_MIN = 60                         // a meal may move this much earlier than planned
const val MEAL_FORWARD_MIN = 180                     // and this much later
const val MEAL_SPACING_MIN = 150                     // a meal that has moved keeps this long after the one before it
const val MEAL_LATEST_START = 21 * 60 + 30           // no meal starts later than this
const val BREAKFAST_LATEST_START = 13 * 60           // a day that starts after this has no breakfast
const val CHORE_FORWARD_MIN = 240
const val WORK_MIN_PIECE = 30                        // a work block is never trimmed below this
const val MIN_STUDY_BLOCK_MIN = 45
const val MAX_SPAN_DAYS = 28

/** What a cycle is made of (the steps of the study cycle, in minutes at the standard length). A block of another length scales every step by the same share. */
val CYCLE_STEPS = listOf("Video or slides" to 15, "Read" to 25, "Notes" to 60, "Practice" to 30, "Error log" to 10)
private val CYCLE_STEP_TOTAL = CYCLE_STEPS.sumOf { it.second }
private val CYCLE_TYPES = setOf("study-standard", "study-light")
private val FIXED_TYPES = setOf("lecture", "tutorial", "match", "church", "mytutor", "training", "gym", "errand")

/** The minutes of each step in a block of [lengthMin] minutes, for example "Video or slides 16 · read 27 · notes 64 ...". */
fun cycleSteps(lengthMin: Int): List<Pair<String, Int>> = CYCLE_STEPS.map { it.first to Math.round(it.second.toDouble() * lengthMin / CYCLE_STEP_TOTAL).toInt() }
fun cycleStepsText(lengthMin: Int): String = cycleSteps(lengthMin).joinToString(" · ") { "${it.first.replaceFirstChar { c -> c.lowercase() }} ${it.second}".replaceFirstChar { c -> c.uppercase() } }
fun notesMinutes(lengthMin: Int): Int = cycleSteps(lengthMin).first { it.first == "Notes" }.second

/** Whole study blocks by module, in the gaps. [floor] counts the lecture cycles the day already has; [stretch] is for a good day, and only while backlog is left. */
data class Devote(
    val labels: List<String>, val slotMin: Int, val breakMin: Int, val floor: Int, val stretch: Int, val from: Int, val to: Int, val perLabel: Int,
    val minSlotMin: Int = MIN_STUDY_BLOCK_MIN, val backlogMin: Int = Int.MAX_VALUE, val suffix: String = ": notes and problems",
    val replaceCode: Boolean = true,                 // project and code blocks (their proof is a commit) are replaced too, or kept
)

/** [wake] is the minute the day starts, or null for as planned. [from] and [to] are the minutes of this day that the span covers. */
data class Rules(
    val wake: Int? = null, val from: Int = 0, val to: Int = 1440, val devote: Devote? = null,
    val protectedFree: Set<Pair<LocalDate, Int>> = emptySet(),
    val extra: List<PatternSlot> = emptyList(),   // blocks that are about to be added: fixed, and the day is laid out around them (YP2)
    val laneFrom: Int? = null,          // nothing movable is laid before this minute: a block that is running over holds the time until then (RN2)
)

data class Built(
    val slots: List<PatternSlot> = emptyList(), val problems: List<String> = emptyList(), val dropped: List<String> = emptyList(),
    val droppedMeals: Set<String> = emptySet(), val blocks: Int = 0, val moved: Int = 0, val startedAt: Int? = null,
    val notes: List<String> = emptyList(),      // things worth saying that give nothing up
)

/**
 * L2, the yield policy (YP1). When time runs out, things give way in this order, each tried fully before the next: unprotected free time, resets, meals (slid inside
 * their window, dropped last), the routine (trimmed to its minimum, optional items dropped), chores (slid, never dropped), work (shortened to [WORK_MIN_PIECE], then
 * dropped, and it comes back as work, RS1). Never: sleep, hard alarms, commitments, protected free time. A refusal is the last step, and says what is in the way.
 */
object YieldPolicy {
    val ORDER = listOf("free time", "resets", "meals", "routine", "chores", "work")
    /** The least a routine block may be cut to. Null means it cannot be cut, only dropped (breakfast) or kept whole. */
    fun routineMin(title: String): Int? = when {
        title.startsWith("Bible") -> 10               // "minimum 10 minutes on the worst morning"
        title.startsWith("Wake") -> 10
        else -> null
    }
}

/** The morning routine laid from [start] so that it ends by [limit]: whole if it fits, else breakfast goes, then the Bible and the wake are cut to their minimum. Null if even that does not fit. */
private fun fitChain(chain: List<PatternSlot>, start: Int, limit: Int, date: LocalDate): Pair<List<Pair<PatternSlot, Int>>, List<String>>? {
    val keep = BooleanArray(chain.size) { true }
    val len = chain.map { it.endMin - it.startMin }.toMutableList()
    val said = ArrayList<String>()
    fun end() = start + chain.indices.filter { keep[it] }.sumOf { len[it] }
    // breakfast is never placed after its cut-off, wherever it would fall
    var t = start
    for (i in chain.indices) { if (chain[i].title.startsWith("Breakfast") && t > BREAKFAST_LATEST_START) { keep[i] = false; said += "Breakfast on ${label(date)} is dropped: the day starts at ${clock(start)}, too late for it." }; if (keep[i]) t += len[i] }
    if (end() > limit) chain.indices.filter { keep[it] && chain[it].title.startsWith("Breakfast") }.forEach { keep[it] = false; said += "Breakfast on ${label(date)} is dropped: there are only ${limit - start} minutes before ${clock(limit)}." }
    for (i in chain.indices.sortedBy { if (chain[it].title.startsWith("Bible")) 0 else 1 }) if (end() > limit && keep[i]) YieldPolicy.routineMin(chain[i].title)?.takeIf { it < len[i] }?.let { m ->
        said += "${chain[i].title.substringBefore(",")} on ${label(date)} is shortened from ${len[i]} to $m minutes: only ${limit - start} minutes before ${clock(limit)}."; len[i] = m
    }
    if (end() > limit) return null
    return chain.indices.filter { keep[it] }.map { chain[it] to len[it] } to said
}

private fun label(d: LocalDate) = "${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
private fun clock(min: Int) = "%02d:%02d".format(min / 60, min % 60)

/** What each block of a day is for the reflow. The leading run of the morning routine and the trailing run of the night routine are chains; everything dated is fixed. */
/** [yieldTo] are the times of something about to be added: a transition that belongs to no commitment and lies in it gives way, because it was only ever the way between things. */
fun classify(sorted: List<DatedSlot>, protectedFree: Set<Pair<LocalDate, Int>>, yieldTo: List<Pair<Int, Int>> = emptyList()): List<Role> {
    val roles = MutableList(sorted.size) { Role.FIXED }
    var i = 0
    fun weeklyRoutine(k: Int) = sorted[k].slot.date == null && sorted[k].slot.type == "routine"
    if (sorted.isNotEmpty() && weeklyRoutine(0) && sorted[0].slot.title.startsWith("Wake")) {
        while (i < sorted.size && weeklyRoutine(i) && (i == 0 || sorted[i].slot.startMin == sorted[i - 1].slot.endMin)) { roles[i] = Role.CHAIN; i++ }
    }
    var j = sorted.size - 1
    while (j >= i && sorted[j].slot.date == null && (sorted[j].slot.type == "routine" || sorted[j].slot.type == "sleep" || sorted[j].slot.title.startsWith("Sleepy milk")) &&
        (j == sorted.size - 1 || sorted[j].slot.endMin == sorted[j + 1].slot.startMin)) { roles[j] = Role.NIGHT; j-- }
    for (k in sorted.indices) {
        if (roles[k] == Role.CHAIN || roles[k] == Role.NIGHT) continue
        val s = sorted[k].slot
        roles[k] = when {
            s.date != null || s.hardAlarm -> Role.FIXED
            s.type in FIXED_TYPES || s.type == "sleep" || s.type in CYCLE_TYPES -> Role.FIXED
            s.type == "meal" -> Role.MEAL
            s.type == "chore" -> Role.CHORE
            s.type == "study" || s.type == "code" -> Role.WORK
            s.type == "free" -> if ((sorted[k].plannedDay to s.startMin) in protectedFree) Role.FIXED else Role.FREE
            s.type == "travel" -> if (s.title.startsWith("Reset") || (yieldTo.any { it.first < s.endMin && it.second > s.startMin } && orphanTravel(sorted, k))) Role.RESET else Role.FIXED
            else -> Role.FIXED
        }
    }
    return roles
}

/** A transition with no commitment touching either end, such as the walk to a lecture that has been cancelled. */
private fun orphanTravel(sorted: List<DatedSlot>, k: Int): Boolean {
    val s = sorted[k].slot
    return sorted.indices.none { j ->
        val o = sorted[j].slot
        j != k && o.type != "travel" && (o.date != null || o.type in FIXED_TYPES) && (o.endMin in (s.startMin - 5)..s.startMin || o.startMin in s.endMin..(s.endMin + 5))
    }
}

/** The free gaps left in [lo, hi] once the busy ranges are taken out. */
private fun gapsIn(lo: Int, hi: Int, busy: List<Pair<Int, Int>>): MutableList<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>(); var cur = lo
    for ((s, e) in busy.sortedBy { it.first }) {
        if (e <= cur) continue
        if (s >= hi) break
        if (s > cur) out += cur to min(s, hi)
        cur = max(cur, e)
        if (cur >= hi) break
    }
    if (cur < hi) out += cur to hi
    return out
}

private class Gaps(var list: MutableList<Pair<Int, Int>>) {
    /** The start of a place of [len] minutes between [earliest] and [latest]: [prefer] if it is free, else the first that fits. */
    fun fit(len: Int, earliest: Int, latest: Int, prefer: Int? = null): Int? {
        fun fitsAt(t: Int) = t in earliest..latest && list.any { t >= it.first && t + len <= it.second }
        if (prefer != null && fitsAt(prefer)) return prefer
        for ((s, e) in list) { val t = max(s, earliest); if (t <= latest && t + len <= e) return t }
        return null
    }
    fun take(t: Int, len: Int) {
        val next = ArrayList<Pair<Int, Int>>()
        for ((s, e) in list) { if (t >= s && t + len <= e) { if (t - s > 0) next += s to t; if (e - (t + len) > 0) next += (t + len) to e } else next += s to e }
        list = next
    }
    fun largestFrom(from: Int): Pair<Int, Int>? = list.filter { it.second > from }.map { max(it.first, from) to it.second }.maxByOrNull { it.second - it.first }
}

/**
 * Builds one day again. Returns the cancellations and the new blocks that make the day fit the rules; a block that comes out the same as it went in is left alone.
 * Nothing changes on a day off. A day with neither a new start nor a devote goal is left as it is.
 */
fun buildDay(base: List<PatternSlot>, date: LocalDate, rules: Rules, dayIndex: Int = 0, zone: ZoneId = LONDON): Built {
    if ((isDayOff(base, date) && rules.extra.isEmpty()) || (rules.wake == null && rules.devote == null && rules.laneFrom == null)) return Built()
    val dow = date.dayOfWeek
    val midnight = java.time.LocalDateTime.of(date, java.time.LocalTime.MIDNIGHT)
    val added = rules.extra.filter { it.date == date }.map { DatedSlot(it, date, java.time.ZonedDateTime.of(midnight.plusMinutes(it.startMin.toLong()), zone), java.time.ZonedDateTime.of(midnight.plusMinutes(it.endMin.toLong()), zone)) }
    val sorted = (slotsFor(base, date, zone).filter { it.slot.type != REMINDER_TYPE } + added).sortedBy { it.slot.startMin }
    val roles = classify(sorted, rules.protectedFree, added.map { it.slot.startMin to it.slot.endMin })
    fun weekly(s: PatternSlot) = s.date == null && base.any { it.date == null && it.day == dow && it.startMin == s.startMin && it.type == s.type }
    fun inside(s: PatternSlot) = s.startMin >= rules.from && s.endMin <= rules.to
    val chain = sorted.indices.filter { roles[it] == Role.CHAIN }.map { sorted[it].slot }
    val night = sorted.indices.filter { roles[it] == Role.NIGHT }.map { sorted[it].slot }
    val dropped = ArrayList<String>(); val problems = ArrayList<String>(); val droppedMeals = HashSet<String>()
    val newBlocks = ArrayList<PatternSlot>()
    val originals = ArrayList<PatternSlot>()                                  // what may be replaced, all weekly

    // the start of the day
    val wake = rules.wake
    val relay = wake != null && chain.isNotEmpty() && chain.all { weekly(it) && it.startMin >= rules.from }
    var laneStart: Int
    var lastMealEnd: Int? = null
    val laneEnd = min(rules.to, night.firstOrNull()?.startMin ?: 1440)
    val notes = ArrayList<String>()
    if (relay && wake != null) {
        originals += chain
        val commitments = sorted.indices.filter { roles[it] == Role.FIXED }.map { sorted[it].slot }
        // the start yields to a commitment that is running: the day begins when it ends
        var start: Int = wake
        while (true) { val running = commitments.firstOrNull { it.startMin <= start && it.endMin > start } ?: break; notes += "${label(date)}: ${running.title.substringBefore(" - ")} runs until ${clock(running.endMin)}, so the day starts then."; start = running.endMin }
        val limit = min(laneEnd, commitments.filter { it.startMin >= start }.minOfOrNull { it.startMin } ?: laneEnd)
        val fit = fitChain(chain, start, limit, date)
            ?: return Built(problems = listOf("${label(date)}: the day cannot start at ${clock(start)}: ${commitments.firstOrNull { it.startMin >= start }?.title?.substringBefore(" - ") ?: "the night"} is at ${clock(limit)}, and the routine needs at least ${chain.sumOf { YieldPolicy.routineMin(it.title) ?: (it.endMin - it.startMin) } - (chain.firstOrNull { it.title.startsWith("Breakfast") }?.let { it.endMin - it.startMin } ?: 0)} minutes."))
        var t = start
        for ((c, l) in fit.first) { newBlocks += c.copy(startMin = t, endMin = t + l, date = date); if (c.title.startsWith("Breakfast")) lastMealEnd = t + l; t += l }
        for (c in chain) if (fit.first.none { it.first === c } || fit.first.first { it.first === c }.second != c.endMin - c.startMin) droppedMeals += "$date|${c.title}"
        dropped += fit.second
        laneStart = t
    } else {
        laneStart = max(max(rules.from, chain.lastOrNull()?.endMin ?: 0), rules.laneFrom ?: 0)
        lastMealEnd = chain.firstOrNull { it.title.startsWith("Breakfast") }?.endMin
    }
    val startedAt = if (relay) wake else chain.firstOrNull()?.startMin

    // what is busy: every commitment, and every block that cannot be replaced
    val dv = rules.devote
    val wFrom = if (dv != null) max(dv.from, max(laneStart, rules.from)) else laneStart
    val wTo = if (dv != null) min(dv.to, min(laneEnd, rules.to)) else laneEnd
    val busy = ArrayList<Pair<Int, Int>>()
    val items = ArrayList<PatternSlot>(); val itemRole = HashMap<PatternSlot, Role>()
    for ((k, ds) in sorted.withIndex()) {
        val s = ds.slot
        when (val r = roles[k]) {
            Role.CHAIN -> if (!relay) busy += s.startMin to s.endMin
            Role.NIGHT -> {}
            Role.FIXED -> busy += s.startMin to s.endMin
            else -> {
                val movable = weekly(s) && inside(s) && (dv == null || r == Role.MEAL || r == Role.CHORE || (s.endMin > wFrom && s.startMin < wTo && (dv.replaceCode || s.type != "code")))
                if (movable) { items += s; itemRole[s] = r } else busy += s.startMin to s.endMin
            }
        }
    }
    originals += items
    val gaps = Gaps(gapsIn(laneStart, laneEnd, busy))

    // meals and chores keep their place if it is still free, else move within their window, else a meal is dropped
    val followsMeal = HashMap<Int, Int>()                                     // the end a meal had -> where it is now
    for (m in items.filter { itemRole[it] == Role.MEAL || itemRole[it] == Role.CHORE }.sortedBy { it.startMin }) {
        val len = m.endMin - m.startMin; val isMeal = m.type == "meal"
        val earliest: Int; val latest: Int
        if (isMeal) {
            earliest = max(laneStart, m.startMin - MEAL_BACK_MIN)
            // never earlier than its own planned time; and when nothing has been eaten yet today, the first meal may go as late as any meal may (RN1)
            latest = max(m.startMin, if (lastMealEnd == null) MEAL_LATEST_START else min(m.startMin + MEAL_FORWARD_MIN, MEAL_LATEST_START))
        }
        else { earliest = max(laneStart, followsMeal[m.startMin] ?: m.startMin); latest = laneEnd }
        var t = gaps.fit(len, earliest, latest, prefer = m.startMin)
        if (t != null && t != m.startMin && isMeal && lastMealEnd != null && t < lastMealEnd + MEAL_SPACING_MIN) t = gaps.fit(len, max(earliest, lastMealEnd + MEAL_SPACING_MIN), latest)
        if (t == null) {
            if (isMeal) { dropped += "${m.title.substringBefore(" (")} on ${label(date)} is dropped: there is no place for it between ${clock(earliest)} and ${clock(latest)}."; droppedMeals += "$date|${m.title}" }
            else problems += "${m.title} on ${label(date)} has no place in the day."
            continue
        }
        gaps.take(t, len); newBlocks += m.copy(startMin = t, endMin = t + len, date = date)
        if (isMeal) { lastMealEnd = t + len; followsMeal[m.endMin] = t + len }
    }

    // work
    var cycles = 0; var placedMin = 0; var count = 0
    if (dv == null) {
        var cursor = laneStart
        for (w in items.filter { itemRole[it] == Role.WORK }.sortedBy { it.startMin }) {
            val len = w.endMin - w.startMin
            var t = gaps.fit(len, cursor, laneEnd, prefer = w.startMin); var l = len
            if (t == null) { val g = gaps.largestFrom(cursor); if (g != null && g.second - g.first >= WORK_MIN_PIECE) { t = g.first; l = min(len, g.second - g.first) } }
            if (t == null) { dropped += "${w.title} on ${label(date)} is dropped: there is no room left in the day."; continue }
            if (l < len) dropped += "${w.title.substringBefore(" - ")} on ${label(date)} is shortened from $len to $l minutes: there is no more room."
            gaps.take(t, l); newBlocks += w.copy(startMin = t, endMin = t + l, date = date); cursor = t + l; placedMin += l
            if (items.any { itemRole[it] == Role.RESET && it.startMin == w.endMin }) gaps.fit(RESET_MIN, t + l, t + l)?.let { r ->
                gaps.take(r, RESET_MIN); newBlocks += PatternSlot(dow, r, r + RESET_MIN, RESET_TITLE, "travel", "", false, MOD_NOTE + "Reset after a work block.", date = date) }
        }
    } else {
        val have = HashMap<String, Int>(); var haveCount = 0
        for ((k, ds) in sorted.withIndex()) if (ds.slot.type in CYCLE_TYPES && ds.slot.endMin > laneStart && ds.slot.startMin < laneEnd) {
            haveCount++; dv.labels.firstOrNull { ds.slot.title.contains(it.take(6), ignoreCase = true) }?.let { have[it] = (have[it] ?: 0) + 1 }
        }
        count = haveCount
        val order = dv.labels.indices.map { dv.labels[(it + dayIndex) % dv.labels.size] }
        val perLabel = HashMap(have)
        var stop = false
        for ((gs, ge) in gaps.list.toList()) {
            if (stop) break
            var t = max(gs, wFrom); val end = min(ge, wTo)
            while (t < end && count < max(dv.floor, dv.stretch)) {
                if (count >= dv.floor && placedMin >= dv.backlogMin) { stop = true; break }
                val len = min(dv.slotMin, end - t)
                if (len < dv.minSlotMin) break
                val lab = order.firstOrNull { (perLabel[it] ?: 0) < dv.perLabel }
                if (lab == null) { stop = true; break }
                newBlocks += PatternSlot(dow, t, t + len, lab + dv.suffix, "study", "", false, MOD_NOTE + "Catch-up block. " + cycleStepsText(len) + ".", date = date)
                perLabel[lab] = (perLabel[lab] ?: 0) + 1; count++; cycles++; placedMin += len; t += len
                if (t + dv.breakMin <= end && count < max(dv.floor, dv.stretch)) { newBlocks += PatternSlot(dow, t, t + dv.breakMin, RESET_TITLE, "travel", "", false, MOD_NOTE + "Reset after a study block.", date = date); t += dv.breakMin }
            }
        }
        if (count < dv.floor) problems += "${label(date)}: ${dv.floor - count} more study block${if (dv.floor - count == 1) "" else "s"} would be needed for the floor of ${dv.floor}, and there is no room for them."
    }
    // what the day gave up is free time again, but time that was empty to begin with stays empty
    val usedByNew = newBlocks.map { it.startMin to it.endMin }
    val wasMovable = items.map { it.startMin to it.endMin }
    for ((gs, ge) in gapsIn(laneStart, laneEnd, busy + usedByNew)) for ((ms, me) in wasMovable) {
        val s = max(gs, ms); val e = min(ge, me)
        if (e - s < MIN_PIECE_MIN) continue
        val orig = items.firstOrNull { itemRole[it] == Role.FREE && it.startMin == s && it.endMin == e }
        newBlocks += orig?.copy(date = date) ?: PatternSlot(dow, s, e, "Free time", "free", "", false, "", date = date)
    }

    // the difference: what was there and is not any more is cancelled, what is new is added
    fun same(a: PatternSlot, b: PatternSlot) = a.startMin == b.startMin && a.endMin == b.endMin && a.title == b.title && a.type == b.type
    val kept = originals.filter { o -> newBlocks.any { same(o, it) } }
    val out = ArrayList<PatternSlot>()
    for (o in originals) if (kept.none { it === o }) out += o.copy(date = date, cancelled = true)
    for (n in newBlocks) if (originals.none { same(it, n) }) out += n
    val moved = originals.count { o -> kept.none { it === o } }
    return Built(out.sortedWith(compareBy({ it.cancelled.not() }, { it.startMin })), problems, dropped, droppedMeals, newBlocks.count { it.type == "study" && it.notes.startsWith(MOD_NOTE) }, moved, startedAt, notes)
}

// ---------- the numbers ----------

private val WORK_TYPES = setOf("study", "code", "study-standard", "study-light")

/** One day, counted: the hours of study, how many blocks, how many cycles, the minutes of notes, free time and lectures, and the hours by module. */
data class DayNumbers(
    val date: LocalDate, val workMin: Int, val blocks: Int, val cycles: Int, val notesMin: Int, val freeMin: Int, val lectures: Int, val byModule: Map<String, Int>,
)

fun dayNumbers(slots: List<PatternSlot>, date: LocalDate, modules: List<String> = emptyList(), zone: ZoneId = LONDON): DayNumbers {
    val day = slotsFor(slots, date, zone).map { it.slot }
    val work = day.filter { it.type in WORK_TYPES }
    val cycleSlots = day.filter { it.type in CYCLE_TYPES || (it.type == "study" && it.title.endsWith(": notes and problems")) }
    val byModule = LinkedHashMap<String, Int>()
    for (c in cycleSlots) {
        val m = modules.firstOrNull { c.title.contains(it.take(6), ignoreCase = true) } ?: continue
        byModule[m] = (byModule[m] ?: 0) + (c.endMin - c.startMin)
    }
    return DayNumbers(date, work.sumOf { it.endMin - it.startMin }, work.size, cycleSlots.size, cycleSlots.sumOf { notesMinutes(it.endMin - it.startMin) },
        day.filter { it.type == "free" }.sumOf { it.endMin - it.startMin }, day.count { it.type == "lecture" }, byModule)
}

/** What a span comes to, counted after and before (RF8). The screen draws it as tiles and bars; [lines] says the same in words. */
data class PeriodSummary(val days: List<DayNumbers>, val daysBefore: List<DayNumbers>) {
    val count get() = days.size
    val workMin get() = days.sumOf { it.workMin }
    val workBeforeMin get() = daysBefore.sumOf { it.workMin }
    val perDayMin get() = if (days.isEmpty()) 0.0 else workMin.toDouble() / days.size
    val perDayBeforeMin get() = if (days.isEmpty()) 0.0 else workBeforeMin.toDouble() / days.size
    val cycles get() = days.sumOf { it.cycles }
    val notesMin get() = days.sumOf { it.notesMin }
    val freeMin get() = days.sumOf { it.freeMin }
    val freeBeforeMin get() = daysBefore.sumOf { it.freeMin }
    val lectures get() = days.sumOf { it.lectures }
    val byModule: Map<String, Int> get() = days.flatMap { it.byModule.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }

    fun lines(): List<String> {
        fun h(min: Int) = fmt1(min / 60.0)
        val out = ArrayList<String>()
        out += "Study and work: ${h(workMin)}h over $count day${if (count == 1) "" else "s"}, about ${fmt1(perDayMin / 60.0)}h a day (it was ${fmt1(perDayBeforeMin / 60.0)}h)."
        if (cycles > 0) out += "$cycles study cycle${if (cycles == 1) "" else "s"}, with ${h(notesMin)}h of notes."
        if (lectures > 0) out += "$lectures lecture${if (lectures == 1) "" else "s"} still on the schedule."
        out += "Free time: ${h(freeMin)}h (it was ${h(freeBeforeMin)}h)."
        if (byModule.isNotEmpty()) out += byModule.entries.joinToString(" · ") { "${it.key.substringBefore(" and ")} ${h(it.value)}h" }
        if (count > 1) days.forEach { d -> out += "${label(d.date)}: ${h(d.workMin)}h, ${d.blocks} block${if (d.blocks == 1) "" else "s"}${if (d.freeMin > 0) ", ${h(d.freeMin)}h free" else ""}." }
        return out
    }
}

/** The days of a span, counted before and after: hours a day, blocks, cycles, notes, free time, and the hours by module. Null when every day is off. */
fun periodSummary(before: List<PatternSlot>, after: List<PatternSlot>, dates: List<LocalDate>, modules: List<String> = emptyList(), zone: ZoneId = LONDON): PeriodSummary? {
    val live = dates.filter { !isDayOff(after, it) }
    if (live.isEmpty()) return null
    return PeriodSummary(live.map { dayNumbers(after, it, modules, zone) }, live.map { dayNumbers(before, it, modules, zone) })
}

fun periodNumbers(before: List<PatternSlot>, after: List<PatternSlot>, dates: List<LocalDate>, modules: List<String> = emptyList(), zone: ZoneId = LONDON): List<String> =
    periodSummary(before, after, dates, modules, zone)?.lines().orEmpty()

// ---------- L3: the one entry for "something changes the plan" (YP2) ----------

/**
 * A disturbance is something at a point in time that changes the day: it starts later, a block runs longer, or time is taken by something new. Every modifier that reacts
 * to a change is one of these with different numbers; the policy that decides what gives way is [YieldPolicy], the same for all of them.
 */
sealed class Disturbance {
    data class StartsAt(val minute: Int) : Disturbance()
    data class RunsOver(val nowMin: Int, val minutes: Int) : Disturbance()
    data class TimeTaken(val fromMin: Int, val toMin: Int, val title: String, val type: String, val nowMin: Int = 0) : Disturbance()
    /** Several things on one day, laid out together so that none of them is placed around another one's leftovers (YP2). */
    data class Several(val events: List<TimeTaken>) : Disturbance()
    /** Time is freed: what was planned and matches [what] is not happening. Its time becomes free time (YP4). */
    data class TimeFreed(val what: String, val nowMin: Int = 0) : Disturbance()
}

/** What a cancellation may take away: the commitments and the lecture cycles. Never sleep, a meal or the routine. */
val CANCELLABLE_TYPES = setOf("lecture", "tutorial", "mytutor", "training", "gym", "church", "match", "errand", "study-standard", "study-light")

/** An event may go in free time, over work, meals and chores (they give way) but never over a commitment, sleep, a hard alarm, the morning routine or the night routine. */
fun eventClash(base: List<PatternSlot>, date: LocalDate, e: Disturbance.TimeTaken, zone: ZoneId = LONDON): String? {
    if (e.toMin <= e.fromMin) return "The event ends before it starts."
    val sorted = slotsFor(base, date, zone).filter { it.slot.type != REMINDER_TYPE }.sortedBy { it.slot.startMin }
    val roles = classify(sorted, emptySet(), listOf(e.fromMin to e.toMin))
    for ((i, ds) in sorted.withIndex()) {
        if (roles[i] in setOf(Role.FIXED, Role.NIGHT, Role.CHAIN) && ds.slot.startMin < e.toMin && ds.slot.endMin > e.fromMin)
            return "${e.title} overlaps ${ds.slot.title.substringBefore(" - ")} at ${clock(ds.slot.startMin)}, which does not move."
    }
    return null
}

fun replanDay(base: List<PatternSlot>, date: LocalDate, d: Disturbance, protectedFree: Set<Pair<LocalDate, Int>> = emptySet(), zone: ZoneId = LONDON): Built = when (d) {
    is Disturbance.StartsAt -> buildDay(base, date, Rules(wake = d.minute, protectedFree = protectedFree), 0, zone)
    is Disturbance.RunsOver -> buildDay(base, date, Rules(from = d.nowMin, laneFrom = ((d.nowMin + d.minutes + 4) / 5) * 5, protectedFree = protectedFree), 0, zone)
    is Disturbance.TimeFreed -> {
        val hits = slotsFor(base, date, zone).map { it.slot }.filter { it.date == null && it.type in CANCELLABLE_TYPES && d.what.isNotBlank() && it.title.contains(d.what.trim(), ignoreCase = true) && it.endMin > d.nowMin }
        if (hits.isEmpty()) Built(problems = listOf("${label(date)}: nothing planned matches \"${d.what.trim()}\"."))
        else Built(
            hits.flatMap { listOf(it.copy(date = date, cancelled = true), PatternSlot(date.dayOfWeek, it.startMin, it.endMin, "Free time (cancelled)", "free", "", false, MOD_NOTE + "Cancelled: ${it.title.substringBefore(" - ")}.", date = date)) },
            dropped = hits.map { "${it.title.substringBefore(" - ")} on ${label(date)} is cancelled." })
    }
    is Disturbance.TimeTaken -> replanDay(base, date, Disturbance.Several(listOf(d)), protectedFree, zone)
    is Disturbance.Several -> {
        val sorted = d.events.sortedBy { it.fromMin }
        val problems = sorted.mapNotNull { eventClash(base, date, it, zone) } + sorted.zipWithNext().filter { (a, b) -> b.fromMin < a.toMin }.map { (a, b) -> "${b.title} overlaps ${a.title}." }
        if (problems.isNotEmpty() || sorted.isEmpty()) Built(problems = problems.map { "${label(date)}: $it" })
        else {
            val events = sorted.map { PatternSlot(date.dayOfWeek, it.fromMin, it.toMin, it.title, it.type, "", false, MOD_NOTE + "Added event.", date = date) }
            // the events are the person's own choice, so they may take protected free time too; everything movable gives way around them
            val b = buildDay(base, date, Rules(from = sorted.minOf { it.nowMin }, laneFrom = 0, extra = events), 0, zone)
            b.copy(slots = events + b.slots)
        }
    }
}