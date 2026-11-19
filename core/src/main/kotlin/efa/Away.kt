package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** PL1: the time zone a date is lived in. An Away span can set one; everywhere else it is London. Alarms, meals and lights-out are read in it. */
fun zoneFor(date: LocalDate, instances: List<Instance>, default: ZoneId = LONDON): ZoneId {
    val away = instances.lastOrNull { it.preset == Preset.AWAY && date in it.dates() } ?: return default
    val id = away.str("zone").trim()
    return if (id.isEmpty()) default else runCatching { ZoneId.of(id) }.getOrDefault(default)
}

/** PL2: attendance and place checks are exempt on a date an Away span covers. The exemption is shown, never silent (see [placeExemptionNote]). */
fun placeChecksExempt(date: LocalDate, instances: List<Instance>): Boolean = instances.any { it.preset == Preset.AWAY && date in it.dates() }

fun placeExemptionNote(date: LocalDate, instances: List<Instance>): String? =
    if (placeChecksExempt(date, instances)) "Place checks are off today: you are away." else null

/** PL3: an alarm is stored as an instant, so it does not move when the zone does. The wall-clock time on [date] in [zone] is what the person set. */
fun alarmInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant = date.atTime(time).atZone(zone).toInstant()

/** PL4: the clock-change rule applies in every zone: a day's length is read in the zone it is lived in. */
fun dayLengthIn(date: LocalDate, zone: ZoneId): Long = dayLengthMinutes(date, zone)
