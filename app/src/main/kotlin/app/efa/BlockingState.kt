package app.efa

import android.content.Context
import app.efa.data.BlockRepo
import app.efa.data.BlockingRepo
import app.efa.data.EfaDb
import efa.*
import java.time.Instant
import java.time.ZonedDateTime

/**
 * What the blocking service and the blocked card need, held in memory so a window event is decided in microseconds, with no
 * database wait (LA4). It is reloaded every minute, when the service connects, and after any change (a wall pass, a new task, a
 * verified block). If the database cannot be read the last snapshot stays, and if there never was one nothing is blocked:
 * the failure is open, never a trapped phone (LA1, LA3).
 */
class Snapshot(
    val pattern: List<PatternSlot>, val list: Set<String>, val allow: Allowances,
    val verified: Set<String>, val tasks: List<DueWork>,
)

object BlockingState {
    @Volatile var snapshot: Snapshot? = null; private set
    /** Tests move the clock here; the real service never sets it. */
    @Volatile var clockOverride: (() -> ZonedDateTime)? = null

    fun now(): ZonedDateTime = clockOverride?.invoke() ?: ZonedDateTime.now(LONDON)

    suspend fun refresh(ctx: Context) {
        try {
            val db = EfaDb.get(ctx)
            val pattern = db.pattern().all().map { it.toSlot() }
            val repo = BlockingRepo(db)
            val now = now()
            snapshot = Snapshot(
                pattern = pattern, list = repo.list().orEmpty(), allow = repo.allowances(now),
                verified = BlockRepo(ctx, db, pattern).verifiedKeys(),
                tasks = db.tasks().open().map { DueWork(it.title, it.hoursEstimate, it.heaviness, Instant.ofEpochMilli(it.dueMs).atZone(now.zone)) },
            )
        } catch (e: Exception) { /* keep the last snapshot */ }
    }

    fun decide(pkg: String): Decision {
        val s = snapshot ?: return Decision.Allow
        return decide(pkg, now(), s.pattern, s.list, s.allow, s.verified)
    }

    /** U7: the Buffer now and the Buffer if the current work block is lost, as arithmetic and not a warning. */
    fun bufferCost(d: Decision.Block): Pair<BufferState, BufferState>? {
        val s = snapshot ?: return null
        val now = now()
        val work = studyCycles(s.pattern, now) + s.tasks
        val before = bufferNow(now, work, s.pattern, shortSleep = false)
        val current = nowAndNext(s.pattern, now).now ?: return before to before
        if (d.unprovenMinutes <= 0) return before to before
        val without = s.pattern.filter { it != current.slot }
        return before to bufferNow(now, work, without, shortSleep = false)
    }
}
