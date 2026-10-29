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

/** Event types: fixed commitments that a university or club sets, and the walk to them (T1). Study blocks are never created by a file (IM6). */
val EVENT_TYPES = listOf("lecture", "tutorial", "mytutor", "training", "match", "gym", "church", "errand", "travel")

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
    val venue: String, val study: Study, val cancel: Boolean, val notes: String,
) : ImportRow
data class DeadlineRow(override val line: Int, val date: LocalDate, val dueMin: Int, val title: String, val heaviness: Int) : ImportRow
data class PeriodRow(override val line: Int, val start: LocalDate, val end: LocalDate, val kind: PeriodKind, val title: String) : ImportRow

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
        is EventRow -> listOf("event", date, startMin, endMin, title.lowercase(), type, if (cancel) "cancel" else "add", study).joinToString("|")
        is DeadlineRow -> listOf("deadline", date, dueMin, title.lowercase()).joinToString("|")
        is PeriodRow -> listOf("period", start, end, kind.csv).joinToString("|")
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
    header.filter { it !in CSV_COLUMNS && it.isNotBlank() }.distinct().forEach { warn(1, it, "Unknown column, ignored.") }
    val idx = CSV_COLUMNS.associateWith { header.indexOf(it) }

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
        if (kind !in listOf("event", "deadline", "period")) { err(line, "kind", if (kind.isBlank()) "Empty. Use event, deadline or period." else "\"$kind\" is not a kind. Use event, deadline or period."); continue }

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
                if (type !in EVENT_TYPES) err(line, "type", if (type.isBlank()) "Empty. Use one of: ${EVENT_TYPES.joinToString(", ")}." else "\"$type\" is not an event type. Use one of: ${EVENT_TYPES.joinToString(", ")}.")
                val action = text("action").lowercase()
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
                    EventRow(line, d, s, e, t, type, text("venue"), study, cancel, text("notes")) else null
            }
            "deadline" -> {
                val d = date("date"); val due = time("start", d); val t = title(true)
                val hv = text("heaviness")
                val heaviness = if (hv.isBlank()) QUICK_ADD_DEFAULT_HEAVINESS else hv.toIntOrNull()?.takeIf { it in 1..10 }
                    ?: run { err(line, "heaviness", "\"$hv\" is not a heaviness. Use a whole number from 1 to 10, or leave it empty for $QUICK_ADD_DEFAULT_HEAVINESS."); null }
                if (listOf("end", "type", "venue", "study", "action", "end_date").any { text(it).isNotBlank() }) warn(line, "type", "A deadline uses only date, start (the time it is due), title and heaviness. The other cells are ignored.")
                if (errors.size == before && d != null && due != null && t != null && heaviness != null) DeadlineRow(line, d, due, t, heaviness) else null
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
        val past = when (row) { is EventRow -> row.date.isBefore(today); is DeadlineRow -> row.date.isBefore(today); is PeriodRow -> row.end.isBefore(today) }
        if (past) { skippedPast++; warn(line, "date", "In the past, skipped."); continue }
        if (!seen.add(row.hash())) { dupes++; warn(line, "", "The same row appears earlier in the file, skipped."); continue }
        rows += row
    }
    return ParsedImport(if (errors.isEmpty()) rows else emptyList(), errors, warnings, skippedPast, dupes)
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
