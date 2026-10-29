package efa

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** Work a missed block leaves behind, shown with the reason (Z4). Its [key] makes it appear once (RS5). */
data class CatchUp(val key: String, val title: String, val hours: Double, val heaviness: Int, val due: ZonedDateTime, val reason: String)

private val CATCH_UP_TYPES = setOf("lecture", "tutorial", "study-standard", "study-light", "study", "code")
const val CATCH_UP_LOOKBACK_DAYS = 3L

/**
 * RS1, D5: a lecture or work block that ended without its proof and whose backfill window has closed (B7: next morning) becomes work in
 * the Buffer, due by the end of the next day. A lecture comes back at its own length (the recording); a study cycle comes back at its
 * cycle size. The lost time stays lost (W2, B6): this adds the content back, it does not forgive anything.
 * [badNightDays] are the days that began after a night of 6.5 hours of sleep or less (B8), so the reason can say so (Z4). [known] holds keys already turned into work. [notBefore] is when the app was first used, so installing it does not invent a backlog.
 */
fun missedCatchUps(
    slots: List<PatternSlot>, now: ZonedDateTime, verified: Set<String>, known: Set<String>, notBefore: ZonedDateTime, zone: ZoneId = now.zone, badNightDays: Set<java.time.LocalDate> = emptySet(),
): List<CatchUp> =
    (-CATCH_UP_LOOKBACK_DAYS..0L).flatMap { slotsFor(slots, now.toLocalDate().plusDays(it), zone) }
        .filter { it.slot.type in CATCH_UP_TYPES && !isConversion(it.slot) }   // a converted slot's work is the open tasks, which stay open
        .filter { !it.end.isAfter(now) && !it.start.isBefore(notBefore) && it.end.isAfter(now.minusDays(CATCH_UP_LOOKBACK_DAYS + 1)) }
        .filter { blockKey(it) !in verified && "missed:" + blockKey(it) !in known }
        .filter { !now.isBefore(nextWake(slots, it.end) ?: it.end.plusHours(12)) }
        .sortedBy { it.start }
        .map { ds ->
            val len = Duration.between(ds.start, ds.end).toMinutes() / 60.0
            val (hours, heaviness) = when (ds.slot.type) {
                "lecture", "tutorial" -> len to 4
                "study-standard" -> STUDY_CYCLE_STANDARD_HOURS to 6
                "study-light" -> STUDY_CYCLE_LIGHT_HOURS to 3
                else -> len to 5
            }
            val name = ds.slot.title.substringBefore(" - ").removePrefix("Notes and problems: ")
            val noun = if (ds.slot.type == "lecture" || ds.slot.type == "tutorial") "lecture" else "study block"
            val label = if (name.endsWith(noun, ignoreCase = true)) name else "$name $noun"
            CatchUp("missed:" + blockKey(ds), "Catch-up: $name", hours, heaviness, endOfDayCutoff(slots, ds.plannedDay.plusDays(1), zone),
                // Z4: after a bad night the reason says so, with what it costs, for example "Bad night, missed Randomness lecture, Buffer -1.0h"
                if (ds.plannedDay in badNightDays) "Bad night, missed $label, Buffer -${fmt1(hours)}h" else "Missed $label")
        }
