package app.efa.data

import efa.*
import java.time.LocalDate
import java.time.ZoneId

/**
 * VR1 to VR6: the VRNPM cycle of one study block, kept in the small key-value store (nothing in the schema changes), and what a finished cycle leaves behind: the N time the next cycle
 * is planned with (from timed pages) and the 30 minutes of weekly remedial it earned, as a task due on the Sunday evening, or after the catch-up span when it was done inside one.
 */
class VrRepo(private val db: EfaDb) {
    private fun key(ds: DatedSlot) = PREFIX + blockKey(ds)

    suspend fun get(ds: DatedSlot): VrRun? = VrRun.decode(db.meta().get(key(ds)))

    private suspend fun put(ds: DatedSlot, r: VrRun) = db.meta().put(MetaRow(key(ds), r.encode()))

    /** The N time for a new cycle: what the timed pages suggested last time, or the spec's 45. */
    suspend fun plannedN(): Int = db.meta().get(N_KEY)?.toIntOrNull()?.coerceIn(20, 90) ?: VrStep.N.minutes

    suspend fun start(ds: DatedSlot, nowMs: Long) { if (get(ds) == null) put(ds, vrStart(plannedN(), nowMs)) }

    suspend fun page(ds: DatedSlot, nowMs: Long) { get(ds)?.let { put(ds, vrPage(it, nowMs)) } }

    /** Ends the running phase. When that finishes the cycle, the next N time and the remedial are settled once. */
    suspend fun advance(ds: DatedSlot, nowMs: Long, zone: ZoneId) {
        val run = get(ds) ?: return
        val next = vrAdvance(run, nowMs)
        if (next == run) return
        put(ds, next)
        if (vrView(next, nowMs).status == VrStatus.DONE && vrView(run, nowMs).status != VrStatus.DONE) finished(ds, next, nowMs, zone)
    }

    private suspend fun finished(ds: DatedSlot, run: VrRun, nowMs: Long, zone: ZoneId) {
        vrAveragePageMs(run)?.let { avg -> db.meta().put(MetaRow(N_KEY, suggestedN(PAGES_PER_CYCLE, avg).toString())) }
        val catchUpEnd = db.modifiers().all().filter { it.state == "active" }.mapNotNull { decodeInstance(it.instance) }.filter { it.preset == Preset.CATCH_UP }.maxOfOrNull { it.end }
        val due = remedialDue(ds.plannedDay, catchUpEnd, zone)
        db.tasks().insertOnce(TaskRow(title = REMEDIAL_TITLE, dueMs = due.toInstant().toEpochMilli(), dueZone = zone.id, heaviness = 3, hoursEstimate = REMEDIAL_MIN_PER_CYCLE / 60.0,
            createdMs = nowMs, source = "remedial", sourceKey = "remedial:" + blockKey(ds), reason = "A finished VRNPM cycle earns 30 minutes of remedial"))
    }

    /** The cycles finished on [date], for the day's count. */
    suspend fun finishedOn(date: LocalDate): Int =
        db.meta().withPrefix(PREFIX + date + "|").mapNotNull { VrRun.decode(it.value) }.count { vrView(it, Long.MAX_VALUE / 4).status == VrStatus.DONE }

    companion object {
        const val PREFIX = "vr:"
        const val N_KEY = "vrnpm_n"
        const val PAGES_PER_CYCLE = 4
    }
}
