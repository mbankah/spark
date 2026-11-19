package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// ---------- HO3: the winter vacation arc ----------

/** One span of the winter vacation, with the modifier that lays it out and a plain note. */
data class ArcWeek(val label: String, val instance: Instance, val note: String)

/**
 * HO3: the winter vacation (14 Dec to 8 Jan) as a recommended shape, made of lecture-free spans because there are no lectures: land and rest, holiday, ramp up, and with January
 * assessments a final prep. It is a recipe: every span is a normal modifier you can open and change. Spans that have already started are left out.
 */
fun winterArc(januaryExams: Boolean = true, today: LocalDate = LocalDate.of(2026, 11, 18), from: LocalDate = WINTER_VACATION_FROM, to: LocalDate = WINTER_VACATION_TO): List<ArcWeek> {
    fun span(label: String, a: LocalDate, b: LocalDate, capH: String, cycles: String, note: String) =
        ArcWeek(label, Instance(Preset.LECTURE_FREE, a, b, mapOf("work_h_max" to capH, "cycles_per_day" to cycles)), note)
    val christmasEnd = LocalDate.of(from.year + 1, 1, 1)
    val weeks = listOf(
        span("Land and rest", from, from.plusDays(6), "2.0", "1", "One block a day while you land and the sleep settles."),
        span("Holiday", from.plusDays(7), christmasEnd, "2.0", "1", "Christmas and New Year as they are. One block a day so the habit never stops."),
        span("Ramp up", christmasEnd.plusDays(1), to, if (januaryExams) "6.0" else "4.0", if (januaryExams) "3" else "2", if (januaryExams) "Revision cycles and past papers for the January assessments." else "The CS Project and Battl.io get the time."),
    ) + if (januaryExams) listOf(span("Final prep", to.plusDays(1), to.plusDays(2), "2.0", "1", "Light. Early nights and the exam-day routine rehearsed.")) else emptyList()
    return weeks.filter { !it.instance.start.isBefore(today) }
}

// ---------- SL5: the gradual shift of a delayed clock ----------

/**
 * SL5: wake moves by [stepMin] minutes every [everyDays] nights until it reaches the target, never more than 30 minutes in a day, so a late body clock moves
 * gradually and not by a sudden change. Returns the night and the wake of that night. A target later than now moves later by the same steps.
 */
fun gradualShift(current: LocalTime, target: LocalTime, firstNight: LocalDate, stepMin: Int = 15, everyDays: Int = 2): List<Pair<LocalDate, LocalTime>> {
    require(stepMin in 1..30) { "never more than 30 minutes at a time" }
    require(everyDays >= 1)
    val out = ArrayList<Pair<LocalDate, LocalTime>>()
    var wake = current; var night = firstNight
    val dir = if (target.isBefore(current)) -1 else 1
    while (wake != target && out.size < 100) {
        val remaining = Math.abs(java.time.Duration.between(wake, target).toMinutes()).toInt()
        wake = wake.plusMinutes((dir * minOf(stepMin, remaining)).toLong())
        out += night to wake
        night = night.plusDays(everyDays.toLong())
    }
    return out
}

// ---------- HO5: leaving and arriving ----------

private val ALL = setOf(Tier.FULL, Tier.SHORT, Tier.FLOOR)

/** HO5: the night before leaving for the family home. */
val LEAVE_HOME = Routine("leave", "Leaving for home", listOf(
    Step("leave.charger", "Pack the charger and the laptop.", 3, ProofTier.TAP, tiers = ALL),
    Step("leave.bible", "Pack the Bible, the kit and your medication.", 3, ProofTier.TAP, tiers = ALL),
    Step("leave.cards", "Pack the student card and the travel card.", 1, ProofTier.TAP, tiers = ALL),
    Step("leave.train", "Check the train time and the leave-by.", 2, ProofTier.TAP, tiers = ALL),
    Step("leave.photo", "Photograph the bag by the door.", 1, ProofTier.PHOTO, tiers = ALL),
), neverDropped = true)

/** HO2, HO5: arriving at the family home. The reference photo of the study spot and of the charger spot is taken once, so the morning proof works there. */
val ARRIVE_HOME = Routine("arrive", "Arriving at the family home", listOf(
    Step("arrive.spot", "Choose the study spot. It is not the bed.", 2, ProofTier.TAP, tiers = ALL),
    Step("arrive.photo", "Photograph the study spot and the charger spot.", 1, ProofTier.PHOTO, tiers = ALL),
    Step("arrive.wifi", "Join the Wi-Fi.", 2, ProofTier.TAP, tiers = ALL),
    Step("arrive.times", "Ask when meals are and put them in.", 2, ProofTier.TAP, tiers = setOf(Tier.FULL, Tier.SHORT)),
), neverDropped = true)

// ---------- HS1, HS2: the home page has modes by the moment ----------

enum class HomeMode(val showsSecondary: Boolean) { MORNING(true), BLOCK(false), BETWEEN(false), FREE(true), WIND_DOWN(false), NIGHT(false) }

/** HS1: the mode of the home page from the calendar and the clock. A running work block is BLOCK, the hour before the cutoff is WIND_DOWN, and from the cutoff to the wake is NIGHT. */
fun homeModeOf(pattern: List<PatternSlot>, now: ZonedDateTime, lateness: Lateness = emptyMap()): HomeMode {
    nightWindow(pattern, now, lateness = lateness)?.let { return HomeMode.NIGHT }
    val cur = nowAndNext(pattern, now).now?.slot
    if (cur?.type == "sleep") return HomeMode.WIND_DOWN          // the Shutdown block before the cutoff is the wind-down
    driftedWake(pattern, now, lateness)?.let { wake -> val cutoff = nightCutoff(wake); if (!now.isBefore(cutoff.minusMinutes(60)) && now.isBefore(cutoff)) return HomeMode.WIND_DOWN }
    return when {
        cur == null -> HomeMode.FREE
        cur.type in CUTTABLE_WORK || cur.type == "planning" -> HomeMode.BLOCK
        cur.type == "routine" && now.hour < 12 -> HomeMode.MORNING
        cur.type == "travel" || cur.type == "chore" -> HomeMode.BETWEEN
        else -> HomeMode.FREE
    }
}

// ---------- CO5 to CO11: the coach's small pieces ----------

data class MoodTap(val at: Instant, val value: Int)

/** CO6: a run of low days: every one of the last [days] calendar days has an average mood of 2 or less. */
fun lowMoodRun(taps: List<MoodTap>, today: LocalDate, zone: ZoneId = LONDON, days: Int = 3): Boolean {
    val byDay = taps.groupBy { it.at.atZone(zone).toLocalDate() }
    return (0 until days).all { n -> byDay[today.minusDays(n.toLong())]?.let { d -> d.map { it.value }.average() <= 2.0 } == true }
}

/** CO6: a run of short nights: each of the last [nights] nights under 4 hours. */
fun shortNightRun(sleepHours: List<Double>, nights: Int = 3): Boolean = sleepHours.size >= nights && sleepHours.takeLast(nights).all { it < 4.0 }

data class Person(val name: String, val how: String)

/** CO6: the people who can help. Information only. Spark never diagnoses and never decides for you. */
val PEOPLE: List<Person> = listOf(
    Person("Your GP", "Book through your surgery."),
    Person("University of Birmingham Mental Health and Wellbeing Service", "Through the university's student support pages."),
    Person("Student Disability Service", "disability@contacts.bham.ac.uk, 0121 414 5130."),
    Person("NHS 111", "Call 111, any time."),
    Person("Samaritans", "Call 116 123, free, any time."),
    Person("Shout", "Text 85258."),
)

/** CO7: one of your own rewards, at random, so it stays fresh and cannot be gamed. The same seed gives the same pick. */
fun rewardPick(menu: List<String>, seed: Long): String? = menu.filter { it.isNotBlank() }.let { if (it.isEmpty()) null else it[kotlin.random.Random(seed).nextInt(it.size)] }

/** CO9: three true facts at wind-down. Never an opinion, never a number you did not earn. With nothing to show it gives the next step, not a verdict. */
fun todaysWins(stepsDone: Int, stepsPlanned: Int, blocksProven: Int, bibleDone: Boolean, upOnTime: Boolean): List<String> {
    val out = ArrayList<String>()
    if (upOnTime) out += "Up on time."
    if (bibleDone) out += "Bible done."
    if (blocksProven > 0) out += "$blocksProven study block${if (blocksProven == 1) "" else "s"} proven."
    if (stepsDone > 0 && out.size < 3) out += "$stepsDone of $stepsPlanned steps done."
    return out.take(3).ifEmpty { listOf("Nothing proven today. Next: water.") }
}

/** VG2: words Spark does not use. A message that contains one is a bug. */
val BANNED_WORDS: List<String> = listOf("failed", "lazy", "bad", "should", "must", "streak", "broken", "behind schedule", "you always", "you never", "disappointing")

fun voiceProblems(text: String): List<String> = BANNED_WORDS.filter { Regex("\\b" + Regex.escape(it) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) }
