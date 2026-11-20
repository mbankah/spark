package app.efa.data

import efa.DatedSlot
import efa.RunRecord
import efa.blockKey
import java.time.LocalDate

/**
 * ST2: what happened to a block with no proof, kept in the small key-value store so nothing in the schema changes. One entry per block, keyed by the block's own key
 * (its planned day, start and title). A start is never overwritten by a later one, and a finish without a start counts the start as the finish.
 */
class RunRepo(private val db: EfaDb) {
    private fun key(ds: DatedSlot) = PREFIX + blockKey(ds)

    suspend fun get(ds: DatedSlot): RunRecord? = RunRecord.decode(db.meta().get(key(ds)))

    private suspend fun put(ds: DatedSlot, r: RunRecord) = db.meta().put(MetaRow(key(ds), r.encode()))

    suspend fun start(ds: DatedSlot, nowMs: Long) { val r = get(ds); if (r?.startMs == null) put(ds, RunRecord(startMs = nowMs)) }

    suspend fun finish(ds: DatedSlot, nowMs: Long) { val r = get(ds) ?: RunRecord(startMs = nowMs); if (r.endMs == null) put(ds, r.copy(startMs = r.startMs ?: nowMs, endMs = nowMs)) }

    suspend fun skip(ds: DatedSlot, reason: String) = put(ds, RunRecord(skippedReason = reason.ifBlank { "No reason given" }))

    /** Every record of one planned day, by block key (the key without its prefix), for the day strip. */
    suspend fun forDay(date: LocalDate): Map<String, RunRecord> =
        db.meta().withPrefix(PREFIX + date + "|").mapNotNull { m -> RunRecord.decode(m.value)?.let { m.key.removePrefix(PREFIX) to it } }.toMap()

    companion object { const val PREFIX = "run:" }
}
