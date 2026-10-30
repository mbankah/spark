package app.efa.data

import androidx.room.withTransaction
import efa.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What the money screen and the home card show (R4, R6 to R9, G6). */
data class MoneyView(
    val live: Boolean, val liveFrom: LocalDate, val week: WeekResult, val savedPence: Int, val closedWeeks: Int,
)

/**
 * The weekly guide and earned phone access, read from what was proven (W2). The money is a guide only: nothing here touches a bank (section 9).
 * Failure behaviour (X7): if the database cannot be read nothing is shown rather than a wrong number.
 */
class RewardsRepo(private val db: EfaDb) {
    private fun ms(d: LocalDate, zone: ZoneId) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The minutes confirmed for each block of the week starting [monday]: the key of the block to its credited minutes. */
    suspend fun credited(monday: LocalDate, zone: ZoneId = LONDON): Map<String, Int> =
        db.blocks().verifiedBetween(ms(monday.minusDays(1), zone), ms(monday.plusDays(8), zone)).associate { it.key to it.creditedMin }

    /** G6: the first week whose money counts is the first one that starts after the 14 record-only days. */
    fun liveFrom(firstUseMs: Long, zone: ZoneId = LONDON): LocalDate {
        val after = Instant.ofEpochMilli(firstUseMs).atZone(zone).toLocalDate().plusDays(RECORD_ONLY_DAYS)
        return if (after == weekStart(after)) after else weekStart(after).plusWeeks(1)
    }

    suspend fun week(slots: List<PatternSlot>, monday: LocalDate, zone: ZoneId = LONDON): WeekResult = weekResult(slots, monday, credited(monday, zone), zone)

    /**
     * R9: weeks that have ended are written once and never changed, then the unearned part of the other 40 pounds is in savings. A week in the first
     * 14 days is written with live = false and counts for nothing (G6).
     */
    suspend fun closeWeeks(slots: List<PatternSlot>, firstUseMs: Long, now: ZonedDateTime): Int = db.withTransaction {
        val zone = now.zone
        val first = weekStart(Instant.ofEpochMilli(firstUseMs).atZone(zone).toLocalDate())
        val thisMonday = weekStart(now.toLocalDate())
        val liveFrom = liveFrom(firstUseMs, zone)
        var n = 0
        var m = first
        while (m.isBefore(thisMonday)) {
            val pendingClaim = db.daysOff().pending().any { val d = LocalDate.parse(it.date); !d.isBefore(m) && d.isBefore(m.plusDays(7)) }
            if (!pendingClaim && db.weekMoney().all().none { it.monday == m.toString() }) {      // H6: a week waits for its claims
                val r = week(slots, m, zone)
                db.weekMoney().insert(WeekMoneyRow(m.toString(), r.plannedWeight, r.verifiedWeight, r.money.freeToSpendPence, r.money.savingsPence, !m.isBefore(liveFrom), now.toInstant().toEpochMilli()))
                n++
            }
            m = m.plusWeeks(1)
        }
        n
    }

    suspend fun view(slots: List<PatternSlot>, firstUseMs: Long, now: ZonedDateTime): MoneyView {
        val liveFrom = liveFrom(firstUseMs, now.zone)
        val thisMonday = weekStart(now.toLocalDate())
        val closed = db.weekMoney().all()
        return MoneyView(!thisMonday.isBefore(liveFrom), liveFrom, week(slots, thisMonday, now.zone),
            closed.filter { it.live }.sumOf { it.savingsPence }, closed.count { it.live })
    }

    /** R2, O3: what each block proven today bought. Lectures and tutorials earn nothing: access is earned by work blocks. */
    suspend fun earnings(now: ZonedDateTime): List<Earning> {
        val dayStart = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
        return db.blocks().provenSince(dayStart).filter { it.type !in setOf("lecture", "tutorial") && blockHeaviness(it.type) != null }
            .mapNotNull { r -> r.provenAtMs?.let { Earning(it, earnedAccessMinutes(r.creditedMin)) } }
    }
}
