package efa

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * LC1: the attendance code. The university asks for a code once a lecture hour has passed, whether you sat in the room or watched at home,
 * so a reminder is due at every full hour of a lecture and at its end. It reads the same calendar as everything else: a lecture that is
 * cancelled, moved or taken off by a day off gives no reminder, and one that an import adds gives one.
 */
data class CodeReminder(val at: ZonedDateTime, val lecture: String, val key: String) {
    fun text(): String = "Enter the attendance code for $lecture."
}

/** A 60-minute lecture is reminded at its end; a 50-minute one at its end; a 2-hour one at 1 hour and at its end. */
fun lectureCodeReminders(slots: List<PatternSlot>, from: LocalDate, days: Int, zone: java.time.ZoneId = LONDON): List<CodeReminder> =
    (0L until days.toLong()).flatMap { slotsFor(slots, from.plusDays(it), zone) }
        .filter { it.slot.type == "lecture" }
        .flatMap { ds ->
            val minutes = Duration.between(ds.start, ds.end).toMinutes()
            val marks = (60L until minutes step 60L).toList() + minutes
            val name = ds.slot.title.substringBefore(" - ")
            marks.map { m -> CodeReminder(ds.start.plusMinutes(m), name, blockKey(ds) + "@" + m) }
        }
        .sortedBy { it.at }

/** The first reminder strictly after [after], looking a week ahead. null if there is none. */
fun nextCodeReminder(slots: List<PatternSlot>, after: ZonedDateTime): CodeReminder? =
    lectureCodeReminders(slots, after.toLocalDate().minusDays(1), 9, after.zone).firstOrNull { it.at.isAfter(after) }
