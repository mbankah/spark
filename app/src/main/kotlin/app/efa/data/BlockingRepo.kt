package app.efa.data

import androidx.room.withTransaction
import efa.*
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant

/**
 * The block list and every loosening of it (K2, M7, N3, LA9). The list is chosen once at setup and fixed; changing it is a
 * wall in the UI. Every pass of the wall leaves a row, which is also the count that lengthens the next night string (N4).
 */
class BlockingRepo(private val db: EfaDb) {
    suspend fun list(): Set<String>? = db.meta().get(META_BLOCKLIST)?.split('\n')?.filter { it.isNotBlank() }?.toSet()

    suspend fun saveList(pkgs: Set<String>) { db.meta().put(MetaRow(META_BLOCKLIST, pkgs.sorted().joinToString("\n"))) }

    suspend fun allowances(now: ZonedDateTime): Allowances {
        val rows = db.allowances().active(now.toInstant().toEpochMilli())
        val zone = now.zone
        fun z(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)
        return Allowances(
            appUntil = rows.filter { it.kind == "app" && it.pkg != null }.groupBy { it.pkg!! }.mapValues { (_, v) -> z(v.maxOf { it.untilMs }) },
            nightUntil = rows.filter { it.kind == "night" }.maxOfOrNull { it.untilMs }?.let { z(it) },
        )
    }

    /** LA9: opens one app for 30 minutes. During a work block the block ends as broken and its unproven time is lost. */
    suspend fun openApp(pkg: String, now: ZonedDateTime, wallLength: Int, current: DatedSlot?, blocks: BlockRepo) = db.withTransaction {
        val ms = now.toInstant().toEpochMilli()
        db.allowances().insert(AllowanceRow(kind = "app", pkg = pkg, untilMs = ms + APP_ALLOWANCE_MINUTES * 60_000, atMs = ms, wallLength = wallLength))
        if (current != null && firmReasonFor(current.slot.type) == BlockReason.WORK_BLOCK) blocks.breakBlock(current, ms)
    }

    /** N3: opens every listed app for 30 minutes, 45 after a fully verified day (N5). */
    suspend fun nightOverride(now: ZonedDateTime, minutes: Int, wallLength: Int) {
        val ms = now.toInstant().toEpochMilli()
        db.allowances().insert(AllowanceRow(kind = "night", pkg = null, untilMs = ms + minutes * 60_000L, atMs = ms, wallLength = wallLength))
    }

    /** Overrides already used in the current night window, so the next string is longer (N4). */
    suspend fun nightUsesSince(since: ZonedDateTime): Int = db.allowances().nightUsesSince(since.toInstant().toEpochMilli())

    /**
     * U4, X5: a setup step that was done and is no longer (a permission switched off, the home app changed) is logged once when it goes
     * off and once when it is back. [wasEverComplete] is false during first run, when nothing is "lost" yet. Kept 1 year (ST4).
     */
    suspend fun noteStep(step: String, ok: Boolean, wasEverComplete: Boolean, nowMs: Long) = db.withTransaction {
        val key = "tamper_open_$step"
        val open = db.meta().get(key) == "1"
        if (wasEverComplete && !ok && !open) { db.tamper().insert(TamperRow(atMs = nowMs, kind = "step_off", detail = step)); db.meta().put(MetaRow(key, "1")) }
        if (ok && open) { db.tamper().insert(TamperRow(atMs = nowMs, kind = "step_restored", detail = step)); db.meta().put(MetaRow(key, "0")) }
    }

    /** U4, X5: logs a tamper event once when blocking goes off and once when it is restored. Kept 1 year (ST4). */
    suspend fun noteServiceState(listSet: Boolean, serviceOn: Boolean, nowMs: Long) = db.withTransaction {
        val open = db.meta().get("tamper_open") == "1"
        if (listSet && !serviceOn && !open) { db.tamper().insert(TamperRow(atMs = nowMs, kind = "blocking_off", detail = "accessibility service off")); db.meta().put(MetaRow("tamper_open", "1")) }
        if (serviceOn && open) { db.tamper().insert(TamperRow(atMs = nowMs, kind = "blocking_restored", detail = "accessibility service on")); db.meta().put(MetaRow("tamper_open", "0")) }
        db.tamper().deleteBefore(nowMs - StoragePolicy.TAMPER_LOG_KEEP.toMillis())
    }
}
