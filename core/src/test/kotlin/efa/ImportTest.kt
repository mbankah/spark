package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.*

private const val HEADER = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"

/** Rows in the full header, so a test only writes the cells it cares about. */
private fun row(kind: String, date: String = "", endDate: String = "", start: String = "", end: String = "", title: String = "", type: String = "",
               venue: String = "", heaviness: String = "", study: String = "", action: String = "", notes: String = "") =
    listOf(kind, date, endDate, start, end, title, type, venue, heaviness, study, action, notes).joinToString(",")

private fun file(vararg rows: String) = (listOf(HEADER) + rows).joinToString("\n")

class CsvReaderTest {
    @Test fun `IM9 commas semicolons and tabs are told apart from the header`() {
        assertEquals(',', Csv.read("a,b\n1,2").delimiter)
        assertEquals(';', Csv.read("a;b\n1;2").delimiter)
        assertEquals('\t', Csv.read("a\tb\n1\t2").delimiter)
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.read("a;b\n1;2").rows)
    }

    @Test fun `IM9 quoted cells keep commas, doubled quotes and line breaks`() {
        val r = Csv.read("t,n\n\"Maths, part 2\",\"say \"\"hi\"\"\"\n\"two\nlines\",x").rows
        assertEquals(listOf("Maths, part 2", "say \"hi\""), r[1]); assertEquals(listOf("two\nlines", "x"), r[2])
    }

    @Test fun `IM9 all three line endings, a byte order mark and blank lines are fine`() {
        val r = Csv.read("﻿a,b\r\n1,2\r3,4\n\n5,6\n").rows
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4"), listOf("5", "6")), r)
    }

    @Test fun `IM4 a quote that never closes is an error with its line`() {
        val r = Csv.read("a,b\n1,\"oops\n2,3")
        assertNotNull(r.error); assertEquals(2, r.errorLine)
    }

    @Test fun `IM9 a cell starting with an equals sign stays plain text`() {
        assertEquals("=1+1", Csv.read("a\n=1+1").rows[1][0])
    }
}

class ImportParseTest {
    private val today = LocalDate.of(2026, 10, 26)
    private fun parse(s: String) = parseTimetableCsv(s, today)
    private fun firstError(s: String) = parse(s).errors.firstOrNull() ?: fail("expected an error")

    private val good = file(
        row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Cryptography lecture", type = "lecture", venue = "Watson LT A"),
        row("event", "2026-11-02", start = "09:00", end = "10:00", title = "Randomness and Computation lecture", type = "lecture", action = "cancel"),
        row("deadline", "2026-11-06", start = "12:00", title = "CS Project report", heaviness = "8"),
        row("period", "2026-11-02", "2026-11-06", type = "assignment_week", title = "Assignment week"),
    )

    @Test fun `IM1 one file can hold events, a cancel, a deadline and a period`() {
        val p = parse(good)
        assertTrue(p.ok, p.errors.joinToString { it.text() })
        assertEquals(4, p.rows.size)
        val e = p.rows.filterIsInstance<EventRow>()
        assertEquals(listOf(false, true), e.map { it.cancel })
        assertEquals(Study.STANDARD, e[0].study)           // D1: a lecture added gets its study cycle
        assertEquals(8, p.rows.filterIsInstance<DeadlineRow>().single().heaviness)
        assertEquals(PeriodKind.ASSIGNMENT_WEEK, p.rows.filterIsInstance<PeriodRow>().single().kind)
    }

    @Test fun `IM1 the columns may come in any order`() {
        val reordered = "title,kind,date,start,end,end_date,type,venue,heaviness,study,action,notes\nCS Project,deadline,2026-11-06,12:00,,,,,5,,,"
        val p = parse(reordered)
        assertTrue(p.ok, p.errors.joinToString { it.text() }); assertEquals("CS Project", (p.rows.single() as DeadlineRow).title)
    }

    @Test fun `IM4 UK dates are read the UK way`() {
        val p = parse(file(row("deadline", "06/11/2026", start = "9:30", title = "Report")))
        assertEquals(LocalDate.of(2026, 11, 6), (p.rows.single() as DeadlineRow).date)
    }

    @Test fun `IM4 a missing header column names what is missing`() {
        val e = firstError("kind,date,title\ndeadline,2026-11-06,x")
        assertTrue("end_date" in e.message && "study" in e.message); assertEquals(1, e.line)
    }

    @Test fun `IM4 an empty file is rejected`() { assertTrue("empty" in firstError("").message) }

    @Test fun `IM4 an error gives the line and the column`() {
        val e = firstError(file(row("event", "2026-11-03", start = "25:00", end = "26:00", title = "x", type = "lecture")))
        assertEquals(2, e.line); assertEquals("start", e.column); assertTrue("24-hour" in e.message)
        assertEquals("Line 2, start: ${e.message}", e.text())
    }

    @Test fun `IM4 a date that does not exist is rejected`() {
        assertEquals("date", firstError(file(row("deadline", "2026-02-30", start = "12:00", title = "x"))).column)
        assertEquals("date", firstError(file(row("deadline", "31/04/2026", start = "12:00", title = "x"))).column)
        assertEquals("date", firstError(file(row("deadline", "next Friday", start = "12:00", title = "x"))).column)
    }

    @Test fun `IM4 an event must end after it starts`() {
        assertEquals("end", firstError(file(row("event", "2026-11-03", start = "10:00", end = "10:00", title = "x", type = "lecture"))).column)
        assertEquals("end", firstError(file(row("event", "2026-11-03", start = "23:00", end = "00:30", title = "x", type = "lecture"))).column)
    }

    @Test fun `X4 IM4 a time that does not exist because the clocks go forward is rejected`() {
        val e = firstError(file(row("event", "2027-03-28", start = "01:30", end = "02:30", title = "x", type = "lecture")))
        assertTrue("does not exist" in e.message, e.message)
    }

    @Test fun `X4 IM4 a time that happens twice when the clocks go back is accepted with a warning`() {
        val p = parse(file(row("event", "2026-10-25", start = "01:30", end = "01:45", title = "Night shift", type = "errand")))
        assertTrue(p.ok); assertTrue(p.warnings.any { "twice" in it.message })
    }

    @Test fun `IM4 an unknown kind, type, action or study value is rejected`() {
        assertEquals("kind", firstError(file(row("meeting", "2026-11-03"))).column)
        assertEquals("type", firstError(file(row("event", "2026-11-03", start = "10:00", end = "11:00", title = "x", type = "party"))).column)
        assertEquals("action", firstError(file(row("event", "2026-11-03", start = "10:00", end = "11:00", title = "x", type = "lecture", action = "delete"))).column)
        assertEquals("study", firstError(file(row("event", "2026-11-03", start = "10:00", end = "11:00", title = "x", type = "lecture", study = "heavy"))).column)
    }

    @Test fun `IM6 a file cannot create a study block, a meal or a day off`() {
        for (t in listOf("study", "study-standard", "code", "meal", "sleep", "free", "routine", "day_off"))
            assertEquals("type", firstError(file(row("event", "2026-11-03", start = "10:00", end = "11:00", title = "x", type = t))).column, t)
    }

    @Test fun `PW1 a day off and a library week are planned in the app, and the message says why`() {
        for (t in listOf("day_off", "library")) {
            val e = firstError(file(row("period", "2026-11-02", "2026-11-03", type = t)))
            assertEquals("type", e.column); assertTrue("in the app" in e.message, e.message)
        }
    }

    @Test fun `PW1 a period is 1 to 14 days and cannot run backwards`() {
        assertTrue(parse(file(row("period", "2026-11-02", "2026-11-02", type = "catch_up_week"))).ok)
        assertTrue(parse(file(row("period", "2026-11-02", "2026-11-15", type = "catch_up_week"))).ok)
        assertEquals("end_date", firstError(file(row("period", "2026-11-02", "2026-11-16", type = "catch_up_week"))).column)
        assertEquals("end_date", firstError(file(row("period", "2026-11-05", "2026-11-02", type = "catch_up_week"))).column)
    }

    @Test fun `D2 IM7 a deadline with no heaviness gets the quick-add default and 10 is the top`() {
        assertEquals(QUICK_ADD_DEFAULT_HEAVINESS, (parse(file(row("deadline", "2026-11-06", start = "12:00", title = "x"))).rows.single() as DeadlineRow).heaviness)
        assertEquals("heaviness", firstError(file(row("deadline", "2026-11-06", start = "12:00", title = "x", heaviness = "11"))).column)
        assertEquals("heaviness", firstError(file(row("deadline", "2026-11-06", start = "12:00", title = "x", heaviness = "0"))).column)
        assertEquals("heaviness", firstError(file(row("deadline", "2026-11-06", start = "12:00", title = "x", heaviness = "heavy"))).column)
    }

    @Test fun `IM7 a deadline must say when it is due`() {
        assertEquals("start", firstError(file(row("deadline", "2026-11-06", title = "x"))).column)
    }

    @Test fun `IM4 a title is needed and has a length limit`() {
        assertEquals("title", firstError(file(row("deadline", "2026-11-06", start = "12:00"))).column)
        assertEquals("title", firstError(file(row("deadline", "2026-11-06", start = "12:00", title = "x".repeat(121)))).column)
    }

    @Test fun `IM4 a date more than a year away is treated as a typo`() {
        assertEquals("date", firstError(file(row("deadline", "2028-01-06", start = "12:00", title = "x"))).column)
    }

    @Test fun `IM4 a row with too many cells is rejected and asks for quotes`() {
        val e = firstError(HEADER + "\n" + "event,2026-11-03,,10:00,11:00,Maths, part 2,lecture,,,,,,extra")
        assertTrue("quotes" in e.message, e.message)
    }

    @Test fun `IM4 one bad row rejects the whole file and nothing is returned to apply`() {
        val p = parse(file(row("deadline", "2026-11-06", start = "12:00", title = "fine"), row("deadline", "bad", start = "12:00", title = "x")))
        assertFalse(p.ok); assertTrue(p.rows.isEmpty())
    }

    @Test fun `IM4 every problem in the file is listed, not just the first`() {
        val p = parse(file(row("deadline", "bad", start = "12:00", title = "a"), row("deadline", "2026-11-06", start = "99:00", title = "b"), row("kind?")))
        assertEquals(3, p.errors.map { it.line }.distinct().size)
    }

    @Test fun `IM8 the same row twice in a file is kept once with a warning`() {
        val r = row("deadline", "2026-11-06", start = "12:00", title = "Report")
        val p = parse(file(r, r.replace("Report", "REPORT")))
        assertEquals(1, p.rows.size); assertEquals(1, p.duplicatesInFile); assertTrue(p.warnings.isNotEmpty())
    }

    @Test fun `IM8 a row's fingerprint depends on its meaning and not on spacing or case`() {
        val a = parse(file(row("deadline", "2026-11-06", start = "12:00", title = "CS  Project"))).rows.single().hash()
        val b = parse(file(row("deadline", "06/11/2026", start = "12:00", title = " cs project "))).rows.single().hash()
        assertEquals(a, b)
        val c = parse(file(row("deadline", "2026-11-06", start = "12:01", title = "CS Project"))).rows.single().hash()
        assertNotEquals(a, c)
    }

    @Test fun `IM9 rows in the past are skipped and counted`() {
        val p = parse(file(row("deadline", "2026-10-01", start = "12:00", title = "old"), row("deadline", "2026-11-06", start = "12:00", title = "new")))
        assertEquals(1, p.rows.size); assertEquals(1, p.skippedPast)
    }

    @Test fun `IM9 a file over 1 MB and a file over 2000 rows are rejected`() {
        assertTrue("1 MB" in firstError("x".repeat(ImportLimits.MAX_CHARS + 1)).message)
        val many = file(*Array(ImportLimits.MAX_ROWS + 1) { row("deadline", "2026-11-06", start = "12:00", title = "t$it") })
        assertTrue("2000" in firstError(many).message)
    }

    @Test fun `IM9 a formula cell is kept as text with a warning and control characters are removed`() {
        val p = parse(file(row("deadline", "2026-11-06", start = "12:00", title = "=HYPERLINK(\"x\")\u0007")))
        assertEquals("=HYPERLINK(\"x\")", (p.rows.single() as DeadlineRow).title)
        assertTrue(p.warnings.any { "plain text" in it.message })
    }

    @Test fun `X7 IM4 reading never throws, whatever the file holds`() {
        val rnd = Random(29)
        val alphabet = "abc,;\t\"\n\r =+-@0123456789:/_﻿\u0000é€"
        repeat(2000) {
            val junk = String(CharArray(rnd.nextInt(0, 400)) { alphabet[rnd.nextInt(alphabet.length)] })
            parseTimetableCsv(junk, today)
            parseTimetableCsv(HEADER + "\n" + junk, today)
        }
        // and a real file with single characters changed or cut
        repeat(2000) {
            val chars = good.toCharArray(); val at = rnd.nextInt(chars.size)
            chars[at] = alphabet[rnd.nextInt(alphabet.length)]
            parseTimetableCsv(String(chars).take(rnd.nextInt(1, good.length + 1)), today)
        }
    }

    @Test fun `IM2 the shared example files read the same way everywhere`() {
        val dir = java.io.File("src/test/resources/csv")
        val goodFiles = dir.listFiles { f -> f.name.startsWith("good-") }.orEmpty()
        val badFiles = dir.listFiles { f -> f.name.startsWith("bad-") }.orEmpty()
        assertTrue(goodFiles.isNotEmpty() && badFiles.isNotEmpty())
        for (f in goodFiles) { val p = parse(f.readText()); assertTrue(p.ok, "${f.name}: " + p.errors.joinToString { it.text() }) }
        for (f in badFiles) {
            val p = parse(f.readText()); assertFalse(p.ok, f.name)
            val m = Regex("^bad-(\\d+)-([a-z_]+)-").find(f.name) ?: fail("bad file name ${f.name}")
            val expected = m.groupValues[1] + ":" + m.groupValues[2].let { if (it == "file") "" else it }
            assertTrue(p.errors.any { "${it.line}:${it.column}" == expected }, "${f.name} expected $expected got ${p.errors.map { "${it.line}:${it.column}" }}")
        }
    }

    @Test fun `RS9 the shared bad files cover every kind of mistake a file can hold`() {
        val names = java.io.File("src/test/resources/csv").listFiles { f -> f.name.startsWith("bad-") }.orEmpty().map { it.name }
        for (column in listOf("file", "kind", "date", "start", "type", "end_date", "heaviness")) assertTrue(names.any { "-$column-" in it }, "no bad file for $column")
    }
}

class ImportVerifyTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val now = at(today, LocalTime.of(12, 0))
    private val slots = WeekB.slots
    private fun verify(f: String, known: Set<String> = emptySet(), work: List<DueWork> = emptyList(), pattern: List<PatternSlot> = slots) =
        verifyImport(parseTimetableCsv(f, today), pattern, known, work, now)

    @Test fun `IM5 a lecture in free time previews its study block and the Buffer before and after`() {
        val v = verify(file(row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Cryptography lecture", type = "lecture", venue = "Watson LT A")))
        assertTrue(v.ok, v.errors.joinToString { it.text() })
        assertEquals(1, v.adds); assertFalse(v.needsWall, "free time is a break, so taking it is not a loosening")
        assertEquals("cycle", v.work.single().key.substringBefore(':')); assertEquals(STUDY_CYCLE_STANDARD_HOURS, v.work.single().hours)
        val before = (v.before as BufferState.Value).result.hours; val after = (v.after as BufferState.Value).result.hours
        assertTrue(after <= before, "adding a lecture adds a study cycle, so the Buffer cannot rise: $before to $after")
        assertTrue(v.summaryLines().last().startsWith("Buffer with this file: ") && v.summaryLines().any { it.startsWith("Buffer now: ") })
    }

    @Test fun `IM5 the preview changes nothing`() {
        val before = slots.toList()
        verify(file(row("event", "2026-11-03", start = "16:30", end = "17:30", title = "x", type = "lecture")))
        assertEquals(before, slots)
    }

    @Test fun `IM6 cancelling a lecture needs the wall and says which one`() {
        val v = verify(file(row("event", "2026-11-02", start = "09:00", end = "10:00", title = "Randomness lecture", type = "lecture", action = "cancel")))
        assertTrue(v.ok, v.errors.joinToString { it.text() })
        assertTrue(v.needsWall); assertTrue(v.loosening.single().startsWith("Cancels Randomness"))
        assertEquals(1, v.cancels); assertTrue(v.overlay.single().slot.cancelled)
    }

    @Test fun `D5 IM6 cancelling a lecture does not remove its study block, so the study cycle is still due`() {
        val v = verify(file(row("event", "2026-11-02", start = "09:00", end = "10:00", title = "Randomness lecture", type = "lecture", action = "cancel")))
        val merged = slots + v.overlay.map { it.slot }
        val cycles = studyCycles(merged, at(LocalDate.of(2026, 10, 30), LocalTime.of(12, 0)), 14).map { it.title }
        assertTrue(cycles.any { "Randomness" in it }, cycles.toString())
    }

    @Test fun `IM6 moving a lecture is one file, cancels and adds, and needs the wall once`() {
        val v = verify(file(
            row("event", "2026-11-02", start = "09:00", end = "10:00", title = "Randomness lecture", type = "lecture", action = "cancel"),
            row("event", "2026-11-02", start = "10:00", end = "10:10", title = "Walk", type = "travel", action = "cancel"),
            row("event", "2026-11-02", start = "09:00", end = "10:30", title = "Randomness lecture", type = "lecture", study = "none"),
            row("event", "2026-11-02", start = "10:30", end = "10:40", title = "Walk", type = "travel"),
        ))
        assertTrue(v.ok, v.errors.joinToString { it.text() }); assertTrue(v.needsWall)
        val day = slotsFor(slots + v.overlay.map { it.slot }, LocalDate.of(2026, 11, 2))
        assertEquals(listOf(540 to 630), day.filter { it.slot.type == "lecture" && it.slot.title.startsWith("Random") }.map { it.slot.startMin to it.slot.endMin })
        assertTrue(v.work.isEmpty(), "study none: the study block already exists")
        assertTrue(v.loosening.any { "takes time from Notes and problems" in it }, v.loosening.toString())
    }

    @Test fun `IM6 an event on top of a study block needs the wall and cuts that block`() {
        val v = verify(file(row("event", "2026-11-02", start = "11:00", end = "11:30", title = "Extra tutorial", type = "tutorial", study = "none")))
        assertTrue(v.ok, v.errors.joinToString { it.text() }); assertTrue(v.needsWall)
        assertTrue(v.loosening.single().contains("Notes and problems: Randomness"))
    }

    @Test fun `IM6 an event on top of sleep, a meal or a hard commitment is an error that names it`() {
        val e = verify(file(row("event", "2026-11-02", start = "12:30", end = "13:30", title = "x", type = "errand"))).errors.first()
        assertTrue("Lunch" in e.message || "Campus League" in e.message, e.message); assertEquals("start", e.column)
    }

    @Test fun `IM6 cancelling something that is not there is an error`() {
        val e = verify(file(row("event", "2026-11-02", start = "09:05", end = "10:00", title = "x", type = "lecture", action = "cancel"))).errors.single()
        assertTrue("No lecture starts at 09:05" in e.message, e.message)
    }

    @Test fun `IM6 a file cannot cancel an event another file added`() {
        val first = verify(file(row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Crypto", type = "lecture", study = "none")))
        val merged = slots + first.overlay.map { it.slot }
        val e = verify(file(row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Crypto", type = "lecture", action = "cancel")), pattern = merged).errors.single()
        assertTrue("Remove that import" in e.message, e.message)
    }

    @Test fun `IM6 an event already in the weekly pattern is skipped, not doubled`() {
        val v = verify(file(row("event", "2026-11-02", start = "09:00", end = "10:00", title = "Randomness", type = "lecture")))
        assertTrue(v.ok); assertEquals(0, v.adds); assertTrue(v.warnings.any { "already in your weekly pattern" in it.message })
    }

    @Test fun `IM7 a deadline becomes work the way quick-add does and lowers the Buffer`() {
        val v = verify(file(row("deadline", "2026-11-06", start = "12:00", title = "CS Project report", heaviness = "8")))
        assertEquals(quickAddHours(8), v.work.single().hours); assertFalse(v.needsWall, "adding work only tightens")
        assertTrue((v.after as BufferState.Value).result.hours <= (v.before as BufferState.Value).result.hours, "adding work cannot raise the Buffer")
    }

    @Test fun `IM7 a deadline that has already passed is skipped`() {
        val v = verify(file(row("deadline", "2026-10-26", start = "09:00", title = "late")))
        assertTrue(v.work.isEmpty()); assertTrue(v.warnings.any { "passed" in it.message })
    }

    @Test fun `IM8 rows already imported change nothing and are counted`() {
        val f = file(row("deadline", "2026-11-06", start = "12:00", title = "Report"), row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Crypto", type = "lecture"))
        val first = verify(f)
        val known = first.overlay.map { it.rowHash }.toSet() + first.work.map { it.key.substringAfter(':') }.toSet()
        val again = verify(f, known = known)
        assertEquals(2, again.alreadyImported); assertTrue(again.changesNothing)
    }

    @Test fun `PW1 periods are carried through with their fingerprints`() {
        val v = verify(file(row("period", "2026-11-02", "2026-11-06", type = "assignment_week")))
        assertEquals(1, v.periods.size); assertEquals(1, v.periodHashes.size); assertFalse(v.needsWall, "a period only orders work, it loosens nothing")
    }

    @Test fun `IM6 a file that only adds never needs the wall`() {
        val v = verify(file(
            row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Crypto", type = "lecture"),
            row("deadline", "2026-11-06", start = "12:00", title = "Report"),
            row("period", "2026-11-02", "2026-11-06", type = "catch_up_week"),
        ))
        assertTrue(v.ok); assertFalse(v.needsWall)
    }

    @Test fun `IM5 an error means the preview shows no change to the Buffer`() {
        val v = verify(file(row("deadline", "bad", start = "12:00", title = "x")))
        assertFalse(v.ok); assertEquals(v.before, v.after)
    }

    @Test fun `IM11 a mytutor event is a hard alarm and a lecture is not (D4)`() {
        val v = verify(file(
            row("event", "2026-11-03", start = "16:30", end = "17:30", title = "Crypto", type = "lecture", study = "none"),
            row("event", "2026-11-04", start = "20:30", end = "21:00", title = "MyTutor", type = "mytutor"),
        ))
        assertEquals(listOf(false, true), v.overlay.map { it.slot.hardAlarm })
    }
}

class DatedSlotTest {
    private val monday = LocalDate.of(2026, 11, 2)
    @Test fun `IM1 a pattern with no dated slots behaves exactly as before`() {
        for (d in 0L..13L) {
            val date = monday.plusDays(d)
            assertEquals(WeekB.slots.filter { it.day == date.dayOfWeek }.sortedBy { it.startMin }, slotsFor(WeekB.slots, date).map { it.slot })
        }
    }

    @Test fun `IM1 a dated slot applies on its date only`() {
        val extra = PatternSlot(monday.dayOfWeek, 1000, 1030, "Extra", "errand", "", false, "", date = monday)
        assertTrue(slotsFor(WeekB.slots + extra, monday).any { it.slot.title == "Extra" })
        assertFalse(slotsFor(WeekB.slots + extra, monday.plusDays(7)).any { it.slot.title == "Extra" })
    }

    @Test fun `IM6 a dated cancel removes the weekly slot on that date only`() {
        val lec = WeekB.slots.first { it.day == monday.dayOfWeek && it.type == "lecture" }
        val cancel = lec.copy(date = monday, cancelled = true)
        assertFalse(slotsFor(WeekB.slots + cancel, monday).any { it.slot == lec })
        assertTrue(slotsFor(WeekB.slots + cancel, monday.plusDays(7)).any { it.slot == lec })
    }

    @Test fun `IM6 an event cuts free time, keeps the pieces over 15 minutes and drops the rest`() {
        val tue = LocalDate.of(2026, 11, 3)                      // free 16:10 to 18:15
        val ev = PatternSlot(tue.dayOfWeek, 990, 1050, "Event", "lecture", "", false, "", date = tue)
        val free = slotsFor(WeekB.slots + ev, tue).filter { it.slot.type == "free" && it.slot.startMin in 970..1095 }
        assertEquals(listOf(970 to 990, 1050 to 1095), free.map { it.slot.startMin to it.slot.endMin })
        val tiny = PatternSlot(tue.dayOfWeek, 980, 1090, "Event", "lecture", "", false, "", date = tue)
        assertTrue(slotsFor(WeekB.slots + tiny, tue).none { it.slot.type == "free" && it.slot.startMin in 970..1095 }, "10 and 5 minute pieces are not kept")
    }

    @Test fun `X4 a dated slot on the day the clocks go back is placed on wall-clock time`() {
        val day = LocalDate.of(2026, 10, 25)
        val ev = PatternSlot(day.dayOfWeek, 180, 240, "Early", "errand", "", false, "", date = day)
        val s = slotsFor(listOf(ev), day).single()
        assertEquals(LocalTime.of(3, 0), s.start.toLocalTime()); assertEquals(java.time.Duration.ofHours(1), java.time.Duration.between(s.start, s.end))
    }
}

class FormatDocTest {
    private val doc = java.io.File("../docs/TIMETABLE_CSV.md").readText().replace("\r\n", "\n")

    @Test fun `IM3 the document an AI is given holds exactly the text the app copies`() {
        assertTrue(TIMETABLE_AI_PROMPT in doc, "docs/TIMETABLE_CSV.md is out of date: run python tools/gen-timetable-doc.py")
    }

    @Test fun `IM3 the prompt and the document name every column and every event type`() {
        for (c in CSV_COLUMNS) { assertTrue(c in TIMETABLE_AI_PROMPT, c); assertTrue("| $c |" in doc, c) }
        for (t in EVENT_TYPES) assertTrue(t in TIMETABLE_AI_PROMPT, t)
        assertTrue("assignment_week" in TIMETABLE_AI_PROMPT && "catch_up_week" in TIMETABLE_AI_PROMPT)
    }

    @Test fun `IM3 the example in the document is a file the reader accepts`() {
        assertTrue(TIMETABLE_EXAMPLE_CSV in doc)
        val p = parseTimetableCsv(TIMETABLE_EXAMPLE_CSV, LocalDate.of(2026, 10, 26))
        assertTrue(p.ok, p.errors.joinToString { it.text() }); assertEquals(5, p.rows.size)
    }

    @Test fun `IM3 the prompt tells the AI the rules the reader enforces`() {
        for (phrase in listOf("Dates are year-month-day", "24-hour", "cancel", "1 to 14 days", "Do not guess")) assertTrue(phrase in TIMETABLE_AI_PROMPT, phrase)
        assertEquals(TIMETABLE_FORMAT_VERSION, 1)
    }
}
