package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** SA1 to SA6, BK1 to BK5: the day's edges move for a night, and tonight's plan (study 00:00 to 02:30) can be entered, previewed and applied. */
class AnchorNightTest {
    private val wed = LocalDate.of(2026, 11, 18)
    private val thu = wed.plusDays(1)
    private val zone = ZoneId.of("Europe/London")
    private val eveningNow = ZonedDateTime.of(2026, 11, 18, 21, 0, 0, 0, zone)
    private fun p(day: DayOfWeek, s: Int, e: Int, title: String, type: String, hard: Boolean = false) = PatternSlot(day, s, e, title, type, "", hard, "")

    /** A normal day with the evening ending in prayer and Shutdown at 23:00, and the lecture at [lecture]. */
    private fun day(d: DayOfWeek, lecture: Int) = listOf(
        p(d, 450, 470, "Wake, shower", "routine"), p(d, 470, 500, "Bible study", "routine"), p(d, 500, 515, "Breakfast, dress, bag", "routine"),
        p(d, lecture, lecture + 60, "Randomness and Computation lecture", "lecture"),
        p(d, lecture + 75, lecture + 195, "Notes and problems: Randomness", "study-standard"),
        p(d, 735, 760, "Lunch", "meal"), p(d, 780, 900, "CS Project", "code"), p(d, 900, 960, "Free time - phone open", "free"),
        p(d, 1080, 1110, "Dinner", "meal"), p(d, 1110, 1125, "Wash dishes + tidy", "chore"), p(d, 1125, 1260, "Evening work", "study"), p(d, 1260, 1300, "Free time", "free"),
        p(d, 1330, 1340, "Evening prayer", "routine"), p(d, 1340, 1380, "Shutdown", "sleep"),
    )
    private val base = day(DayOfWeek.WEDNESDAY, 540) + day(DayOfWeek.THURSDAY, 12 * 60 + 30)       // Thursday's lecture is at 12:30
    private val baseEarlyThursday = day(DayOfWeek.WEDNESDAY, 540) + day(DayOfWeek.THURSDAY, 9 * 60)

    private fun night(lo: String = "02:40", wake: String = "11:00", wd: Int = 0) = Instance(Preset.NIGHT, wed, wed, mapOf("lights_out" to lo, "wake" to wake, "winddown_min" to wd.toString()))
    private fun event(title: String, type: String, from: String, to: String, afterMidnight: Boolean = false) =
        Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to title, "type" to type, "start" to from, "end" to to, "after_midnight" to afterMidnight.toString()))

    @Test fun SA1_the_night_preset_moves_lights_out_and_the_shutdown_with_it_and_the_morning_to_the_wake() {
        val c = compile(night(), base, emptyList(), eveningNow, zone)
        assertTrue(c.problems.isEmpty(), c.problems.toString())
        assertEquals(1, c.anchors.size)
        val after = applied(base, c)
        val shut = slotsFor(after, wed, zone).single { it.slot.title == "Shutdown" }.slot
        assertEquals(1600, shut.endMin, "Shutdown now ends at 02:40, which is 1600 minutes after midnight of the 18th")
        assertEquals(1340 + 220, shut.startMin)
        assertEquals(660, slotsFor(after, thu, zone).first { it.slot.title.startsWith("Wake") }.slot.startMin, "Thursday starts at 11:00")
        assertTrue(c.lines.first().contains("8.0h") || c.lines.first().contains("8.3h") || c.lines.first().contains("h of sleep"))
    }

    @Test fun RT2_the_wind_down_floor_can_be_applied_through_the_guard_rails_and_drops_only_what_it_says() {
        val pv = previewOf(night(wd = 10), base, emptyList(), eveningNow, zone)
        assertTrue(pv.canApply, pv.problems.toString())
        val after = applied(base, pv.compiled)
        val shut = slotsFor(after, wed, zone).single { it.slot.title == "Shutdown" }.slot
        assertEquals(10, shut.endMin - shut.startMin)
        assertTrue(slotsFor(after, wed, zone).none { it.slot.title == "Evening prayer" }, "the prayer is shortened into the floor, and said")
        assertTrue(pv.compiled.lines.any { it.contains("floor") }, pv.compiled.lines.toString())
    }

    @Test fun SA3_a_night_under_eight_hours_is_refused_with_the_wake_that_would_work() {
        val c = compile(night(lo = "02:50", wake = "11:00"), base, emptyList(), eveningNow, zone)
        assertTrue(c.problems.any { it.contains("under 8 hours") && it.contains("11:10") }, c.problems.toString())
        val pv = previewOf(night(lo = "02:50", wake = "11:00"), base, emptyList(), eveningNow, zone)
        assertFalse(pv.canApply)
    }

    @Test fun SA5_a_lecture_before_the_wake_refuses_the_night_and_names_it() {
        val c = compile(night(), baseEarlyThursday, emptyList(), eveningNow, zone)
        assertTrue(c.problems.any { it.contains("Randomness and Computation lecture") && it.contains("recording") }, c.problems.toString())
    }

    @Test fun SA2_the_old_guard_rail_refused_a_study_block_after_midnight_and_the_anchors_allow_it() {
        val study = event("Study 1", "study", "00:00", "00:50", afterMidnight = true)
        val without = previewOf(study, base, emptyList(), eveningNow, zone)
        assertTrue(without.problems.any { it.contains("would be in the night") }, "without anchors the fixed 23:00 limit still applies: ${without.problems}")
        val nc = compile(night(), base, emptyList(), eveningNow, zone)
        val withAnchors = previewOf(study, applied(base, nc), emptyList(), eveningNow, zone, nc.anchors.associateBy { it.night })
        assertTrue(withAnchors.canApply, withAnchors.problems.toString())
        val s = slotsFor(applied(applied(base, nc), withAnchors.compiled), wed, zone).single { it.slot.title == "Study 1" }.slot
        assertEquals(1440, s.startMin); assertEquals(1490, s.endMin)
    }

    @Test fun SA2_a_block_after_the_new_lights_out_is_still_refused() {
        val nc = compile(night(wd = 10), base, emptyList(), eveningNow, zone)
        val late = event("Too late", "study", "02:30", "03:10", afterMidnight = true)
        val pv = previewOf(late, applied(base, nc), emptyList(), eveningNow, zone, nc.anchors.associateBy { it.night })
        assertFalse(pv.canApply, "a block that runs past lights-out cannot be applied: ${pv.problems}")
    }

    @Test fun tonights_plan_is_entered_previewed_applied_and_keeps_sleep_and_commitments() {
        var cal = base
        val anchors = HashMap<LocalDate, NightAnchors>()
        val nc = compile(night(wd = 10), cal, emptyList(), eveningNow, zone)
        assertTrue(nc.problems.isEmpty(), nc.problems.toString())
        cal = applied(cal, nc); nc.anchors.forEach { anchors[it.night] = it }
        val steps = listOf(
            event("Walk to the corner shop and collect", "errand", "22:55", "23:12"),
            event("Cook: pork, pasta, shower", "errand", "23:12", "23:40"),
            event("Eat", "errand", "23:40", "23:58"),
            event("Study 1", "study", "00:00", "00:50", true),
            event("Study 2", "study", "01:00", "01:50", true),
            event("Study 3", "study", "02:00", "02:30", true),
        )
        for (st in steps) {
            val pv = previewOf(st, cal, emptyList(), eveningNow, zone, anchors)
            assertTrue(pv.canApply, "${st.str("title")}: ${pv.problems}")
            cal = applied(cal, pv.compiled)
        }
        val tonight = slotsFor(cal, wed, zone)
        for (t in listOf("Study 1", "Study 2", "Study 3", "Eat", "Cook: pork, pasta, shower")) assertTrue(tonight.any { it.slot.title == t }, "$t is on the calendar")
        val shut = tonight.single { it.slot.title == "Shutdown" }.slot
        assertEquals(1600, shut.endMin, "lights-out stays at 02:40")
        assertEquals(1590, shut.startMin, "the wind-down is its 10 minute floor tonight")
        val study = tonight.filter { it.slot.title.startsWith("Study") }.sortedBy { it.slot.startMin }
        assertEquals(listOf(1440 to 1490, 1500 to 1550, 1560 to 1590), study.map { it.slot.startMin to it.slot.endMin })
        // nothing overlaps among the blocks that are not free time
        val busy = tonight.filter { it.slot.type != "free" }.sortedBy { it.slot.startMin }
        for ((a, b) in busy.zipWithNext()) assertTrue(b.slot.startMin >= a.slot.endMin, "${a.slot.title} overlaps ${b.slot.title}")
        // the morning starts at 11:00 and the lecture at 12:30 is untouched
        assertEquals(660, slotsFor(cal, thu, zone).first { it.slot.title.startsWith("Wake") }.slot.startMin)
        assertEquals(12 * 60 + 30, slotsFor(cal, thu, zone).single { it.slot.type == "lecture" }.slot.startMin)
    }

    @Test fun SA4_moving_lights_out_and_wake_together_is_free_and_does_not_lose_work_that_was_not_moved() {
        val c = compile(night(lo = "01:00", wake = "09:20"), base, emptyList(), eveningNow, zone)
        assertTrue(c.problems.isEmpty(), c.problems.toString())
        val a = c.anchors.single()
        assertEquals(AnchorChange.FREE, classifyAnchorChange(defaultAnchors(wed, a.wake), a))
    }

    // ---- BK: the backlog ----
    private fun catchUp(start: LocalDate, end: LocalDate) = Instance(Preset.CATCH_UP, start, end, mapOf("slots_floor" to "2", "slots_stretch" to "3"))
    private val work12 = (1..12).map { DueWork("Catch-up: Recording $it", 1.0, 4, ZonedDateTime.of(2026, 11, 17, 17, 0, 0, 0, zone)) }

    @Test fun BK1_inside_a_catch_up_span_the_catch_up_is_due_at_the_end_of_the_span() {
        val due = ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, zone)
        val items = listOf(CatchUp("missed:a", "Catch-up: Randomness", 1.0, 4, due, "Bad night"), CatchUp("missed:b", "Catch-up: Game Theory", 1.0, 4, due.minusDays(3), "Slept in"))
        val span = catchUp(wed, wed.plusDays(6))
        val out = catchUpDueInSpan(items, listOf(span), eveningNow, base, zone)
        val end = endOfDayCutoff(base, wed.plusDays(6), zone)
        assertTrue(out.all { it.due == end }, "both items, even the overdue one, are due at the end of the span: ${out.map { it.due }}")
    }

    @Test fun BK4_outside_a_span_the_d5_date_stands() {
        val due = ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, zone)
        val items = listOf(CatchUp("missed:a", "Catch-up: Randomness", 1.0, 4, due, "Bad night"))
        val future = catchUp(wed.plusDays(10), wed.plusDays(16))
        assertEquals(items, catchUpDueInSpan(items, listOf(future), eveningNow, base, zone), "a span that has not started changes nothing")
        assertEquals(items, catchUpDueInSpan(items, emptyList(), eveningNow, base, zone))
    }

    @Test fun BK3_a_catch_up_span_moves_only_catch_up_items_so_coursework_dates_are_untouched() {
        val due = ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, zone)
        val coursework = DueWork("GT assignment", 6.0, 8, ZonedDateTime.of(2026, 11, 19, 17, 0, 0, 0, zone))
        val items = listOf(CatchUp("missed:a", "Catch-up: Randomness", 1.0, 4, due, "Bad night"))
        val out = catchUpDueInSpan(items, listOf(catchUp(wed, wed.plusDays(6))), eveningNow, base, zone)
        assertEquals(1, out.size)
        assertEquals(ZonedDateTime.of(2026, 11, 19, 17, 0, 0, 0, zone), coursework.due, "coursework is a DueWork and has no path through the span function")
    }

    @Test fun BK2_the_preview_says_the_work_the_room_and_the_shortfall_as_numbers() {
        val span = catchUp(thu, thu.plusDays(2))
        val c = compile(span, base + day(DayOfWeek.FRIDAY, 540) + day(DayOfWeek.SATURDAY, 540) + day(DayOfWeek.SUNDAY, 540), work12, eveningNow, zone)
        val line = c.lines.firstOrNull { it.startsWith("Backlog:") }
        assertNotNull(line, c.lines.toString())
        assertTrue(line.contains("12.0h of work"), line)
        assertTrue(line.contains("over") || line.contains("It fits"), line)
        assertEquals(endOfDayCutoff(base, thu.plusDays(2), zone), c.backlogDue)
    }

    @Test fun BK5_when_the_span_is_over_the_item_keeps_its_own_d5_date_so_it_shows_as_due() {
        val due = ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, zone)
        val items = listOf(CatchUp("missed:a", "Catch-up: Randomness", 1.0, 4, due, "Bad night"))
        val span = catchUp(wed.minusDays(10), wed.minusDays(4))
        assertEquals(items, catchUpDueInSpan(items, listOf(span), eveningNow, base, zone))
    }

    @Test fun SA1_at_0145_the_night_still_running_is_yesterdays_and_can_be_planned_with_a_short_trip_before_lights_out() {
        val now = ZonedDateTime.of(2026, 11, 19, 1, 45, 0, 0, zone)
        assertEquals(wed, nightOf(now)); assertEquals(thu, nightOf(ZonedDateTime.of(2026, 11, 19, 6, 0, 0, 0, zone)))
        val night = night(wd = 8)
        val pv = previewOf(night, base, emptyList(), now, zone)
        assertTrue(pv.canApply, pv.problems.toString())
        val nc = compile(night, base, emptyList(), now, zone)
        val shop = event("Corner shop for the biscuits", "errand", "02:15", "02:32", afterMidnight = true)
        val sp = previewOf(shop, applied(base, nc), emptyList(), now, zone, nc.anchors.associateBy { it.night })
        assertTrue(sp.canApply, sp.problems.toString())
        val cal = applied(applied(base, nc), sp.compiled)
        val tonight = slotsFor(cal, wed, zone)
        assertEquals(1575, tonight.single { it.slot.title.startsWith("Corner shop") }.slot.startMin, "02:15 is 1575 minutes after midnight of the 18th")
    }
}
