package efa

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * MC1: a meal is asked about. Ten minutes after a meal block ends Spark asks "Did you eat?" with two answers, Ate and Not yet. Not yet asks again in 30 minutes, up to
 * three times; Ate closes it. A question that nobody answered within three hours is dropped, not carried to the next meal. It costs nothing, changes no plan and is never a
 * wall, because it is about your health and not about your work: it is awareness, and the record it keeps is yours.
 */
const val MEAL_CHECK_DELAY_MIN = 10
const val MEAL_CHECK_REASK_MIN = 30
const val MEAL_CHECK_MAX_ASKS = 3
const val MEAL_CHECK_STALE_MIN = 180

enum class MealAnswer { ATE, NOT_YET }

/** [asks] is how many times it has been asked so far, including the one this answer was given to. */
data class MealEntry(val answer: MealAnswer, val atMs: Long, val asks: Int)

data class MealLog(val entries: Map<String, MealEntry> = emptyMap()) {
    fun answered(key: String, answer: MealAnswer, atMs: Long): MealLog =
        MealLog(entries + (key to MealEntry(answer, atMs, (entries[key]?.asks ?: 0) + 1)))

    /** Only the last week is kept, so the text stays small. */
    fun encode(nowMs: Long): String = entries.filter { nowMs - it.value.atMs < 7L * 24 * 3_600_000 }.entries.joinToString("\n") { (k, e) -> "$k;${e.answer.name};${e.atMs};${e.asks}" }

    companion object {
        fun decode(text: String?): MealLog = MealLog((text ?: "").lines().mapNotNull { line ->
            val p = line.split(";")
            if (p.size != 4) null else runCatching { p[0] to MealEntry(MealAnswer.valueOf(p[1]), p[2].toLong(), p[3].toInt()) }.getOrNull()
        }.toMap())
    }
}

/** A meal block asked about, and when. [key] is the date and the start minute, so a meal that moves is a new question. */
data class MealCheck(val key: String, val title: String, val at: ZonedDateTime)

fun mealKey(date: LocalDate, startMin: Int) = "$date|$startMin"

/** The questions still wanted around [now], earliest first: from yesterday to two days ahead. */
fun mealChecks(slots: List<PatternSlot>, log: MealLog, now: ZonedDateTime, zone: ZoneId = now.zone): List<MealCheck> =
    (-1L..2L).flatMap { slotsFor(slots, now.toLocalDate().plusDays(it), zone) }
        .filter { it.slot.type == "meal" && !it.slot.title.startsWith("Sleepy milk") }
        .mapNotNull { ds ->
            val key = mealKey(ds.plannedDay, ds.slot.startMin)
            val e = log.entries[key]
            val at = when {
                e == null -> ds.end.plusMinutes(MEAL_CHECK_DELAY_MIN.toLong())
                e.answer == MealAnswer.ATE -> return@mapNotNull null
                e.asks >= MEAL_CHECK_MAX_ASKS -> return@mapNotNull null
                else -> java.time.Instant.ofEpochMilli(e.atMs).atZone(zone).plusMinutes(MEAL_CHECK_REASK_MIN.toLong())
            }
            if (now.isAfter(at.plusMinutes(MEAL_CHECK_STALE_MIN.toLong()))) null else MealCheck(key, ds.slot.title.substringBefore(" ("), at)
        }.sortedBy { it.at }

/** The question to put to the person right now: due, and not stale. */
fun dueMealCheck(slots: List<PatternSlot>, log: MealLog, now: ZonedDateTime): MealCheck? = mealChecks(slots, log, now).firstOrNull { !it.at.isAfter(now) }

/** The next question to set an alarm for. */
fun nextMealCheck(slots: List<PatternSlot>, log: MealLog, now: ZonedDateTime): MealCheck? = mealChecks(slots, log, now).firstOrNull { it.at.isAfter(now) }
