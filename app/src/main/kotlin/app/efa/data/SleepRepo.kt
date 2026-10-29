package app.efa.data

import androidx.room.withTransaction
import efa.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Passive sleep logging, record only (layer 2). Events go in as they happen, a heartbeat shows the logger stayed alive (S7), each finished
 * night is reduced to one summary line, and raw events older than 30 days are deleted once their night is summarised (ST4).
 * Failure behaviour (X7): a write that fails is dropped and logging carries on, because a missed event must never stop the blocker.
 */
class SleepRepo(private val db: EfaDb) {
    suspend fun record(kind: SensorKind, value: Int = 0, atMs: Long = System.currentTimeMillis()) {
        try { db.sensors().insert(SensorEventRow(atMs = atMs, kind = kind.code, value = value)) } catch (e: Exception) { /* dropped, see above */ }
    }

    /** S7: writes a heartbeat if nothing at all has been written for [HEARTBEAT_EVERY]. Returns whether it wrote one. */
    suspend fun heartbeat(nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = db.sensors().lastAt()
        if (last != null && nowMs - last < HEARTBEAT_EVERY.toMillis()) return false
        record(SensorKind.HEARTBEAT, 0, nowMs); return true
    }

    suspend fun events(fromMs: Long, untilMs: Long): List<SensorEvent> =
        db.sensors().between(fromMs, untilMs).mapNotNull { r -> SensorKind.of(r.kind)?.let { SensorEvent(r.atMs, it, r.value) } }

    /**
     * Summarises every night that has finished (it is past noon the next day) among the last [days] evenings, and any night that old raw
     * events belong to, then deletes raw events older than 30 days. Safe to run as often as you like.
     */
    suspend fun rollup(now: Instant, zone: ZoneId = LONDON, days: Int = 4): Int = db.withTransaction {
        val today = now.atZone(zone).toLocalDate()
        val recent = (0..days).map { today.minusDays(it.toLong() + 1) }.toSet()
        val dates = recent.toMutableSet()
        val cutoff = rawEventsToDelete(db.sensors().before(now.toEpochMilli()).mapNotNull { r -> SensorKind.of(r.kind)?.let { SensorEvent(r.atMs, it, r.value) } }, now)
        cutoff.forEach { dates += nightDateOf(it.atMs, zone) }
        var written = 0
        for (d in dates) {
            val (from, until) = nightWindow(d, zone)
            if (until > now.toEpochMilli()) continue                        // the night is not over
            if (d !in recent && db.nights().get(d.toString()) != null) continue   // an old night already summarised: its raw events may be half deleted, so never redo it
            // Charging state at bedtime comes from the last charging event before the window, so read a day earlier.
            val ev = events(from - 24L * 3_600_000, until)
            // S1, S2: the night-before photo is the shutdown photo and the photo that stopped the alarm is the wake photo. Either may be missing.
            val shutdown = db.references().get(RefKind.NIGHT)?.takenAtMs
            val wakePhoto = db.mornings().get(d.plusDays(1).toString())?.stoppedMs
            val s = summariseNight(d, ev, zone, shutdown, wakePhoto) ?: continue
            db.nights().upsert(NightSummaryRow(d.toString(), s.night.startMs, s.night.endMs, s.night.sleepMin, s.night.checkCount, s.night.checkMin,
                s.chargingPct, s.motionEvents, s.darkPct, s.gapCount, now.toEpochMilli()))
            written++
        }
        db.sensors().deleteBefore(now.minus(StoragePolicy.SENSOR_EVENTS_KEEP).toEpochMilli())
        written
    }

    /** The time of the night-before photo, which is the shutdown photo (S1), or null. */
    suspend fun shutdownPhotoMs(): Long? = db.references().get(RefKind.NIGHT)?.takenAtMs

    suspend fun latestNight(): NightSummaryRow? = db.nights().latest()
}
