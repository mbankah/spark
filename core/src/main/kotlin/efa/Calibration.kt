package efa

/**
 * Calibration (layer 7: B5, R10, E9). The estimate for a task starts from its heaviness and is corrected quietly from how long your tasks really took.
 * The correction is kept per band of heaviness, so inflating the slider teaches the band that its estimates run high and they come back down: inflating
 * heaviness gains nothing (R10). Until a band has [CAL_MIN_SAMPLES] finished tasks the estimate is the plain first guess and the screen says how many it
 * is based on (E9, G6).
 */

const val CAL_MIN_SAMPLES = 3
const val CAL_PRIOR_WEIGHT = 3.0
const val CAL_MIN_FACTOR = 0.25
const val CAL_MAX_FACTOR = 3.0

enum class Band(val range: IntRange, val label: String) {
    LIGHT(1..3, "light (1 to 3)"), MEDIUM(4..6, "medium (4 to 6)"), HEAVY(7..10, "heavy (7 to 10)");
    companion object { fun of(heaviness: Int): Band { require(heaviness in 1..10); return entries.first { heaviness in it.range } } }
}

/** One finished task: what Spark estimated and how long it really took. */
data class CalSample(val heaviness: Int, val estimatedHours: Double, val actualHours: Double)

data class Correction(val band: Band, val factor: Double, val samples: Int, val applied: Boolean) {
    fun text(): String = when {
        applied -> "corrected x${fmt2(factor)} from $samples finished ${band.label} tasks"
        samples == 0 -> "a first guess: no finished ${band.label} tasks yet, $CAL_MIN_SAMPLES needed"
        else -> "a first guess: $samples of $CAL_MIN_SAMPLES finished ${band.label} tasks needed"
    }
}

private fun fmt2(d: Double) = String.format(java.util.Locale.UK, "%.2f", d)

class Calibration(samples: List<CalSample> = emptyList()) {
    private val byBand = samples.filter { it.estimatedHours > 0 && it.actualHours > 0 }.groupBy { Band.of(it.heaviness) }

    /** B5: real time over estimated time for the band, pulled toward 1 by a prior of [CAL_PRIOR_WEIGHT] tasks so a few outliers cannot swing it. */
    fun correction(heaviness: Int): Correction {
        val band = Band.of(heaviness)
        val s = byBand[band].orEmpty()
        if (s.size < CAL_MIN_SAMPLES) return Correction(band, 1.0, s.size, false)
        val ratio = s.sumOf { it.actualHours } / s.sumOf { it.estimatedHours }
        val factor = ((s.size * ratio + CAL_PRIOR_WEIGHT) / (s.size + CAL_PRIOR_WEIGHT)).coerceIn(CAL_MIN_FACTOR, CAL_MAX_FACTOR)
        return Correction(band, factor, s.size, true)
    }

    /** The estimate for a new task of [heaviness]: the first guess times the band's correction. */
    fun hours(heaviness: Int): Double = quickAddHours(heaviness) * correction(heaviness).factor

    /** E9: said wherever an estimate is shown. */
    fun note(heaviness: Int): String = "Heaviness $heaviness is about ${fmt1(hours(heaviness))}h of work, ${correction(heaviness).text()}."
}

/** What the person says about a finished task. Only the first and the second can be said freely; the third loosens the Buffer and needs the wall. */
sealed interface TookReport {
    data object AsPlanned : TookReport
    data class MoreMinutes(val minutes: Int) : TookReport
    data class LessFraction(val fraction: Double) : TookReport
}

val CAL_MORE_CHOICES = listOf(30, 60, 120)
val CAL_LESS_CHOICES = listOf(0.75, 0.5)

/** The sample a report makes, or null for a report that is not allowed. */
fun sampleFrom(heaviness: Int, estimatedHours: Double, report: TookReport): CalSample? = when (report) {
    TookReport.AsPlanned -> CalSample(heaviness, estimatedHours, estimatedHours)
    is TookReport.MoreMinutes -> if (report.minutes in CAL_MORE_CHOICES) CalSample(heaviness, estimatedHours, estimatedHours + report.minutes / 60.0) else null
    is TookReport.LessFraction -> if (report.fraction in CAL_LESS_CHOICES) CalSample(heaviness, estimatedHours, estimatedHours * report.fraction) else null
}
