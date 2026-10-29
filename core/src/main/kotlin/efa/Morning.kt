package efa

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The morning (layer 3, section 6): the alarm time, the photo that stops it, the ladder when the photo fails, and when social stays blocked.
 * Everything here is arithmetic on numbers and times, so it is tested on the JVM. The camera, the alarm manager and the screens are in the app.
 */

// ---- Matching a live photo to a reference (M2, M4) ----

const val SIGNATURE_SIDE = 16

/** A photo reduced to 16 x 16 grey values (M2). It is small enough to keep with the reference and cannot be turned back into the photo. */
class ImageSignature(val grey: ByteArray) {
    init { require(grey.size == SIGNATURE_SIDE * SIGNATURE_SIDE) { "a signature is ${SIGNATURE_SIDE * SIGNATURE_SIDE} values" } }
    override fun equals(other: Any?) = other is ImageSignature && grey.contentEquals(other.grey)
    override fun hashCode() = grey.contentHashCode()
}

/** Averages a grey picture of any size down to a signature. [grey] holds 0..255 values, row by row. */
fun signatureOf(grey: IntArray, width: Int, height: Int): ImageSignature {
    require(width > 0 && height > 0 && grey.size >= width * height) { "bad picture size" }
    val out = ByteArray(SIGNATURE_SIDE * SIGNATURE_SIDE)
    for (sy in 0 until SIGNATURE_SIDE) for (sx in 0 until SIGNATURE_SIDE) {
        val x0 = sx * width / SIGNATURE_SIDE; val x1 = max(x0 + 1, (sx + 1) * width / SIGNATURE_SIDE)
        val y0 = sy * height / SIGNATURE_SIDE; val y1 = max(y0 + 1, (sy + 1) * height / SIGNATURE_SIDE)
        var sum = 0L; var n = 0
        for (y in y0 until minOf(y1, height)) for (x in x0 until minOf(x1, width)) { sum += grey[y * width + x].coerceIn(0, 255); n++ }
        out[sy * SIGNATURE_SIDE + sx] = (if (n == 0) 0 else (sum / n).toInt()).toByte()
    }
    return ImageSignature(out)
}

/** Below this much spread in grey values a picture shows nothing: a covered lens, a black room, a blank wall. It never matches anything. */
const val MIN_CONTRAST = 6.0

/** M2: a live photo passes at this score or more (0 to 100). A default; the score of every attempt is kept so it can be tuned (Q-G53). */
const val MATCH_PASS_SCORE = 65

data class MatchResult(val score: Int, val passed: Boolean, val tooDark: Boolean = false)

private fun contrast(g: ByteArray): Double {
    val v = g.map { it.toInt() and 0xFF }
    val mean = v.average()
    return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
}

/**
 * Zero-mean normalised correlation of two signatures, taken over their overlap at each shift of up to one cell either way (about 6% of
 * the frame), best shift wins. It ignores overall brightness and contrast, so the room being lighter or darker does not change the
 * answer, and a small movement of the phone does not either. A different scene scores near zero.
 */
fun matchScore(reference: ImageSignature, live: ImageSignature): MatchResult {
    if (contrast(reference.grey) < MIN_CONTRAST || contrast(live.grey) < MIN_CONTRAST) return MatchResult(0, false, tooDark = contrast(live.grey) < MIN_CONTRAST)
    var best = -1.0
    for (dy in -1..1) for (dx in -1..1) {
        val xs = (0 until SIGNATURE_SIDE).filter { it + dx in 0 until SIGNATURE_SIDE }
        val ys = (0 until SIGNATURE_SIDE).filter { it + dy in 0 until SIGNATURE_SIDE }
        val a = ArrayList<Double>(); val b = ArrayList<Double>()
        for (y in ys) for (x in xs) {
            a += (reference.grey[y * SIGNATURE_SIDE + x].toInt() and 0xFF).toDouble()
            b += (live.grey[(y + dy) * SIGNATURE_SIDE + (x + dx)].toInt() and 0xFF).toDouble()
        }
        val ma = a.average(); val mb = b.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { num += (a[i] - ma) * (b[i] - mb); da += (a[i] - ma) * (a[i] - ma); db += (b[i] - mb) * (b[i] - mb) }
        if (da <= 0.0 || db <= 0.0) continue
        best = max(best, num / sqrt(da * db))
    }
    val score = (max(0.0, best) * 100).roundToInt().coerceIn(0, 100)
    return MatchResult(score, score >= MATCH_PASS_SCORE)
}

/** M4: the live photo is matched against the most recent valid reference and, when one is set, the standing reference too. The better score counts. */
fun matchAny(references: List<ImageSignature>, live: ImageSignature): MatchResult =
    references.map { matchScore(it, live) }.maxByOrNull { it.score } ?: MatchResult(0, false)

// ---- When the alarm rings (Z1, Z2, G2, Y7) ----

val SLEEP_TARGET: Duration = Duration.ofHours(8)                // Z1: onset plus 8 hours
val ALARM_FLOOR: Duration = Duration.ofHours(3)                 // Z2: nothing rings sooner than about 3 hours after onset, except a hard alarm
val BACKSTOP_AT: LocalTime = LocalTime.NOON                     // G2: one alarm a day at the latest, 12:00
val HARD_LEAD: Duration = Duration.ofMinutes(30)                // a hard alarm rings this long before the commitment (Q-G55)

enum class AlarmReason { BACKSTOP, ONSET, HARD }

data class AlarmPlan(val at: ZonedDateTime, val reason: AlarmReason)

/** D4: the first hard-alarm commitment of [date] that starts before the backstop, and when its alarm rings. */
fun hardAlarmAt(slots: List<PatternSlot>, date: LocalDate, zone: ZoneId): ZonedDateTime? =
    slotsFor(slots, date, zone).filter { it.slot.hardAlarm && it.start.toLocalDate() == date && it.start.toLocalTime().isBefore(BACKSTOP_AT) }
        .minByOrNull { it.start }?.start?.minus(HARD_LEAD)

/**
 * Z1, Z2, G2, Y7: with no onset yet the alarm waits at the backstop (or a hard alarm). Once sleep has started the alarm is onset plus 8 hours,
 * never sooner than 3 hours after onset, never later than the backstop (the backstop counts as hard, so the floor does not hold it back),
 * and a hard alarm earlier than that rings first. [onset] is where the live night says sleep started, or null.
 */
fun planAlarm(now: ZonedDateTime, onset: ZonedDateTime?, slots: List<PatternSlot>, isDayOff: Boolean = false): AlarmPlan? {
    if (isDayOff) return null                                   // G2: always set unless it is a planned day off
    val zone = now.zone
    val wakeDate = if (onset != null) onset.plus(SLEEP_TARGET).toLocalDate() else if (now.toLocalTime().isBefore(BACKSTOP_AT)) now.toLocalDate() else now.toLocalDate().plusDays(1)
    val backstop = ZonedDateTime.of(wakeDate, BACKSTOP_AT, zone)
    val hard = hardAlarmAt(slots, wakeDate, zone)
    var plan = AlarmPlan(backstop, AlarmReason.BACKSTOP)
    if (onset != null) {
        val ring = maxOf(onset.plus(SLEEP_TARGET), onset.plus(ALARM_FLOOR))
        if (ring.isBefore(backstop)) plan = AlarmPlan(ring, AlarmReason.ONSET)
    }
    if (hard != null && hard.isBefore(plan.at)) plan = AlarmPlan(hard, AlarmReason.HARD)
    return plan
}

// ---- Stopping the alarm (M2, M3, M10) ----

const val PHOTO_ATTEMPTS = 2                                    // M10: a second attempt, then steps
const val STEPS_REQUIRED = 40                                   // the fallback walk (Q-G54)
val SHORT_WAIT: Duration = Duration.ofMinutes(3)                // M10: then a short wait

enum class StopMethod { PHOTO, STEPS, WAIT }

/** What the alarm screen offers right now. The photo comes first and is the strong check; steps are the weaker one and only after the photo has failed (M3). */
sealed interface Offer {
    data class Photo(val attempt: Int) : Offer
    data class Steps(val have: Int, val need: Int) : Offer
    data class Wait(val untilMs: Long) : Offer
    data class CanStop(val method: StopMethod) : Offer
}

/**
 * M10: attempt the photo up to twice; after that, or when the camera does not work at all, walk 40 steps; if steps cannot be counted on this phone,
 * wait 3 minutes. Calls and messages are never locked by any of this. [stepsAvailable] is false without the sensor or its permission.
 * Steps are counted from the start of the ring, in parallel, so walking while the photo is failing is not wasted (M2).
 */
fun nextOffer(failedPhotos: Int, cameraBroken: Boolean, steps: Int, stepsAvailable: Boolean, waitStartedMs: Long?, nowMs: Long): Offer {
    if (!cameraBroken && failedPhotos < PHOTO_ATTEMPTS) return Offer.Photo(failedPhotos + 1)
    if (stepsAvailable) return if (steps >= STEPS_REQUIRED) Offer.CanStop(StopMethod.STEPS) else Offer.Steps(steps, STEPS_REQUIRED)
    val start = waitStartedMs ?: nowMs
    val until = start + SHORT_WAIT.toMillis()
    return if (nowMs >= until) Offer.CanStop(StopMethod.WAIT) else Offer.Wait(until)
}

/** The strength of what stopped the alarm, for the record (M2: steps are accepted as the weaker check). */
fun StopMethod.isWeaker() = this != StopMethod.PHOTO

// ---- Social stays blocked until ready (M5, M6, M8, M9) ----

/** One morning's state, as the app stores it. Times are epoch milliseconds. */
data class MorningState(
    val date: LocalDate,
    val ringStartedMs: Long? = null, val stoppedMs: Long? = null, val stopMethod: StopMethod? = null,
    val readyMs: Long? = null, val readyMethod: StopMethod? = null,
)

/** A social block can never run all day if the ready photo cannot be taken (LA1): it ends at this time at the latest (Q-G56). */
val READY_RELEASE_BY: LocalTime = LocalTime.of(14, 0)

enum class MorningPhase { NONE, RINGING, WAITING_FOR_READY, DONE }

/** The block never lasts less than this after the alarm stops if the stop came late, so a late stop still has time to get ready (Q-G56). */
val READY_MIN_WINDOW: Duration = Duration.ofHours(2)

/** When the social block ends if the ready photo never comes (LA1): 14:00 on the day the alarm stopped, or 2 hours after it stopped if that is later. */
fun readyReleaseAt(stoppedMs: Long, zone: ZoneId): ZonedDateTime {
    val stopped = java.time.Instant.ofEpochMilli(stoppedMs).atZone(zone)
    return maxOf(ZonedDateTime.of(stopped.toLocalDate(), READY_RELEASE_BY, zone), stopped.plus(READY_MIN_WINDOW))
}

/** M5, M6, M8: the phone is limited from the moment the alarm fires, social stays blocked after it stops, and it ends with the ready photo, an event and not a timer. */
fun morningPhase(m: MorningState?, now: ZonedDateTime): MorningPhase {
    if (m == null || m.ringStartedMs == null) return MorningPhase.NONE
    val stopped = m.stoppedMs ?: return MorningPhase.RINGING
    if (m.readyMs != null) return MorningPhase.DONE
    return if (now.isBefore(readyReleaseAt(stopped, now.zone))) MorningPhase.WAITING_FOR_READY else MorningPhase.DONE
}

/** M2, S4: the time between waking (the first long screen-on) and the photo that stops the alarm is the awake time in bed. */
fun awakeBeforeAlarmStopped(wakeMs: Long, stoppedMs: Long): Duration = awakeInBed(wakeMs, stoppedMs)
