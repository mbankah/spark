package efa

import java.time.Duration
import java.time.Instant

/** ST1 to ST5 numbers. */
object StoragePolicy {
    const val THUMB_BYTES = 30 * 1024
    val THUMB_KEEP: Duration = Duration.ofDays(14)
    const val THUMB_CEILING_BYTES = 25L * 1024 * 1024
    const val REFERENCE_BYTES = 300 * 1024
    val SENSOR_EVENTS_KEEP: Duration = Duration.ofDays(30)
    val TAMPER_LOG_KEEP: Duration = Duration.ofDays(365)
    val HOSTS_LOG_KEEP: Duration = Duration.ofDays(90)
    const val ALL_DATA_CEILING_BYTES = 150L * 1024 * 1024
    const val RECORD_BYTES_MAX = 600     // ST2: a few hundred bytes
}

data class Thumb(val id: String, val takenAt: Instant, val bytes: Int)

/** Which thumbnails to delete: older than 14 days, then oldest first until under the ceiling. Verification is never touched (ST5). */
fun thumbsToDelete(all: List<Thumb>, now: Instant): List<Thumb> {
    val expired = all.filter { Duration.between(it.takenAt, now) >= StoragePolicy.THUMB_KEEP }
    val keep = all.filter { it !in expired }.sortedBy { it.takenAt }.toMutableList()
    val over = mutableListOf<Thumb>()
    while (keep.sumOf { it.bytes.toLong() } > StoragePolicy.THUMB_CEILING_BYTES) over += keep.removeAt(0)
    return expired + over
}

/** ST2: the forever record. Coordinates are not a field, by design (ST7). */
data class PhotoRecord(
    val at: Instant, val blockId: String, val type: String,
    val locationResult: String,        // "pass-near", "pass-weaker", "fail-home", "fail-far", "not-required"
    val distanceBand: String, val matchScore: Int, val fingerprint: Long,
) {
    fun approxBytes(): Int = 8 + blockId.length + type.length + locationResult.length + distanceBand.length + 4 + 8
}

/** ST2: a reused photo is rejected when its fingerprint is within a small Hamming distance of a stored one. */
fun isReusedPhoto(fingerprint: Long, stored: Collection<Long>, maxDistance: Int = 4): Boolean =
    stored.any { java.lang.Long.bitCount(it xor fingerprint) <= maxDistance }
