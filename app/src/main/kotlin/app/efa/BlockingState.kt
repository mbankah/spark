package app.efa

import android.content.Context
import app.efa.data.BlockRepo
import app.efa.data.BlockingRepo
import app.efa.data.EfaDb
import app.efa.data.META_FIRST_USE
import app.efa.data.MorningRepo
import app.efa.data.PlanRepo
import app.efa.data.toState
import efa.*
import java.time.ZonedDateTime

/**
 * What the blocking service and the blocked card need, held in memory so a window event is decided in microseconds, with no
 * database wait (LA4). It is reloaded every minute, when the service connects, and after any change (a wall pass, a new task, a
 * verified block). If the database cannot be read the last snapshot stays, and if there never was one nothing is blocked:
 * the failure is open, never a trapped phone (LA1, LA3).
 */
class Snapshot(
    val pattern: List<PatternSlot>, val list: Set<String>, val allow: Allowances,
    val verified: Set<String>, val tasks: List<DueWork>, val morning: MorningState? = null, val lateness: Lateness = emptyMap(),
)

object BlockingState {
    @Volatile var snapshot: Snapshot? = null; private set
    /** Tests move the clock here; the real service never sets it. */
    @Volatile var clockOverride: (() -> ZonedDateTime)? = null

    fun now(): ZonedDateTime = clockOverride?.invoke() ?: ZonedDateTime.now(LONDON)

    suspend fun refresh(ctx: Context) {
        try {
            val db = EfaDb.get(ctx)
            val repo = BlockingRepo(db)
            val now = now()
            val plan = PlanRepo(db)
            // RS1, RS3: missed work comes back and free time turns into work. It must never stop blocking, so a failure here is ignored.
            try {
                val firstUse = db.meta().get(META_FIRST_USE)?.toLongOrNull() ?: now.toInstant().toEpochMilli()
                plan.automate(now, firstUse, BlockRepo(ctx, db, emptyList()).verifiedKeys())
            } catch (e: Exception) { android.util.Log.w("efa-plan", "automation skipped: ${e.message}") }
            val pattern = plan.slots()
            android.util.Log.i("efa-state", "snapshot loaded: ${pattern.size} blocks")
            snapshot = Snapshot(
                pattern = pattern, list = repo.list().orEmpty(), allow = repo.allowances(now),
                verified = BlockRepo(ctx, db, pattern).verifiedKeys(),
                tasks = db.tasks().open().map { it.toDue() },
                morning = MorningRepo(db).latest()?.toState(),
                lateness = MorningRepo(db).lateness(pattern, now.toLocalDate()),
            )
        } catch (e: Exception) { /* keep the last snapshot */ }
    }

    fun decide(pkg: String): Decision {
        val s = snapshot ?: return Decision.Allow
        return decide(pkg, now(), s.pattern, s.list, s.allow, s.verified, s.morning, s.lateness)
    }

    /** N5: whether the day this night belongs to was fully verified, which makes an override 45 minutes and not 30. */
    fun fullyVerifiedTonight(): Boolean {
        val s = snapshot ?: return false
        val now = now()
        val wake = driftedWake(s.pattern, now, s.lateness) ?: return false
        return fullyVerifiedDay(s.pattern, dayOfNight(wake), s.verified, now)
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
