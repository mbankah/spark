package efa

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Passive sleep logging, record only (layer 2, S1 to S7). Nothing here changes a rule: it turns what the phone noticed into a night.
 * Screen, charging, movement and light only. There is no microphone (S5).
 */
enum class SensorKind(val code: Int) {
    SCREEN_ON(1), SCREEN_OFF(2), CHARGING_ON(3), CHARGING_OFF(4),
    MOTION(5),          // Android's significant-motion trigger fired (an accelerometer-based hardware event)
    LIGHT(6),           // value = light bucket 0 dark, 1 dim, 2 room, 3 bright
    SERVICE_START(7),   // the logging service connected: a gap before this is time nothing was recorded (S7)
    HEARTBEAT(8);       // the service was alive at this moment
    companion object { fun of(code: Int) = entries.firstOrNull { it.code == code } }
}

data class SensorEvent(val atMs: Long, val kind: SensorKind, val value: Int = 0)

/** F3 (section 20): a long quiet stretch is at least 60 minutes of screen-off. */
val QUIET_STRETCH: Duration = Duration.ofMinutes(60)
/** S3: a night check is under 15 minutes of screen-on. 15 minutes or more is a long screen-on and ends the night (S2). */
val NIGHT_CHECK_MAX: Duration = Duration.ofMinutes(15)
/** S7: the service writes a heartbeat this often, so a silence longer than [SERVICE_GAP] while the phone was on is a gap. */
val HEARTBEAT_EVERY: Duration = Duration.ofMinutes(15)
val SERVICE_GAP: Duration = Duration.ofMinutes(40)

/** The evening the night is counted from and the time it is read until, as wall-clock times on the evening's date and the next day. */
val NIGHT_FROM: LocalTime = LocalTime.of(18, 0)
val NIGHT_UNTIL: LocalTime = LocalTime.of(12, 0)
/** A shutdown photo counts only if it was taken within this long of the evening start (18:00 to 03:00), so a photo taken in the morning cannot cut the night short (Q-G64). */
val SHUTDOWN_VALID_FOR: Duration = Duration.ofHours(9)

data class SleepNight(
    val startMs: Long, val endMs: Long,
    val checkCount: Int, val checkMin: Int,       // S3: short screen-ons inside the night, subtracted from sleep
    val sleepMin: Int,                            // S2, S3: end - start - checks
)

/** One line per night, kept after the raw events are removed (ST4). Percentages are whole numbers 0..100. */
data class NightSummary(
    val date: LocalDate, val night: SleepNight,
    val chargingPct: Int, val motionEvents: Int, val darkPct: Int, val gapCount: Int,
)

/**
 * S2, S3: sleep starts at the first screen-off after the evening start that is followed by a quiet stretch, joins small night checks into
 * the same night, and ends at the first long screen-on after that. Scrolling in bed is not sleep because a screen-on of 15 minutes or
 * more before the quiet stretch moves the start later. [events] may be in any order. Returns null when there is no quiet stretch.
 */
fun deriveNight(events: List<SensorEvent>, fromMs: Long, untilMs: Long, shutdownPhotoMs: Long? = null, wakePhotoMs: Long? = null): SleepNight? {
    // S1, S2: the shutdown photo and the wake photo bound the sleep window. A photo outside the evening-to-noon window, or none, changes nothing.
    val from = shutdownPhotoMs?.takeIf { it in fromMs until untilMs && it <= fromMs + SHUTDOWN_VALID_FOR.toMillis() } ?: fromMs
    val until = wakePhotoMs?.takeIf { it in (from + 1) until untilMs } ?: untilMs
    return deriveNightIn(events, from, until)
}

private fun deriveNightIn(events: List<SensorEvent>, fromMs: Long, untilMs: Long): SleepNight? {
    val screen = events.filter { it.atMs in fromMs until untilMs && (it.kind == SensorKind.SCREEN_ON || it.kind == SensorKind.SCREEN_OFF) }.sortedBy { it.atMs }
    if (screen.isEmpty()) return null
    // Screen-off intervals, each starting at an observed screen-off.
    data class Gap(val offAt: Long, val onAt: Long?)
    val gaps = ArrayList<Gap>()
    var offAt: Long? = null          // a gap only starts at a screen-off that was seen; before the first event the state is unknown
    for (e in screen) {
        if (e.kind == SensorKind.SCREEN_OFF) { if (offAt == null) offAt = e.atMs }
        else if (offAt != null) { gaps += Gap(offAt, e.atMs); offAt = null }
    }
    if (offAt != null) gaps += Gap(offAt, null)                   // still off at the end of the window
    fun len(g: Gap) = Duration.ofMillis((g.onAt ?: untilMs) - g.offAt)
    val first = gaps.indexOfFirst { len(it) >= QUIET_STRETCH }
    if (first < 0) return null
    // Join the following gaps while the screen-on between them is a night check.
    var end = gaps[first].onAt ?: untilMs
    var checks = 0; var checkMs = 0L
    var i = first
    while (i + 1 < gaps.size) {
        val on = Duration.ofMillis(gaps[i + 1].offAt - (gaps[i].onAt ?: break))
        if (on >= NIGHT_CHECK_MAX) break
        checks++; checkMs += on.toMillis(); i++
        end = gaps[i].onAt ?: untilMs
    }
    val start = gaps[first].offAt
    val sleep = ((end - start) - checkMs).coerceAtLeast(0) / 60_000
    return SleepNight(start, end, checks, (checkMs / 60_000).toInt(), sleep.toInt())
}

/** The window a night is read from: 18:00 on [date] to 12:00 the next day, in wall-clock time, so the 25-hour night of a clock change is whole (X4). */
fun nightWindow(date: LocalDate, zone: ZoneId = LONDON): Pair<Long, Long> =
    ZonedDateTime.of(date, NIGHT_FROM, zone).toInstant().toEpochMilli() to ZonedDateTime.of(date.plusDays(1), NIGHT_UNTIL, zone).toInstant().toEpochMilli()

/** S5, S7: the extra signals over the sleeping part of the night, and how many times the logger went quiet. */
fun summariseNight(date: LocalDate, events: List<SensorEvent>, zone: ZoneId = LONDON, shutdownPhotoMs: Long? = null, wakePhotoMs: Long? = null): NightSummary? {
    val (from, until) = nightWindow(date, zone)
    val night = deriveNight(events, from, until, shutdownPhotoMs, wakePhotoMs) ?: return null
    val inNight = events.filter { it.atMs in night.startMs..night.endMs }.sortedBy { it.atMs }
    // Charging share: time between the night's start and end spent charging, from the last charging event before the start.
    val charging = events.filter { it.kind == SensorKind.CHARGING_ON || it.kind == SensorKind.CHARGING_OFF }.sortedBy { it.atMs }
    var state = charging.lastOrNull { it.atMs <= night.startMs }?.kind == SensorKind.CHARGING_ON
    var cursor = night.startMs; var chargedMs = 0L
    for (e in charging.filter { it.atMs > night.startMs && it.atMs <= night.endMs }) {
        if (state) chargedMs += e.atMs - cursor
        cursor = e.atMs; state = e.kind == SensorKind.CHARGING_ON
    }
    if (state) chargedMs += night.endMs - cursor
    val span = (night.endMs - night.startMs).coerceAtLeast(1)
    val lights = inNight.filter { it.kind == SensorKind.LIGHT }
    val dark = if (lights.isEmpty()) 0 else lights.count { it.value == 0 } * 100 / lights.size
    // Gaps: silence between any two recorded events (heartbeats included) longer than SERVICE_GAP, or a service start after silence.
    val all = events.filter { it.atMs in night.startMs..night.endMs }.map { it.atMs }.sorted()
    val marks = listOf(night.startMs) + all + listOf(night.endMs)
    val gaps = marks.zipWithNext().count { (a, b) -> b - a > SERVICE_GAP.toMillis() }
    return NightSummary(date, night, (chargedMs * 100 / span).toInt().coerceIn(0, 100), inNight.count { it.kind == SensorKind.MOTION }, dark, gaps)
}

/** S4: awake time in bed is the time from the first long screen-on (waking) to the wake photo. Never negative. */
fun awakeInBed(wakeMs: Long, wakePhotoMs: Long): Duration = Duration.ofMillis((wakePhotoMs - wakeMs).coerceAtLeast(0))

/**
 * ST4: raw events are kept 30 days, then reduced to nightly summaries. These are the events old enough to delete. The caller writes the
 * summary for every night they belong to first ([nightDateOf]), so nothing is lost before its summary is kept.
 */
fun rawEventsToDelete(events: List<SensorEvent>, now: Instant): List<SensorEvent> {
    val cutoff = now.minus(StoragePolicy.SENSOR_EVENTS_KEEP).toEpochMilli()
    return events.filter { it.atMs < cutoff }
}

/** The evening date a night is counted from: an event before noon belongs to the night that started the evening before. */
fun nightDateOf(atMs: Long, zone: ZoneId = LONDON): LocalDate {
    val z = Instant.ofEpochMilli(atMs).atZone(zone)
    return if (z.toLocalTime().isBefore(NIGHT_UNTIL)) z.toLocalDate().minusDays(1) else z.toLocalDate()
}

/** The light sensor reading as a bucket, so only four values are ever stored. */
fun lightBucket(lux: Float): Int = when { lux < 1f -> 0; lux < 10f -> 1; lux < 100f -> 2; else -> 3 }
