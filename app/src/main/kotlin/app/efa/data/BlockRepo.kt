package app.efa.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import efa.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime

/** What the phone derives from one photo before the full image is discarded (ST1, ST2, ST7). */
class Processed(val fingerprint: Long, val thumb: ByteArray)

object ProofProcessor {
    private const val THUMB_LONG_EDGE = 480

    /**
     * Decode, fingerprint, and make a thumbnail of about 30 KB. Re-encoding drops all EXIF, so no location data survives (ST7).
     * The caller never writes the original bytes anywhere. Throws if the bytes are not an image.
     */
    fun process(jpeg: ByteArray): Processed {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_LONG_EDGE) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("could not decode")
        val tiny = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        val px = IntArray(72).also { tiny.getPixels(it, 0, 9, 0, 0, 9, 8) }
        val gray = IntArray(72) { i -> val c = px[i]; ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 }
        val scale = THUMB_LONG_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
        var q = 75
        var out = ByteArray(0)
        while (q >= 25) {
            val s = ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG, q, s); out = s.toByteArray()
            if (out.size <= StoragePolicy.THUMB_BYTES) break
            q -= 10
        }
        return Processed(differenceHash(gray), out)
    }
}

/**
 * Reads and writes block state and proof records. Nothing here keeps the original photo: it is processed in memory and dropped (ST1).
 * Failure behaviour (X7): an undecodable photo is reported as a failed capture and changes nothing.
 */
class BlockRepo(
    private val ctx: Context, private val db: EfaDb, private val pattern: List<PatternSlot>,
    private val commitSource: CommitSource = GitHubSource(),
) {
    private val places = PlaceRepo(db)
    private val thumbDir get() = File(ctx.filesDir, "thumbs").also { it.mkdirs() }

    suspend fun load(ds: DatedSlot): BlockRow? = db.blocks().get(blockKey(ds))

    suspend fun verifiedKeys(): Set<String> = db.blocks().verifiedKeys().toSet()

    suspend fun recordHold(ds: DatedSlot, atMs: Long) {
        val existing = load(ds)
        db.blocks().upsert((existing ?: newRow(ds)).copy(holdAtMs = existing?.holdAtMs ?: atMs))
    }

    /** One capture, one verdict, no review step (G5). Rejected photos are not stored. */
    suspend fun submitPhoto(ds: DatedSlot, jpeg: ByteArray, now: ZonedDateTime, fix: Fix? = null): Verdict {
        val proof = proofFor(ds.slot)
        val row = load(ds)
        if (row?.verified == true) return Verdict(true, Reason.OK, row.creditedMin, row.recovered, row.weaker)
        if (row?.brokenAtMs != null) return Verdict(false, Reason.BLOCK_BROKEN)      // LA9
        val processed = try { ProofProcessor.process(jpeg) } catch (e: Exception) { throw CaptureFailed() }
        // L1 to L3: attendance is checked against where the phone was when the photo was taken. The fix is used here and dropped (A8, ST7).
        var loc: LocationResult? = null
        var band = distanceBand(null)
        if (proof == Proof.ATTENDANCE) {
            val venue = places.pointFor(ds.slot.venue)
            val home = places.home()
            loc = locate(fix, venue, home)
            if (fix != null && venue != null) band = distanceBand(haversineM(fix.lat, fix.lon, venue.first, venue.second))
        }
        val dup = isReusedPhoto(processed.fingerprint, db.photos().fingerprints())
        val deadline = nextWake(pattern, ds.end) ?: ds.end.plusDays(1)
        val since = lastWake(now)?.toInstant()?.toEpochMilli() ?: (now.toInstant().toEpochMilli() - 24 * 3_600_000L)
        val v = verifyProof(proof, ds.start, ds.end, now, holdDone = row?.holdAtMs != null, duplicate = dup,
            location = loc, backfillDeadline = deadline, recoveredSoFarMin = db.blocks().recoveredMinutesSince(since))
        if (!v.verified) return v
        val key = blockKey(ds)
        val file = File(thumbDir, "${now.toInstant().toEpochMilli()}.jpg").also { it.writeBytes(processed.thumb) }
        db.photos().insert(PhotoRow(blockKey = key, atMs = now.toInstant().toEpochMilli(), type = ds.slot.type,
            locationResult = loc?.name?.lowercase()?.replace('_', '-') ?: "not-required", distanceBand = band, matchScore = -1,
            fingerprint = processed.fingerprint, thumbPath = file.absolutePath, thumbBytes = processed.thumb.size))
        db.blocks().upsert((row ?: newRow(ds)).copy(verified = true, creditedMin = v.creditedMinutes, recovered = v.recovered,
            weaker = v.weaker, provenAtMs = now.toInstant().toEpochMilli()))
        return v
    }

    /**
     * Code blocks end with a commit where other blocks end with a photo (G1). Order: timer, hold and the proof window are checked first
     * with no network call, so a too-early tap costs nothing. Then the configured repos are asked for the user's own commits.
     * A commit that already proved another block does not count again.
     */
    suspend fun checkCommit(ds: DatedSlot, now: ZonedDateTime): Verdict {
        if (proofFor(ds.slot) != Proof.GIT_COMMIT) return Verdict(false, Reason.NOT_REQUIRED)
        val row = load(ds)
        if (row?.verified == true) return Verdict(true, Reason.OK, row.creditedMin, row.recovered, row.weaker)
        if (row?.brokenAtMs != null) return Verdict(false, Reason.BLOCK_BROKEN)      // LA9
        val deadline = nextWake(pattern, ds.end) ?: ds.end.plusDays(1)
        val since = lastWake(now)?.toInstant()?.toEpochMilli() ?: (now.toInstant().toEpochMilli() - 24 * 3_600_000L)
        val recoveredSoFar = db.blocks().recoveredMinutesSince(since)
        fun judge(outcome: CommitOutcome) = verifyProof(Proof.GIT_COMMIT, ds.start, ds.end, now, holdDone = row?.holdAtMs != null,
            duplicate = false, location = null, backfillDeadline = deadline, recoveredSoFarMin = recoveredSoFar, commit = outcome)
        val pre = judge(CommitOutcome.FOUND)
        if (!pre.verified) return pre                                   // timer, hold, window or recovery limit: no network call
        val config = GitConfigRepo(db).get() ?: return judge(CommitOutcome.NOT_CONFIGURED)
        val fetched = commitSource.fetch(config.user, config.repos, ds.start.toInstant().minusSeconds(3600))
        val match = when (fetched) {
            is CommitFetch.Unreachable -> CommitMatch(CommitOutcome.UNREACHABLE)
            is CommitFetch.NotFound -> CommitMatch(CommitOutcome.REPO_NOT_FOUND)
            is CommitFetch.Ok -> matchCommit(fetched.commits, ds.start, ds.end, db.commits().usedShas().toSet())
        }
        val v = judge(match.outcome)
        val sha = match.sha
        if (v.verified && sha != null) {
            val ms = now.toInstant().toEpochMilli()
            db.commits().insert(CommitUseRow(sha, blockKey(ds), ms))
            db.blocks().upsert((row ?: newRow(ds)).copy(verified = true, creditedMin = v.creditedMinutes, recovered = v.recovered, weaker = false, provenAtMs = ms))
        }
        return v
    }

    /** LA9: ends a block as broken when an app is opened through the wall. Its unproven time is lost (W2) and it can no longer be proved. */
    suspend fun breakBlock(ds: DatedSlot, nowMs: Long) {
        val row = load(ds) ?: newRow(ds)
        if (!row.verified && row.brokenAtMs == null) db.blocks().upsert(row.copy(brokenAtMs = nowMs))
    }

    /** ST1, ST4: delete thumbnails older than 14 days, then oldest first above 25 MB. Verification is never touched (ST5). */
    suspend fun cleanThumbs(now: Instant = Instant.now()): Int {
        val rows = db.photos().withThumbs()
        val doomed = thumbsToDelete(rows.map { Thumb(it.id.toString(), Instant.ofEpochMilli(it.atMs), it.thumbBytes) }, now)
        for (t in doomed) {
            rows.firstOrNull { it.id.toString() == t.id }?.let { r -> r.thumbPath?.let { File(it).delete() }; db.photos().dropThumb(r.id) }
        }
        return doomed.size
    }

    /** ST5: the clear-thumbnails button. */
    suspend fun clearAllThumbs(): Int {
        val rows = db.photos().withThumbs()
        rows.forEach { r -> r.thumbPath?.let { File(it).delete() }; db.photos().dropThumb(r.id) }
        return rows.size
    }

    private fun lastWake(now: ZonedDateTime): ZonedDateTime? =
        (-1L..0L).flatMap { slotsFor(pattern, now.toLocalDate().plusDays(it), now.zone) }
            .filter { it.slot.title == "Wake, shower" && !it.start.isAfter(now) }.maxOfOrNull { it.start }

    private fun newRow(ds: DatedSlot) = BlockRow(key = blockKey(ds), title = ds.slot.title, type = ds.slot.type,
        startMs = ds.start.toInstant().toEpochMilli(), endMs = ds.end.toInstant().toEpochMilli(), zone = ds.start.zone.id)
}

class CaptureFailed : Exception("The photo could not be read. Take it again.")
