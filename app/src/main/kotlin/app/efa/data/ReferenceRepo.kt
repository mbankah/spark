package app.efa.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import efa.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration

/** The three reference photos the morning uses (M1, M4, M8). */
object RefKind {
    const val NIGHT = "night"          // the setup, photographed the night before (M1)
    const val STANDING = "standing"    // the optional fixed reference for when the setup moves around (M4)
    const val READY = "ready"          // the last getting-ready step at its fixed spot (M8)
}

/** How long a night-before photo counts as the most recent valid reference (M4, Q-G53). */
val NIGHT_REFERENCE_VALID: Duration = Duration.ofHours(36)

sealed interface RefResult {
    data class Saved(val bytes: Int) : RefResult
    data object TooDark : RefResult         // the picture shows nothing to match against, so it is refused
    data object Unreadable : RefResult
}

/** The result of matching a live photo, with the score kept so the pass mark can be tuned from real mornings (Q-G53). */
data class MatchOutcome(val result: MatchResult, val hadReference: Boolean)

/**
 * Reference photos and matching (M1 to M4, M8, ST3, ST7). A photo is decoded in memory, summarised to 16 x 16 grey values, and re-encoded at
 * working size without location data; the camera's own file is never kept. A new reference replaces the old one.
 * Failure behaviour (X7): an unreadable photo changes nothing.
 */
class ReferenceRepo(private val ctx: Context, private val db: EfaDb) {
    private val dir get() = File(ctx.filesDir, "refs").also { it.mkdirs() }

    /** Decodes a photo small, once, and returns its signature, or null if it is not a picture. */
    fun signatureOf(jpeg: ByteArray): ImageSignature? = decode(jpeg, 160)?.let { bmp ->
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        signatureOf(IntArray(w * h) { i -> val c = px[i]; ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 }, w, h)
    }

    private fun decode(jpeg: ByteArray, longEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longEdge) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun set(kind: String, jpeg: ByteArray, nowMs: Long): RefResult {
        val sig = signatureOf(jpeg) ?: return RefResult.Unreadable
        if (matchScore(sig, sig).score == 0) return RefResult.TooDark          // a blank reference could never match anything
        val full = decode(jpeg, 1024) ?: return RefResult.Unreadable
        var q = 85; var out = ByteArray(0)
        while (q >= 35) {
            val s = ByteArrayOutputStream(); full.compress(Bitmap.CompressFormat.JPEG, q, s); out = s.toByteArray()
            if (out.size <= StoragePolicy.REFERENCE_BYTES) break
            q -= 10
        }
        val file = File(dir, "$kind.jpg").also { it.writeBytes(out) }
        db.references().put(ReferencePhotoRow(kind, sig.grey, file.absolutePath, out.size, nowMs))
        return RefResult.Saved(out.size)
    }

    /** The references a live photo is matched against for [kinds]: the night-before photo only while it is still the most recent valid one (M4). */
    suspend fun valid(kinds: List<String>, nowMs: Long): List<ReferencePhotoRow> = kinds.mapNotNull { db.references().get(it) }.filter {
        it.kind != RefKind.NIGHT || nowMs - it.takenAtMs <= NIGHT_REFERENCE_VALID.toMillis()
    }

    suspend fun match(kinds: List<String>, jpeg: ByteArray, nowMs: Long): MatchOutcome {
        val refs = valid(kinds, nowMs)
        val live = signatureOf(jpeg) ?: return MatchOutcome(MatchResult(0, false), refs.isNotEmpty())
        if (refs.isEmpty()) return MatchOutcome(MatchResult(0, false), false)
        return MatchOutcome(matchAny(refs.map { ImageSignature(it.signature) }, live), true)
    }

    suspend fun status(): Map<String, ReferencePhotoRow> = db.references().all().associateBy { it.kind }

    /** The one place a reference can be removed: setting a new one replaces it, and the standing one may be cleared. */
    suspend fun clear(kind: String) {
        db.references().get(kind)?.let { File(it.path).delete() }
        db.references().delete(kind)
    }
}
