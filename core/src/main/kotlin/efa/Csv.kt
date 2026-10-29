package efa

/** The result of reading a CSV file into cells. [error] is set when the file cannot be read as CSV at all. */
data class CsvRead(val rows: List<List<String>>, val delimiter: Char, val error: String? = null, val errorLine: Int = 0)

/**
 * A small CSV reader for IM9: UTF-8 with or without a byte-order mark, comma, semicolon or tab between cells (picked from the header line),
 * quoted cells with doubled quotes, line ends in any of the three styles, and blank lines skipped. It never evaluates anything:
 * a cell that starts with = + - or @ is just text here.
 */
object Csv {
    fun read(input: String): CsvRead {
        val text = input.removePrefix("﻿")
        val delimiter = detectDelimiter(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false          // inside a quoted cell
        var wasQuoted = false       // the cell had quotes, so an empty value is still a cell
        var line = 1
        var startLine = 1
        var i = 0
        fun endCell() { row.add(if (wasQuoted) cell.toString() else cell.toString().trim()); cell.clear(); wasQuoted = false }
        fun endRow() {
            endCell()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else { if (c == '\n') line++; cell.append(c) }
            } else when {
                c == '"' && cell.isBlank() -> { quoted = true; wasQuoted = true; cell.clear(); startLine = line }
                c == delimiter -> endCell()
                c == '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow(); line++ }
                c == '\n' -> { endRow(); line++ }
                else -> cell.append(c)
            }
            i++
        }
        if (quoted) return CsvRead(rows, delimiter, "A quote opened on line $startLine is never closed.", startLine)
        if (cell.isNotEmpty() || row.isNotEmpty() || wasQuoted) endRow()
        return CsvRead(rows, delimiter)
    }

    private fun detectDelimiter(text: String): Char {
        val header = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        var inQuote = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (ch in header) { if (ch == '"') inQuote = !inQuote else if (!inQuote && ch in counts) counts[ch] = counts.getValue(ch) + 1 }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }
}
