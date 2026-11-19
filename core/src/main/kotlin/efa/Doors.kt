package efa

import java.time.Duration
import java.time.Instant

/**
 * DP1: a door is any feature that relaxes a rule. DP2: all of them draw from one pool, so two doors opened together cannot give more than the pool.
 * [verifiable] doors cost half (DP3): a night of 3 hours or less backs an ill day; being away from home backs "I'm out".
 * [exempt] doors are never locked by the pool (DP4); they are still recorded.
 */
enum class Door(val verifiable: Boolean, val exempt: Boolean) {
    DAY_OFF(false, false),
    ILL(true, true),            // exempt only when verified, see [doorDecision]
    OVERRIDE(false, false),
    LATE_NIGHT(false, false),   // free when the 8 hours hold (SA4), so it costs nothing; it is listed so the weekly review can show it
    OUT(true, false),
    LITE(false, false),
    RECOVERY(false, false),
    AWAY(false, false),
    TAKEOVER(false, false),
    CANCEL(false, false),
    WALL_ENDS_COOLDOWN(false, false),  // GT2: the wall that ends a WhatsApp cooldown
    BREAK_GLASS(false, true),
    EMERGENCY(false, true),
}

/** What a use of a door took from the pool. */
data class DoorUse(val door: Door, val at: Instant, val obligationMin: Int, val gateMin: Int, val verified: Boolean = false)

data class PoolCaps(val obligationMin: Int = 600, val gateMin: Int = 180, val windowDays: Int = 7)

data class Spent(val obligationMin: Int, val gateMin: Int)

/** DP2, DP3: the price of a use in pool units. Verified doors cost half, exempt ones cost nothing against the cap. */
fun price(use: DoorUse): Spent {
    if (use.door == Door.EMERGENCY || use.door == Door.BREAK_GLASS) return Spent(0, 0)
    val f = if (use.door.verifiable && use.verified) 0.5 else 1.0
    return Spent((use.obligationMin * f).toInt(), (use.gateMin * f).toInt())
}

/** What has been taken in the rolling window ending at [now]. */
fun spentInWindow(uses: List<DoorUse>, now: Instant, caps: PoolCaps = PoolCaps()): Spent {
    val from = now.minus(Duration.ofDays(caps.windowDays.toLong()))
    var o = 0; var g = 0
    for (u in uses) if (!u.at.isBefore(from) && !u.at.isAfter(now)) { val p = price(u); o += p.obligationMin; g += p.gateMin }
    return Spent(o, g)
}

sealed interface DoorDecision {
    /** [wallLength] is 0 when no typed string is needed. */
    data class Open(val wallLength: Int, val fractionAfter: Double) : DoorDecision
    data class Locked(val reason: String, val reopensAt: Instant?) : DoorDecision
}

/** DP2, DP4: the wall grows as the pool drains: 75 up to half, 100 to three quarters, 130 to the cap; over the cap the door is locked. */
fun wallForFraction(f: Double): Int = when { f <= 0.5 -> WALL_BASE; f <= 0.75 -> WALL_BASE + 25; else -> WALL_BASE + 55 }

/**
 * Decide whether [request] may open now. Exempt doors always open (DP4), at no cost. Otherwise the request, added to what is spent, must stay at or under
 * both caps; if not, the door is locked until the oldest use leaves the window. Sleep protection is not a door, so nothing here can relax it (DP4).
 */
fun doorDecision(uses: List<DoorUse>, request: DoorUse, now: Instant, caps: PoolCaps = PoolCaps()): DoorDecision {
    if (request.door.exempt && (request.door != Door.ILL || request.verified)) return DoorDecision.Open(0, spentFraction(spentInWindow(uses, now, caps), caps))
    val have = spentInWindow(uses, now, caps); val p = price(request)
    val afterO = have.obligationMin + p.obligationMin; val afterG = have.gateMin + p.gateMin
    if (afterO > caps.obligationMin || afterG > caps.gateMin) {
        val oldest = uses.filter { price(it) != Spent(0, 0) && !it.at.isBefore(now.minus(Duration.ofDays(caps.windowDays.toLong()))) }.minByOrNull { it.at }
        return DoorDecision.Locked("The weekly pool is used up: ${have.obligationMin} of ${caps.obligationMin} obligation minutes and ${have.gateMin} of ${caps.gateMin} gate minutes. Only emergencies, a verified ill day and break glass are open.", oldest?.at?.plus(Duration.ofDays(caps.windowDays.toLong())))
    }
    val f = spentFraction(Spent(afterO, afterG), caps)
    return DoorDecision.Open(wallForFraction(f), f)
}

fun spentFraction(s: Spent, caps: PoolCaps = PoolCaps()): Double = maxOf(s.obligationMin.toDouble() / caps.obligationMin, s.gateMin.toDouble() / caps.gateMin)

/** CE7: break glass lifts one lock, once in 30 days. */
fun breakGlassAvailable(uses: List<DoorUse>, now: Instant): Boolean = uses.none { it.door == Door.BREAK_GLASS && it.at.isAfter(now.minus(Duration.ofDays(30))) }

/** DP6: used by the property test. Applies a request if it opens, as if the wall had been typed, and returns the new list. */
fun applyIfOpen(uses: List<DoorUse>, request: DoorUse, now: Instant, caps: PoolCaps = PoolCaps()): List<DoorUse> =
    if (doorDecision(uses, request, now, caps) is DoorDecision.Open) uses + request else uses
