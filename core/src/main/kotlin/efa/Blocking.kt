package efa

import java.time.Duration
import java.time.ZonedDateTime

/** Why a blocked app is blocked right now. Shown on the blocked card (U7, W5). */
enum class BlockReason { WORK_BLOCK, GETTING_READY, TRANSITION, PLANNING, NIGHT, MORNING_ALARM, MORNING_READY }

/** M5, M6: the morning reasons cannot be passed with the typed wall, because the person chose the list once and the block ends with a photo (M6, M8). */
val BlockReason.isMorning get() = this == BlockReason.MORNING_ALARM || this == BlockReason.MORNING_READY

/** K3: blocked during independent study and work blocks, the wake-up and getting-ready window, transitions. Planning blocks are independent work. */
fun firmReasonFor(type: String): BlockReason? = when (type) {
    "study", "code", "study-standard", "study-light" -> BlockReason.WORK_BLOCK
    "routine" -> BlockReason.GETTING_READY
    "travel" -> BlockReason.TRANSITION
    "planning" -> BlockReason.PLANNING
    else -> null    // lectures, tutorials, training, matches, church, gym, meals, free time, chores, errands, MyTutor: the phone is open (K3)
}

/** The phone is allowed open for some time after the wall (N3, LA9). Both are lists of until-times. */
data class Allowances(val appUntil: Map<String, ZonedDateTime> = emptyMap(), val nightUntil: ZonedDateTime? = null)

sealed interface Decision {
    data object Allow : Decision
    /**
     * [until] is when this block ends. [unprovenMinutes] is the part of the current block that opening the app would lose
     * (W2, LA9); 0 outside a work block or when the block is already verified. [night] true means the night rule, which a
     * night override can open (N3) until the hard stop (N7).
     */
    data class Block(val reason: BlockReason, val label: String, val until: ZonedDateTime, val unprovenMinutes: Int, val night: Boolean) : Decision
}

/** Where tonight's night window sits for [now]: from the cutoff to the next planned wake (N1, N2). null outside it. */
data class NightWindow(val cutoff: ZonedDateTime, val wake: ZonedDateTime)

fun nightWindow(pattern: List<PatternSlot>, now: ZonedDateTime, fallAsleep: Duration = FALL_ASLEEP_DEFAULT): NightWindow? {
    val wake = nextWake(pattern, now) ?: return null
    val cutoff = nightCutoff(wake, fallAsleep)
    return if (!now.isBefore(cutoff)) NightWindow(cutoff, wake) else null
}

/**
 * Decide whether [pkg] is blocked at [now].
 * Order: not on the list is open; an app opened through the wall in the last 30 minutes is open; a work block, getting-ready
 * block, transition or planning block blocks as usual (N2 says the night rule never lifts these); then the night rule (after the
 * cutoff, or in a sleep block), which a night override opens until the hard stop.
 * Never blocked here: anything not on the list, so calls, maps, banking and authenticator stay open (X1, LA1).
 */
fun decide(
    pkg: String, now: ZonedDateTime, pattern: List<PatternSlot>, list: Set<String>,
    allow: Allowances = Allowances(), verifiedKeys: Set<String> = emptySet(), morning: MorningState? = null,
): Decision {
    if (pkg !in list) return Decision.Allow
    // M5, M6, M8, M9: while the alarm rings and until the ready photo, the listed apps are blocked completely. Calls, maps, banking, music and
    // everything not on the list stay open (the check above), and a stuck block ends by itself (LA1, see readyReleaseAt).
    when (morningPhase(morning, now)) {
        MorningPhase.RINGING -> return Decision.Block(BlockReason.MORNING_ALARM, "Alarm", now.plusMinutes(1), 0, night = false)
        MorningPhase.WAITING_FOR_READY -> return Decision.Block(BlockReason.MORNING_READY, "Ready photo", readyReleaseAt(morning!!.stoppedMs!!, now.zone), 0, night = false)
        else -> {}
    }
    if (allow.appUntil[pkg]?.isAfter(now) == true) return Decision.Allow
    val current = nowAndNext(pattern, now).now
    val firm = current?.let { firmReasonFor(it.slot.type) }
    if (current != null && firm != null) {
        val minutes = if (firm == BlockReason.WORK_BLOCK && blockKey(current) !in verifiedKeys) Duration.between(current.start, current.end).toMinutes().toInt() else 0
        return Decision.Block(firm, current.slot.title, current.end, minutes, night = false)
    }
    val window = nightWindow(pattern, now)
    val sleeping = current?.slot?.type == "sleep"
    if (window != null || sleeping) {
        if (allow.nightUntil?.isAfter(now) == true) return Decision.Allow
        val until = window?.wake ?: current!!.end
        return Decision.Block(BlockReason.NIGHT, "Night: past the cutoff", until, 0, night = true)
    }
    return Decision.Allow
}

/** N3 to N7: what the night override offers right now. */
data class NightOffer(val allowed: Boolean, val minutes: Int, val wallLength: Int)

fun nightOffer(now: ZonedDateTime, wake: ZonedDateTime, overridesUsedTonight: Int, fullyVerifiedDay: Boolean): NightOffer =
    NightOffer(overrideAllowed(now, wake), overrideMinutes(fullyVerifiedDay), WallLength.nightOverride(overridesUsedTonight + 1))

/** LA9: passing the wall opens that one app for 30 minutes. */
const val APP_ALLOWANCE_MINUTES = 30L
