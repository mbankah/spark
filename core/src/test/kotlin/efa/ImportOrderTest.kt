package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

private const val HO = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"

/** IX10, IX11: a file's rows depend on each other (a night after the lectures it would have hidden are gone), so they are read in the order given. */
class ImportOrderTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 11, 19, 17, 50, 0, 0, zone)
    private val base = WeekB.slots
    private val small = DayOfWeek.MONDAY.let { d -> listOf(
        PatternSlot(d, 450, 470, "Wake, shower", "routine", "", false, ""), PatternSlot(d, 470, 500, "Bible study", "routine", "", false, ""), PatternSlot(d, 500, 515, "Breakfast, dress, bag", "routine", "", false, ""),
        PatternSlot(d, 540, 600, "Algebra lecture", "lecture", "", false, ""), PatternSlot(d, 720, 750, "Lunch", "meal", "", false, ""), PatternSlot(d, 780, 900, "CS Project", "code", "", false, ""),
        PatternSlot(d, 1080, 1110, "Dinner", "meal", "", false, ""), PatternSlot(d, 1335, 1380, "Shutdown", "sleep", "", false, ""),
        PatternSlot(DayOfWeek.SUNDAY, 450, 470, "Wake, shower", "routine", "", false, ""), PatternSlot(DayOfWeek.SUNDAY, 1335, 1380, "Shutdown", "sleep", "", false, "")) }
    private fun parse(vararg rows: String) = parseTimetableCsv((listOf(HO) + rows).joinToString("\n"), now.toLocalDate())
    private fun verify(p: ParsedImport, pattern: List<PatternSlot> = base) = verifyImport(p, pattern, emptySet(), emptyList(), now, zone, reflow = true)

    private val lectureFree = "modifier,2026-11-23,2026-11-23,,,No lectures,lecture_free,,,,,work_h_max=10;cycles_per_day=0"
    private val night = "anchor,2026-11-22,,01:00,09:20,Sleep reset,,,,,,"

    @Test fun IX10_a_night_that_waits_for_the_lectures_to_go_is_accepted_when_the_lectures_come_first_and_refused_when_they_do_not() {
        val ok = verify(parse(lectureFree, night), small)
        assertTrue(ok.ok, ok.errors.toString()); assertEquals(2, ok.modifiers.size)
        val wrongWay = verify(parse(night, lectureFree), small)
        assertFalse(wrongWay.ok, "the night is checked before the lectures are gone, so it is refused")
        assertTrue(wrongWay.errors.any { "lecture starts at 09:00" in it.message }, wrongWay.errors.toString())
    }

    @Test fun IX10_the_work_a_file_adds_counts_as_backlog_for_the_rows_after_it() {
        val p = parse("deadline,2026-11-27,,22:00,,Game Theory: notes and problems,,,8,,,", "modifier,2026-11-23,2026-11-27,,,Catch-up,catch_up,,,,,slot_h=2.5;slots_floor=1")
        val v = verify(p)
        assertTrue(v.ok, v.errors.toString())
        assertTrue(v.summaryLines().any { it.contains("Catch-up") })
    }

    @Test fun IX11_a_file_still_cannot_cancel_a_study_or_project_block_whatever_order_its_rows_come_in() {
        for (t in listOf("code", "study", "study-standard")) assertFalse(parse("event,2026-11-22,,10:00,11:20,Battl.io,$t,,,none,cancel,").ok, t)
        val v = verify(parse(lectureFree, night, "event,2026-11-22,,10:00,11:20,Battl.io,errand,,,none,cancel,"))
        assertFalse(v.ok, "no errand of that name starts there")
    }
}
