package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

private const val HD = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"
private fun row(kind: String, date: String = "", end: String = "", start: String = "", to: String = "", title: String = "", type: String = "", notes: String = "") =
    listOf(kind, date, end, start, to, title, type, "", "", "", "", if (',' in notes) "\"$notes\"" else notes).joinToString(",")

/** IX1, IX6, IX9: a file can carry modifiers, nights and keeps, and its events go through the same engine as the app's. */
class ImportV2FileTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, zone)
    private val mon = LocalDate.of(2026, 11, 23)
    private fun p(d: DayOfWeek, s: Int, e: Int, title: String, type: String) = PatternSlot(d, s, e, title, type, "", false, "")
    private val base = DayOfWeek.MONDAY.let { d ->
        listOf(
            p(d, 450, 470, "Wake, shower", "routine"), p(d, 470, 500, "Bible study", "routine"), p(d, 540, 600, "Lecture", "lecture"),
            p(d, 735, 760, "Lunch", "meal"), p(d, 780, 900, "CS Project", "code"), p(d, 900, 960, "Free time", "free"),
            p(d, 1080, 1110, "Dinner", "meal"), p(d, 1125, 1260, "Evening work", "study"), p(d, 1260, 1300, "Free time", "free"), p(d, 1340, 1380, "Shutdown", "sleep"),
        )
    }
    private fun parse(vararg rows: String) = parseTimetableCsv((listOf(HD) + rows).joinToString("\n"), now.toLocalDate())

    @Test fun IX6_a_modifier_row_is_read_with_its_preset_dates_and_settings() {
        val ps = parse(row("modifier", "2026-11-23", "2026-11-25", type = "lecture_free"))
        assertTrue(ps.ok, ps.errors.toString())
        val m = ps.rows.single() as ModifierImport
        assertEquals(Preset.LECTURE_FREE, m.instance.preset); assertEquals(mon, m.instance.start); assertEquals(mon.plusDays(2), m.instance.end)
    }

    @Test fun IX6_a_modifier_that_needs_items_or_does_not_exist_names_the_line_and_column() {
        val a = parse(row("modifier", "2026-11-23", "2026-11-25", type = "assignment")); assertFalse(a.ok)
        assertTrue(a.errors.any { it.line == 2 && it.column == "type" && "planned in the app" in it.message }, a.errors.toString())
        val b = parse(row("modifier", "2026-11-23", "2026-11-25", type = "holiday")); assertTrue(b.errors.any { it.column == "type" })
        val c = parse(row("modifier", "2026-11-23", "2026-11-25", type = "lecture_free", notes = "nonsense=1")); assertTrue(c.errors.any { it.column == "notes" && "not a setting" in it.message }, c.errors.toString())
        val d = parse(row("modifier", "2026-11-23", "2026-11-25", type = "lecture_free", notes = "broken")); assertTrue(d.errors.any { it.column == "notes" && "key=value" in it.message })
    }

    @Test fun IX6_an_anchor_row_is_a_night_and_needs_eight_hours() {
        val ok = parse(row("anchor", "2026-11-23", start = "02:30", to = "10:30")); assertTrue(ok.ok, ok.errors.toString())
        val n = (ok.rows.single() as ModifierImport).instance
        assertEquals(Preset.NIGHT, n.preset); assertEquals("02:30", n.settings["lights_out"]); assertEquals("10:30", n.settings["wake"])
        val short = parse(row("anchor", "2026-11-23", start = "03:00", to = "09:00"))
        assertFalse(verifyImport(short, base, emptySet(), emptyList(), now, zone).ok, "six hours is not a night: the check against the plan refuses it")
    }

    @Test fun IX6_a_keep_row_needs_a_time_that_ends_after_it_starts() {
        assertTrue(parse(row("keep", "2026-11-23", start = "15:00", to = "16:00", title = "Call mum")).ok)
        assertTrue(parse(row("keep", "2026-11-23", start = "16:00", to = "15:00")).errors.any { it.column == "end" })
    }

    @Test fun IX8_the_same_modifier_twice_changes_nothing_the_second_time() {
        val ps = parse(row("modifier", "2026-11-23", "2026-11-25", type = "lecture_free"))
        val first = verifyImport(ps, base, emptySet(), emptyList(), now, zone)
        assertTrue(first.ok, first.errors.toString()); assertEquals(1, first.modifiers.size)
        val again = verifyImport(ps, base, setOf(first.modifiers.single().hash), emptyList(), now, zone)
        assertTrue(again.modifiers.isEmpty()); assertEquals(1, again.alreadyImported)
    }

    @Test fun IX6_a_file_modifier_is_checked_like_one_in_the_app() {
        val ps = parse(row("modifier", "2026-11-23", "2026-11-25", type = "lecture_free"), row("modifier", "2026-11-24", "2026-11-26", type = "lecture_free"))
        val v = verifyImport(ps, base, emptySet(), emptyList(), now, zone)
        assertFalse(v.ok, "two of the same preset over the same days clash"); assertTrue(v.errors.any { it.line == 3 }, v.errors.toString())
    }

    @Test fun IX6_a_keep_row_protects_its_time_from_a_replace() {
        val ps = parse(row("event", "2026-11-23", start = "10:00", to = "11:00", title = "Dentist", type = "errand"), row("keep", "2026-11-23", start = "13:00", to = "14:00"))
        val v = verifyImportIn(ImportMode.REPLACE, ps, base, emptySet(), emptyList(), now, zone)
        assertTrue(v.ok, v.errors.toString())
        val after = slotsFor(base + v.overlay.map { it.slot }, mon, zone).map { it.slot.title }
        assertTrue("CS Project" in after, "the project block under the kept hour stays")
        assertTrue("Evening work" !in after)
    }

    @Test fun IX1_an_event_over_a_meal_is_refused_without_reflow_and_laid_out_around_with_it() {
        val ps = parse(row("event", "2026-11-23", start = "12:15", to = "13:15", title = "Dentist", type = "errand"))
        assertFalse(verifyImport(ps, base, emptySet(), emptyList(), now, zone).ok, "without the engine a clash with a meal is an error")
        val v = verifyImport(ps, base, emptySet(), emptyList(), now, zone, reflow = true)
        assertTrue(v.ok, v.errors.toString())
        val r = reflowEvents(v, ps, base, emptySet(), now, zone)
        val after = slotsFor(base + r.overlay.map { it.slot }, mon, zone)
        assertTrue(after.any { it.slot.title == "Dentist" }, "the event stands")
        val lunch = after.firstOrNull { it.slot.title.startsWith("Lunch") }
        assertNotNull(lunch, "lunch is still in the day"); assertTrue(lunch.slot.startMin >= 795 || lunch.slot.endMin <= 735, "and it has moved out of the way")
    }

    @Test fun IX1_the_engine_never_moves_a_lecture_or_the_shutdown_and_says_when_it_cannot_fit() {
        val ps = parse(row("event", "2026-11-23", start = "09:30", to = "10:30", title = "Clash", type = "errand"))
        val v = verifyImport(ps, base, emptySet(), emptyList(), now, zone, reflow = true)
        assertFalse(v.ok, "an event over a lecture is still an error: a file cannot move a lecture")
    }
}
