package app.efa.data

import efa.Door
import efa.DoorDecision
import efa.DoorUse
import efa.doorDecision
import java.time.Duration
import java.time.Instant

/**
 * DP1, DP2: every use of a door is one line in one place, so the weekly pool counts them all together. A line is the door, the epoch millisecond, the obligation minutes,
 * the gate minutes and whether it was verified. Uses older than 14 days are dropped when a new one is written.
 */
class DoorRepo(private val db: EfaDb) {
    suspend fun uses(): List<DoorUse> = db.meta().get(META_DOOR_USES).orEmpty().lines().mapNotNull(::decode)

    suspend fun record(u: DoorUse) {
        val keep = uses().filter { it.at.isAfter(u.at.minus(Duration.ofDays(14))) } + u
        db.meta().put(MetaRow(META_DOOR_USES, keep.joinToString("\n") { encode(it) }))
    }

    suspend fun decide(request: DoorUse, now: Instant = Instant.now()): DoorDecision = doorDecision(uses(), request, now)

    private fun encode(u: DoorUse) = listOf(u.door.name, u.at.toEpochMilli(), u.obligationMin, u.gateMin, u.verified).joinToString("|")

    private fun decode(line: String): DoorUse? = runCatching {
        val p = line.split("|")
        DoorUse(Door.valueOf(p[0]), Instant.ofEpochMilli(p[1].toLong()), p[2].toInt(), p[3].toInt(), p[4].toBoolean())
    }.getOrNull()
}
