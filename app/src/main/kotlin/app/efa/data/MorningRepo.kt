package app.efa.data

import androidx.room.withTransaction
import efa.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One morning at a time (M5, M6, M8, X2): when the alarm rang, how it was stopped, and when the ready photo ended the social block.
 * Every write is one small transaction, so a crash half way leaves the last complete state (X7).
 */
class MorningRepo(private val db: EfaDb) {
    fun dateOf(ms: Long, zone: ZoneId = LONDON): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    suspend fun get(date: LocalDate): MorningRow? = db.mornings().get(date.toString())

    /** The newest morning, which is the one that matters for the phone right now. */
    suspend fun latest(): MorningRow? = db.mornings().latest()

    /** Starts ringing for the alarm planned at [alarmAtMs]. Safe to call twice: a morning that is already ringing is left alone. */
    suspend fun fire(alarmAtMs: Long, reason: String, nowMs: Long): MorningRow = db.withTransaction {
        val date = dateOf(alarmAtMs)
        val existing = db.mornings().get(date.toString())
        if (existing?.ringStartedMs != null) return@withTransaction existing
        (existing ?: MorningRow(date.toString(), alarmAtMs, reason)).copy(ringStartedMs = nowMs, missed = false).also { db.mornings().put(it) }
    }

    /** X2: the phone was off or dead at alarm time. The morning is flagged and nothing is penalised or blocked. */
    suspend fun markMissed(alarmAtMs: Long, reason: String): Boolean = db.withTransaction {
        val date = dateOf(alarmAtMs).toString()
        if (db.mornings().get(date) != null) return@withTransaction false
        db.mornings().put(MorningRow(date, alarmAtMs, reason, missed = true)); true
    }

    suspend fun failedPhoto(date: LocalDate, score: Int) = update(date) { it.copy(failedPhotos = it.failedPhotos + 1, bestScore = maxOf(it.bestScore, score)) }
    suspend fun skipToSteps(date: LocalDate) = update(date) { it.copy(failedPhotos = maxOf(it.failedPhotos, PHOTO_ATTEMPTS)) }
    suspend fun setSteps(date: LocalDate, steps: Int) = update(date) { it.copy(steps = maxOf(it.steps, steps)) }
    suspend fun startWait(date: LocalDate, nowMs: Long) = update(date) { if (it.waitStartedMs == null) it.copy(waitStartedMs = nowMs) else it }
    suspend fun stop(date: LocalDate, method: StopMethod, score: Int, nowMs: Long) =
        update(date) { if (it.stoppedMs != null) it else it.copy(stoppedMs = nowMs, stopMethod = method.name, bestScore = maxOf(it.bestScore, score)) }

    suspend fun readyFailed(date: LocalDate) = update(date) { it.copy(readyFailed = it.readyFailed + 1) }
    suspend fun startReadyWait(date: LocalDate, nowMs: Long) = update(date) { if (it.readyWaitStartedMs == null) it.copy(readyWaitStartedMs = nowMs) else it }
    suspend fun ready(date: LocalDate, method: StopMethod, nowMs: Long) =
        update(date) { if (it.readyMs != null) it else it.copy(readyMs = nowMs, readyMethod = method.name) }

    /**
     * Z3: for each of the last 14 mornings that the alarm was stopped, how many minutes later than the planned wake that was. Only late mornings
     * are listed. A morning with no planned wake (a free morning) has none.
     */
    suspend fun lateness(slots: List<PatternSlot>, today: LocalDate, zone: ZoneId = LONDON): Lateness =
        db.mornings().since(today.minusDays(14).toString()).mapNotNull { r ->
            val stopped = r.stoppedMs ?: return@mapNotNull null
            val date = LocalDate.parse(r.date)
            val planned = slotsFor(slots, date, zone).firstOrNull { it.slot.title == "Wake, shower" }?.start ?: return@mapNotNull null
            val late = ((stopped - planned.toInstant().toEpochMilli()) / 60_000).toInt()
            if (late > 0) date to late else null
        }.toMap()

    private suspend fun update(date: LocalDate, f: (MorningRow) -> MorningRow) = db.withTransaction {
        db.mornings().get(date.toString())?.let { db.mornings().put(f(it)) }
    }
}

/** The core view of a stored morning. */
fun MorningRow.toState(): MorningState = MorningState(
    LocalDate.parse(date), ringStartedMs, stoppedMs, stopMethod?.let { runCatching { StopMethod.valueOf(it) }.getOrNull() },
    readyMs, readyMethod?.let { runCatching { StopMethod.valueOf(it) }.getOrNull() },
)
