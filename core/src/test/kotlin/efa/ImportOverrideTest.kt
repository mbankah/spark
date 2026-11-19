package efa

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

private const val HOV = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes,override"
private const val HNO = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes"

/**
 * OV1: a file may carry an optional override column. A cancel row with yes in it cancels a block a file otherwise cannot touch (a study, code, planning, routine or chore block).
 * It is never quiet: the wall, the named block, work that comes back. Meals, sleep, the wake and the Bible study are never overridden.
 */
class ImportOverrideTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 11, 19, 17, 50, 0, 0, zone)
    private val base = WeekB.slots
    private fun parse(header: String, vararg rows: String) = parseTimetableCsv((listOf(header) + rows).joinToString("\n"), now.toLocalDate())
    private fun verify(p: ParsedImport, pattern: List<PatternSlot> = base) = verifyImport(p, pattern, emptySet(), emptyList(), now, zone, reflow = true)

    private val notes = "event,2026-11-23,,10:10,12:15,Notes and problems: Randomness,study-standard,,,none,cancel,,yes"
    private val lectureFree = "modifier,2026-11-23,2026-11-23,,,No lectures,lecture_free,,,,,work_h_max=10;cycles_per_day=0,"
    private val night = "anchor,2026-11-22,,02:30,10:50,Sleep reset,,,,,,,"

    @Test fun OV1_without_the_word_a_file_still_cannot_touch_a_study_block_and_the_error_says_how() {
        val p = parse(HNO, "event,2026-11-23,,10:10,12:15,Notes and problems: Randomness,study-standard,,,none,cancel,")
        assertFalse(p.ok)
        assertTrue(p.errors.single().message.contains("override"), p.errors.toString())
        assertFalse(parse(HOV, "event,2026-11-23,,10:10,12:15,Notes and problems: Randomness,study-standard,,,none,cancel,,no").ok, "no is not an override")
    }

    @Test fun OV1_OV2_OV3_a_cancel_row_with_yes_cancels_the_block_behind_the_wall_and_the_cycle_comes_back_as_work() {
        val v = verify(parse(HOV, notes))
        assertTrue(v.ok, v.errors.toString())
        assertTrue(v.needsWall, "an override is always a loosening")
        assertTrue(v.loosening.any { it.startsWith("Overrides Notes and problems: Randomness") }, v.loosening.toString())
        assertEquals(1, v.overrides.size); assertTrue(v.overrides.single().contains("study-standard"))
        val cancelled = v.overlay.single().slot
        assertTrue(cancelled.cancelled && cancelled.notes == "imported override" && cancelled.startMin == 610)
        val back = v.work.single()
        assertEquals("import-override", back.source); assertEquals(STUDY_CYCLE_STANDARD_HOURS, back.hours, 0.001)
        assertTrue(back.due.toLocalDate() == java.time.LocalDate.of(2026, 11, 24), "due the next working day: ${back.due}")
        assertTrue(v.summaryLines().any { it.contains("overridden") } && v.summaryLines().any { it.contains("come back as work") })
        assertTrue(slotsFor(base + v.overlay.map { it.slot }, java.time.LocalDate.of(2026, 11, 23), zone).none { it.slot.title.startsWith("Notes and problems: Randomness") && !it.slot.cancelled })
    }

    @Test fun OV1_a_code_planning_routine_or_chore_block_can_be_overridden_and_returns_nothing() {
        val sun = java.time.LocalDate.of(2026, 11, 22)
        val batch = slotsFor(base, sun, zone).first { it.slot.title == "Sunday batch cook" }.slot
        val v = verify(parse(HOV, "event,2026-11-22,,${minutesText(batch.startMin)},${minutesText(batch.endMin)},Sunday batch cook,planning,,,none,cancel,,yes"))
        assertTrue(v.ok, v.errors.toString()); assertTrue(v.needsWall); assertTrue(v.work.isEmpty(), "a planning block has no study cycle to return")
        val ready = slotsFor(base, sun, zone).first { it.slot.title == "Get ready for church" }.slot
        val v2 = verify(parse(HOV, "event,2026-11-22,,${minutesText(ready.startMin)},${minutesText(ready.endMin)},Get ready for church,routine,,,none,cancel,,yes"))
        assertTrue(v2.ok, v2.errors.toString())
    }

    @Test fun OV1_OV4_meals_sleep_the_wake_and_the_bible_study_are_never_overridden() {
        for (t in listOf("meal", "sleep", "reminder-x")) assertFalse(parse(HOV, "event,2026-11-23,,10:10,12:15,X,$t,,,none,cancel,,yes").ok, t)
        val sun = java.time.LocalDate.of(2026, 11, 22)
        val bible = slotsFor(base, sun, zone).first { it.slot.title == "Bible study" }.slot
        val v = verify(parse(HOV, "event,2026-11-22,,${minutesText(bible.startMin)},${minutesText(bible.endMin)},Bible study,routine,,,none,cancel,,yes"))
        assertFalse(v.ok); assertTrue(v.errors.any { it.message.contains("never overridden") }, v.errors.toString())
        val wake = slotsFor(base, sun, zone).first { it.slot.title == "Wake, shower" }.slot
        val v2 = verify(parse(HOV, "event,2026-11-22,,${minutesText(wake.startMin)},${minutesText(wake.endMin)},Wake,routine,,,none,cancel,,yes"))
        assertFalse(v2.ok); assertTrue(v2.errors.any { it.message.contains("never overridden") })
    }

    @Test fun OV4_a_meal_is_overridden_only_when_the_plan_marks_it_optional() {
        val sun = java.time.LocalDate.of(2026, 11, 22)
        val dinner = slotsFor(base, sun, zone).first { it.slot.title.startsWith("Student Dinner") }.slot
        val v = verify(parse(HOV, "event,2026-11-22,,${minutesText(dinner.startMin)},${minutesText(dinner.endMin)},Student Dinner (optional),meal,,,none,cancel,,yes"))
        assertTrue(v.ok, v.errors.toString()); assertTrue(v.needsWall)
        val lunch = slotsFor(base, sun, zone).first { it.slot.title.startsWith("Lunch") }.slot
        val bad = verify(parse(HOV, "event,2026-11-22,,${minutesText(lunch.startMin)},${minutesText(lunch.endMin)},Lunch (chicken-strip wrap),meal,,,none,cancel,,yes"))
        assertFalse(bad.ok); assertTrue(bad.errors.any { it.message.contains("meal") }, bad.errors.toString())
    }

    @Test fun OV1_an_override_goes_only_on_a_cancel_row() {
        val p = parse(HOV, "event,2026-11-23,,10:10,12:15,Extra,errand,,,none,add,,yes")
        assertFalse(p.ok); assertTrue(p.errors.single().message.contains("cancel row"), p.errors.toString())
        assertFalse(parse(HOV, "event,2026-11-23,,10:10,12:15,Extra,errand,,,none,,,yes").ok)
        assertFalse(parse(HOV, "event,2026-11-23,,10:10,12:15,Extra,errand,,,none,cancel,,maybe").ok)
    }

    @Test fun OV1_the_flag_is_part_of_the_fingerprint_but_only_when_set_so_earlier_imports_stay_recognised() {
        val plain = parse(HNO, "event,2026-11-23,,17:00,18:00,MyTutor session,mytutor,,,none,cancel,").rows.single()
        val withColumnEmpty = parse(HOV, "event,2026-11-23,,17:00,18:00,MyTutor session,mytutor,,,none,cancel,,").rows.single()
        val withColumnNo = parse(HOV, "event,2026-11-23,,17:00,18:00,MyTutor session,mytutor,,,none,cancel,,no").rows.single()
        assertEquals(plain.hash(), withColumnEmpty.hash()); assertEquals(plain.hash(), withColumnNo.hash())
        val ov = parse(HOV, "event,2026-11-23,,10:10,12:15,Notes and problems: Randomness,study-standard,,,none,cancel,,yes").rows.single()
        assertNotEquals(ov.hash(), (ov as EventRow).copy(override = false).hash())
        val v = verifyImport(parse(HOV, notes), base, setOf(ov.hash()), emptyList(), now, zone, reflow = true)
        assertEquals(1, v.alreadyImported); assertTrue(v.overlay.isEmpty())
    }

    @Test fun OV1_the_twenty_minute_sleep_step_the_app_refused_works_when_the_file_overrides_the_block_that_hid_the_wake() {
        val refused = verify(parse(HOV, lectureFree, night))
        assertFalse(refused.ok, "Monday's 10:10 study block hides a wake at 10:50, and a file cannot move it")
        val ok = verify(parse(HOV, lectureFree, night, notes))
        assertTrue(ok.ok, ok.errors.toString()); assertTrue(ok.needsWall)
        assertEquals(2, ok.modifiers.size)
    }

    @Test fun OV5_the_format_document_and_the_text_an_ai_is_given_name_the_override_column_and_what_it_may_cancel() {
        val doc = java.io.File("../docs/TIMETABLE_CSV.md").readText().replace("\r\n", "\n")
        for (t in OVERRIDE_TYPES) { assertTrue(t in TIMETABLE_AI_PROMPT, t); assertTrue(t in doc, t) }
        assertTrue("override" in TIMETABLE_AI_PROMPT && "| override |" in doc)
        assertTrue("Never use it for sleep, the wake or the Bible study" in TIMETABLE_AI_PROMPT)
    }

    @Test fun OV1_a_block_that_came_from_an_earlier_file_cannot_be_overridden_and_a_missing_block_is_named() {
        val v = verify(parse(HOV, "event,2026-11-23,,03:00,04:00,Nothing here,study-standard,,,none,cancel,,yes"))
        assertFalse(v.ok); assertTrue(v.errors.single().message.contains("No study-standard starts at 03:00"), v.errors.toString())
    }
}
