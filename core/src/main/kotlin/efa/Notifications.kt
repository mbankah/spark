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
 * U8: a notification is held only while its app is blocked right now. Notifications from apps that are not blocked come through
 * normally, and so does anything LA1 protects. Ongoing notifications (such as a call in progress) and group summaries are left alone,
 * because holding them would break the app or hide a whole group.
 */
fun shouldHold(n: Incoming, decision: Decision): Boolean =
    decision is Decision.Block && !n.ongoing && !n.groupSummary && n.category !in NEVER_HELD_CATEGORIES && !neverHeldPackage(n.pkg)

/** A held notification, as stored on the phone. Nothing is deleted before it has been seen (LA2). */
data class HeldItem(val id: Long, val pkg: String, val label: String, val title: String, val text: String, val atMs: Long)

/** What the digest shows now. [ready] are in time order with the app name. [waitingUntil] is when the rest become readable. */
data class Digest(val ready: List<HeldItem>, val waitingUntil: ZonedDateTime?)

/**
 * LA2: shown after the block, in time order with the app name. An item whose app is still blocked is not shown yet. It waits, and
 * the digest says until when, with no count (WG7, AP-rules: no counts of held notifications).
 */
fun digest(items: List<HeldItem>, decideNow: (String) -> Decision): Digest {
    val ready = mutableListOf<HeldItem>()
    var until: ZonedDateTime? = null
    for (i in items.sortedBy { it.atMs }) {
        val d = decideNow(i.pkg)
        if (d is Decision.Block) until = listOfNotNull(until, d.until).min() else ready += i
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
