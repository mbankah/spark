package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

/**
 * Voice (section 14: V1 to V4, G7, E10). Speech is turned into text by the phone; this reads the text. Voice adds a deadline fast ("assignment due Friday, heavy"),
 * opens screens ("show the Buffer") and can START a loosening request ("I need a day off") but cannot finish one: there is no result here that completes a
 * loosening, so the typed wall always stands in the way (V4). What was heard is shown on a card before anything is saved (V2), because speech recognition is
 * never perfect.
 */

/** Where voice can take you. [loosening] ones only open the screen where the typed wall is (V4). */
enum class VoiceTarget(val loosening: Boolean) {
    BUFFER(false), MONEY(false), SHOPPING(false), SETUP(false), WIDGETS(false), LIBRARY(false), IMPORT(false), HOME(false), QUICK_ADD(false),
    DAY_OFF(true), BLOCKLIST(true),
}

sealed interface VoiceIntent {
    /** V1: a deadline heard, ready to be shown on the card. [heaviness] and [time] are null when they were not said, so quick-add's own defaults apply. */
    data class AddTask(val heard: String, val title: String, val day: LocalDate?, val time: LocalTime?, val heaviness: Int?) : VoiceIntent
    /** V3: open a screen. A loosening only opens its request screen (V4). */
    data class Go(val heard: String, val target: VoiceTarget) : VoiceIntent
    data class Unknown(val heard: String) : VoiceIntent
}

private val NAV: List<Pair<List<String>, VoiceTarget>> = listOf(
    listOf("day off", "days off", "i am ill", "i'm ill", "i am sick", "i'm sick", "weekend off") to VoiceTarget.DAY_OFF,
    listOf("block list", "blocklist", "unblock", "allow an app") to VoiceTarget.BLOCKLIST,
    listOf("show the buffer", "show buffer", "buffer", "the working") to VoiceTarget.BUFFER,
    listOf("free to spend", "money", "show the money") to VoiceTarget.MONEY,
    listOf("shopping", "shopping list") to VoiceTarget.SHOPPING,
    listOf("widgets", "edit widgets") to VoiceTarget.WIDGETS,
    listOf("library") to VoiceTarget.LIBRARY,
    listOf("import", "timetable file") to VoiceTarget.IMPORT,
    listOf("settings", "setup", "set up") to VoiceTarget.SETUP,
    listOf("go home", "show home", "home") to VoiceTarget.HOME,
    listOf("quick add", "add a task", "new task") to VoiceTarget.QUICK_ADD,
)

private val WEEKDAYS = DayOfWeek.entries.associateBy { it.name.lowercase() }
private val MONTHS = Month.entries.associateBy { it.name.lowercase() } + Month.entries.associateBy { it.name.take(3).lowercase() }
private val HEAVY = mapOf("tiny" to 1, "easy" to 2, "light" to 3, "small" to 3, "medium" to 5, "normal" to 5, "big" to 7, "heavy" to 8, "hard" to 8, "huge" to 10, "massive" to 10)

/** Reads what was said. [vocabulary] holds module names and the person's own words, so they come out with the right spelling (V2). */
fun parseVoice(heard: String, today: LocalDate, vocabulary: List<String> = emptyList()): VoiceIntent {
    val text = heard.trim().lowercase().replace(Regex("[,.!?]"), " ").replace(Regex("\\s+"), " ").trim()
    if (text.isEmpty()) return VoiceIntent.Unknown(heard)
    val dueAt = Regex("\\bdue\\b").find(text)
    if (dueAt == null) NAV.firstOrNull { (phrases, _) -> phrases.any { containsPhrase(text, it) } }?.let { return VoiceIntent.Go(heard, it.second) }
    if (dueAt == null) return VoiceIntent.Unknown(heard)
    val before = text.substring(0, dueAt.range.first).trim(); val after = text.substring(dueAt.range.last + 1).trim()
    var heaviness: Int? = Regex("\\b(?:heaviness|weight)\\s+(\\d{1,2})\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..10 }
    val words = (before + " " + after).split(" ")
    if (heaviness == null) heaviness = words.firstNotNullOfOrNull { HEAVY[it] }
    val day = parseDay(after, today)
    val time = parseTime(after)
    val title = before.split(" ").filter { it !in HEAVY && it !in setOf("add", "new", "task", "remember", "a", "an", "the", "my", "i", "have", "heaviness", "weight") || (it == "a" && false) }
        .joinToString(" ").trim().ifEmpty { return VoiceIntent.Unknown(heard) }
    return VoiceIntent.AddTask(heard, vocabularyCase(title, vocabulary), day, time, heaviness)
}

private fun containsPhrase(text: String, phrase: String) = Regex("(^| )" + Regex.escape(phrase) + "( |$)").containsMatchIn(text)

private fun vocabularyCase(title: String, vocabulary: List<String>): String {
    var out = title.replaceFirstChar { it.uppercase() }
    for (v in vocabulary.sortedByDescending { it.length }) out = Regex(Regex.escape(v), RegexOption.IGNORE_CASE).replace(out, v)
    return out
}

private fun parseDay(after: String, today: LocalDate): LocalDate? {
    val w = after.split(" ")
    if ("today" in w) return today
    if ("tomorrow" in w) return if (Regex("day after tomorrow").containsMatchIn(after)) today.plusDays(2) else today.plusDays(1)
    // "4 november", "4th november", "4th of november", "november 4"
    Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?(?: of)? ([a-z]+)\\b").find(after)?.let { m ->
        val month = MONTHS[m.groupValues[2]]; val d = m.groupValues[1].toInt()
        if (month != null) return nextDate(today, month, d)
    }
    Regex("\\b([a-z]+) (\\d{1,2})(?:st|nd|rd|th)?\\b").find(after)?.let { m ->
        val month = MONTHS[m.groupValues[1]]; val d = m.groupValues[2].toInt()
        if (month != null) return nextDate(today, month, d)
    }
    val next = "next" in w
    for (word in w) WEEKDAYS[word]?.let { dow ->
        var ahead = (dow.value - today.dayOfWeek.value + 7) % 7
        if (ahead == 0) ahead = 7
        if (next && ahead < 7) ahead += 7
        return today.plusDays(ahead.toLong())
    }
    return null
}

private fun nextDate(today: LocalDate, month: Month, day: Int): LocalDate? {
    if (day !in 1..month.length(false)) return null
    var y = today.year
    repeat(2) { val d = runCatching { LocalDate.of(y, month, day) }.getOrNull(); if (d != null && !d.isBefore(today)) return d; y++ }
    return null
}

private fun parseTime(after: String): LocalTime? {
    if (Regex("\\bnoon\\b").containsMatchIn(after)) return LocalTime.NOON
    if (Regex("\\bmidnight\\b").containsMatchIn(after)) return LocalTime.of(23, 59)
    Regex("\\b(\\d{1,2})[:.](\\d{2})\\b").find(after)?.let { m -> return runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() }
    Regex("\\b(\\d{1,2})(?::(\\d{2}))? ?(am|pm)\\b").find(after)?.let { m ->
        var h = m.groupValues[1].toInt() % 12; if (m.groupValues[3] == "pm") h += 12
        return runCatching { LocalTime.of(h, m.groupValues[2].ifEmpty { "0" }.toInt()) }.getOrNull()
    }
    return null
}

/** V2: the words to teach the recogniser: module names from the timetable and the person's own words. */
fun voiceVocabulary(slots: List<PatternSlot>, own: List<String> = emptyList()): List<String> =
    (slots.filter { it.type == "lecture" || it.type == "tutorial" }.map { it.title.substringBefore(" lecture").substringBefore(" tutorial").substringBefore(" - ").trim() } + own)
        .filter { it.isNotBlank() }.distinct()

/**
 * G7, E10: voice can be switched off in the library and in lectures, where speaking aloud is not possible. Typed quick-add is always available, so this never
 * stops a deadline being added. [quietOff] is the setting.
 */
fun voiceAllowed(nowSlot: PatternSlot?, quietOff: Boolean): Boolean =
    !(quietOff && nowSlot != null && (nowSlot.type == "lecture" || nowSlot.type == "tutorial" || nowSlot.venue == LIBRARY_VENUE))
