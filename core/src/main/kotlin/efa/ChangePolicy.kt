package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * CE1: every change to the plan has one of four classes, decided by what it does to obligations, not by which screen it came from.
 * REALITY reports what happened; ADD adds work, events or rules; MOVE keeps the same work at other times; LOOSEN removes or shortens an obligation.
 */
enum class ChangeClass { REALITY, ADD, MOVE, LOOSEN }

/** One counted change: a class and whether it was an import (CE5). The week is Monday to Sunday, London time (CE5). */
data class ChangeEntry(val cls: ChangeClass, val at: Instant, val import: Boolean = false, val id: Long = 0)

data class ChangeLimits(
    val freeMovesPerWeek: Int = 5, val shortWall: Int = 25,
    val freeLoosenPerWeek: Int = 2, val lockAtLoosen: Int = 4,
    val freeImportsPerWeek: Int = 2, val cooldownAtImport: Int = 5, val importCooldownHours: Long = 24,
    val trialDays: Int = 7,
)

sealed interface ChangeDecision {
    /** [counted] is false for REALITY and ADD, which are free and unlimited (CE2). [trial] is true during the first days, when counts are kept and nothing locks (CE6). */
    data class Free(val counted: Boolean, val trial: Boolean = false) : ChangeDecision
    data class Wall(val length: Int, val reason: String) : ChangeDecision
    data class Cooldown(val until: Instant, val reason: String) : ChangeDecision
    data class Locked(val until: Instant, val reason: String) : ChangeDecision
}

/** Monday 00:00 of the week [at] is in, London time. */
fun weekStart(at: Instant, zone: ZoneId = LONDON): Instant =
    at.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()

fun nextWeekStart(at: Instant, zone: ZoneId = LONDON): Instant =
    at.atZone(zone).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()

/**
 * CE2 to CE6: what a change costs. REALITY and ADD are free. MOVE: 5 free a week, then a short string. LOOSEN: the existing wall for the first two a week, longer for the
 * third, and the fourth locks loosening until Monday. An import also counts against the import allowance: 2 free a week, then the short string, and after 5 a cooldown.
 * During the trial (the first 7 days after install) everything is free but counted. [bundleClass] is the most costly class in an import.
 */
fun decideChange(
    history: List<ChangeEntry>, cls: ChangeClass, isImport: Boolean, now: Instant, installedAt: Instant,
    limits: ChangeLimits = ChangeLimits(), zone: ZoneId = LONDON,
): ChangeDecision {
    val trial = Duration.between(installedAt, now) < Duration.ofDays(limits.trialDays.toLong())
    if (cls == ChangeClass.REALITY || cls == ChangeClass.ADD) {
        if (!isImport) return ChangeDecision.Free(counted = false, trial = trial)
    }
    val from = weekStart(now, zone)
    val week = history.filter { !it.at.isBefore(from) && !it.at.isAfter(now) }
    if (trial) return ChangeDecision.Free(counted = cls != ChangeClass.REALITY && cls != ChangeClass.ADD || isImport, trial = true)
    var wall = 0; var reason = ""
    if (isImport) {
        val imports = week.count { it.import }
        if (imports >= limits.cooldownAtImport) {
            val last = week.filter { it.import }.maxOf { it.at }
            val until = last.plus(Duration.ofHours(limits.importCooldownHours))
            if (until.isAfter(now)) return ChangeDecision.Cooldown(until, "Imports are closed until the cooldown ends. You can still check a file.")
        } else if (imports >= limits.freeImportsPerWeek) { wall = maxOf(wall, limits.shortWall); reason = "That is import ${imports + 1} this week; the first ${limits.freeImportsPerWeek} are free." }
    }
    when (cls) {
        ChangeClass.MOVE -> {
            val moves = week.count { it.cls == ChangeClass.MOVE }
            if (moves >= limits.freeMovesPerWeek) { wall = maxOf(wall, limits.shortWall); reason = "That is move ${moves + 1} this week; the first ${limits.freeMovesPerWeek} are free." }
        }
        ChangeClass.LOOSEN -> {
            val loosens = week.count { it.cls == ChangeClass.LOOSEN }
            if (loosens + 1 >= limits.lockAtLoosen) return ChangeDecision.Locked(nextWeekStart(now, zone), "You have used ${loosens} of ${limits.lockAtLoosen - 1} loosening changes this week. The next opens on Monday, or use break glass (once in 30 days).")
            val len = WALL_BASE + 25 * maxOf(0, loosens + 1 - limits.freeLoosenPerWeek)
            wall = maxOf(wall, len); reason = "Loosening change ${loosens + 1} this week."
        }
        else -> {}
    }
    return if (wall > 0) ChangeDecision.Wall(wall, reason) else ChangeDecision.Free(counted = true)
}

/** CE8: undoing a restore point refunds the count. */
fun refund(history: List<ChangeEntry>, id: Long): List<ChangeEntry> = history.filter { it.id != id }

/** CE10: a change that loosens the economy itself takes effect 24 hours after it is asked for. */
fun economyChangeEffectiveAt(requestedAt: Instant, loosens: Boolean): Instant = if (loosens) requestedAt.plus(Duration.ofHours(24)) else requestedAt
