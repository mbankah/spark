package app.efa.data

import efa.ChangeClass
import efa.ChangeEntry
import java.time.Duration
import java.time.Instant

/** CE1, CE5: every counted change in one place, so the weekly allowances count imports, moves and loosening together. Entries older than 60 days are dropped when one is written. */
class ChangeRepo(private val db: EfaDb) {
    suspend fun history(): List<ChangeEntry> = db.meta().get(META_CHANGES).orEmpty().lines().mapNotNull(::decode)

    suspend fun record(cls: ChangeClass, import: Boolean, id: Long, at: Instant = Instant.now()) {
        val keep = history().filter { it.at.isAfter(at.minus(Duration.ofDays(60))) } + ChangeEntry(cls, at, import, id)
        db.meta().put(MetaRow(META_CHANGES, keep.joinToString("\n") { encode(it) }))
    }

    /** CE7: break glass writes the history back without the entries it lifted. */
    suspend fun replace(l: List<ChangeEntry>) { db.meta().put(MetaRow(META_CHANGES, l.joinToString("\n") { encode(it) })) }

    /** CE8: an undo refunds the count. */
    suspend fun refund(id: Long) { db.meta().put(MetaRow(META_CHANGES, efa.refund(history(), id).joinToString("\n") { encode(it) })) }

    /** CE6: the trial counts from the first use. */
    suspend fun installedAt(): Instant = db.meta().get(META_FIRST_USE)?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now()

    private fun encode(e: ChangeEntry) = listOf(e.id, e.at.toEpochMilli(), e.cls.name, e.import).joinToString("|")

    private fun decode(line: String): ChangeEntry? = runCatching {
        val p = line.split("|")
        ChangeEntry(ChangeClass.valueOf(p[2]), Instant.ofEpochMilli(p[1].toLong()), p[3].toBoolean(), p[0].toLong())
    }.getOrNull()
}
