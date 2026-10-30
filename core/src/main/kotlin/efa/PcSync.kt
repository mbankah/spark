package efa

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What the phone tells the Windows service (A2, A4, A6, K4, K9). The phone is the source of truth: it works out, for the next hours, when the listed sites are blocked
 * and sends that with the domains, signed with the key shown by the PC (docs/PC_PROTOCOL.md). The service enforces it and has no say in what is blocked.
 */

/** One stretch in which the PC blocks the listed sites. */
data class PcWindow(val start: Instant, val end: Instant, val why: String)

/** How far ahead the phone sends. The PC keeps enforcing the last state it was given if the phone is away, so this is how long a missing phone is covered. */
const val PC_HOURS_AHEAD = 36L

/**
 * Walks [from] until [to] a minute at a time and joins the minutes in which [decisionAt] blocks. The phone's own decision is used, so the PC blocks exactly when the
 * phone's apps are blocked: work blocks, the getting-ready window, transitions, planning and the night (K3). A wall allowance on the phone does not open the PC (K9).
 */
fun pcWindows(from: ZonedDateTime, to: ZonedDateTime, decisionAt: (ZonedDateTime) -> Decision): List<PcWindow> {
    val out = ArrayList<PcWindow>()
    var t = from.withSecond(0).withNano(0)
    var startedAt: ZonedDateTime? = null; var why = ""
    while (t.isBefore(to)) {
        val d = decisionAt(t)
        if (d is Decision.Block) { if (startedAt == null) { startedAt = t; why = d.label } }
        else if (startedAt != null) { out += PcWindow(startedAt.toInstant(), t.toInstant(), why); startedAt = null }
        t = t.plusMinutes(1)
    }
    if (startedAt != null) out += PcWindow(startedAt.toInstant(), t.toInstant(), why)
    return out
}

private fun esc(s: String) = buildString { for (c in s) when { c == '"' -> append("\\\""); c == '\\' -> append("\\\\"); c < ' ' -> append(' '); else -> append(c) } }

/** The exact bytes that are signed and sent. The PC checks the signature over these bytes, so nothing about the layout has to match anywhere else. */
fun pcStateJson(issuedAt: Instant, domains: Collection<String>, windows: List<PcWindow>): String =
    "{\"v\":1,\"issuedAtUtc\":\"${DateTimeFormatter.ISO_INSTANT.format(issuedAt)}\",\"domains\":[" + domains.sorted().joinToString(",") { "\"${esc(it)}\"" } +
        "],\"windows\":[" + windows.joinToString(",") { "{\"start\":\"${DateTimeFormatter.ISO_INSTANT.format(it.start)}\",\"end\":\"${DateTimeFormatter.ISO_INSTANT.format(it.end)}\",\"why\":\"${esc(it.why)}\"}" } + "]}"

/** HMAC-SHA256 of the body with the pairing code as the key, in lower-case hex (the PC's X-Spark-Signature header). */
fun pcSignature(code: String, body: String): String {
    val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(code.trim().toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** The domains of the apps on the block list, for the PC. */
fun pcDomains(list: Set<String>): Set<String> = domainOwners(list).keys

/** What the PC link screen asks for: an address like 192.168.1.20 or 192.168.1.20:47831, and the pairing code. */
data class PcAddress(val host: String, val port: Int)

fun parsePcAddress(text: String): PcAddress? {
    val t = text.trim().removePrefix("http://").removeSuffix("/")
    if (t.isEmpty() || t.contains('/') || t.contains(' ')) return null
    val host = t.substringBefore(':'); val port = t.substringAfter(':', "47831").toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    val ok = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$").matches(host)
    return if (ok) PcAddress(host, port) else null
}

/** A plain account of the link for the screen. */
fun pcStatusText(lastOkMs: Long?, lastError: String?, nowMs: Long): String = when {
    lastOkMs != null && lastError == null -> "Sent ${Duration.ofMillis(nowMs - lastOkMs).toMinutes()} minutes ago."
    lastError != null -> "Not reached: $lastError" + (lastOkMs?.let { " Last sent ${Duration.ofMillis(nowMs - it).toMinutes()} minutes ago." } ?: "")
    else -> "Not sent yet."
}
