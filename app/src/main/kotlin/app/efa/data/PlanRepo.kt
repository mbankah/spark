package app.efa.data

import androidx.room.withTransaction
import efa.*
import java.time.LocalDate
import java.time.ZonedDateTime

/** What applying a file did (IM11). */
data class Applied(val importId: Long, val overlay: Int, val tasks: Int, val periods: Int, val alreadyLogged: Boolean)

/** What the automatic step added this time (RS8). */
data class AutoResult(val catchUps: List<TaskRow>, val conversions: List<OverlayRow>)

/**
 * The calendar as the weekly pattern plus every dated change (IM1, F3), the work that is open, the planned periods, and the writes
 * for importing, removing, catching up and crediting. Every write that touches more than one table runs in one transaction, so a
 * failure leaves everything as it was (IM11, X7).
 */
class PlanRepo(private val db: EfaDb) {
    suspend fun slots(): List<PatternSlot> = db.pattern().all().map { it.toSlot() } + db.overlay().all().map { it.toSlot() }

    suspend fun periods(): List<PlannedPeriodRow> = db.periods().all()

    /** Fingerprints of every row already stored, so offering the same row again changes nothing (IM8). */
    suspend fun known(): Set<String> =
        (db.overlay().hashes() + db.periods().hashes() + db.tasks().sourceKeys().map { it.substringAfter(':') }).toSet()

    suspend fun apply(v: Verification, name: String, fileHash: String, nowMs: Long, zoneId: String, summary: String): Applied = db.withTransaction {
        require(v.ok) { "a file with errors is never applied" }
        val existing = db.imports().byHash(fileHash)
        val id = existing?.id ?: db.imports().insert(ImportLogRow(atMs = nowMs, name = name.take(80), fileHash = fileHash, summary = summary))
        var o = 0; var t = 0; var p = 0
        for (item in v.overlay) {
            val s = item.slot
            if (db.overlay().insert(OverlayRow(date = s.date.toString(), startMin = s.startMin, endMin = s.endMin, title = s.title, type = s.type, venue = s.venue,
                    notes = s.notes, hardAlarm = s.hardAlarm, cancelled = s.cancelled, kind = "import", rowHash = item.rowHash, importId = id,
                    reason = if (s.cancelled) "Cancelled by $name" else "Added by $name", createdMs = nowMs)) > 0) o++
        }
        for (w in v.work) {
            if (db.tasks().insertOnce(TaskRow(title = w.title, dueMs = w.due.toInstant().toEpochMilli(), dueZone = zoneId, heaviness = w.heaviness, hoursEstimate = w.hours,
                    createdMs = nowMs, source = w.source, sourceKey = w.key, importId = id, reason = "From $name")) > 0) t++
        }
        v.periods.forEachIndexed { i, r ->
            if (db.periods().insert(PlannedPeriodRow(startDate = r.start.toString(), endDate = r.end.toString(), kind = r.kind.csv, title = r.title,
                    rowHash = v.periodHashes[i], importId = id)) > 0) p++
        }
        Applied(id, o, t, p, existing != null)
    }

    /** IM12: takes an import's events, periods and untouched tasks away again. Work that already has proven minutes stays. */
    suspend fun removeImport(id: Long) = db.withTransaction {
        db.overlay().deleteByImport(id); db.periods().deleteByImport(id); db.tasks().deleteByImport(id); db.imports().delete(id)
    }

    /**
     * RS1, RS3: missed blocks come back as work, and free time turns into work while the Buffer is under its margin. Safe to call as
     * often as you like: both steps write a row only once (RS5).
     */
    suspend fun automate(now: ZonedDateTime, firstUseMs: Long, verified: Set<String>): AutoResult = db.withTransaction {
        val slots = slots()
        val known = db.tasks().sourceKeys().toSet()
        val nowMs = now.toInstant().toEpochMilli()
        val added = ArrayList<TaskRow>()
        for (c in missedCatchUps(slots, now, verified, known, java.time.Instant.ofEpochMilli(firstUseMs).atZone(now.zone))) {
            val row = TaskRow(title = c.title, dueMs = c.due.toInstant().toEpochMilli(), dueZone = c.due.zone.id, heaviness = c.heaviness, hoursEstimate = c.hours,
                createdMs = nowMs, source = "missed", sourceKey = c.key, reason = c.reason)
            if (db.tasks().insertOnce(row) > 0) added += row
        }
        val work = studyCycles(slots, now) + db.tasks().open().map { it.toDue() }
        val conv = ArrayList<OverlayRow>()
        for (c in planConversions(slots, work, now).conversions) {
            val s = c.slot
            val row = OverlayRow(date = s.date.toString(), startMin = s.startMin, endMin = s.endMin, title = s.title, type = s.type, venue = s.venue, notes = s.notes,
                hardAlarm = false, cancelled = false, kind = "convert", rowHash = "convert|${s.date}|${s.startMin}", importId = null, reason = c.reason, createdMs = nowMs)
            if (db.overlay().insert(row) > 0) conv += row
        }
        AutoResult(added, conv)
    }

    /** RS6: proven minutes in a task block reduce the open tasks, earliest deadline first. A task with nothing left is closed. */
    suspend fun credit(minutes: Int, nowMs: Long, period: PlannedPeriodRow?) = db.withTransaction {
        val open = db.tasks().open()
        val shares = distributeCredit(open.map { it.toOpen(period) }, minutes)
        for ((id, m) in shares) {
            db.tasks().addDone(id, m)
            if ((db.tasks().get(id)?.remainingMin() ?: 0) <= 0) db.tasks().close(id, nowMs)
        }
        shares
    }
}

/** PW1, PW2: the running period decides which kind of work goes first when deadlines are equal. */
fun TaskRow.priority(period: PlannedPeriodRow?): Int = when {
    period == null -> 0
    period.kind == PeriodKind.ASSIGNMENT_WEEK.csv -> if (source == "missed") 0 else 1
    period.kind == PeriodKind.CATCH_UP_WEEK.csv -> if (source == "missed") 1 else 0
    else -> 0
}

fun TaskRow.toOpen(period: PlannedPeriodRow?) = OpenTask(id, title, dueAt(), remainingMin(), priority(period))

/** PW3: the period running on [today], if any. */
fun activePeriod(periods: List<PlannedPeriodRow>, today: LocalDate): PlannedPeriodRow? =
    periods.firstOrNull { !LocalDate.parse(it.startDate).isAfter(today) && !LocalDate.parse(it.endDate).isBefore(today) }
