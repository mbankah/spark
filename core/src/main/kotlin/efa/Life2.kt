package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// ---------- EM1, EM4, EM5: the emergency door and the lost phone ----------

/** EM1: what the Emergency door holds. It is never gated, never asks a question, and is logged quietly. */
enum class EmergencyAction(val label: String, val how: String) {
    CALL_EMERGENCY("Call 999", "Opens the dialler with 999 ready. You press call."),
    CALL_ANYONE("Phone", "Opens the phone app."),
    MAPS("Maps and taxi", "Opens your maps app."),
    BANK("Bank", "Opens your banking app."),
    SHARE_LOCATION("Share where I am", "Opens the share sheet with a link to your location in your maps app."),
    HEALTH("NHS 111", "Opens the dialler with 111 ready."),
}

/** EM5: what to do when the phone is lost, dead or broken. One screen, no network needed to read it. */
val LOST_PHONE_STEPS: List<String> = listOf(
    "Use another phone or the PC to call your own number, then your network to block the SIM if it is stolen.",
    "Find My Device (Google) can ring, lock or locate it from any browser.",
    "Your day is on the PC and web view of Spark if you set it up (G9). Your alarm will not ring; set one on another device.",
    "On a new phone, install Spark, open Right now, open Your data and help, Restore from a backup file, and choose your latest spark-backup file from Downloads/Spark.",
    "Then give the permissions on the list Spark shows.",
)

// ---------- EM2: "I'm out until HH:MM" ----------

/**
 * EM2: out is honoured only if the phone is not at the home point. At home the claim fails and the normal rules stand. [atHome] null means the place is not known,
 * which is treated as not away, so a missing fix never opens anything.
 */
fun outActive(outUntil: ZonedDateTime?, now: ZonedDateTime, atHome: Boolean?): Boolean = outUntil != null && now.isBefore(outUntil) && atHome == false

// ---------- CO2, CO3: the four prompt levels and the haptic vocabulary ----------

enum class PromptLevel { HAPTIC, SPOKEN, NOTIFICATION, GATE }

/** CO2: a prompt escalates once per level and then stops at the gate. [ignored] is how many times it has gone unanswered. */
fun promptLevel(ignored: Int): PromptLevel = PromptLevel.entries[ignored.coerceIn(0, PromptLevel.entries.lastIndex)]

/** CO3: patterns you can act on without looking. Each is millisecond waits and buzzes: wait, buzz, wait, buzz ... */
enum class Haptic(val meaning: String, val pattern: LongArray) {
    START("Start", longArrayOf(0, 80, 90, 80)),
    STOP("Stop", longArrayOf(0, 500)),
    ALARM("Up", longArrayOf(0, 400, 150, 90, 150, 400)),
    LEAVE("Leave now", longArrayOf(0, 70, 80, 70, 80, 70)),
    REWARD("Reward", longArrayOf(0, 40)),
    GOODNIGHT("Goodnight, docked", longArrayOf(0, 120, 300, 120)),
}

// ---------- SL7: the nap offer ----------

/** SL7: after a night under 4 hours, one 20 minute nap window before 15:00. Longer or later naps work against the night, so they are not offered. */
fun napOffer(lastNightHours: Double?, now: ZonedDateTime): Boolean = lastNightHours != null && lastNightHours < 4.0 && now.hour < 15

// ---------- SL4: tomorrow's fun ----------

/** SL4: a night-time urge goes into a real slot tomorrow at 17:00, so the pull gets an answer and not a no. Returns the event to preview. */
fun tomorrowsFun(idea: String, today: LocalDate): Instance =
    Instance(Preset.ADD_EVENT, today.plusDays(1), today.plusDays(1), mapOf("title" to idea.trim().ifEmpty { "Something fun" }.take(60), "type" to "errand", "start" to "17:00", "end" to "18:00"))

// ---------- SL6: your own reminders, never advice ----------

/** SL6: a reminder you set (a medication, anything). Spark logs taken or not taken for you to show a prescriber and gives no advice on doses or times. */
data class Reminder(val name: String, val time: LocalTime)

fun encodeReminders(l: List<Reminder>): String = l.joinToString("\n") { "${it.time}|${it.name.replace("|", " ").replace("\n", " ")}" }

fun decodeReminders(s: String?): List<Reminder> = s.orEmpty().lines().mapNotNull { line ->
    val p = line.split("|", limit = 2); if (p.size != 2) return@mapNotNull null
    runCatching { Reminder(p[1], LocalTime.parse(p[0])) }.getOrNull()
}.sortedBy { it.time }

/** The next reminder after [now], today or tomorrow, with the instant it is due. */
fun nextReminder(l: List<Reminder>, now: ZonedDateTime): Pair<Reminder, ZonedDateTime>? =
    l.flatMap { r -> listOf(now.toLocalDate(), now.toLocalDate().plusDays(1)).map { d -> r to ZonedDateTime.of(d, r.time, now.zone) } }.filter { it.second.isAfter(now) }.minByOrNull { it.second }

// ---------- HO4: the university's real routes ----------

data class SupportRoute(val name: String, val when_: String, val what: String)

/** HO4: from the University of Birmingham's own pages, read on 18 Nov 2026. Information only; check the university's pages for the current rules. */
val SUPPORT_ROUTES: List<SupportRoute> = listOf(
    SupportRoute("Coursework extension", "Before the deadline", "An online form for exceptional, unforeseeable circumstances beyond your control. Evidence is not usually needed. Not for exams, class tests, timed assessments or presentations."),
    SupportRoute("Extenuating circumstances", "After the impact", "A claim to your School by an advertised deadline, usually with a medical certificate or a letter from the Mental Health and Wellbeing Service. A panel looks at it."),
    SupportRoute("Reasonable Adjustment Plan", "Any time in your course", "Through the Student Disability Service for a condition with a significant, adverse and long-term effect. ADHD is named. Can include extended deadlines, rest breaks, extra time and a specialist mentor. disability@contacts.bham.ac.uk, 0121 414 5130. The university does not diagnose ADHD; a GP can refer."),
    SupportRoute("Your GP and NHS 111", "When it is about how you are", "Book through your surgery, or call 111 at any time."),
)

// ---------- CX1: one context record ----------

/** CX1: where you are and what state you are in, in one place, so no part of the app keeps its own idea of it. */
data class LifeContext(val place: PlaceKind, val dayType: DayType, val mode: HomeMode, val phase: Phase, val lite: Boolean, val out: Boolean)

fun lifeContext(
    date: LocalDate, now: ZonedDateTime, place: PlaceKind, pattern: List<PatternSlot>, daysSinceStart: Int, lite: Boolean, outUntil: ZonedDateTime?, atHome: Boolean?,
    zone: ZoneId = LONDON,
): LifeContext = LifeContext(place, proposeDayType(date, place, null, pattern, zone = zone).type, homeModeOf(pattern, now), phaseOf(daysSinceStart), lite, outActive(outUntil, now, atHome))

// ---------- CE3, CE4: modifiers are counted too ----------

/** CE1: the class of a modifier. Reporting what happened is Reality; anything that removes an obligation (it needs the wall) is Loosen; the rest adds. */
/** AD2: an event whose displaced work comes back as work is a move (same work, new times), counted but free within the week's moves; it is a loosening only if something is truly given up. */
fun changeClassOfInstance(inst: Instance, loosens: Boolean, returnedTasks: Boolean): ChangeClass =
    if (!loosens && inst.preset == Preset.ADD_EVENT && inst.bool("return_work") && returnedTasks) ChangeClass.MOVE else changeClassOfPreset(inst.preset, loosens)

fun changeClassOfPreset(preset: Preset, loosens: Boolean): ChangeClass = when {
    loosens -> ChangeClass.LOOSEN
    preset == Preset.LATE_START || preset == Preset.RUN_OVER || preset == Preset.NIGHT || preset == Preset.CANCEL -> ChangeClass.REALITY
    else -> ChangeClass.ADD
}

/** DP2: a day off or a loosening modifier takes obligation minutes from the weekly pool; a night override takes gate minutes. */
fun obligationDoor(door: Door, at: Instant, minutes: Int, verified: Boolean = false) = DoorUse(door, at, minutes, 0, verified)
fun gateDoor(door: Door, at: Instant, minutes: Int, verified: Boolean = false) = DoorUse(door, at, 0, minutes, verified)

// ---------- CO11: tone, CO10: identity ----------

/** CO11: the tone changes the style of a line, never what it says. Calm is the default. */
enum class Tone(val label: String) {
    CALM("Calm"), PLAIN("Plain"), LIGHT("Light");
    companion object { fun of(id: String?): Tone = entries.firstOrNull { it.name == id } ?: CALM }
}

fun toneLine(text: String, tone: Tone): String = when (tone) {
    Tone.CALM -> text
    Tone.PLAIN -> text.substringBefore(". ").let { if (it.endsWith(".")) it else "$it." }.let { if (text.contains(". ")) it else text }
    Tone.LIGHT -> if (text.endsWith(".")) "$text Easy does it." else text
}

/** CO10: one of the three sentences you wrote about who you are trying to be, tied to a fact, used sparingly (at most one in three wins lines). */
fun identityLine(sentences: List<String>, blocksProven: Int, seed: Long): String? {
    val s = sentences.filter { it.isNotBlank() }; if (s.isEmpty() || blocksProven <= 0) return null
    return "${s[kotlin.random.Random(seed).nextInt(s.size)].trim().trimEnd('.')}. That is $blocksProven kept today."
}

// ---------- CE3, CE4: what a modifier costs the weekly pool ----------

/** DP2: the obligation minutes a loosening modifier takes: a day off is 6 hours; a lighter or away day is 2 hours (Default, from the lecture-free and recovery caps). */
/** DP2: what one day off takes from the pool. Two days fit a fresh pool (480 of 600), a third needs the pool to have room or break glass. */
const val PERIOD_OFF_DAY_MIN = 240

fun obligationMinutesOf(inst: Instance, c: Compiled): Int = when {
    inst.preset == Preset.PERIOD_OFF -> c.dayOffDates.size * PERIOD_OFF_DAY_MIN
    inst.preset in setOf(Preset.RECOVERY, Preset.AWAY, Preset.LECTURE_FREE) -> inst.days * 120
    c.loosening.isNotEmpty() -> 120
    else -> 0
}
