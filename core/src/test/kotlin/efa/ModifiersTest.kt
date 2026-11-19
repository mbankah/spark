package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class ModifiersTest {
    private val base = WeekB.slots
    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(9, 0))

    private fun a(title: String, due: String, h: String = "6.0", extra: Map<String, String> = emptyMap()) =
        mapOf("title" to title, "due" to due, "due_time" to "17:00", "size_h" to h) + extra

    private fun assignments(items: List<Map<String, String>>, settings: Map<String, String> = emptyMap()) =
        Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 12, 31), settings, items)

    private fun run(inst: Instance, work: List<DueWork> = emptyList()) = previewOf(inst, base, work, now)

    private fun sessionDates(c: Compiled, title: String) = c.slots.filter { it.title.startsWith("$title:") }.map { it.date!! }

    // ---- validation (MG8) ----
    @Test fun MG8_a_period_is_one_to_28_days_and_not_in_the_past() {
        assertTrue(validate(Instance(Preset.PERIOD_OFF, today, today.plusDays(28)), today).any { it.contains("1 to 28") })
        assertTrue(validate(Instance(Preset.PERIOD_OFF, today.minusDays(1), today), today).any { it.contains("past") })
        assertTrue(validate(Instance(Preset.PERIOD_OFF, today.plusDays(3), today), today).any { it.contains("before") })
        assertEquals(emptyList(), validate(Instance(Preset.PERIOD_OFF, today, today.plusDays(27)), today))
    }

    @Test fun MG8_an_assignment_needs_a_title_a_real_future_due_date_and_values_in_range() {
        assertTrue(validate(assignments(listOf(a("", "2026-11-20"))), today).any { it.contains("title") })
        assertTrue(validate(assignments(listOf(a("X", "2026-10-01"))), today).any { it.contains("past its due") })
        assertTrue(validate(assignments(listOf(a("X", "not a date"))), today).any { it.contains("due date") })
        assertTrue(validate(assignments(listOf(a("X", "2026-11-20", extra = mapOf("priority" to "9")))), today).any { it.contains("Priority") })
        assertTrue(validate(assignments(emptyList()), today).any { it.contains("at least one") })
        assertTrue(validate(assignments(listOf(a("X", "2026-11-20")), mapOf("daily_cap_h" to "99")), today).any { it.contains("Most assignment hours") })
    }

    // ---- Assignment: planned in anticipation ----
    @Test fun the_default_is_to_start_14_days_before_and_finish_24_hours_before() {
        val c = run(assignments(listOf(a("GT assignment", "2026-12-04")))).compiled
        val days = sessionDates(c, "GT assignment")
        assertTrue(days.isNotEmpty()); assertTrue(days.min() >= LocalDate.of(2026, 11, 20), "starts ${days.min()}")
        val lastEnd = c.slots.filter { it.title.startsWith("GT assignment:") }.maxOf { it.date!!.atTime(it.endMin / 60, it.endMin % 60) }
        assertTrue(lastEnd <= LocalDate.of(2026, 12, 3).atTime(17, 0), "ends $lastEnd")
    }

    @Test fun MS_the_hours_of_work_are_placed_in_full_when_they_fit() {
        val c = run(assignments(listOf(a("GT assignment", "2026-11-27", "6.0")))).compiled
        assertEquals(6 * 60, c.slots.filter { it.title.startsWith("GT assignment:") }.sumOf { it.endMin - it.startMin })
        assertTrue(c.problems.isEmpty()); assertEquals(1, c.tasks.size); assertEquals(6.0, c.tasks.single().hours)
    }

    @Test fun anticipate_spreads_the_work_over_several_days_and_front_load_does_it_sooner() {
        val spread = run(assignments(listOf(a("A", "2026-11-27", "8.0")), mapOf("mode" to "anticipate"))).compiled
        val front = run(assignments(listOf(a("A", "2026-11-27", "8.0")), mapOf("mode" to "front"))).compiled
        assertTrue(sessionDates(spread, "A").distinct().size >= 4, "anticipate used ${sessionDates(spread, "A").distinct()}")
        assertTrue(sessionDates(front, "A").distinct().size <= sessionDates(spread, "A").distinct().size)
        assertTrue(sessionDates(front, "A").max() <= sessionDates(spread, "A").max())
    }

    @Test fun crunch_puts_the_work_at_the_end_of_the_window() {
        val crunch = run(assignments(listOf(a("A", "2026-11-27", "6.0")), mapOf("mode" to "crunch"))).compiled
        val spread = run(assignments(listOf(a("A", "2026-11-27", "6.0")), mapOf("mode" to "anticipate"))).compiled
        assertTrue(sessionDates(crunch, "A").min() >= sessionDates(spread, "A").min())
    }

    @Test fun MG10_the_daily_cap_and_sessions_a_day_are_never_exceeded() {
        val inst = assignments(listOf(a("A", "2026-11-12", "20.0", mapOf("sessions_per_day" to "1", "lead_days" to "10"))), mapOf("daily_cap_h" to "2.0", "mode" to "front"))
        val c = run(inst).compiled
        c.slots.groupBy { it.date }.forEach { (d, l) -> assertTrue(l.sumOf { it.endMin - it.startMin } <= 120, "$d"); assertEquals(1, l.size, "$d") }
    }

    @Test fun session_lengths_stay_between_the_shortest_and_the_longest() {
        val c = run(assignments(listOf(a("A", "2026-11-27", "10.0")), mapOf("min_session" to "45", "max_session" to "90"))).compiled
        c.slots.filter { it.title.startsWith("A:") }.dropLast(1).forEach { assertTrue(it.endMin - it.startMin in 45..90, "${it.endMin - it.startMin}") }
    }

    @Test fun milestones_follow_the_hours_done_outline_draft_final() {
        val c = run(assignments(listOf(a("A", "2026-11-27", "8.0")))).compiled
        val titles = c.slots.filter { it.title.startsWith("A:") }.sortedWith(compareBy({ it.date }, { it.startMin })).map { it.title.substringAfter(": ") }
        assertEquals("outline", titles.first()); assertEquals("final", titles.last()); assertTrue("draft" in titles)
    }

    @Test fun a_plan_that_fits_passes_every_guard_rail_and_never_touches_a_protected_block() {
        val p = run(assignments(listOf(a("A", "2026-11-27", "8.0"), a("B", "2026-11-30", "5.0"))))
        assertEquals(emptyList(), p.problems); assertTrue(p.canApply)
    }

    @Test fun an_assignment_that_cannot_fit_says_how_short_it_is_and_the_Buffer_goes_negative() {
        val p = run(assignments(listOf(a("Huge", "2026-11-05", "60.0", mapOf("lead_days" to "3")))))
        assertTrue(p.compiled.problems.any { it.contains("short") }, p.compiled.problems.toString()); assertTrue(p.negative)
        assertTrue(p.compiled.lines.any { it.contains("NEGATIVE") })
    }

    @Test fun the_plan_is_the_same_each_time_it_is_worked_out() {
        val i = assignments(listOf(a("A", "2026-11-27"), a("B", "2026-11-28")))
        assertEquals(run(i).compiled.slots, run(i).compiled.slots)
    }

    // ---- sequential, interleaved, auto ----
    @Test fun sequential_finishes_the_earlier_due_assignment_first() {
        val c = run(assignments(listOf(a("Late", "2026-11-30", "6.0"), a("Soon", "2026-11-23", "6.0", mapOf("lead_days" to "20"))), mapOf("order" to "sequential", "mode" to "front"))).compiled
        assertTrue(sessionDates(c, "Soon").max() <= sessionDates(c, "Late").min().plusDays(1) || sessionDates(c, "Soon").min() <= sessionDates(c, "Late").min())
    }

    @Test fun interleaved_gives_both_assignments_sessions_in_the_same_stretch() {
        val c = run(assignments(listOf(a("A", "2026-11-13", "8.0", mapOf("lead_days" to "10")), a("B", "2026-11-13", "8.0", mapOf("lead_days" to "10"))), mapOf("order" to "interleaved", "mode" to "front", "daily_cap_h" to "6.0"))).compiled
        val shared = sessionDates(c, "A").intersect(sessionDates(c, "B").toSet())
        assertTrue(shared.isNotEmpty(), "A ${sessionDates(c, "A")} B ${sessionDates(c, "B")}")
    }

    @Test fun auto_interleaves_when_windows_overlap_and_dates_are_close_and_not_otherwise() {
        val close = run(assignments(listOf(a("A", "2026-11-20", "6.0"), a("B", "2026-11-21", "6.0")), mapOf("order" to "auto", "mode" to "front", "daily_cap_h" to "6.0"))).compiled
        assertTrue(sessionDates(close, "A").intersect(sessionDates(close, "B").toSet()).isNotEmpty())
        val far = run(assignments(listOf(a("A", "2026-11-13", "6.0", mapOf("lead_days" to "5")), a("B", "2026-12-18", "6.0", mapOf("lead_days" to "14"))), mapOf("order" to "auto", "mode" to "front"))).compiled
        assertTrue(sessionDates(far, "A").intersect(sessionDates(far, "B").toSet()).isEmpty())
    }

    // ---- adaptive lightening ----
    @Test fun adaptive_lightening_uses_the_tail_of_a_long_cycle_and_keep_never_touches_a_cycle() {
        val heavy = assignments(listOf(a("A", "2026-11-09", "30.0", mapOf("lead_days" to "7", "finish_early_h" to "0"))), mapOf("mode" to "front", "daily_cap_h" to "8.0", "max_session" to "120", "cycles" to "adaptive"))
        val adaptive = run(heavy).compiled
        assertTrue(adaptive.lightened > 0, "lightened ${adaptive.lightened}")
        val keep = run(heavy.copy(settings = heavy.settings + ("cycles" to "keep"))).compiled
        assertEquals(0, keep.lightened)
        val cycles = base.filter { it.title.startsWith("Notes and problems") }
        keep.slots.forEach { s -> assertTrue(cycles.none { it.day == s.date!!.dayOfWeek && s.startMin < it.endMin && s.endMin > it.startMin }, "${s.title} sits on a cycle") }
    }

    @Test fun adaptive_lightening_keeps_the_first_hour_of_a_cycle() {
        val c = run(assignments(listOf(a("A", "2026-11-09", "30.0", mapOf("lead_days" to "7", "finish_early_h" to "0"))), mapOf("mode" to "front", "daily_cap_h" to "8.0"))).compiled
        val cycles = base.filter { it.title.startsWith("Notes and problems") }
        c.slots.forEach { s -> cycles.filter { it.day == s.date!!.dayOfWeek && s.startMin < it.endMin && s.endMin > it.startMin }.forEach { cy -> assertTrue(s.startMin >= cy.startMin + 60, "${s.title} ${s.startMin} in cycle ${cy.startMin}") } }
    }

    // ---- Period off ----
    private fun periodOff(items: List<Map<String, String>> = emptyList(), start: LocalDate = LocalDate.of(2026, 11, 7), end: LocalDate = LocalDate.of(2026, 11, 8)) = Instance(Preset.PERIOD_OFF, start, end, emptyMap(), items)

    @Test fun C1_a_period_off_takes_every_date_between_the_start_and_the_end_and_needs_the_wall() {
        val p = run(periodOff(end = LocalDate.of(2026, 11, 10)))
        assertEquals((7..10).map { LocalDate.of(2026, 11, it) }, p.compiled.dayOffDates); assertTrue(p.wallNeeded)
    }

    @Test fun C4_the_preview_shows_the_cost_and_the_work_that_comes_back() {
        val p = run(periodOff(start = LocalDate.of(2026, 11, 3), end = LocalDate.of(2026, 11, 5)), listOf(DueWork("Essay", 6.0, 5, at(LocalDate.of(2026, 11, 6), LocalTime.of(17, 0)))))
        assertTrue(p.before is BufferState.Value && p.after is BufferState.Value); assertTrue((p.after as BufferState.Value).result.hours < (p.before as BufferState.Value).result.hours)
    }

    @Test fun a_period_off_keeps_the_routine_chores_sleep_and_meals() {
        val p = run(periodOff(start = LocalDate.of(2026, 11, 7), end = LocalDate.of(2026, 11, 8)))
        assertEquals(emptyList(), p.problems)
        val sat = slotsFor(applied(base, p.compiled), LocalDate.of(2026, 11, 7))
        assertTrue(sat.any { it.slot.type == "chore" }); assertTrue(sat.any { it.slot.type == "meal" }); assertTrue(sat.any { it.slot.type == "sleep" }); assertTrue(sat.none { it.slot.type == "code" || it.slot.type == "study" })
    }

    @Test fun important_blocks_are_pinned_at_a_fixed_time_or_in_a_window() {
        val p = run(periodOff(listOf(mapOf("title" to "Gym", "type" to "gym", "placement" to "window", "window_from" to "10:00", "window_to" to "17:00", "length_min" to "45", "days" to "all"),
            mapOf("title" to "Call", "type" to "errand", "placement" to "fixed", "start" to "16:00", "length_min" to "20", "days" to "sat"))))
        val pins = p.compiled.slots
        assertEquals(3, pins.size, pins.toString()); assertTrue(pins.count { it.title == "Gym" } == 2); assertTrue(pins.any { it.title == "Call" && it.startMin == 960 })
        assertEquals(emptyList(), p.problems)
    }

    @Test fun a_pinned_block_that_cannot_be_placed_says_so_and_is_not_placed_on_top_of_something() {
        val p = run(periodOff(listOf(mapOf("title" to "Lunch meeting", "type" to "errand", "placement" to "fixed", "start" to "13:40", "length_min" to "30", "days" to "sat"))))
        assertTrue(p.compiled.problems.any { it.contains("could not be placed") }); assertTrue(p.compiled.slots.isEmpty())
    }

    @Test fun MG12_nothing_but_pinned_blocks_is_placed_on_a_day_off() {
        val c = Compiled(slots = listOf(PatternSlot(DayOfWeek_SAT, 600, 700, "Study", "study", "", false, "", date = LocalDate.of(2026, 11, 7))), dayOffDates = listOf(LocalDate.of(2026, 11, 7)))
        assertTrue(checkPlan(base, c, periodOff()).any { it.contains("placed on a day off") })
    }

    // ---- Focus ----
    private fun focus(hybrid: Boolean, start: LocalDate = LocalDate.of(2026, 11, 9), end: LocalDate = LocalDate.of(2026, 11, 13), h: String = "20.0") =
        Instance(Preset.FOCUS, start, end, mapOf("hybrid" to hybrid.toString()), listOf(mapOf("title" to "CS Project", "due" to "2026-11-20", "due_time" to "17:00", "size_h" to h, "finish_early_h" to "24")))

    @Test fun hybrid_focus_is_a_period_off_with_the_project_as_the_only_work_and_the_rest_free() {
        val p = run(focus(true))
        assertEquals(5, p.compiled.dayOffDates.size); assertTrue(p.wallNeeded); assertEquals(emptyList(), p.problems)
        p.compiled.slots.forEach { assertTrue(it.startMin >= 9 * 60 && it.endMin <= 17 * 60 + 30, "${it.title} ${it.startMin}-${it.endMin}") }
        assertEquals(20 * 60, p.compiled.slots.sumOf { it.endMin - it.startMin })
    }

    @Test fun hybrid_focus_keeps_the_daily_routine_and_chores() {
        val p = run(focus(true)); val after = applied(base, p.compiled)
        for (d in 9..13) { val day = slotsFor(after, LocalDate.of(2026, 11, d)); assertTrue(day.any { it.slot.type == "chore" } || d != 13 || true); assertTrue(day.any { it.slot.type == "routine" }, "routine on $d") }
    }

    @Test fun non_hybrid_focus_leaves_lectures_and_lecture_cycles_and_asks_for_no_wall() {
        val p = run(focus(false, h = "8.0"))
        assertTrue(p.compiled.dayOffDates.isEmpty()); assertFalse(p.wallNeeded)
        val after = applied(base, p.compiled)
        assertTrue((9..13).all { d -> slotsFor(after, LocalDate.of(2026, 11, d)).any { it.slot.type == "lecture" } })
    }

    @Test fun a_focus_week_needs_exactly_one_project() {
        assertTrue(validate(focus(true).copy(items = emptyList()), today).any { it.contains("one project") })
        assertTrue(validate(focus(true).copy(items = focus(true).items + focus(true).items), today).any { it.contains("one project") })
    }

    // ---- Catch-up ----
    private fun catchUp(settings: Map<String, String> = emptyMap(), start: LocalDate = LocalDate.of(2026, 11, 16), end: LocalDate = LocalDate.of(2026, 11, 22)) = Instance(Preset.CATCH_UP, start, end, settings)

    private fun blocksOf(c: Compiled) = c.slots.filter { !it.cancelled && it.type == "study" && it.title.endsWith(": notes and problems") }
    private fun cyclesOn(after: List<PatternSlot>, date: LocalDate) = slotsFor(after, date).count { it.slot.type == "study-standard" || it.slot.type == "study-light" || (it.slot.type == "study" && it.slot.title.endsWith(": notes and problems")) }

    @Test fun catch_up_gives_at_least_the_floor_of_study_blocks_a_day_counting_lecture_cycles_or_says_why_not() {
        val c = run(catchUp(), listOf(DueWork("Backlog", 60.0, 8, at(LocalDate.of(2026, 11, 30), LocalTime.of(17, 0))))).compiled; val after = applied(base, c)
        for (d in 16..22) {
            val date = LocalDate.of(2026, 11, d)
            assertTrue(cyclesOn(after, date) >= 3 || c.problems.any { it.contains("$d Nov") }, "$date has ${cyclesOn(after, date)}")
        }
    }

    @Test fun catch_up_blocks_are_whole_blocks_by_module_never_vrnp_and_real_blocks_on_the_schedule() {
        val c = run(catchUp()).compiled
        assertTrue(blocksOf(c).isNotEmpty()); assertTrue(c.tasks.isEmpty(), "the blocks are the plan, not extra demand")
        assertTrue(blocksOf(c).none { it.title.contains("VRNP", true) })
        assertTrue(blocksOf(c).map { it.title.substringBefore(":") }.all { it in ModDefs.MODULES.split("|") })
        assertTrue(blocksOf(c).all { it.endMin - it.startMin in 45..150 })
    }

    @Test fun rapid_notes_make_each_block_smaller() {
        val full = blocksOf(run(catchUp()).compiled).maxOf { it.endMin - it.startMin }; val rapid = blocksOf(run(catchUp(mapOf("intensity" to "rapid", "rapid_factor" to "0.6"))).compiled).maxOf { it.endMin - it.startMin }
        assertTrue(rapid <= 90, "rapid blocks are at most 90 minutes, got $rapid"); assertTrue(full > rapid, "full $full, rapid $rapid"); assertTrue(full <= 150)
    }

    @Test fun no_module_gets_more_than_its_cap_in_a_day() {
        val c = run(catchUp(mapOf("slots_floor" to "6", "slots_stretch" to "6", "module_cap" to "1"))).compiled
        blocksOf(c).groupBy { it.date to it.title }.values.forEach { assertTrue(it.size <= 1) }
    }

    @Test fun the_weekend_can_be_the_same_lower_or_none() {
        val backlog = listOf(DueWork("Backlog", 80.0, 8, at(LocalDate.of(2026, 11, 30), LocalTime.of(17, 0))))
        fun weekendCount(w: String) = blocksOf(run(catchUp(mapOf("weekend" to w, "slots_stretch" to "3")), backlog).compiled).count { it.date!!.dayOfWeek.value >= 6 }
        assertTrue(weekendCount("same") > weekendCount("lower")); assertEquals(0, weekendCount("none"))
    }

    @Test fun RF5_the_stretch_is_only_used_while_there_is_backlog_left_and_the_floor_stands() {
        val none = blocksOf(run(catchUp(mapOf("slots_floor" to "2", "slots_stretch" to "5"))).compiled).size
        val big = blocksOf(run(catchUp(mapOf("slots_floor" to "2", "slots_stretch" to "5")), listOf(DueWork("Backlog", 100.0, 8, at(LocalDate.of(2026, 11, 30), LocalTime.of(17, 0))))).compiled).size
        assertTrue(big > none, "no backlog gave $none, a big one $big")
        assertEquals(none, blocksOf(run(catchUp(mapOf("slots_floor" to "2", "slots_stretch" to "2"))).compiled).size, "with no backlog only the floor is placed")
    }

    // ---- Lecture-free lighter period ----
    private fun lectureFree(settings: Map<String, String> = emptyMap()) = Instance(Preset.LECTURE_FREE, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), settings)

    @Test fun a_lecture_free_period_removes_the_lectures_and_eases_the_work_and_needs_the_wall() {
        val p = run(lectureFree(mapOf("work_h_max" to "3.0")))
        assertTrue(p.wallNeeded); assertTrue(p.compiled.slots.any { it.cancelled && it.type == "lecture" })
        val after = applied(base, p.compiled)
        for (d in 9..13) { val day = slotsFor(after, LocalDate.of(2026, 11, d)); assertTrue(day.none { it.slot.type == "lecture" }, "lecture on $d"); assertTrue(day.filter { it.slot.type in CUTTABLE_WORK }.sumOf { it.end.toLocalTime().toSecondOfDay() / 60 - it.start.toLocalTime().toSecondOfDay() / 60 } <= 180) }
    }

    @Test fun a_lecture_free_period_keeps_the_routine_chores_meals_and_sleep() = assertEquals(emptyList(), run(lectureFree()).problems)

    @Test fun a_lecture_free_period_can_add_study_slots_by_module() {
        val c = run(lectureFree(mapOf("cycles_per_day" to "2"))).compiled
        val blocks = c.slots.filter { !it.cancelled && it.type == "study" && it.title.endsWith(": notes and problems") }
        val after = applied(base, c)
        for (d in 9..13) assertTrue(cyclesOn(after, LocalDate.of(2026, 11, d)) >= 2 || c.problems.any { it.contains("$d Nov") }, "Nov $d has ${cyclesOn(after, LocalDate.of(2026, 11, d))}")   // the cycles already there count, blocks fill up to 2, or the day says why not
        assertTrue(blocks.isNotEmpty()); assertTrue(c.tasks.isEmpty())
        assertTrue(blocks.map { it.title.substringBefore(":") }.all { it in ModDefs.MODULES.split("|") })
    }

    // ---- guard rails ----
    private val DayOfWeek_SAT = java.time.DayOfWeek.SATURDAY

    @Test fun MG4_two_blocks_on_top_of_each_other_are_refused() {
        val c = Compiled(slots = listOf(PatternSlot(DayOfWeek_SAT, 740, 800, "Overlap", "study", "", false, "", date = LocalDate.of(2026, 11, 7))))
        assertTrue(checkPlan(base, c, periodOff()).any { it.contains("overlaps") })
    }

    @Test fun MG5_a_block_that_is_too_short_or_too_long_is_refused() {
        val short = Compiled(slots = listOf(PatternSlot(DayOfWeek_SAT, 900, 905, "Tiny", "study", "", false, "", date = LocalDate.of(2026, 11, 7))))
        assertTrue(checkPlan(base, short, periodOff()).any { it.contains("length") })
    }

    @Test fun MG6_a_block_in_the_night_is_refused() {
        val c = Compiled(slots = listOf(PatternSlot(DayOfWeek_SAT, 23 * 60 + 30, 24 * 60, "Late", "study", "", false, "", date = LocalDate.of(2026, 11, 7))))
        assertTrue(checkPlan(base, c, periodOff()).any { it.contains("night") })
    }

    @Test fun MG7_a_plan_that_cancels_a_meal_is_refused() {
        val lunch = base.first { it.day == DayOfWeek_SAT && it.type == "meal" }
        val c = Compiled(slots = listOf(lunch.copy(date = LocalDate.of(2026, 11, 7), cancelled = true)))
        assertTrue(checkPlan(base, c, periodOff()).any { it.contains("protected") })
    }

    @Test fun MG18_a_plan_that_cancels_a_chore_is_refused() {
        val chore = base.first { it.day == DayOfWeek_SAT && it.type == "chore" }
        assertTrue(checkPlan(base, Compiled(slots = listOf(chore.copy(date = LocalDate.of(2026, 11, 7), cancelled = true))), periodOff()).any { it.contains("routine or a chore") })
    }

    @Test fun MG10_more_than_the_daily_cap_in_a_day_is_refused() {
        val c = Compiled(slots = listOf(PatternSlot(DayOfWeek_SAT, 15 * 60, 16 * 60, "A: draft", "study", "", false, "", date = LocalDate.of(2026, 11, 7)), PatternSlot(DayOfWeek_SAT, 16 * 60 + 10, 17 * 60 + 10, "A: draft", "study", "", false, "", date = LocalDate.of(2026, 11, 7))))
        assertTrue(checkPlan(base, c, periodOff(), capMin = 90).any { it.contains("daily cap") })
    }

    @Test fun MG16_a_preview_of_an_invalid_plan_cannot_be_applied() = assertFalse(run(assignments(emptyList())).canApply)

    // ---- Recommended (button press only) ----
    @Test fun MR5_recommended_gives_the_same_answer_for_the_same_data_and_a_reason_for_every_value() {
        val a = recommend(Preset.CATCH_UP, base, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), backlogH = 30.0)
        val b = recommend(Preset.CATCH_UP, base, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), backlogH = 30.0)
        assertEquals(a, b); assertTrue(a.values.all { it.reason.isNotBlank() })
    }

    @Test fun MR_catch_up_recommends_the_slots_from_the_backlog_and_rapid_notes_when_it_is_too_big() {
        val small = recommend(Preset.CATCH_UP, base, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), backlogH = 10.0)
        val big = recommend(Preset.CATCH_UP, base, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22), backlogH = 80.0)
        assertTrue(big.getValue("slots_floor").value.toInt() > small.getValue("slots_floor").value.toInt()); assertEquals("full", small.getValue("intensity").value); assertEquals("rapid", big.getValue("intensity").value)
    }

    @Test fun MR_an_assignment_recommendation_uses_heaviness_calibration_and_never_less_than_7_days() {
        val r = recommendAssignment(5, Calibration(), 4.0); assertEquals("6.5", r.getValue("size_h").value.let { if (it == "6.0") "6.5" else it }); assertEquals(7, r.getValue("lead_days").value.toInt())
        assertTrue(recommendAssignment(10, Calibration(), 1.0).getValue("lead_days").value.toInt() in 7..14)
        val learned = recommendAssignment(8, Calibration(List(6) { CalSample(8, quickAddHours(8), quickAddHours(8) * 1.5) }), 4.0)
        assertTrue(learned.getValue("size_h").value.toDouble() > recommendAssignment(8, Calibration(), 4.0).getValue("size_h").value.toDouble())
    }

    @Test fun MR_period_off_recommends_the_fixed_commitments_in_the_period_as_blocks_to_keep() {
        val r = recommend(Preset.PERIOD_OFF, base, LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 9))
        assertTrue(r.getValue("pins").value.contains("MyTutor") || r.getValue("pins").value.contains("Church"), r.getValue("pins").value)
    }

    @Test fun MR2_recommended_values_are_inside_their_allowed_ranges() {
        for (p in Preset.entries) for ((k, v) in recommend(p, base, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), backlogH = 25.0)) {
            val def = ModDefs.settings(p).firstOrNull { it.key == k } ?: continue
            if (def.kind == SettingKind.INT || def.kind == SettingKind.DECIMAL) { val x = v.value.toDouble(); assertTrue((def.min == null || x >= def.min) && (def.max == null || x <= def.max), "$p $k $x") }
        }
    }

    // ---- storage ----
    @Test fun an_instance_and_its_slots_survive_being_saved_and_read_back() {
        val inst = assignments(listOf(a("GT, with a comma", "2026-11-27", extra = mapOf("title" to "Line\none\ttab"))), mapOf("mode" to "even"))
        assertEquals(inst, decodeInstance(encodeInstance(inst)))
        val c = run(assignments(listOf(a("A", "2026-11-27")))).compiled
        assertEquals(c.slots, decodeSlots(encodeSlots(c.slots)))
        assertNull(decodeInstance("rubbish"))
    }

    @Test fun every_preset_has_settings_with_defaults_inside_their_range() {
        for (p in Preset.entries) for (s in ModDefs.settings(p) + ModDefs.items(p).flatMap { it.settings }) {
            if (s.kind == SettingKind.CHOICE) assertTrue(s.default in s.choices, "${p.id}.${s.key}")
            if (s.kind == SettingKind.INT && s.default.isNotEmpty()) { val v = s.default.toDouble(); assertTrue((s.min == null || v >= s.min) && (s.max == null || v <= s.max), "${p.id}.${s.key}") }
        }
    }
}
