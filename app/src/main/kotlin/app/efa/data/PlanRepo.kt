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
    internal val dbForAuto: EfaDb get() = db
    suspend fun slots(): List<PatternSlot> {
        val base = db.pattern().all().map { it.toSlot() } + db.overlay().all().filter { it.kind != "declined" }.map { it.toSlot() } +
            db.daysOff().effective().map { dayOffMarker(LocalDate.parse(it.date)) }          // C5: a day off is a marker the calendar reads
        val hours = parseLibraryHours(db.meta().get(META_LIBRARY_HOURS)) ?: return base
        // C2: a library period is worked out from the calendar each time, so it always agrees with imports and days off.
        return base + db.periods().all().filter { it.kind == LIBRARY_KIND }.flatMap { libraryItems(base, LocalDate.parse(it.startDate), LocalDate.parse(it.endDate), hours) }
    }

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
        // Z4: a day that began after 6.5 hours of sleep or less is a bad night, and the reason says so.
        val bad = db.nights().all().filter { it.sleepMin <= SHORT_SLEEP_MAX_HOURS * 60 }.map { LocalDate.parse(it.date).plusDays(1) }.toSet()
        for (c in missedCatchUps(slots, now, verified, known, java.time.Instant.ofEpochMilli(firstUseMs).atZone(now.zone), badNightDays = bad)) {
            val row = TaskRow(title = c.title, dueMs = c.due.toInstant().toEpochMilli(), dueZone = c.due.zone.id, heaviness = c.heaviness, hoursEstimate = c.hours,
                createdMs = nowMs, source = "missed", sourceKey = c.key, reason = c.reason)
            if (db.tasks().insertOnce(row) > 0) added += row
        }
        // F7, Y5: personal football for this week and next, from tomorrow, before free time is turned into work.
        val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        var withFootball = slots
        for (w in 0..1) {
            val monday = weekStart(now.toLocalDate()).plusWeeks(w.toLong())
            for (g in placeFootball(withFootball, monday, if (w == 0) tomorrow else monday.atStartOfDay(now.zone))) {
                val row = OverlayRow(date = g.date.toString(), startMin = g.startMin, endMin = g.endMin, title = g.title, type = g.type, venue = "", notes = g.notes, hardAlarm = false,
                    cancelled = false, kind = "football", rowHash = "football|${g.date}|${g.startMin}", importId = null, reason = "3 hours of personal football a week", createdMs = nowMs)
                db.overlay().insert(row); withFootball = withFootball + g
            }
        }
        val work = studyCycles(withFootball, now) + db.tasks().open().map { it.toDue() }
        val declined = db.overlay().declined().map { java.time.LocalDate.parse(it.date) to it.startMin }.toSet()
        val conv = ArrayList<OverlayRow>()
        for (c in planConversions(withFootball, work, now, declined = declined).conversions) {
            val s = c.slot
            val row = OverlayRow(date = s.date.toString(), startMin = s.startMin, endMin = s.endMin, title = s.title, type = s.type, venue = s.venue, notes = s.notes,
                hardAlarm = false, cancelled = false, kind = "convert", rowHash = "convert|${s.date}|${s.startMin}", importId = null, reason = c.reason, createdMs = nowMs)
            if (db.overlay().insert(row) > 0) {
                conv += row                                                    // W4: its last 10 minutes are the reset
                val r = c.reset
                db.overlay().insert(OverlayRow(date = r.date.toString(), startMin = r.startMin, endMin = r.endMin, title = r.title, type = r.type, venue = "", notes = r.notes,
                    hardAlarm = false, cancelled = false, kind = "reset", rowHash = "reset|${r.date}|${r.startMin}", importId = null, reason = "Reset after work", createdMs = nowMs))
            }
        }
        autoDayOff(now)
        resolveClaims(nowMs)
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
    periods.filter { it.kind != LIBRARY_KIND }.firstOrNull { !LocalDate.parse(it.startDate).isAfter(today) && !LocalDate.parse(it.endDate).isBefore(today) }

/**
 * F4: the person turns a converted free slot back into a break. It needs the typed wall in the UI. The window is remembered so the planner does not
 * convert it again, and the conversion and its reset are removed.
 */
suspend fun PlanRepo.turnBack(db: EfaDb, date: LocalDate, windowStartMin: Int, windowEndMin: Int, nowMs: Long) = db.withTransaction {
    db.overlay().deleteConversion(date.toString(), windowStartMin, windowEndMin)
    db.overlay().insert(OverlayRow(date = date.toString(), startMin = windowStartMin, endMin = windowEndMin, title = "Turned back to free time", type = "free", venue = "", notes = "",
        hardAlarm = false, cancelled = false, kind = "declined", rowHash = "declined|$date|$windowStartMin", importId = null, reason = "You turned this back into a break", createdMs = nowMs))
}

/** C1, H1 to H5, X3: the walls spent so far, from every day off including the automatic ones (H4). */
suspend fun PlanRepo.uses(db: EfaDb): List<DayOffUse> = db.daysOff().all().filter { it.state != "failed" || it.source == "claim" }.map { DayOffUse(it.groupId, it.usedAtMs) }

/**
 * Plans [dates] as days off after the wall of [wallLength] was typed (H1). Weekend days share a group, so they are one use (H5). The work and the
 * lectures a day takes away come back as work due by the end of the next working day, so a day off cannot make work disappear (C4). Days that already
 * are days off are skipped. A claim (H6) adds no work: the work already came back when the day was missed, and the claim waits on it.
 */
suspend fun PlanRepo.addDaysOff(db: EfaDb, dates: List<LocalDate>, source: String, wallLength: Int, now: ZonedDateTime, sameDay: Boolean): Int = db.withTransaction {
    val nowMs = now.toInstant().toEpochMilli()
    val fresh = dates.filter { db.daysOff().get(it.toString()) == null }
    if (fresh.isEmpty()) return@withTransaction 0
    val slots = slots()
    val claim = source == "claim"
    for (d in fresh) db.daysOff().insert(DayOffRow(date = d.toString(), groupId = nowMs, usedAtMs = nowMs, sameDay = sameDay, source = source, wallLength = wallLength, state = if (claim) "pending" else "active"))
    if (!claim) for (w in dayOffDemand(slots, fresh, now)) {
        db.tasks().insertOnce(TaskRow(title = w.title, dueMs = w.due.toInstant().toEpochMilli(), dueZone = w.due.zone.id, heaviness = w.heaviness, hoursEstimate = w.hours,
            createdMs = nowMs, source = "dayoff", sourceKey = w.key, reason = w.reason))
    }
    fresh.size
}

/** H4, X3: a night of 3 hours of sleep or less, with the logger alive, makes today a day off with no typing. It counts toward the same 30-day tally. */
suspend fun PlanRepo.autoDayOff(now: ZonedDateTime) {
    val db = this.dbForAuto
    val n = db.nights().latest() ?: return
    val endedToday = java.time.Instant.ofEpochMilli(n.endMs).atZone(now.zone).toLocalDate() == now.toLocalDate()
    if (endedToday && autoDayOff(n.sleepMin, n.gapCount)) addDaysOff(db, listOf(now.toLocalDate()), "auto_sleep", 0, now, sameDay = true)
}

/** H6: a claim holds when the work that came back from that day is done by its due times, and fails when one is overdue or done late. */
suspend fun PlanRepo.resolveClaims(nowMs: Long) {
    val db = this.dbForAuto
    for (c in db.daysOff().pending()) {
        val tasks = db.tasks().byKeyPrefix("missed:${c.date}|%").map { ClaimTask(it.dueMs, it.doneAtMs) }
        when (resolveClaim(tasks, nowMs)) {
            ClaimState.HELD -> db.daysOff().setState(c.id, "held")
            ClaimState.FAILED -> db.daysOff().setState(c.id, "failed")
            ClaimState.PENDING -> {}
        }
    }
}


/** O11: the library's hours, asked for once and kept. */
suspend fun PlanRepo.libraryHours(db: EfaDb): LibraryHours? = parseLibraryHours(db.meta().get(META_LIBRARY_HOURS))
suspend fun PlanRepo.saveLibraryHours(db: EfaDb, hours: LibraryHours) = db.meta().put(MetaRow(META_LIBRARY_HOURS, hours.text()))

/**
 * C1, C2: plans a library period. The walks take work minutes, so they come back as catch-up work after the period (like a day off), one piece a day. Returns
 * the reason it was refused, or null. The hours must have been given first.
 */
suspend fun PlanRepo.addLibraryPeriod(db: EfaDb, start: LocalDate, end: LocalDate, now: ZonedDateTime): String? = db.withTransaction {
    val hours = libraryHours(db) ?: return@withTransaction "Tell Spark the library's opening hours first."
    checkLibraryPeriod(start, end, now.toLocalDate())?.let { return@withTransaction it }
    val hash = "$LIBRARY_KIND|$start|$end"
    if (db.periods().hashes().contains(hash)) return@withTransaction "That library period is already planned."
    val base = slots()
    val nowMs = now.toInstant().toEpochMilli()
    db.periods().insert(PlannedPeriodRow(startDate = start.toString(), endDate = end.toString(), kind = LIBRARY_KIND, title = "Library", rowHash = hash, importId = null))
    for (w in libraryDemand(base, start, end, hours, now)) {
        db.tasks().insertOnce(TaskRow(title = w.title, dueMs = w.due.toInstant().toEpochMilli(), dueZone = w.due.zone.id, heaviness = w.heaviness, hoursEstimate = w.hours,
            createdMs = nowMs, source = "library", sourceKey = w.key, reason = w.reason))
    }
    null
}

/** Removes a library period and the catch-up work it made that has not been started. Work already done on it stays. */
suspend fun PlanRepo.removeLibraryPeriod(db: EfaDb, id: Long) = db.withTransaction {
    val row = db.periods().all().firstOrNull { it.id == id && it.kind == LIBRARY_KIND } ?: return@withTransaction
    var d = LocalDate.parse(row.startDate); val end = LocalDate.parse(row.endDate)
    while (!d.isAfter(end)) { db.tasks().deleteOpenByKey("library:$d"); d = d.plusDays(1) }
    db.periods().deleteById(id)
}
