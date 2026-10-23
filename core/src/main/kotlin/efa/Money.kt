package efa

import java.math.BigDecimal
import java.math.RoundingMode

/** All money in pence so there is no float drift. */
const val WEEKLY_TOTAL_PENCE = 8000
const val FOOD_PENCE = 4000          // R6: guaranteed, never tied to work
const val OTHER_PENCE = 4000
const val FREE_CAP_PENCE = 2500      // R7
const val MIN_SAVED_PENCE = 1500     // R7: OTHER - cap

data class WeeklyMoney(val freeToSpendPence: Int, val savingsPence: Int, val foodPence: Int = FOOD_PENCE)

/** Q-G3 / Y4: a unit of work counts heaviness * planned hours. Days off are left out by the caller (C5). */
data class WeightedWork(val heaviness: Int, val hours: Double) { val weight: Double get() = heaviness * hours }

/** R8: free to spend = 25 * (weighted verified / weighted planned), linear, clamped 0..1. Planned 0 pays nothing. */
fun weeklyMoney(verified: List<WeightedWork>, planned: List<WeightedWork>): WeeklyMoney {
    val p = planned.sumOf { it.weight }
    val v = verified.sumOf { it.weight }
    val ratio = if (p <= 0.0) 0.0 else (v / p).coerceIn(0.0, 1.0)
    return fromRatio(ratio)
}

fun fromRatio(ratio: Double): WeeklyMoney {
    val free = BigDecimal(FREE_CAP_PENCE).multiply(BigDecimal(ratio)).setScale(0, RoundingMode.HALF_UP).toInt()
    return WeeklyMoney(free, OTHER_PENCE - free)
}

/** G6 / R9: the first 14 days record only; money counts after. */
const val RECORD_ONLY_DAYS = 14L
fun moneyIsLive(daysSinceFirstUse: Long) = daysSinceFirstUse >= RECORD_ONLY_DAYS

fun pounds(pence: Int): String = "£" + BigDecimal(pence).movePointLeft(2).setScale(2).toPlainString()
