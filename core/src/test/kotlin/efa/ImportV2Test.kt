package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

private const val H2 = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"
private fun r2(kind: String, date: String = "", start: String = "", end: String = "", title: String = "", type: String = "") =
    listOf(kind, date, "", start, end, title, type, "", "", "", "", "").joinToString(",")

/** IX1 to IX9, CE5, CE9: one path for a file, merge or replace, chosen in the app, with its cost. */
class ImportV2Test {
    private val zone = ZoneId.of("Europe/London")
    private val mon = LocalDate.of(2026, 11, 23)
    private val now = ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, zone)
    private fun p(d: DayOfWeek, s: Int, e: Int, title: String, type: String, hard: Boolean = false) = PatternSlot(d, s, e, title, type, "", hard, "")
    private fun day(d: DayOfWeek) = listOf(
        p(d, 450, 470, "Wake, shower", "routine"), p(d, 470, 500, "Bible study", "routine"),
        p(d, 540, 600, "Lecture", "lecture"), p(d, 615, 735, "Notes and problems: Randomness", "study-standard"),
        p(d, 735, 760, "Lunch", "meal"), p(d, 780, 900, "CS Project", "code"), p(d, 900, 960, "Free time", "free"),
        p(d, 1080, 1110, "Dinner", "meal"), p(d, 1125, 1260, "Evening work", "study"), p(d, 1260, 1300, "Free time", "free"),
        p(d, 1190, 1200, "Football", "training"), p(d, 1340, 1380, "Shutdown", "sleep"),
    )
    private val base = day(DayOfWeek.MONDAY) + day(DayOfWeek.TUESDAY)
    private fun parse(vararg rows: String) = parseTimetableCsv((listOf(H2) + rows).joinToString("\n"), now.toLocalDate())
    private val dentist = r2("event", "2026-11-23", "14:00", "15:00", "Dentist", "errand")

    @Test fun IX2_there_are_two_modes_with_their_plain_names() {
        assertEquals(listOf("Add to my plan", "Replace my plan for these days"), ImportMode.entries.map { it.label })
    }

    @Test fun IX1_add_is_the_existing_path_and_changes_nothing_about_it() {
        val parsed = parse(dentist)
        val a = verifyImportIn(ImportMode.ADD, parsed, base, emptySet(), emptyList(), now, zone)
        val b = verifyImport(parsed, base, emptySet(), emptyList(), now, zone)
        assertEquals(b, a)
    }

    @Test fun IX4_replace_cancels_the_movable_blocks_on_the_days_of_the_file_and_keeps_the_rest() {
        val v = verifyImportIn(ImportMode.REPLACE, parse(dentist), base, emptySet(), emptyList(), now, zone)
        assertTrue(v.ok, v.errors.toString())
        val after = base + v.overlay.map { it.slot }
        val monday = slotsFor(after, mon, zone).map { it.slot.title to it.slot.type }
        assertTrue(monday.none { it.first == "CS Project" }, "project block is replaced")
        assertTrue(monday.none { it.first == "Evening work" }, "evening work is replaced")
        for (kept in listOf("Wake, shower" to "routine", "Bible study" to "routine", "Lecture" to "lecture", "Notes and problems: Randomness" to "study-standard", "Lunch" to "meal", "Dinner" to "meal", "Football" to "training", "Shutdown" to "sleep", "Dentist" to "errand"))
            assertTrue(kept in monday, "$kept stays or is added")
        // the next day is not in the file, so it is untouched
        assertEquals(slotsFor(base, mon.plusDays(1), zone).map { it.slot.title }, slotsFor(after, mon.plusDays(1), zone).map { it.slot.title })
    }

    @Test fun IX5_replace_that_takes_work_is_a_loosening_and_says_how_many_hours() {
        val v = verifyImportIn(ImportMode.REPLACE, parse(dentist), base, emptySet(), emptyList(), now, zone)
        assertTrue(v.needsWall)
        assertTrue(v.loosening.any { it.startsWith("Replaces 5.3h of work blocks") || it.contains("h of work blocks") }, v.loosening.toString())
        assertEquals(ChangeClass.LOOSEN, importChangeClass(v, ImportMode.REPLACE))
    }

    @Test fun IX5_replace_that_only_takes_free_time_is_a_move_and_needs_no_wall() {
        val onlyFree = day(DayOfWeek.MONDAY).filter { it.type != "code" && it.type != "study" }
        val v = verifyImportIn(ImportMode.REPLACE, parse(r2("event", "2026-11-23", "16:00", "16:30", "Walk", "errand")), onlyFree, emptySet(), emptyList(), now, zone)
        assertFalse(v.needsWall, v.loosening.toString())
        assertTrue(importChangeClass(v, ImportMode.REPLACE) in setOf(ChangeClass.MOVE, ChangeClass.ADD), "never a loosening: ${v.loosening}")
    }

    @Test fun IX4_protected_free_time_hard_alarms_and_days_off_survive_a_replace() {
        val hard = base + p(DayOfWeek.MONDAY, 1200, 1230, "Exam prep call", "free", hard = true)
        val v = verifyImportIn(ImportMode.REPLACE, parse(dentist), hard, emptySet(), emptyList(), now, zone)
        assertTrue(v.overlay.none { it.slot.title == "Exam prep call" })
        val off = base + PatternSlot(DayOfWeek.MONDAY, 0, 1440, "Day off", DAY_OFF_TYPE, "", false, "", date = mon)
        val v2 = verifyImportIn(ImportMode.REPLACE, parse(dentist), off, emptySet(), emptyList(), now, zone)
        assertTrue(v2.overlay.none { it.slot.cancelled && it.rowHash.startsWith("span|") }, "a day off keeps what it keeps")
    }

    @Test fun IX7_importing_the_same_replace_twice_changes_nothing_the_second_time() {
        val first = verifyImportIn(ImportMode.REPLACE, parse(dentist), base, emptySet(), emptyList(), now, zone)
        val known = first.overlay.map { it.rowHash }.toSet()
        val second = verifyImportIn(ImportMode.REPLACE, parse(dentist), base, known, emptyList(), now, zone)
        assertTrue(second.changesNothing || second.overlay.isEmpty(), "nothing is left to apply: ${second.overlay.size}")
    }

    @Test fun IX8_the_preview_shows_the_buffer_before_the_plan_as_it_was_and_after_with_the_replacement() {
        val work = listOf(DueWork("GT assignment", 6.0, 8, ZonedDateTime.of(2026, 11, 27, 17, 0, 0, 0, zone)))
        val v = verifyImportIn(ImportMode.REPLACE, parse(dentist), base, emptySet(), work, now, zone)
        val b = (v.before as BufferState.Value).result.availableHours; val a = (v.after as BufferState.Value).result.availableHours
        assertTrue(a < b, "taking work blocks lowers the hours available: $b to $a")
    }

    @Test fun IX5_a_file_that_only_adds_is_an_add_and_one_that_changes_nothing_is_reality_class() {
        val add = verifyImportIn(ImportMode.ADD, parse(r2("event", "2026-11-23", "16:00", "16:30", "Walk", "errand")), base, emptySet(), emptyList(), now, zone)
        assertEquals(ChangeClass.ADD, importChangeClass(add, ImportMode.ADD))
        val none = verifyImportIn(ImportMode.ADD, parse(), base, emptySet(), emptyList(), now, zone)
        assertEquals(ChangeClass.REALITY, importChangeClass(none, ImportMode.ADD))
    }

    // ---- the economy for imports, end to end ----
    private val installed = now.toInstant().minus(Duration.ofDays(90))
    private fun imp(daysAgo: Long, cls: ChangeClass = ChangeClass.ADD) = ChangeEntry(cls, now.toInstant().minus(Duration.ofHours(daysAgo * 24)).let { if (it.isBefore(weekStart(now.toInstant()))) weekStart(now.toInstant()).plusSeconds(60) else it }, import = true)

    @Test fun CE5_a_replace_import_is_a_loosening_and_costs_the_wall_and_counts_against_the_week() {
        val v = verifyImportIn(ImportMode.REPLACE, parse(dentist), base, emptySet(), emptyList(), now, zone)
        val d = decideChange(emptyList(), importChangeClass(v, ImportMode.REPLACE), true, now.toInstant(), installed)
        assertTrue(d is ChangeDecision.Wall && d.length == 75, d.toString())
    }

    @Test fun CE9_the_meter_says_the_counts_in_plain_words() {
        val m = economyMeter(listOf(imp(0), ChangeEntry(ChangeClass.MOVE, now.toInstant().minusSeconds(60)), ChangeEntry(ChangeClass.LOOSEN, now.toInstant().minusSeconds(120))), now.toInstant())
        assertTrue(m.contains("imports 1 of 2") && m.contains("moves 1 of 5") && m.contains("loosening 1 of 3"), m)
    }

    @Test fun IX3_add_refuses_an_overlap_with_a_commitment_with_a_reason_and_calls_taking_study_time_a_loosening() {
        val onLecture = verifyImportIn(ImportMode.ADD, parse(r2("event", "2026-11-23", "09:30", "10:30", "Dentist", "errand")), base, emptySet(), emptyList(), now, zone)
        assertFalse(onLecture.ok); assertTrue(onLecture.errors.any { it.text().contains("Overlaps") }, onLecture.errors.toString())
        val onStudy = verifyImportIn(ImportMode.ADD, parse(r2("event", "2026-11-23", "19:00", "19:30", "Call", "errand")), base, emptySet(), emptyList(), now, zone)
        assertTrue(onStudy.ok); assertTrue(onStudy.needsWall && onStudy.loosening.any { it.contains("takes time from") }, onStudy.loosening.toString())
    }
}
