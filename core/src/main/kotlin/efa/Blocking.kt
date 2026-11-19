package efa

import java.time.Duration
import java.time.ZonedDateTime

/** Why a blocked app is blocked right now. Shown on the blocked card (U7, W5). */
enum class BlockReason { WORK_BLOCK, GETTING_READY, TRANSITION, PLANNING, NIGHT, MORNING_ALARM, MORNING_READY, APP_CAP, SCREEN_LIMIT }

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

/**
 * GT2: WhatsApp is capped. It opens for [windowMin] minutes at a time and then a cooldown of [cooldownMin] minutes follows. Ending the cooldown early is only through the wall (LA9).
 * An active call is never cut off, and Messages (SMS) and phone calls are not capped.
 */
data class AppCap(val apps: Set<String> = WHATSAPP_PACKAGES, val windowMin: Int = 10, val cooldownMin: Int = 30)

val WHATSAPP_PACKAGES: Set<String> = setOf("com.whatsapp", "com.whatsapp.w4b")

enum class CapPhase { FREE, OPEN, COOLDOWN }

/** [until] is when the open window ends, or when the cooldown ends; null when free. */
data class CapStatus(val phase: CapPhase, val until: ZonedDateTime?)

/** [startedAt] is when the current window was opened, or null when none is running. After the cooldown the app is free again. */
fun capStatus(startedAt: ZonedDateTime?, now: ZonedDateTime, cap: AppCap = AppCap()): CapStatus {
    if (startedAt == null) return CapStatus(CapPhase.FREE, null)
    val openEnd = startedAt.plusMinutes(cap.windowMin.toLong()); val coolEnd = openEnd.plusMinutes(cap.cooldownMin.toLong())
    return when { now.isBefore(startedAt) -> CapStatus(CapPhase.FREE, null); now.isBefore(openEnd) -> CapStatus(CapPhase.OPEN, openEnd); now.isBefore(coolEnd) -> CapStatus(CapPhase.COOLDOWN, coolEnd); else -> CapStatus(CapPhase.FREE, null) }
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

fun nightWindow(pattern: List<PatternSlot>, now: ZonedDateTime, fallAsleep: Duration = FALL_ASLEEP_DEFAULT, lateness: Lateness = emptyMap()): NightWindow? {
    val wake = driftedWake(pattern, now, lateness) ?: return null          // Z3: a late morning moves the next target later, then it comes back
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
    allow: Allowances = Allowances(), verifiedKeys: Set<String> = emptySet(), morning: MorningState? = null, lateness: Lateness = emptyMap(), earnedAccess: Boolean = false,
    essentials: Set<String> = ESSENTIAL_CORE, cap: AppCap? = null, capStart: Map<String, ZonedDateTime> = emptyMap(), inCall: Boolean = false,
    outUntil: ZonedDateTime? = null, atHome: Boolean? = null, limit: ScreenLimit? = null, limitStart: Map<String, ZonedDateTime> = emptyMap(),
): Decision {
    // GT2: the WhatsApp cap comes before everything else, because WhatsApp is on your never-blocked list and would otherwise always be open
    if (cap != null && pkg in cap.apps) {
        if (inCall) return Decision.Allow
        if (allow.appUntil[pkg]?.isAfter(now) == true) return Decision.Allow          // the wall ended the cooldown (LA9)
        val st = capStatus(capStart[pkg], now, cap)
        if (st.phase == CapPhase.COOLDOWN) return Decision.Block(BlockReason.APP_CAP, "${cap.windowMin} minutes at a time", st.until!!, 0, night = false)
    }
    // MX6: a period off can give the blocked apps a daily budget. When it is spent they are closed until midnight; the wall opens one for the allowance as usual.
    if (limit != null && pkg in limit.apps && pkg !in essentials && !outActive(outUntil, now, atHome)) {
        if (allow.appUntil[pkg]?.isAfter(now) != true) {
            val st = limitStatus(limitStart[pkg], now, limit.minutes)
            if (st.phase == LimitPhase.CLOSED) return Decision.Block(BlockReason.SCREEN_LIMIT, "${limit.minutes} minutes today", st.until!!, 0, night = false)
        }
    }
    if (pkg in essentials) return Decision.Allow                       // MG1: an essential is never blocked, whatever is on the list or in force
    if (pkg !in list) return Decision.Allow
    // M5, M6, M8, M9: while the alarm rings and until the ready photo, the listed apps are blocked completely. Calls, maps, banking, music and
    // everything not on the list stay open (the check above), and a stuck block ends by itself (LA1, see readyReleaseAt).
    when (morningPhase(morning, now)) {
        MorningPhase.RINGING -> return Decision.Block(BlockReason.MORNING_ALARM, "Alarm", now.plusMinutes(1), 0, night = false)
        MorningPhase.WAITING_FOR_READY -> return Decision.Block(BlockReason.MORNING_READY, "Ready photo", readyReleaseAt(morning!!.stoppedMs!!, now.zone), 0, night = false)
        else -> {}
    }
    if (outActive(outUntil, now, atHome)) return Decision.Allow          // EM2: you said you are out and the phone is not at home, so nothing is closed until then
    if (allow.appUntil[pkg]?.isAfter(now) == true) return Decision.Allow
    val current = nowAndNext(pattern, now).now
    // R2, Y8: earned phone access lifts a transition or planning block. It never lifts a work block, the getting-ready block, the morning block or the night cutoff.
    val firm = current?.let { firmReasonFor(it.slot.type) }?.takeUnless { earnedAccess && it in ACCESS_LIFTS }
    if (current != null && firm != null) {
        val minutes = if (firm == BlockReason.WORK_BLOCK && blockKey(current) !in verifiedKeys) Duration.between(current.start, current.end).toMinutes().toInt() else 0
        return Decision.Block(firm, current.slot.title, current.end, minutes, night = false)
    }
    val window = nightWindow(pattern, now, lateness = lateness)
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

/**
 * HS8: every refusal says what happened, why, and when it ends or what to do next. One place writes it, so the screen and the test read the same words.
 * [until] is the time the block ends, already formatted (HH:mm).
 */
fun refusalLine(reason: BlockReason, label: String, until: String): String = when (reason) {
    BlockReason.WORK_BLOCK -> "Work block: $label, until $until."
    BlockReason.GETTING_READY -> "Getting ready: $label, until $until."
    BlockReason.TRANSITION -> "Transition: $label, until $until."
    BlockReason.PLANNING -> "Planning block: $label, until $until."
    BlockReason.SCREEN_LIMIT -> "Your screen-time limit for today is spent. It lifts at midnight."
    BlockReason.APP_CAP -> "WhatsApp opens for 10 minutes at a time. The cooldown ends at $until. A call is never cut off."
    BlockReason.NIGHT -> "Past the night cutoff. It lifts at your wake time, $until."
    BlockReason.MORNING_ALARM -> "The alarm is ringing. Stop it with the photo first."
    BlockReason.MORNING_READY -> "Morning: social stays blocked until you photograph your ready spot. If you cannot, it ends by itself at $until."
}
