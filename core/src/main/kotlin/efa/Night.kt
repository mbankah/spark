package efa

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val LONDON: ZoneId = ZoneId.of("Europe/London")
val FALL_ASLEEP_DEFAULT: Duration = Duration.ofMinutes(20)   // N1 default

/** N1: cutoff = tomorrow's target wake - 8 h - time to fall asleep. Real elapsed time, so it is right across a clock change (X4). */
fun nightCutoff(targetWake: ZonedDateTime, fallAsleep: Duration = FALL_ASLEEP_DEFAULT): ZonedDateTime =
    targetWake.minusHours(8).minus(fallAsleep)

/** N7: no override is possible from 6 h before target wake. */
fun hardStop(targetWake: ZonedDateTime): ZonedDateTime = targetWake.minusHours(6)

/** An override can be bought from the cutoff until the hard stop. */
fun overrideAllowed(now: ZonedDateTime, targetWake: ZonedDateTime) =
    now.isBefore(hardStop(targetWake)) && !now.isBefore(nightCutoff(targetWake))

/** N3 / N5: 30 minutes, 45 after a fully verified day. */
fun overrideMinutes(fullyVerifiedDay: Boolean) = if (fullyVerifiedDay) 45 else 30

/** N6: "Wake 09:00, so sleeping after 01:00 means under 8h". */
fun eightHourSum(targetWake: ZonedDateTime): String {
    val f = DateTimeFormatter.ofPattern("HH:mm")
    return "Wake ${f.format(targetWake)}, so sleeping after ${f.format(targetWake.minusHours(8))} means under 8h"
}

fun at(date: LocalDate, t: LocalTime, zone: ZoneId = LONDON): ZonedDateTime = ZonedDateTime.of(date, t, zone)
