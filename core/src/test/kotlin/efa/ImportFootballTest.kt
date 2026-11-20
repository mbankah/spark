package efa

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

private const val HDR = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes,override"

/**
 * FB1: the app places 3 hours of personal football a week in free time. A file can cancel it, by its title, and the cancel stands (the planner does not put it back).
 * RE1: a file that cancels a block itself must not fail in Replace mode only because Replace took the block first.
 */
class ImportFootballTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 11, 20, 12, 12, 0, 0, zone)
    private val sat = LocalDate.of(2026, 11, 21); private val tue = LocalDate.of(2026, 11, 24); private val sat2 = LocalDate.of(2026, 11, 28)
    private fun fb(d: LocalDate, a: Int, b: Int) = PatternSlot(d.dayOfWeek, a, b, FOOTBALL_TITLE, "match", "", false, FOOTBALL_NOTE, date = d)
    private val football = listOf(fb(sat, 975, 1095), fb(tue, 970, 1090), fb(sat2, 975, 1035))
    private val base = WeekB.slots + football
    private fun parse(vararg rows: String) = parseTimetableCsv((listOf(HDR) + rows).joinToString("\n"), now.toLocalDate())
    private fun verify(p: ParsedImport, mode: ImportMode = ImportMode.ADD, slots: List<PatternSlot> = base) = verifyImportIn(mode, p, slots, emptySet(), emptyList(), now, zone, reflow = true)
    private fun footballOn(slots: List<PatternSlot>, d: LocalDate) = slotsFor(slots, d, zone).filter { isFootball(it.slot) }

    @Test fun FB1_a_cancel_row_for_the_personal_football_cancels_it_at_its_exact_time() {
        val v = verify(parse("event,2026-11-21,,16:15,18:15,Personal football,match,,,none,cancel,Not this week.,yes"))
        assertTrue(v.ok, v.errors.toString()); assertTrue(v.needsWall, "cancelling a commitment is a loosening")
        assertTrue(footballOn(base, sat).isNotEmpty()); assertTrue(footballOn(base + v.overlay.map { it.slot }, sat).isEmpty(), "the football is gone from that day")
    }

    @Test fun FB1_it_is_found_by_its_title_wherever_the_app_put_it_that_day() {
        val v = verify(parse("event,2026-11-24,,09:00,10:00,Personal football,match,,,none,cancel,Not this week.,yes"))
        assertTrue(v.ok, v.errors.toString())
        assertTrue(footballOn(base + v.overlay.map { it.slot }, tue).isEmpty())
    }

    @Test fun FB1_only_personal_football_can_be_cancelled_this_way_and_a_missing_one_is_said() {
        val other = verify(parse("event,2026-11-21,,12:00,13:00,Something else,match,,,none,cancel,x,yes"))
        assertFalse(other.ok); assertTrue(other.errors.single().message.contains("No match starts"), other.errors.toString())
        val none = verify(parse("event,2026-11-22,,16:15,18:15,Personal football,match,,,none,cancel,x,yes"))
        assertFalse(none.ok, "there is no football on Sunday")
    }

    @Test fun FB1_without_the_override_word_the_football_is_protected_and_the_error_says_how() {
        val v = verify(parse("event,2026-11-21,,16:15,18:15,Personal football,match,,,none,cancel,Not this week.,"))
        assertFalse(v.ok); assertTrue(v.errors.single().message.contains("override"), v.errors.toString())
        val byTitle = verify(parse("event,2026-11-24,,09:00,10:00,Personal football,match,,,none,cancel,Not this week.,no"))
        assertFalse(byTitle.ok, "the title alone is not enough, it needs the override")
        val ok = verify(parse("event,2026-11-21,,16:15,18:15,Personal football,match,,,none,cancel,Not this week.,yes"))
        assertTrue(ok.ok && ok.needsWall && ok.overrides.single().contains("Personal football"), ok.errors.toString())
    }

    @Test fun FB1_the_cancel_stands_the_planner_does_not_place_the_week_again() {
        val monday = LocalDate.of(2026, 11, 23); val from = ZonedDateTime.of(2026, 11, 23, 0, 0, 0, 0, zone)
        assertTrue(placeFootball(WeekB.slots, monday, from).isNotEmpty(), "with no football in the week the planner places some")
        // three hours of football cancelled by a file in that week: the planner counts them as dealt with and adds nothing
        val gone = listOf(
            fb(LocalDate.of(2026, 11, 24), 970, 1090).copy(cancelled = true, notes = "imported cancel"),
            fb(LocalDate.of(2026, 11, 26), 900, 960).copy(cancelled = true, notes = "imported cancel"),
        )
        assertTrue(placeFootball(WeekB.slots + gone, monday, from).isEmpty(), "a cancelled week is not planned again")
    }

    @Test fun FB1_a_dated_cancel_does_not_remove_other_added_blocks() {
        val added = PatternSlot(sat.dayOfWeek, 975, 1095, "Film break", "match", "", false, "added", date = sat)
        val cancel = PatternSlot(sat.dayOfWeek, 975, 1095, "Film break", "match", "", false, "x", date = sat, cancelled = true)
        assertEquals(1, slotsFor(WeekB.slots + added + cancel, sat, zone).count { it.slot.title == "Film break" }, "only the app's personal football is cancelled this way")
    }

    @Test fun RE1_override_rows_for_work_blocks_work_in_replace_mode_too() {
        val rows = arrayOf(
            "event,2026-11-22,,19:45,20:15,Errand,errand,,,none,add,,",
            "event,2026-11-22,,15:15,16:15,Sunday batch cook,planning,,,none,cancel,Not this week.,yes",
        )
        val add = verify(parse(*rows), ImportMode.ADD)
        assertTrue(add.ok, add.errors.toString())
        val rep = verify(parse(*rows), ImportMode.REPLACE)
        assertTrue(rep.ok, "Replace used to take the batch cook first and the override row then found nothing: ${rep.errors}")
        assertTrue(rep.needsWall)
        assertEquals(1, rep.overlay.count { it.slot.notes == "imported override" && it.slot.title == "Sunday batch cook" })
        assertEquals(0, rep.overlay.count { it.slot.notes == "imported replace" && it.slot.title == "Sunday batch cook" }, "it is not taken twice")
    }

    @Test fun RE1_a_replace_still_takes_the_blocks_the_file_does_not_name() {
        val rep = verify(parse("event,2026-11-22,,19:45,20:15,Errand,errand,,,none,add,,"), ImportMode.REPLACE)
        assertTrue(rep.ok, rep.errors.toString())
        assertTrue(rep.overlay.any { it.slot.notes == "imported replace" && it.slot.title == "Sunday batch cook" }, "an unnamed work block is still replaced")
    }

    @Test fun IM13_the_words_above_the_wall_are_short_however_many_things_loosen() {
        val many = (1..65).map { "Cancels Block $it on Fri 20 Nov" }
        val words = loosenWords(many)
        assertTrue(words.length < 200, words.length.toString()); assertTrue(words.startsWith("65 things loosen: Cancels Block 1"), words); assertTrue(words.contains("and 62 more"), words)
        assertEquals("1 thing loosen: Cancels X.", loosenWords(listOf("Cancels X")).replace("loosens", "loosen")); assertEquals("", loosenWords(emptyList()))
    }
}
