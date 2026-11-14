package app.efa.data

import androidx.room.withTransaction
import efa.*
import java.time.ZonedDateTime

/**
 * Applying and removing modifiers (Modify Schedule). Applying works the plan out again, refuses anything that fails a guard rail (spec section 10), and writes everything in
 * one transaction, so a failure leaves nothing half done (IM11). A period off also records its days off, so the days off tally and the wall string rise as they do for any day off.
 */
class ModifierRepo(private val db: EfaDb) {
    suspend fun all(): List<ModifierRow> = db.modifiers().all()

    suspend fun preview(inst: Instance, now: ZonedDateTime): ModPreview {
        val base = PlanRepo(db).slots()
        return previewOf(inst, base, db.tasks().open().map { it.toDue() }, now)
    }

    /** The wall a period off needs: one request, one use toward the 30 day tally, 25 characters more for each day beyond two (spec 7.2, proposed). */
    suspend fun periodOffWall(days: Int, now: ZonedDateTime, sameDay: Boolean): Int =
        dayOffWall(PlanRepo(db).uses(db), now, sameDay) + 25 * (days - 2).coerceAtLeast(0)

    suspend fun apply(inst: Instance, now: ZonedDateTime, wallLength: Int): Long = db.withTransaction {
        val p = preview(inst, now)
        check(p.canApply) { p.problems.joinToString(" ") }
        val nowMs = now.toInstant().toEpochMilli()
        val c = p.compiled
        val label = "${inst.preset.label}, until ${inst.end}"
        val id = db.modifiers().insert(ModifierRow(preset = inst.preset.id, startDate = inst.start.toString(), endDate = inst.end.toString(), label = label,
            instance = encodeInstance(inst), compiled = encodeSlots(c.slots), state = "active", createdMs = nowMs, loosened = p.wallNeeded))
        c.tasks.forEachIndexed { n, w ->
            db.tasks().insertOnce(TaskRow(title = w.title, dueMs = w.due.toInstant().toEpochMilli(), dueZone = w.due.zone.id, heaviness = w.heaviness, hoursEstimate = w.hours,
                createdMs = nowMs, source = "modifier", sourceKey = "mod:$id:$n", reason = "${inst.preset.label}"))
        }
        if (c.dayOffDates.isNotEmpty()) PlanRepo(db).addDaysOff(db, c.dayOffDates, "period_off", wallLength, now, sameDay = c.dayOffDates.first() == now.toLocalDate())
        id
    }

    /** Removes a modifier and what it made that has not been started. Work already done stays. */
    suspend fun remove(id: Long) = db.withTransaction {
        val row = db.modifiers().get(id) ?: return@withTransaction
        db.tasks().deleteOpenByPrefix("mod:$id:%")
        val inst = decodeInstance(row.instance)
        val dates = inst?.takeIf { it.preset == Preset.PERIOD_OFF || (it.preset == Preset.FOCUS && it.bool("hybrid")) }?.dates().orEmpty()
        if (dates.isNotEmpty()) {
            db.daysOff().deleteBySource(dates.map { it.toString() }, "period_off")
            dates.forEach { db.tasks().deleteOpenByPrefix("dayoff:$it|%") }
        }
        db.modifiers().delete(id)
    }
}
