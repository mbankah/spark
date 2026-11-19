package efa

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Timetable CSV, version 1 (IM1). One header, three kinds of row. The same rules are written out in docs/TIMETABLE_CSV.md, which is also
 * what an AI is given to build the file, and the same test files (core/src/test/resources/csv/) are for every platform that reads it (IM2).
 */
object ImportLimits {
    const val MAX_CHARS = 1_000_000
    const val MAX_ROWS = 2000
    const val MAX_TITLE = 120
    const val MAX_PERIOD_DAYS = 14
    const val MAX_AHEAD_DAYS = 366L
    const val MAX_ISSUES_SHOWN = 50
}

/** The header, in this order when Spark writes it. A file may order the columns differently but must have all of them (IM4). */
val CSV_COLUMNS = listOf("kind", "date", "end_date", "start", "end", "title", "type", "venue", "heaviness", "study", "action", "notes")

/**
 * OV1: columns a file may add after the required ones. `override` goes on a cancel row and says "I mean it": the one way a file can cancel a block it otherwise cannot touch
 * (a study or code block, a planning block, a routine, a chore). It is never a quiet change: it always needs the typed wall, the preview names each block, and a study cycle it
 * cancels comes back as work, so nothing disappears from the Buffer.
 */
val OPTIONAL_COLUMNS = listOf("override")

/** OV1: the block types an override can cancel. Meals, sleep and reminders are not in it: nothing overrides a meal or sleep. */
val OVERRIDE_TYPES = listOf("study", "study-standard", "study-light", "code", "planning", "routine", "chore")

/** OV1: the wake (the alarm is planned from it) and the Bible study (first, always) are never overridden, whatever the file says. */
fun neverOverridden(title: String) = title.startsWith("Wake") || title.startsWith("Bible study")

/** Event types: fixed commitments that a university or club sets, and the walk to them (T1). Study blocks are never created by a file (IM6). */
val EVENT_TYPES = listOf("lecture", "tutorial", "mytutor", "training", "match", "gym", "church", "errand", "travel", "reminder")

/** RP1: an event of this type sounds a notification when it starts. It may sit on top of anything and takes no time from anything. */
const val REMINDER_TYPE = "reminder"

enum class PeriodKind(val csv: String, val label: String) {
    ASSIGNMENT_WEEK("assignment_week", "Assignment week"),
    CATCH_UP_WEEK("catch_up_week", "Catch-up week"),
}

/** Period names that exist in the ledger (C1) but are planned in the app, because a day off needs the typed wall (H1). */
val PERIODS_IN_APP_ONLY = setOf("day_off", "library")

enum class Study { STANDARD, LIGHT, NONE }

sealed interface ImportRow { val line: Int }
data class EventRow(
    override val line: Int, val date: LocalDate, val startMin: Int, val endMin: Int, val title: String, val type: String,
    val venue: String, val study: Study, val cancel: Boolean, val notes: String, val override: Boolean = false,
) : ImportRow
data class DeadlineRow(override val line: Int, val date: LocalDate, val dueMin: Int, val title: String, val heaviness: Int) : ImportRow
data class PeriodRow(override val line: Int, val start: LocalDate, val end: LocalDate, val kind: PeriodKind, val title: String) : ImportRow
/** RP2: everything the calendar holds on [date] from [startMin] to [endMin] is taken away and the events of the same file stand in its place. It needs the typed wall. */
data class ReplaceRow(override val line: Int, val date: LocalDate, val startMin: Int, val endMin: Int, val title: String) : ImportRow

/**
 * IX6, format version 2: a file can say what a modifier says. A modifier row is a preset with its dates and settings (date, end_date, type is the preset id, notes is key=value;key=value, start and end the
 * times of the first and last day). An anchor row is a night: date the night, start lights-out, end wake. A keep row protects a time on a date from a Replace in the same file.
 */
data class ModifierImport(override val line: Int, val instance: Instance) : ImportRow
data class KeepRow(override val line: Int, val date: LocalDate, val startMin: Int, val endMin: Int, val title: String) : ImportRow

/** The presets a file may carry. The ones that need items (assignments, exams, a project) and a day off are planned in the app, where the wall and the items are. */
val FILE_PRESETS: List<Preset> = listOf(Preset.CATCH_UP, Preset.LECTURE_FREE, Preset.LATE_START, Preset.RUN_OVER, Preset.ADD_EVENT, Preset.CANCEL, Preset.RECOVERY, Preset.GUESTS, Preset.AWAY, Preset.NIGHT)

/** [column] is blank for a problem with the file as a whole. [line] is the line in the file, counting the header as line 1. */
data class ImportIssue(val line: Int, val column: String, val message: String) {
    fun text() = (if (line > 0) "Line $line" else "File") + (if (column.isNotBlank()) ", $column" else "") + ": $message"
}

data class ParsedImport(
    val rows: List<ImportRow>, val errors: List<ImportIssue>, val warnings: List<ImportIssue>,
    val skippedPast: Int = 0, val duplicatesInFile: Int = 0,
) { val ok: Boolean get() = errors.isEmpty() }

/** A fixed fingerprint of a row's meaning, so importing the same row twice changes nothing (IM8). */
fun ImportRow.hash(): String {
    val canon = when (this) {
        is EventRow -> listOf("event", date, startMin, endMin, title.lowercase(), type, if (cancel) "cancel" else "add", study).plus(if (override) listOf("override") else emptyList()).joinToString("|")      // OV1: the flag is part of the meaning, but only when set, so earlier fingerprints stay
        is DeadlineRow -> listOf("deadline", date, dueMin, title.lowercase()).joinToString("|")
        is PeriodRow -> listOf("period", start, end, kind.csv).joinToString("|")
        is ReplaceRow -> listOf("replace", date, startMin, endMin).joinToString("|")
        is ModifierImport -> listOf("modifier", instance.preset.id, instance.start, instance.end, instance.fromMin, instance.toMin, instance.settings.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }).joinToString("|")
        is KeepRow -> listOf("keep", date, startMin, endMin).joinToString("|")
    }
    return MessageDigest.getInstance("SHA-256").digest(canon.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

fun fileHash(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

fun minutesText(min: Int) = "%02d:%02d".format(min / 60, min % 60)

/**
 * Reads and checks a whole file. It never throws: every problem becomes an [ImportIssue] with its line and column and what to change,
 * and a file with any error applies nothing (IM4). Rows dated before [today] are skipped with a warning.
 */
fun parseTimetableCsv(input: String, today: LocalDate, zone: ZoneId = LONDON): ParsedImport {
    val errors = ArrayList<ImportIssue>(); val warnings = ArrayList<ImportIssue>()
    fun err(line: Int, col: String, msg: String) { errors += ImportIssue(line, col, msg) }
    fun warn(line: Int, col: String, msg: String) { warnings += ImportIssue(line, col, msg) }

    if (input.length > ImportLimits.MAX_CHARS) return ParsedImport(emptyList(), listOf(ImportIssue(0, "", "The file is over 1 MB. Split it into smaller files.")), emptyList())
    val read = Csv.read(input)
    if (read.error != null) return ParsedImport(emptyList(), listOf(ImportIssue(read.errorLine, "", read.error)), emptyList())
    if (read.rows.isEmpty()) return ParsedImport(emptyList(), listOf(ImportIssue(0, "", "The file is empty. It needs the header line and at least one row.")), emptyList())
    if (read.rows.size - 1 > ImportLimits.MAX_ROWS) return ParsedImport(emptyList(), listOf(ImportIssue(0, "", "More than ${ImportLimits.MAX_ROWS} rows. Split the file.")), emptyList())

    val header = read.rows[0].map { it.trim().lowercase() }
    val missing = CSV_COLUMNS.filter { it !in header }
    if (missing.isNotEmpty()) return ParsedImport(emptyList(), listOf(ImportIssue(1, "", "The header is missing: ${missing.joinToString(", ")}. It must have all of: ${CSV_COLUMNS.joinToString(",")}.")), emptyList())
    header.filter { it !in CSV_COLUMNS && it !in OPTIONAL_COLUMNS && it.isNotBlank() }.distinct().forEach { warn(1, it, "Unknown column, ignored.") }
    val idx = (CSV_COLUMNS + OPTIONAL_COLUMNS).associateWith { header.indexOf(it) }          // an optional column that is absent is -1, and its cell reads as empty

    val rows = ArrayList<ImportRow>(); val seen = HashSet<String>()
    var skippedPast = 0; var dupes = 0
    val limit = today.plusDays(ImportLimits.MAX_AHEAD_DAYS)
    var formulaWarned = false

    for ((n, cells) in read.rows.withIndex()) {
        if (n == 0) continue
        val line = n + 1
        if (cells.size > header.size && cells.drop(header.size).any { it.isNotBlank() }) { err(line, "", "This row has more cells than the header. A comma inside a title needs the title in quotes."); continue }
        fun cell(c: String): String = cells.getOrNull(idx.getValue(c)).orEmpty()
        fun text(c: String): String {
            val raw = cell(c).filter { !it.isISOControl() || it == ' ' }.replace(Regex("\\s+"), " ").trim()
            if (raw.firstOrNull() in listOf('=', '+', '-', '@') && !formulaWarned) { warn(line, c, "Starts with = + - or @. Kept as plain text and never calculated."); formulaWarned = true }
            return raw
        }
        val before = errors.size

        val kind = text("kind").lowercase()
        if (kind !in listOf("event", "deadline", "period", "replace", "modifier", "anchor", "keep")) { err(line, "kind", if (kind.isBlank()) "Empty. Use event, deadline, period, replace, modifier, anchor or keep." else "\"$kind\" is not a kind. Use event, deadline, period, replace, modifier, anchor or keep."); continue }

        fun date(c: String): LocalDate? {
            val s = text(c)
            if (s.isBlank()) { err(line, c, "Empty. Give a date as 2026-11-02 or 02/11/2026."); return null }
            val d = parseDate(s)
            if (d == null) { err(line, c, "\"$s\" is not a real date. Use 2026-11-02 or 02/11/2026."); return null }
            if (d.isAfter(limit)) { err(line, c, "$d is more than a year away. Check the year."); return null }
            return d
        }
        fun time(c: String, on: LocalDate?): Int? {
            val s = text(c)
            if (s.isBlank()) { err(line, c, "Empty. Give a time as 09:00."); return null }
            val m = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(s)
            val h = m?.groupValues?.get(1)?.toInt(); val mi = m?.groupValues?.get(2)?.toInt()
            if (m == null || h!! > 23 || mi!! > 59) { err(line, c, "\"$s\" is not a time. Use 24-hour 09:00 or 14:30."); return null }
            if (on != null) {
                val offsets = zone.rules.getValidOffsets(LocalDateTime.of(on, LocalTime.of(h, mi)))
                if (offsets.isEmpty()) { err(line, c, "$s does not exist on $on because the clocks go forward that night."); return null }
                if (offsets.size > 1) warn(line, c, "$s happens twice on $on because the clocks go back. The first one is used.")
            }
            return h * 60 + mi
        }
        fun title(required: Boolean): String? {
            val t = text("title")
            if (t.isBlank()) { if (required) err(line, "title", "Empty. Every ${kind} needs a title."); return if (required) null else "" }
            if (t.length > ImportLimits.MAX_TITLE) { err(line, "title", "Longer than ${ImportLimits.MAX_TITLE} characters."); return null }
            return t
        }

        val row: ImportRow? = when (kind) {
            "event" -> {
                val d = date("date"); val s = time("start", d); val e = time("end", d); val t = title(true)
                val type = text("type").lowercase()
                val ov = text("override").lowercase()
                if (ov !in listOf("", "yes", "true", "1", "no", "false", "0")) err(line, "override", "\"$ov\" is not a choice. Use yes, or leave it empty.")
                val override = ov in listOf("yes", "true", "1")
                if (type !in EVENT_TYPES && !(override && type in OVERRIDE_TYPES)) err(line, "type", when {
                    type.isBlank() -> "Empty. Use one of: ${EVENT_TYPES.joinToString(", ")}."
                    type in OVERRIDE_TYPES -> "A file cannot touch a $type block. To cancel one on purpose, put yes in the override column of a cancel row."
                    else -> "\"$type\" is not an event type. Use one of: ${EVENT_TYPES.joinToString(", ")}."
                })
                val action = text("action").lowercase()
                if (override && action != "cancel") err(line, "override", "An override goes only on a cancel row. Set action to cancel, or leave override empty.")
                if (action !in listOf("", "add", "cancel")) err(line, "action", "\"$action\" is not an action. Leave it empty or use add or cancel.")
                if (s != null && e != null && e <= s) err(line, "end", "${minutesText(e)} is not after the start ${minutesText(s)}. An event must end on the same day it starts.")
                val studyText = text("study").lowercase()
                val cancel = action == "cancel"
                val study = when {
                    studyText !in listOf("", "standard", "light", "none") -> { err(line, "study", "\"$studyText\" is not a choice. Use standard, light or none, or leave it empty."); Study.NONE }
                    studyText.isNotBlank() && (cancel || type !in listOf("lecture", "tutorial")) && studyText != "none" -> { err(line, "study", "A study block belongs only to a lecture or tutorial that is added."); Study.NONE }
                    studyText == "standard" -> Study.STANDARD
                    studyText == "light" -> Study.LIGHT
                    studyText == "none" -> Study.NONE
                    !cancel && type in listOf("lecture", "tutorial") -> Study.STANDARD      // D1: every lecture creates a study cycle
                    else -> Study.NONE
                }
                if (text("heaviness").isNotBlank() || text("end_date").isNotBlank()) warn(line, "heaviness", "Not used by an event, ignored.")
                if (errors.size == before && d != null && s != null && e != null && t != null)
                    EventRow(line, d, s, e, t, type, text("venue"), study, cancel, text("notes"), override) else null
            }
            "deadline" -> {
                val d = date("date"); val due = time("start", d); val t = title(true)
                val hv = text("heaviness")
                val heaviness = if (hv.isBlank()) QUICK_ADD_DEFAULT_HEAVINESS else hv.toIntOrNull()?.takeIf { it in 1..10 }
                    ?: run { err(line, "heaviness", "\"$hv\" is not a heaviness. Use a whole number from 1 to 10, or leave it empty for $QUICK_ADD_DEFAULT_HEAVINESS."); null }
                if (listOf("end", "type", "venue", "study", "action", "end_date", "override").any { text(it).isNotBlank() }) warn(line, "type", "A deadline uses only date, start (the time it is due), title and heaviness. The other cells are ignored.")
                if (errors.size == before && d != null && due != null && t != null && heaviness != null) DeadlineRow(line, d, due, t, heaviness) else null
            }
            "replace" -> {
                val d = date("date"); val s = time("start", d); val e = time("end", d)
                if (s != null && e != null && e <= s) err(line, "end", "${minutesText(e)} is not after the start ${minutesText(s)}. Replace one day at a time, up to 23:59.")
                val t = title(false)
                if (errors.size == before && d != null && s != null && e != null) ReplaceRow(line, d, s, e, t.orEmpty().ifBlank { "Replaced" }) else null
            }
            "keep" -> {
                val d = date("date"); val s = time("start", d); val e = time("end", d)
                if (s != null && e != null && e <= s) err(line, "end", "${minutesText(e)} is not after the start ${minutesText(s)}. Keep one day at a time.")
                if (errors.size == before && d != null && s != null && e != null) KeepRow(line, d, s, e, title(false).orEmpty().ifBlank { "Kept" }) else null
            }
            "anchor" -> {
                val d = date("date"); val lo = time("start", d); val wk = time("end", d)
                val extra = parseSettings(text("notes")) { m -> err(line, "notes", m) }
                if (lo != null && wk != null && d != null) {
                    val settings = mapOf("lights_out" to "%02d:%02d".format(lo / 60, lo % 60), "wake" to "%02d:%02d".format(wk / 60, wk % 60)) + extra.filterKeys { it == "winddown_min" }
                    extra.keys.filter { it != "winddown_min" }.forEach { err(line, "notes", "\"$it\" is not a setting of an anchor. Use winddown_min, or leave notes empty.") }
                    val inst = Instance(Preset.NIGHT, d, d, settings)
                    if (errors.size == before) validate(inst, today.minusDays(1)).forEach { err(line, "date", it) }
                    if (errors.size == before) ModifierImport(line, inst) else null
                } else null
            }
            "modifier" -> {
                val d = date("date"); val e = date("end_date")
                val id = text("type").lowercase()
                val preset = FILE_PRESETS.firstOrNull { it.id == id }
                if (preset == null) err(line, "type", when {
                    Preset.of(id) != null -> "\"$id\" needs its own items or the typed wall, so it is planned in the app. Use one of: ${FILE_PRESETS.joinToString(", ") { it.id }}."
                    id.isBlank() -> "Empty. Use one of: ${FILE_PRESETS.joinToString(", ") { it.id }}."
                    else -> "\"$id\" is not a modifier. Use one of: ${FILE_PRESETS.joinToString(", ") { it.id }}."
                })
                val sm = if (text("start").isBlank()) 0 else time("start", d); val em = if (text("end").isBlank()) 1440 else time("end", e ?: d)
                val settings = parseSettings(text("notes")) { m -> err(line, "notes", m) }
                if (preset != null) settings.keys.filter { key -> ModDefs.settings(preset).none { it.key == key } }.forEach { err(line, "notes", "\"$it\" is not a setting of ${preset.label}. Its settings are: ${ModDefs.settings(preset).joinToString(", ") { s -> s.key }}.") }
                if (errors.size == before && preset != null && d != null && e != null && sm != null && em != null) {
                    val inst = Instance(preset, d, e, settings, emptyList(), sm, em)
                    validate(inst, today).forEach { err(line, "date", it) }
                    if (errors.size == before) ModifierImport(line, inst) else null
                } else null
            }
            else -> {
                val d = date("date"); val e = date("end_date")
                val type = text("type").lowercase()
                val pk = PeriodKind.entries.firstOrNull { it.csv == type }
                if (pk == null) err(line, "type", when {
                    type in PERIODS_IN_APP_ONLY -> "\"$type\" is planned in the app, where a day off needs the typed wall. Use assignment_week or catch_up_week here."
                    type.isBlank() -> "Empty. Use assignment_week or catch_up_week."
                    else -> "\"$type\" is not a period. Use assignment_week or catch_up_week."
                })
                if (d != null && e != null) {
                    if (e.isBefore(d)) err(line, "end_date", "$e is before the start $d.")
                    else if (ChronoUnit.DAYS.between(d, e) + 1 > ImportLimits.MAX_PERIOD_DAYS) err(line, "end_date", "A period is at most ${ImportLimits.MAX_PERIOD_DAYS} days. Split it.")
                }
                val t = title(false)
                if (errors.size == before && d != null && e != null && pk != null) PeriodRow(line, d, e, pk, t.orEmpty().ifBlank { pk.label }) else null
            }
        }
        if (row == null) continue
        val past = when (row) { is EventRow -> row.date.isBefore(today); is DeadlineRow -> row.date.isBefore(today); is PeriodRow -> row.end.isBefore(today); is ReplaceRow -> row.date.isBefore(today); is ModifierImport -> row.instance.end.isBefore(today) && !(row.instance.preset == Preset.NIGHT && row.instance.start == today.minusDays(1)); is KeepRow -> row.date.isBefore(today) }
        if (past) { skippedPast++; warn(line, "date", "In the past, skipped."); continue }
        if (!seen.add(row.hash())) { dupes++; warn(line, "", "The same row appears earlier in the file, skipped."); continue }
        rows += row
    }
    return ParsedImport(if (errors.isEmpty()) rows else emptyList(), errors, warnings, skippedPast, dupes)
}

/** IX6: "key=value;key=value". A key or value with a bar or a line break is refused so a row cannot break a line. [onError] gets a plain sentence. */
fun parseSettings(notes: String, onError: (String) -> Unit): Map<String, String> {
    if (notes.isBlank()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (part in notes.split(";")) {
        if (part.isBlank()) continue
        val i = part.indexOf('=')
        if (i <= 0) { onError("\"${part.trim()}\" is not key=value. Separate settings with ;"); continue }
        out[part.substring(0, i).trim()] = part.substring(i + 1).trim()
    }
    return out
}

/** ISO 2026-11-02, or UK 02/11/2026. Anything else is not a date. */
fun parseDate(s: String): LocalDate? {
    Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(s)?.let { m ->
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }
    Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{4})$").matchEntire(s)?.let { m ->
        return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }
    return null
}
