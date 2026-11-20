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
        val active = db.modifiers().all().filter { it.state == "active" }.mapNotNull { decodeInstance(it.instance) }
        val anchors = active.mapNotNull { nightAnchorsOf(it) }.associateBy { it.night }          // SA1, SA2: the guard rail reads the nights that were moved
        val p = previewOf(inst, base, db.tasks().open().map { it.toDue() }, now, anchors = anchors)
        val clash = modifierConflicts(inst, active)
        return if (clash.isEmpty()) p else p.copy(problems = p.problems + clash)
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
        c.anchors.firstOrNull()?.let { a -> db.meta().put(MetaRow(META_WAKE_CHOSEN, a.wakeAt().toInstant().toEpochMilli().toString())) }      // WT1, SA1: the alarm rings at the wake of the night
        if (c.dayOffDates.isNotEmpty()) PlanRepo(db).addDaysOff(db, c.dayOffDates, "period_off", wallLength, now, sameDay = c.dayOffDates.first() == now.toLocalDate(), withDemand = !c.noDemand)
        id
    }

    /** CW1: what choosing [chosen] as the wake would do to the morning: the Late start it needs (null when the morning stays), why it cannot stand, and whether it needs the wall. */
    suspend fun wakeChoicePlan(chosen: ZonedDateTime, now: ZonedDateTime): WakeChoicePlan {
        val inst = wakeChoiceInstance(plannedWakeMin(PlanRepo(db).slots(), chosen.toLocalDate(), chosen.zone), chosen) ?: return WakeChoicePlan(null, emptyList(), false)
        val p = preview(inst, now)
        return WakeChoicePlan(inst, p.problems, p.wallNeeded)
    }

    /** CW1: takes away the Late start a chosen wake made, so the morning is the normal one again. Returns how many were removed. */
    suspend fun clearWakeChoices(): Int {
        val rows = db.modifiers().all().filter { it.state == "active" && isWakeChoice(decodeInstance(it.instance)) }
        rows.forEach { remove(it.id) }
        return rows.size
    }

    /** Removes a modifier and what it made that has not been started. Work already done stays. */
    suspend fun remove(id: Long) = db.withTransaction {
        val row = db.modifiers().get(id) ?: return@withTransaction
        db.tasks().deleteOpenByPrefix("mod:$id:%")
        val inst = decodeInstance(row.instance)
        val dates = inst?.takeIf { it.preset == Preset.PERIOD_OFF || (it.preset == Preset.FOCUS && it.bool("hybrid")) }?.dates().orEmpty()
        if (dates.isNotEmpty()) {
            db.daysOff().cancelBySource(dates.map { it.toString() }, "period_off")   // H1: the use stays in the tally
            dates.forEach { db.tasks().deleteOpenByPrefix("dayoff:$it|%") }
        }
        if (inst?.preset == Preset.NIGHT) db.meta().put(MetaRow(META_WAKE_CHOSEN, "0"))
        db.modifiers().delete(id)
    }
}
