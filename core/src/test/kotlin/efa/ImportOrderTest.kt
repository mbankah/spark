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

    @Test fun IX12_a_row_after_a_night_row_is_checked_against_that_nights_lights_out_and_not_against_23_00() {
        val late = "modifier,2026-11-22,2026-11-22,,,Late study,catch_up,,,,,day_from=21:00;day_to=23:59;slot_h=1;slots_floor=2;slots_stretch=2"
        val p = parse(lectureFree, night, late)
        val v = verify(p, small)
        assertTrue(v.ok, v.errors.toString())
        // the same row, checked the way the file's own verification now does (with the night) and the way it used to (without it)
        var cur = small + v.overlay.map { it.slot }
        val mods = p.rows.filterIsInstance<ModifierImport>()
        for (m in mods.dropLast(1)) cur = applied(cur, previewOf(m.instance, cur, emptyList(), now, zone).compiled)
        val lateRow = mods.last().instance
        val nights = mods.mapNotNull { nightAnchorsOf(it.instance) }.associateBy { it.night }
        val with = previewOf(lateRow, cur, emptyList(), now, zone, nights)
        assertTrue(with.canApply, with.problems.toString())
        assertTrue(slotsFor(applied(cur, with.compiled), java.time.LocalDate.of(2026, 11, 22), zone).any { it.slot.type == "study" && !it.slot.cancelled && it.slot.endMin > 23 * 60 })
        val without = previewOf(lateRow, cur, emptyList(), now, zone)
        assertFalse(without.canApply); assertTrue(without.problems.any { "would be in the night" in it }, without.problems.toString())
    }

    @Test fun LN1_a_late_nights_study_can_run_past_midnight_when_the_row_says_so() {
        val night2 = "anchor,2026-11-22,,02:00,10:20,Sleep reset,,,,,,,"
        val p = parse(lectureFree, night2, "modifier,2026-11-22,2026-11-22,,,Late study,catch_up,,,,,day_from=21:00;day_to=00:30;after_midnight=true;slot_h=1;slots_floor=3;slots_stretch=3;module_cap=4")
        val v = verify(p, small)
        assertTrue(v.ok, v.errors.toString())
        var cur = small + v.overlay.map { it.slot }
        val mods = p.rows.filterIsInstance<ModifierImport>()
        val nights = mods.mapNotNull { nightAnchorsOf(it.instance) }.associateBy { it.night }
        for (m in mods.dropLast(1)) cur = applied(cur, previewOf(m.instance, cur, emptyList(), now, zone, nights).compiled)
        val pv = previewOf(mods.last().instance, cur, emptyList(), now, zone, nights)
        assertTrue(pv.canApply, pv.problems.toString())
        val study = slotsFor(applied(cur, pv.compiled), java.time.LocalDate.of(2026, 11, 22), zone).filter { it.slot.type == "study" && !it.slot.cancelled }
        assertTrue(study.any { it.slot.endMin > 1440 }, "a block runs past midnight: ${study.map { it.slot.startMin to it.slot.endMin }}")
        assertTrue(study.all { it.slot.endMin <= 1440 + 30 }, "and none past the 00:30 asked for")
        val without = verify(parse(lectureFree, night2, "modifier,2026-11-22,2026-11-22,,,Late study,catch_up,,,,,day_from=21:00;day_to=00:30;slot_h=1;slots_floor=3;slots_stretch=3"), small)
        assertFalse(without.ok); assertTrue(without.errors.any { "leaves no room" in it.message }, without.errors.toString())
    }

    @Test fun LN2_protected_free_time_is_used_only_when_the_row_says_so_and_then_it_needs_the_wall() {
        val sat = java.time.LocalDate.of(2026, 11, 21)
        val prot = protectedFree(base, weekStart(sat), zone).filter { it.first == sat }
        assertTrue(prot.isNotEmpty(), "Saturday has protected free time in the pattern")
        fun study(row: String): Pair<List<DatedSlot>, Verification> {
            val p = parse(row)
            val v = verify(p)
            val cur = applied(base, previewOf(p.rows.filterIsInstance<ModifierImport>().single().instance, base, emptyList(), now, zone).compiled)
            return slotsFor(cur, sat, zone).filter { it.slot.type == "study" && !it.slot.cancelled } to v
        }
        fun overlapsProtected(s: List<DatedSlot>) = s.any { b -> prot.any { (_, start) -> val end = slotsFor(base, sat, zone).first { it.slot.startMin == start }.slot.endMin; b.slot.startMin < end && b.slot.endMin > start } }
        val cfg = "day_from=09:00;day_to=21:30;slot_h=1;slots_floor=6;slots_stretch=6;weekend=same"
        val (kept, vk) = study("modifier,2026-11-21,2026-11-21,,,Catch-up,catch_up,,,,,$cfg")
        assertFalse(overlapsProtected(kept)); assertTrue(vk.loosening.none { it.contains("protected free") })
        val (used, vu) = study("modifier,2026-11-21,2026-11-21,,,Catch-up,catch_up,,,,,$cfg;use_free=true")
        assertTrue(overlapsProtected(used)); assertTrue(vu.needsWall); assertTrue(vu.loosening.any { it.contains("Uses protected free time on") }, vu.loosening.toString())
    }

    @Test fun LN3_a_late_wake_keeps_all_three_meals_and_moves_lunch_later_to_keep_its_spacing() {
        val fri = java.time.LocalDate.of(2026, 11, 20)          // lunch is planned 12:10, dinner 19:30
        val friNoLectures = base.filter { it.day != DayOfWeek.FRIDAY || it.type !in setOf("lecture", "tutorial", "training", "travel") }          // a lecture-free Friday, as in the catch-up week
        val late = replanDay(friNoLectures, fri, Disturbance.StartsAt(11 * 60 + 50), emptySet(), zone)
        assertTrue(late.dropped.none { it.startsWith("Breakfast") || it.startsWith("Lunch") || it.startsWith("Cook") }, "no meal is dropped: ${late.dropped}")
        val breakfast = late.slots.first { it.title.startsWith("Breakfast") && !it.cancelled }
        val lunch = late.slots.first { it.title.startsWith("Lunch") && !it.cancelled }
        assertTrue(lunch.startMin >= breakfast.endMin + 60 && lunch.endMin <= 17 * 60, "lunch is not back to back with breakfast and is in the afternoon: ${breakfast.endMin} then ${lunch.startMin}")   // a fixed study block at 15:10 leaves no room for the full spacing, so lunch goes right before it
        val sun = java.time.LocalDate.of(2026, 11, 22)           // a wake at 10:30 on a Sunday is not late
        val normal = replanDay(base, sun, Disturbance.StartsAt(10 * 60 + 30), emptySet(), zone)
        assertTrue(normal.dropped.none { it.startsWith("Breakfast") }, normal.dropped.toString())
    }

    @Test fun IX11_a_file_still_cannot_cancel_a_study_or_project_block_whatever_order_its_rows_come_in() {
        for (t in listOf("code", "study", "study-standard")) assertFalse(parse("event,2026-11-22,,10:00,11:20,Battl.io,$t,,,none,cancel,").ok, t)
        val v = verify(parse(lectureFree, night, "event,2026-11-22,,10:00,11:20,Battl.io,errand,,,none,cancel,"))
        assertFalse(v.ok, "no errand of that name starts there")
    }
}
