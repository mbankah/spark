package efa

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What the listener knows about a notification that has just been posted. */
data class Incoming(val pkg: String, val category: String?, val ongoing: Boolean, val groupSummary: Boolean)

/** LA1: calls and alarms are never held, whichever app posts them. Android category names. */
val NEVER_HELD_CATEGORIES: Set<String> = setOf("call", "missed_call", "alarm")

/** LA1: authenticator codes, banking alerts and MyUoB notices are never held, even if the app were put on the block list. */
fun neverHeldPackage(pkg: String): Boolean {
    val p = pkg.lowercase()
    return pkg in NEVER_PRESELECTED || "authenticator" in p || "bham" in p || "birmingham" in p || "banking" in p
}

/**
 * QM1: notification categories that quiet mode never holds, besides those LA1 protects: they are the phone working (directions, a download or
 * upload in progress, an error, a system message, a service, a status, a media transport, a stopwatch, a voicemail, a shared location).
 */
val QUIET_PASS_CATEGORIES: Set<String> = NEVER_HELD_CATEGORIES + setOf("navigation", "progress", "err", "sys", "service", "status", "transport", "stopwatch", "voicemail", "location_sharing")

/** QM1: the phone's own packages. Holding their notifications (an emergency alert, a battery warning, a permission prompt) would break the phone, so quiet mode never does. */
private val PLATFORM_PACKAGES = listOf(
    "android", "com.android.systemui", "com.android.settings", "com.android.providers", "com.android.server", "com.android.phone", "com.android.emergency",
    "com.android.cellbroadcastreceiver", "com.android.packageinstaller", "com.android.permissioncontroller", "com.android.bluetooth", "com.android.nfc",
    "com.android.managedprovisioning", "com.google.android.gms", "com.google.android.gsf", "com.google.android.permissioncontroller", "com.google.android.cellbroadcastreceiver",
    "com.google.android.packageinstaller", "com.google.android.apps.wellbeing", "com.google.android.apps.safetyhub",
)
fun platformPackage(pkg: String): Boolean = PLATFORM_PACKAGES.any { pkg == it || pkg.startsWith("$it.") }

/**
 * U8, QM1: a notification is held while its app is blocked right now (U8). With quiet mode on, and while the phone is in a quiet window (a work block or the night),
 * every other notification is held too, except an essential, the phone's own packages, and what LA1 and [QUIET_PASS_CATEGORIES] protect. Outside a quiet window,
 * or with quiet mode off, an app that is not blocked comes through as it always did. Ongoing notifications (a call in progress, navigation, music) and group
 * summaries are always left alone, because holding them would break the app or hide a whole group.
 */
fun shouldHold(n: Incoming, decision: Decision, quiet: Boolean = false, essentials: Set<String> = ESSENTIAL_CORE): Boolean {
    if (n.ongoing || n.groupSummary || n.category in NEVER_HELD_CATEGORIES || neverHeldPackage(n.pkg)) return false
    if (decision is Decision.Block) return true
    return quiet && n.pkg !in essentials && !platformPackage(n.pkg) && n.category !in QUIET_PASS_CATEGORIES
}

/** QM1: the reasons that make the phone quiet. Getting ready, transitions and planning blocks keep blocking listed apps, but do not hold everything else. */
val QUIET_REASONS: Set<BlockReason> = setOf(BlockReason.WORK_BLOCK, BlockReason.NIGHT)

/** A stand-in package that is on the list, used only to ask the blocking rules whether the phone is in a block now. */
const val QUIET_PROBE = "efa.quiet.probe"

/**
 * QM1: whether the phone is in a quiet window at [now], and until when. It asks the same rules as blocking (decide) about an app that is on the list, so a work block,
 * the night cutoff, a night override and the wall all agree with it. null when the phone is open.
 */
fun quietWindow(
    now: ZonedDateTime, pattern: List<PatternSlot>, allow: Allowances = Allowances(), verifiedKeys: Set<String> = emptySet(), morning: MorningState? = null,
    lateness: Lateness = emptyMap(), earnedAccess: Boolean = false, essentials: Set<String> = ESSENTIAL_CORE,
): Decision.Block? =
    (decide(QUIET_PROBE, now, pattern, setOf(QUIET_PROBE), allow, verifiedKeys, morning, lateness, earnedAccess, essentials) as? Decision.Block)?.takeIf { it.reason in QUIET_REASONS }

/** A held notification, as stored on the phone. Nothing is deleted before it has been seen (LA2). */
data class HeldItem(val id: Long, val pkg: String, val label: String, val title: String, val text: String, val atMs: Long)

/** What the digest shows now. [ready] are in time order with the app name. [waitingUntil] is when the rest become readable. */
data class Digest(val ready: List<HeldItem>, val waitingUntil: ZonedDateTime?)

/**
 * LA2: shown after the block, in time order with the app name. An item whose app is still blocked is not shown yet. It waits, and
 * the digest says until when, with no count (WG7, AP-rules: no counts of held notifications).
 */
fun digest(items: List<HeldItem>, quietUntil: ZonedDateTime? = null, decideNow: (String) -> Decision): Digest {
    val ready = mutableListOf<HeldItem>()
    var until: ZonedDateTime? = null
    for (i in items.sortedBy { it.atMs }) {
        val d = decideNow(i.pkg)
        when {
            d is Decision.Block -> until = listOfNotNull(until, d.until).min()
            quietUntil != null -> until = listOfNotNull(until, quietUntil).min()            // QM1: while the phone is quiet, everything held waits for the end of it
            else -> ready += i
        }
    }
    return Digest(ready, until)
}

private val HM = DateTimeFormatter.ofPattern("HH:mm")
fun HeldItem.line(zone: java.time.ZoneId = LONDON): String = "${HM.format(Instant.ofEpochMilli(atMs).atZone(zone))} $label: ${listOf(title, text).filter { it.isNotBlank() }.joinToString(" - ")}"

/** The persistent Buffer notification (B2, LA11): plain text, no badge, no count of held notifications, no motivational words. */
data class NoticeText(val title: String, val text: String, val big: String)

fun bufferNotice(buffer: BufferState, now: String?, next: String?, updated: String): NoticeText = when (buffer) {
    BufferState.NoDeadline -> NoticeText("Buffer: no deadline", "Add a deadline to see the Buffer.", listOfNotNull(now?.let { "Now: $it" }, next?.let { "Next: $it" }, "Updated $updated").joinToString("\n"))
    is BufferState.Value -> NoticeText(
        buffer.result.label(), "To ${buffer.result.deadlineLabel}",
        listOfNotNull("To ${buffer.result.deadlineLabel}", now?.let { "Now: $it" }, next?.let { "Next: $it" }, "Updated $updated").joinToString("\n"),
    )
}
