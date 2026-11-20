package efa

import java.time.Instant
import java.time.ZoneId

/**
 * VR16: a cycle belongs to one study block, and every card that shows the cycle says which block, when it runs and where the cycle stands against it. [window] is the block's hours, [status] says how far
 * through the block it is now, and [ended] is true once the block has ended while the cycle runs on.
 */
data class VrBlockLink(val window: String, val status: String, val ended: Boolean)

private fun hm(ms: Long, zone: ZoneId): String { val t = Instant.ofEpochMilli(ms).atZone(zone); return "%02d:%02d".format(t.hour, t.minute) }

private fun mins(ms: Long): String { val m = ((ms + 59_999L) / 60_000L).toInt().coerceAtLeast(1); return if (m >= 120) (if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}min") else "$m min" }

/** How long ago: minutes, then hours, then days (a cycle left running overnight is a day or more old, and 30 hours reads better as a day). */
private fun ago(ms: Long): String = if (ms >= 48L * 3_600_000L) "${ms / 86_400_000L} days" else mins(ms)

fun vrBlockLink(blockStartMs: Long, blockEndMs: Long, nowMs: Long, zone: ZoneId = LONDON): VrBlockLink {
    val window = "${hm(blockStartMs, zone)} to ${hm(blockEndMs, zone)}"
    return when {
        nowMs < blockStartMs -> VrBlockLink(window, "The block starts in ${mins(blockStartMs - nowMs)}", false)
        nowMs < blockEndMs -> VrBlockLink(window, "The block ends in ${mins(blockEndMs - nowMs)}", false)
        else -> VrBlockLink(window, "The block ended at ${hm(blockEndMs, zone)}, ${ago(nowMs - blockEndMs)} ago", true)
    }
}

/** One running cycle and the block it belongs to, as the times that choose between them. */
data class VrCandidate(val blockStartMs: Long, val blockEndMs: Long)

/**
 * VR16: when more than one cycle is running (one left over from an earlier block and one in this block), the one in the block that is on now is the current one; failing that, the one whose
 * block ended most recently. Returns the index, or null with no candidates.
 */
fun vrCurrent(candidates: List<VrCandidate>, nowMs: Long): Int? {
    if (candidates.isEmpty()) return null
    candidates.indices.filter { candidates[it].blockStartMs <= nowMs && nowMs < candidates[it].blockEndMs }.maxByOrNull { candidates[it].blockStartMs }?.let { return it }
    return candidates.indices.maxByOrNull { candidates[it].blockEndMs }
}
