package app.efa.data

import efa.*
import java.time.LocalDate
import java.time.ZoneId

/**
 * VR1 to VR8: the VRNPM cycle of one study block and the settings that shape it, kept in the small key-value store (nothing in the schema changes). A finished cycle leaves behind the N time
 * the next cycle is planned with (from timed pages) and, unless the Red Engine has taken it over or the remedial time is 0, the remedial it earned, as a task due on the Sunday evening
 * or after the catch-up span when it was done inside one.
 */
class VrRepo(private val db: EfaDb) {
    private fun key(ds: DatedSlot) = PREFIX + blockKey(ds)

    suspend fun get(ds: DatedSlot): VrRun? = VrRun.decode(db.meta().get(key(ds)))

    private suspend fun put(ds: DatedSlot, r: VrRun) = db.meta().put(MetaRow(key(ds), r.encode()))

    /** VR7: the settings, or the optimal cycle of the spec when none were saved. */
    suspend fun config(): VrConfig = VrConfig.decode(db.meta().get(CONFIG_KEY))

    suspend fun saveConfig(cfg: VrConfig) = db.meta().put(MetaRow(CONFIG_KEY, cfg.clamped().encode()))

    /** The N time for a new cycle: what the settings say, which timed pages keep tuned. */
    suspend fun plannedN(): Int = config().n

    /** The nine times a cycle started now would use, after the longest-cycle cap. */
    suspend fun plan(): List<Int> = vrPlan(config())

    suspend fun start(ds: DatedSlot, nowMs: Long) { if (get(ds) == null) put(ds, vrStart(plan(), nowMs, overridden = if (config().skipVR) "VR" else "")) }

    suspend fun page(ds: DatedSlot, nowMs: Long) { get(ds)?.let { put(ds, vrPage(it, nowMs)) } }

    /** VR9: the cycle's clock stops while it is paused; the time paused is kept. */
    suspend fun pause(ds: DatedSlot, nowMs: Long) { get(ds)?.let { put(ds, vrPause(it, nowMs)) } }

    suspend fun resume(ds: DatedSlot, nowMs: Long) { get(ds)?.let { put(ds, vrResume(it, nowMs)) } }

    /** Ends the running phase. When that finishes the cycle, the next N time and the remedial are settled once. */
    suspend fun advance(ds: DatedSlot, nowMs: Long, zone: ZoneId) {
        val run = get(ds) ?: return
        val next = vrAdvance(run, nowMs)
        if (next == run) return
        put(ds, next)
        if (vrView(next, nowMs).status == VrStatus.DONE && vrView(run, nowMs).status != VrStatus.DONE) finished(ds, next, nowMs, zone)
    }

    private suspend fun finished(ds: DatedSlot, run: VrRun, nowMs: Long, zone: ZoneId) {
        val cfg = config()
        vrAveragePageMs(run)?.let { avg -> saveConfig(cfg.copy(n = suggestedN(cfg.pagesPerCycle, avg))) }
        if (!remedialBooked(cfg)) return                       // VR8: the Red Engine has taken it over, or the remedial time is 0
        val catchUpEnd = db.modifiers().all().filter { it.state == "active" }.mapNotNull { decodeInstance(it.instance) }.filter { it.preset == Preset.CATCH_UP }.maxOfOrNull { it.end }
        val due = remedialDue(ds.plannedDay, catchUpEnd, zone)
        db.tasks().insertOnce(TaskRow(title = REMEDIAL_TITLE, dueMs = due.toInstant().toEpochMilli(), dueZone = zone.id, heaviness = 3, hoursEstimate = cfg.remedialMin / 60.0,
            createdMs = nowMs, source = "remedial", sourceKey = "remedial:" + blockKey(ds), reason = "A finished VRNPM cycle earns ${cfg.remedialMin} minutes of remedial"))
    }

    /** VR8: when the Red Engine is built the remedial it replaces is taken off the list: the ones not started and with no minutes done. Work already begun stays. */
    suspend fun removeOpenRemedial(): Int = db.tasks().deleteOpenRemedial()

    /** The cycles finished on [date], for the day's count. */
    suspend fun finishedOn(date: LocalDate): Int =
        db.meta().withPrefix(PREFIX + date + "|").mapNotNull { VrRun.decode(it.value) }.count { vrView(it, Long.MAX_VALUE / 4).status == VrStatus.DONE }

    companion object {
        const val PREFIX = "vr:"
        const val CONFIG_KEY = "vrcfg"
    }
}
