package app.efa.data

import efa.Fact
import efa.NfcPurpose
import efa.decodeFact
import efa.decodeTags
import efa.encodeFact
import efa.encodeTags
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** FB1: facts, one a line, the last 500. NFC taps and tap proofs from the routine runner write here; the runner and the day's wins read here. */
class FactRepo(private val db: EfaDb) {
    suspend fun all(): List<Fact> = db.meta().get(META_FACTS).orEmpty().lines().mapNotNull(::decodeFact)

    suspend fun record(f: Fact) { db.meta().put(MetaRow(META_FACTS, (all() + f).takeLast(500).joinToString("\n") { encodeFact(it) })) }

    suspend fun today(zone: ZoneId = efa.LONDON, date: LocalDate = LocalDate.now(zone)): List<Fact> = all().filter { it.at.atZone(zone).toLocalDate() == date }

    /** The places tapped in the last [minutes], so a tap is not counted for a step an hour later. */
    suspend fun recentTaps(minutes: Long = 30): Set<NfcPurpose> {
        val from = Instant.now().minusSeconds(minutes * 60)
        return all().filter { it.type == efa.FactType.NFC_TAG && !it.at.isBefore(from) }.mapNotNull { runCatching { NfcPurpose.valueOf(it.ref) }.getOrNull() }.toSet()
    }

    suspend fun tags(): Map<String, NfcPurpose> = decodeTags(db.meta().get(META_NFC_TAGS))
    suspend fun saveTags(m: Map<String, NfcPurpose>) { db.meta().put(MetaRow(META_NFC_TAGS, encodeTags(m))) }
}
