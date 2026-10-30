package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Rewards (layer 6, section 9): the weekly money guide, earned phone access, relief, progress and the reward tied to a block.
 * The money is a guide only: the app never touches a bank (section 9).
 */

// ---- Money: Y4, R7 to R9, G6 ----

/** Y4: a block counts toward the ratio at this heaviness. Work blocks and lecture attendance count; other fixed commitments are proven but stay out. */
fun blockHeaviness(type: String): Int? = when (type) {
    "lecture", "tutorial" -> 4
    "study-standard" -> 6
    "study-light" -> 3
    "study", "code" -> 5
    else -> null
}

/** One block of a week with what it was planned at and what was confirmed (W2). Weights are heaviness times hours (Q-G3). */
data class WeekBlock(val key: String, val title: String, val type: String, val plannedMin: Int, val creditedMin: Int, val heaviness: Int) {
    val plannedWeight: Double get() = heaviness * plannedMin / 60.0
    val verifiedWeight: Double get() = heaviness * minOf(creditedMin, plannedMin) / 60.0
}

/** The Monday of the week [date] is in. The week runs Monday to Sunday (R9). */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Every block of the week from [monday] that counts toward the ratio, with the minutes confirmed for it (key to minutes) (Y4). */
fun weekBlocks(slots: List<PatternSlot>, monday: LocalDate, credited: Map<String, Int>, zone: ZoneId = LONDON): List<WeekBlock> =
    (0L..6L).flatMap { slotsFor(slots, monday.plusDays(it), zone) }.mapNotNull { ds ->
        val h = blockHeaviness(ds.slot.type) ?: return@mapNotNull null
        val mins = Duration.between(ds.start, ds.end).toMinutes().toInt()
        WeekBlock(blockKey(ds), ds.slot.title, ds.slot.type, mins, credited[blockKey(ds)] ?: 0, h)
    }

/** The week's result (R7, R8): the ratio, the money, and the numbers behind it for the working on tap. */
data class WeekResult(
    val monday: LocalDate, val blocks: List<WeekBlock>, val ratio: Double, val money: WeeklyMoney,
    val plannedHours: Double, val verifiedHours: Double,
) {
    val plannedWeight: Double get() = blocks.sumOf { it.plannedWeight }
    val verifiedWeight: Double get() = blocks.sumOf { it.verifiedWeight }
}

fun weekResult(slots: List<PatternSlot>, monday: LocalDate, credited: Map<String, Int>, zone: ZoneId = LONDON): WeekResult {
    val blocks = weekBlocks(slots, monday, credited, zone)
    val planned = blocks.map { WeightedWork(it.heaviness, it.plannedMin / 60.0) }
    val verified = blocks.map { WeightedWork(it.heaviness, minOf(it.creditedMin, it.plannedMin) / 60.0) }
    val p = planned.sumOf { it.weight }; val v = verified.sumOf { it.weight }
    val ratio = if (p <= 0.0) 0.0 else (v / p).coerceIn(0.0, 1.0)
    return WeekResult(monday, blocks, ratio, weeklyMoney(verified, planned),
        blocks.sumOf { it.plannedMin } / 60.0, blocks.sumOf { minOf(it.creditedMin, it.plannedMin) } / 60.0)
}

/** R8: the working shown on tap, one line per block with weight, then the sum and the payout. */
fun WeekResult.working(): List<String> = buildList {
    add("Food: ${pounds(FOOD_PENCE)}, guaranteed every week, never tied to work.")
    blocks.filter { it.creditedMin > 0 }.forEach { add("${it.title}: ${it.heaviness} x ${fmt1(minOf(it.creditedMin, it.plannedMin) / 60.0)}h = ${fmt1(it.verifiedWeight)}") }
    add("Confirmed ${fmt1(verifiedWeight)} of ${fmt1(plannedWeight)} planned")
    add("Free to spend = ${pounds(FREE_CAP_PENCE)} x ${fmt1(verifiedWeight)} / ${fmt1(plannedWeight)} = ${pounds(money.freeToSpendPence)}")
    add("Moved to savings: ${pounds(money.savingsPence)}")
}

/** R4: "14 of 60 hours done". */
fun WeekResult.progress(): String = "${fmt1(verifiedHours)} of ${fmt1(plannedHours)} hours done"

/** G6, R20-F5: the first 14 days record only, so money is shown from the 15th day and the week it falls in counts from its own start. */
fun moneyLive(firstUse: ZonedDateTime, now: ZonedDateTime): Boolean = moneyIsLive(Duration.between(firstUse, now).toDays())

// ---- Earned phone access: R2, O3, Y8, R5 ----

/** O3: 10 minutes of open phone per hour of confirmed work. */
const val EARNED_MIN_PER_HOUR = 10

fun earnedAccessMinutes(creditedMin: Int): Int = creditedMin * EARNED_MIN_PER_HOUR / 60

/** A completed block's earnings: when it was proven and how many minutes it bought. */
data class Earning(val atMs: Long, val minutes: Int)

/** Which blocks earned access can lift (Q-G67): transitions and planning blocks. Never a work block, the getting-ready block, the night cutoff or the morning block (Y8, R2, R5). */
val ACCESS_LIFTS = setOf(BlockReason.TRANSITION, BlockReason.PLANNING)

/**
 * R2: whether earned access is open at [now]. Minutes earned today go into a bank, and the bank is spent only while a transition or planning
 * block would have closed the phone, minute by minute, so none is wasted while the phone is open anyway. What is left is gone at midnight.
 */
fun earnedAccessActive(now: ZonedDateTime, earnings: List<Earning>, slots: List<PatternSlot>): Boolean {
    val dayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
    val today = earnings.filter { it.atMs >= dayStart && it.minutes > 0 }.sortedBy { it.atMs }
    if (today.isEmpty()) return false
    val liftable = (-1L..0L).flatMap { slotsFor(slots, now.toLocalDate().plusDays(it), now.zone) }
        .filter { firmReasonFor(it.slot.type) in ACCESS_LIFTS }
        .map { it.start.toInstant().toEpochMilli() to it.end.toInstant().toEpochMilli() }
    val nowMs = now.toInstant().toEpochMilli()
    var bank = 0
    var t = (today.first().atMs / 60_000) * 60_000
    var i = 0
    while (t <= nowMs) {
        while (i < today.size && today[i].atMs <= t + 59_999) { bank += today[i].minutes; i++ }
        val inLiftable = liftable.any { t >= it.first && t < it.second }
        if (t + 60_000 > nowMs) return inLiftable && bank > 0          // the minute that holds "now"
        if (inLiftable && bank > 0) bank--
        t += 60_000
    }
    return false
}

// ---- Relief and reward: R1, R3 ----

/** R1: what completing a block kept out of the Buffer. A lecture or work block that is missed comes back as work; a proven one does not. */
fun reliefHours(slot: PatternSlot, lengthMin: Int): Double = when (slot.type) {
    "lecture", "tutorial" -> lengthMin / 60.0
    "study-standard" -> STUDY_CYCLE_STANDARD_HOURS
    "study-light" -> STUDY_CYCLE_LIGHT_HOURS
    "study", "code" -> lengthMin / 60.0
    else -> 0.0
}

/** R3: the physical reward after a block: the next meal or free slot, with its time. Null if none is coming today. */
fun rewardNext(slots: List<PatternSlot>, after: ZonedDateTime): DatedSlot? =
    (0L..1L).flatMap { slotsFor(slots, after.toLocalDate().plusDays(it), after.zone) }
        .filter { it.start >= after && (it.slot.type == "meal" || it.slot.type == "free") }.minByOrNull { it.start }
        ?.takeIf { it.start.toLocalDate() == after.toLocalDate() || Duration.between(after, it.start).toHours() < 12 }

/** The line shown when a block is verified: relief, access, reward (R1, R2, R3). */
fun verifiedLines(slot: PatternSlot, plannedMin: Int, creditedMin: Int, slots: List<PatternSlot>, now: ZonedDateTime): List<String> = buildList {
    val relief = reliefHours(slot, plannedMin)
    if (relief > 0) add("This kept ${fmt1(relief)}h out of the Buffer.")
    val access = earnedAccessMinutes(creditedMin)
    if (access > 0 && blockHeaviness(slot.type) != null && slot.type != "lecture" && slot.type != "tutorial") add("Earned $access minutes of open phone for transitions and planning.")
    rewardNext(slots, now)?.let { add("Next: ${it.slot.title.substringBefore(" - ")} at ${"%02d:%02d".format(it.start.hour, it.start.minute)}.") }
}

// ---- F1: a free slot has a start, a stop and a next block waiting ----

data class FreeSlotInfo(val slot: DatedSlot, val next: DatedSlot?)

/** F1: the free slot running at [now] and the block after it. All apps are open during it, and the stop is announced by an alarm. */
fun freeSlotNow(slots: List<PatternSlot>, now: ZonedDateTime): FreeSlotInfo? {
    val all = (-1L..1L).flatMap { slotsFor(slots, now.toLocalDate().plusDays(it), now.zone) }.sortedBy { it.start }
    val cur = all.lastOrNull { it.slot.type == "free" && !it.start.isAfter(now) && now.isBefore(it.end) } ?: return null
    return FreeSlotInfo(cur, all.firstOrNull { it.start >= cur.end })
}
