package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** RF1 to RF9: the general reflow. A span with a start and an end lays the day out again around what cannot move. */
class ReflowTest {
    private val wed = LocalDate.of(2026, 11, 18)
    private fun p(s: Int, e: Int, title: String, type: String, hard: Boolean = false) = PatternSlot(DayOfWeek.WEDNESDAY, s, e, title, type, "", hard, "")
    private val mods = ModDefs.MODULES.split("|")

    /** A small day with every kind of block: the morning routine, a lecture, a cycle, meals, work, free time, a chore and the night routine. */
    private fun day(withCycle: Boolean = true, extra: List<PatternSlot> = emptyList()) = listOfNotNull(
        p(450, 470, "Wake, shower", "routine"), p(470, 500, "Bible study", "routine"), p(500, 515, "Breakfast, dress, bag", "routine"),
        p(540, 600, "Randomness and Computation lecture", "lecture"),
        if (withCycle) p(615, 735, "Notes and problems: Randomness", "study-standard") else null,
        p(735, 760, "Lunch", "meal"), p(760, 900, "CS Project", "code"), p(900, 960, "Free time - phone open", "free"),
        p(1080, 1110, "Dinner", "meal"), p(1110, 1125, "Wash dishes + tidy", "chore"), p(1125, 1260, "Evening work", "study"), p(1260, 1300, "Free time", "free"),
        p(1370, 1380, "Evening prayer", "routine"), p(1380, 1410, "Shutdown", "sleep"),
    ) + extra

    private fun after(base: List<PatternSlot>, b: Built) = base + b.slots
    private fun dated(a: List<PatternSlot>) = slotsFor(a, wed)
    private fun find(a: List<PatternSlot>, title: String) = dated(a).filter { it.slot.title == title }

    // ---- RF1: a span has a time at each end ----
    @Test fun RF1_a_span_has_a_time_at_each_end_and_keeps_them_when_stored() {
        val i = Instance(Preset.CATCH_UP, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), emptyMap(), emptyList(), 14 * 60, 12 * 60)
        val back = decodeInstance(encodeInstance(i))!!
        assertEquals(14 * 60, back.fromMin); assertEquals(12 * 60, back.toMin); assertEquals(i, back)
        assertEquals(0, decodeInstance(encodeInstance(Instance(Preset.CATCH_UP, wed, wed)))!!.fromMin)
        val today = LocalDate.of(2026, 11, 16)
        assertTrue(validate(Instance(Preset.CATCH_UP, today, today, fromMin = 15 * 60, toMin = 10 * 60), today).any { it.contains("ends before") })
        assertEquals(emptyList(), validate(Instance(Preset.CATCH_UP, today, today.plusDays(2), fromMin = 15 * 60, toMin = 10 * 60), today), "ending earlier in the day is fine on a later day")
        assertTrue(validate(Instance(Preset.CATCH_UP, today, today.plusDays(28)), today).any { it.contains("1 to 28") })
        assertEquals(emptyList(), validate(Instance(Preset.CATCH_UP, today, today.plusDays(27)), today))
    }

    @Test fun RF1_a_span_that_starts_this_afternoon_leaves_the_morning_alone() {
        val base = day()
        val b = buildDay(base, wed, Rules(from = 13 * 60, devote = Devote(mods, 150, 10, 3, 3, 9 * 60, 21 * 60 + 30, 2)))
        val a = after(base, b)
        assertTrue(b.slots.none { it.startMin < 13 * 60 }, "nothing before 13:00 is touched")
        assertTrue(b.slots.any { !it.cancelled && it.type == "study" }); assertEquals(760, find(a, "CS Project").single().slot.startMin, "a block that started before 13:00 is left as it was")
    }

    // ---- RF2: what cannot move stays ----
    @Test fun RF2_a_day_with_no_goal_and_no_new_start_is_left_exactly_as_it_is() {
        assertEquals(Built(), buildDay(day(), wed, Rules()))
    }

    @Test fun RF2_commitments_the_lecture_cycle_sleep_and_dated_events_never_move() {
        val event = PatternSlot(DayOfWeek.WEDNESDAY, 1000, 1060, "Aldi run", "errand", "", false, "", date = wed)
        val base = day(extra = listOf(event))
        for (goal in listOf(Rules(wake = 11 * 60 + 40), Rules(devote = Devote(mods, 150, 10, 4, 4, 9 * 60, 21 * 60 + 30, 2)))) {
            val b = buildDay(base, wed, goal); val a = after(base, b)
            for (title in listOf("Randomness and Computation lecture", "Notes and problems: Randomness", "Shutdown", "Aldi run"))
                assertEquals(dated(base).filter { it.slot.title == title }.map { it.start to it.end }, find(a, title).map { it.start to it.end }, "$title moved")
        }
    }

    // ---- RF3: devote the window to whole study blocks ----
    @Test fun RF3_the_window_is_given_to_whole_blocks_by_module_around_the_fixed_things_and_counting_the_lecture_cycle() {
        val base = day()
        val b = buildDay(base, wed, Rules(devote = Devote(mods, 150, 10, 3, 3, 9 * 60, 21 * 60 + 30, 2)))
        val blocks = b.slots.filter { !it.cancelled && it.type == "study" }
        assertEquals(2, blocks.size, "3 a day, and the lecture cycle is one of them")
        assertTrue(blocks.all { it.endMin - it.startMin == 150 }); assertTrue(blocks.all { it.title.endsWith(": notes and problems") && it.title.substringBefore(":") in mods })
        assertTrue(blocks.all { it.notes.contains("Notes ") && it.notes.contains("Video or slides") }, "each block says how its time is spent")
        assertTrue(CS_AND_EVENING_WORK_GONE(base, b), "the ordinary work blocks in the window were replaced")
        assertEquals(emptyList(), checkPlan(base, Compiled(slots = b.slots), Instance(Preset.CATCH_UP, wed, wed)))
    }

    private fun CS_AND_EVENING_WORK_GONE(base: List<PatternSlot>, b: Built) = find(after(base, b), "CS Project").isEmpty() && find(after(base, b), "Evening work").isEmpty()

    @Test fun RF3_meals_and_the_chore_keep_their_place_when_nothing_forces_them_to_move() {
        val base = day(); val b = buildDay(base, wed, Rules(devote = Devote(mods, 150, 10, 3, 3, 9 * 60, 21 * 60 + 30, 2)))
        val a = after(base, b)
        assertEquals(735, find(a, "Lunch").single().slot.startMin); assertEquals(1080, find(a, "Dinner").single().slot.startMin); assertEquals(1110, find(a, "Wash dishes + tidy").single().slot.startMin)
        assertTrue(b.slots.none { it.title == "Lunch" || it.title == "Dinner" }, "an unchanged block is not cancelled and added again")
    }

    @Test fun RF3_a_block_is_followed_by_a_reset_and_nothing_overlaps() {
        val base = day(); val b = buildDay(base, wed, Rules(devote = Devote(mods, 150, 10, 4, 4, 9 * 60, 21 * 60 + 30, 2)))
        val a = after(base, b); val d = dated(a).filter { it.slot.type != "free" }
        assertTrue(d.any { it.slot.title == RESET_TITLE }); d.zipWithNext().forEach { (x, y) -> assertTrue(y.slot.startMin >= x.slot.endMin, "${x.slot.title} overlaps ${y.slot.title}") }
    }

    @Test fun RF3_protected_free_time_is_never_turned_into_study() {
        val base = day(); val keep = wed to 900
        val b = buildDay(base, wed, Rules(devote = Devote(mods, 150, 10, 4, 4, 9 * 60, 21 * 60 + 30, 2), protectedFree = setOf(keep)))
        assertTrue(find(after(base, b), "Free time - phone open").isNotEmpty(), "the protected 15:00 to 16:00 free time is still there")
        assertTrue(b.slots.none { it.cancelled && it.title == "Free time - phone open" })
    }

    @Test fun RF3_when_the_floor_cannot_fit_the_day_says_how_many_are_missing() {
        val busy = day(extra = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 540 + 0, 1290, "All day training", "training", "", false, "", date = wed)))
        val b = buildDay(busy, wed, Rules(devote = Devote(mods, 150, 10, 4, 4, 9 * 60, 21 * 60 + 30, 2)))
        assertTrue(b.problems.any { it.contains("more study block") }, b.problems.toString())
    }

    @Test fun RF3_a_block_is_never_shorter_than_the_least_that_is_worth_it() {
        val tight = day(withCycle = false, extra = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 570, 1290, "Match", "match", "", false, "", date = wed)))
        val b = buildDay(tight, wed, Rules(devote = Devote(mods, 150, 10, 1, 1, 9 * 60, 21 * 60 + 30, 2)))
        assertTrue(b.slots.filter { !it.cancelled && it.type == "study" }.all { it.endMin - it.startMin >= MIN_STUDY_BLOCK_MIN })
    }

    @Test fun RF5_only_the_floor_is_placed_when_the_backlog_is_already_covered() {
        val base = day(withCycle = false)
        val floorOnly = buildDay(base, wed, Rules(devote = Devote(mods, 150, 10, 2, 4, 9 * 60, 21 * 60 + 30, 2, backlogMin = 0)))
        val stretched = buildDay(base, wed, Rules(devote = Devote(mods, 150, 10, 2, 4, 9 * 60, 21 * 60 + 30, 2, backlogMin = 10_000)))
        assertEquals(2, floorOnly.blocks); assertTrue(stretched.blocks > 2, "stretch ${stretched.blocks}")
    }

    @Test fun RF5_no_module_gets_more_than_its_cap_in_a_day() {
        val b = buildDay(day(withCycle = false), wed, Rules(devote = Devote(mods, 150, 10, 4, 4, 9 * 60, 21 * 60 + 30, 1)))
        b.slots.filter { !it.cancelled && it.type == "study" }.groupBy { it.title }.values.forEach { assertEquals(1, it.size) }
    }

    // ---- RF4: a new start for the day, and everything movable follows ----
    @Test fun RF4_a_later_start_moves_the_morning_routine_and_leaves_the_rest_where_it_still_fits() {
        val base = day(withCycle = false); val b = buildDay(base, wed, Rules(wake = 10 * 60)); val a = after(base, b)
        assertEquals(listOf(600, 620, 650), listOf("Wake, shower", "Bible study", "Breakfast, dress, bag").map { find(a, it).single().slot.startMin })
        assertEquals(735, find(a, "Lunch").single().slot.startMin, "lunch still fits where it was"); assertEquals(1080, find(a, "Dinner").single().slot.startMin)
        assertTrue(find(a, "Randomness and Computation lecture").isNotEmpty(), "the lecture before the start is still on the schedule, as a missed block")
    }

    @Test fun RF4_a_day_that_starts_after_the_breakfast_cut_off_has_no_breakfast_and_says_so() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 12 * 60 + 15))
        assertTrue("$wed|Breakfast, dress, bag" in b.droppedMeals); assertTrue(b.dropped.any { it.contains("Breakfast") && it.contains("12:15") })
        assertTrue(find(after(base, b), "Breakfast, dress, bag").isEmpty()); assertEquals(755, find(after(base, b), "Bible study").single().slot.startMin.let { it }, "wake 12:15, wake takes 20 minutes")
    }

    @Test fun RF4_a_meal_that_cannot_stay_slides_within_its_window_and_one_that_has_no_window_is_dropped() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 12 * 60 + 15)); val a = after(base, b)
        val lunch = find(a, "Lunch").single().slot
        assertTrue(lunch.startMin in 785..915, "lunch slid to ${lunch.startMin}"); assertEquals(25, lunch.endMin - lunch.startMin)
        val early = buildDay(base, wed, Rules(wake = 11 * 60 + 40 - 0)); assertTrue(early.droppedMeals.none { it.contains("Lunch") } || find(after(base, early), "Lunch").isEmpty())
    }

    @Test fun RF4_the_work_keeps_its_order_and_its_length_where_there_is_room_and_is_trimmed_or_dropped_where_there_is_not() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 12 * 60 + 15)); val a = after(base, b)
        val project = find(a, "CS Project").singleOrNull()?.slot
        assertTrue(project == null || project.endMin - project.startMin <= 140)
        find(a, "Evening work").singleOrNull()?.let { assertTrue(it.slot.startMin >= 1125 - 5 && it.slot.endMin - it.slot.startMin == 135) }
        dated(a).filter { it.slot.type != "free" }.zipWithNext().forEach { (x, y) -> assertTrue(y.slot.startMin >= x.slot.endMin, "${x.slot.title} overlaps ${y.slot.title}") }
    }

    @Test fun RF4_a_start_that_runs_into_a_fixed_commitment_is_refused_with_the_reason() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 9 * 60 + 40))
        assertTrue(b.slots.isEmpty()); assertTrue(b.problems.single().contains("cannot start at 10:00"), "09:40 is inside the lecture, so the day starts when it ends: ${b.problems}")
    }

    @Test fun RF4_the_guard_rails_let_a_meal_move_but_not_vanish_unless_the_plan_says_so() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 12 * 60 + 15))
        val c = Compiled(slots = b.slots, droppedMeals = b.droppedMeals); val inst = Instance(Preset.LATE_START, wed, wed)
        assertEquals(emptyList(), checkPlan(base, c, inst).filter { it.contains("protected") || it.contains("routine") }, "declared")
        assertTrue(checkPlan(base, c.copy(droppedMeals = emptySet()), inst).any { it.contains("protected") || it.contains("routine") }, "a drop nobody said is refused")
        val sleep = Compiled(slots = listOf(base.first { it.type == "sleep" }.copy(date = wed, cancelled = true)))
        assertTrue(checkPlan(base, sleep, inst).any { it.contains("protected") }, "sleep never moves")
    }

    // ---- RF6: the late start preset ----
    @Test fun RF6_late_start_needs_the_wall_when_the_day_starts_later_than_planned_and_says_what_it_gives_up() {
        val inst = Instance(Preset.LATE_START, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21), mapOf("wake" to "12:15"))
        val p = previewOf(inst, WeekB.slots, emptyList(), at(LocalDate.of(2026, 11, 16), LocalTime.of(9, 0)))
        assertEquals(emptyList(), p.problems); assertTrue(p.wallNeeded, "later than planned is a loosening")
        assertTrue(p.compiled.loosening.any { it.contains("starts at 12:15") }, p.compiled.loosening.toString())
    }

    @Test fun RF6_a_start_that_is_no_later_than_planned_loosens_nothing() {
        val inst = Instance(Preset.LATE_START, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21), mapOf("wake" to "07:00"))
        val p = previewOf(inst, WeekB.slots, emptyList(), at(LocalDate.of(2026, 11, 16), LocalTime.of(9, 0)))
        assertFalse(p.wallNeeded, p.compiled.loosening.toString())
    }

    @Test fun RF6_a_start_that_has_already_passed_today_is_refused() {
        val today = LocalDate.of(2026, 11, 16)
        val c = compile(Instance(Preset.LATE_START, today, today, mapOf("wake" to "10:00")), WeekB.slots, emptyList(), at(today, LocalTime.of(11, 0)))
        assertTrue(c.problems.any { it.contains("already passed") }); assertTrue(c.slots.isEmpty())
    }

    // ---- RN1: start the day from now ----
    private fun nowAt(h: Int, m: Int = 0) = at(wed, LocalTime.of(h, m))
    /** The small day, plus a long free time on another day so that this day's free time is not the protected 6 hours of the week (G3). */
    private fun week(withCycle: Boolean = true, extra: List<PatternSlot> = emptyList()) = day(withCycle, extra) + PatternSlot(DayOfWeek.THURSDAY, 600, 1000, "Free time - phone open", "free", "", false, "")

    @Test fun RN1_a_day_started_from_now_begins_at_the_next_5_minutes_and_needs_no_wall_for_the_time_already_gone() {
        val inst = Instance(Preset.LATE_START, wed, wed, mapOf("from_now" to "true"))
        val p = previewOf(inst, week(withCycle = false), emptyList(), nowAt(14, 32))
        assertEquals(emptyList(), p.problems); assertTrue(p.compiled.loosening.none { it.startsWith("Breakfast") || it.contains("starts at") }, "neither the lost breakfast nor the later start is a loosening: ${p.compiled.loosening}")
        val a = applied(week(withCycle = false), p.compiled)
        assertEquals(14 * 60 + 35, slotsFor(a, wed).first { it.slot.title.startsWith("Wake") }.slot.startMin)
        assertTrue(slotsFor(a, wed).none { it.slot.title.startsWith("Breakfast") }); assertTrue(p.compiled.lines.first().contains("14:35"), p.compiled.lines.first())
    }

    @Test fun RN1_when_nothing_has_been_eaten_the_first_meal_can_come_late_instead_of_being_dropped() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 14 * 60 + 30))
        assertTrue(b.droppedMeals.none { it.contains("Lunch") }, "lunch is not dropped: ${b.dropped}")
        val lunch = find(after(base, b), "Lunch").single().slot
        assertTrue(lunch.startMin >= 14 * 60 + 30 + 50 && lunch.startMin <= MEAL_LATEST_START, "lunch at ${lunch.startMin}")
    }

    @Test fun RN1_start_from_now_is_for_today_and_a_start_that_is_not_from_now_still_refuses_a_time_that_has_passed() {
        val tomorrow = wed.plusDays(1)
        assertTrue(validate(Instance(Preset.LATE_START, tomorrow, tomorrow, mapOf("from_now" to "true")), wed).any { it.contains("for today") })
        val p = previewOf(Instance(Preset.LATE_START, wed, wed, mapOf("wake" to "10:00")), day(), emptyList(), nowAt(11))
        assertFalse(p.canApply); assertTrue(p.problems.single().contains("already passed"))
    }

    // ---- RN2: running over ----
    private fun overrun(minutes: Int, hour: Int = 15, extra: List<PatternSlot> = emptyList()): Pair<ModPreview, List<PatternSlot>> {
        val base = week(withCycle = false, extra = extra)
        val p = previewOf(Instance(Preset.RUN_OVER, wed, wed, mapOf("minutes" to minutes.toString())), base, emptyList(), nowAt(hour))
        return p to applied(base, p.compiled)
    }

    @Test fun RN2_running_over_uses_free_time_first_and_leaves_everything_that_still_fits_where_it_is() {
        val (p, a) = overrun(30)
        assertEquals(emptyList(), p.problems); assertFalse(p.wallNeeded)
        assertEquals(1080, find(a, "Dinner").single().slot.startMin); assertEquals(1125, find(a, "Evening work").single().slot.startMin)
        val free = find(a, "Free time").map { it.slot }.first { it.startMin < 1000 }; assertEquals(930, free.startMin, "the free hour starts after the overrun"); assertEquals(960, free.endMin)
        assertTrue(find(a, "CS Project").single().slot.startMin == 760, "what already started is not touched")
    }

    @Test fun RN2_a_long_overrun_slides_the_dinner_and_the_work_after_it_and_keeps_every_block_from_overlapping() {
        val (p, a) = overrun(180, hour = 16)
        assertEquals(emptyList(), p.problems)
        assertEquals(1140, find(a, "Dinner").single().slot.startMin); assertEquals(25 + 5, find(a, "Dinner").single().slot.let { it.endMin - it.startMin })
        dated(a).filter { it.slot.type != "free" }.zipWithNext().forEach { (x, y) -> assertTrue(y.slot.startMin >= x.slot.endMin, "${x.slot.title} overlaps ${y.slot.title}") }
        assertEquals(1380, find(a, "Shutdown").single().slot.startMin, "sleep never moves")
    }

    @Test fun RN2_a_commitment_inside_the_overrun_stays_where_it_is_and_the_preview_says_so() {
        val (p, a) = overrun(60, extra = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 930, 990, "Team meeting", "errand", "", false, "", date = wed)))
        assertEquals(930, find(a, "Team meeting").single().slot.startMin)
        assertTrue(p.compiled.lines.any { it.contains("Team meeting") && it.contains("not moved") }, p.compiled.lines.toString())
    }

    @Test fun RN2_work_that_no_longer_fits_is_shortened_with_the_reason_and_needs_the_wall() {
        val (p, a) = overrun(180, hour = 18)
        assertTrue(p.compiled.lines.any { it.contains("Evening work") && it.contains("shortened") }, p.compiled.lines.toString()); assertTrue(p.wallNeeded)
        assertTrue(find(a, "Evening work").single().slot.let { it.endMin - it.startMin } < 135)
    }

    @Test fun RN2_running_over_is_for_today_only_and_changing_nothing_is_not_offered() {
        assertTrue(validate(Instance(Preset.RUN_OVER, wed.plusDays(1), wed.plusDays(1), mapOf("minutes" to "30")), wed).any { it.contains("today only") })
        val pv = previewOf(Instance(Preset.RUN_OVER, wed, wed, mapOf("minutes" to "5")), listOf(p(450, 470, "Wake, shower", "routine"), p(1380, 1410, "Shutdown", "sleep")), emptyList(), nowAt(15))
        assertFalse(pv.canApply)
    }

    @Test fun RN2_the_same_overrun_gives_the_same_plan_and_two_overruns_on_a_day_are_refused_until_the_first_is_removed() {
        val a = overrun(30).first.compiled.slots; assertEquals(a, overrun(30).first.compiled.slots)
        val first = Instance(Preset.RUN_OVER, wed, wed, mapOf("minutes" to "30"))
        assertTrue(modifierConflicts(Instance(Preset.RUN_OVER, wed, wed, mapOf("minutes" to "45")), listOf(first)).any { it.contains("already covers") })
    }


    // ---- YP1 and YP2: the policy and the one entry, on a spread of situations (the full set of teeth) ----
    private val commitmentTypes = setOf("lecture", "tutorial", "match", "church", "mytutor", "training", "gym", "errand", "sleep", "study-standard", "study-light")

    @Test fun YP1_the_routine_gives_way_before_the_day_is_refused_breakfast_first_then_the_Bible_and_the_wake_to_their_minimum() {
        val base = day(withCycle = false, extra = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 12 * 60 + 40, 13 * 60 + 40, "Meeting", "errand", "", false, "", date = wed)))
        val b = buildDay(base, wed, Rules(wake = 12 * 60)); val a = after(base, b)
        assertEquals(emptyList(), b.problems); assertTrue(find(a, "Breakfast, dress, bag").isEmpty(), "breakfast goes first")
        assertEquals(10, find(a, "Bible study").single().slot.let { it.endMin - it.startMin }, "then the Bible is cut to its minimum of 10")
        assertEquals(20, find(a, "Wake, shower").single().slot.let { it.endMin - it.startMin }, "the wake keeps its 20 minutes when 30 are enough")
        assertTrue(b.dropped.any { it.contains("Bible") && it.contains("shortened") } && b.dropped.any { it.contains("Breakfast") }); assertTrue("$wed|Bible study" in b.droppedMeals)
        assertEquals(emptyList(), checkPlan(base, Compiled(slots = b.slots, droppedMeals = b.droppedMeals), Instance(Preset.LATE_START, wed, wed)).filter { it.contains("routine") || it.contains("protected") })
    }

    @Test fun YP1_a_start_inside_a_running_commitment_begins_when_it_ends_and_says_so() {
        val base = day(withCycle = false); val b = buildDay(base, wed, Rules(wake = 9 * 60 + 30))
        assertEquals(emptyList(), b.problems); assertTrue(b.notes.any { it.contains("runs until 10:00") }, b.notes.toString())
        assertEquals(600, find(after(base, b), "Wake, shower").single().slot.startMin)
    }

    @Test fun YP1_a_refusal_is_the_last_step_and_names_what_is_in_the_way_and_what_it_needs() {
        val base = day(withCycle = false, extra = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 12 * 60 + 10, 13 * 60, "Meeting", "errand", "", false, "", date = wed)))
        val b = buildDay(base, wed, Rules(wake = 12 * 60)); assertTrue(b.slots.isEmpty())
        assertTrue(b.problems.single().contains("Meeting is at 12:10") && b.problems.single().contains("at least 20 minutes"), b.problems.toString())
    }

    @Test fun YP2_an_event_in_free_time_changes_nothing_else_and_needs_no_wall() {
        val base = week(); val p = previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "Call", "type" to "errand", "start" to "15:00", "end" to "15:30")), base, emptyList(), at(wed.minusDays(5), LocalTime.of(9, 0)))
        assertEquals(emptyList(), p.problems); assertFalse(p.wallNeeded, p.compiled.loosening.toString())
        val a = applied(base, p.compiled); assertEquals(1080, find(a, "Dinner").single().slot.startMin); assertEquals(900, find(a, "CS Project").single().slot.endMin)
        assertEquals(900, find(a, "Call").single().slot.startMin)
    }

    @Test fun YP2_an_event_over_dinner_slides_the_dinner_and_the_chore_after_it_inside_their_window() {
        val base = week(); val p = previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "Birthday", "type" to "errand", "start" to "17:30", "end" to "19:00")), base, emptyList(), at(wed.minusDays(5), LocalTime.of(9, 0)))
        assertEquals(emptyList(), p.problems); val a = applied(base, p.compiled)
        val dinner = find(a, "Dinner").single().slot; assertTrue(dinner.endMin <= 1050 || dinner.startMin >= 1140, "dinner is not in the event: ${dinner.startMin}"); assertTrue(dinner.startMin in 1020..1080 + MEAL_FORWARD_MIN, "dinner stays in its window: ${dinner.startMin}")
        assertTrue(find(a, "Wash dishes + tidy").single().slot.startMin >= dinner.endMin, "the chore stays after the meal")
        dated(a).filter { it.slot.type != "free" }.zipWithNext().forEach { (x, y) -> assertTrue(y.slot.startMin >= x.slot.endMin, "${x.slot.title} overlaps ${y.slot.title}") }
    }

    @Test fun YP2_an_event_over_work_slides_the_work_where_there_is_room_and_loses_nothing() {
        val base = week(); val p = previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "Dentist", "type" to "errand", "start" to "12:30", "end" to "14:30")), base, emptyList(), at(wed.minusDays(5), LocalTime.of(9, 0)))
        assertEquals(emptyList(), p.problems); assertFalse(p.wallNeeded, p.compiled.loosening.toString())
        assertEquals(140, find(applied(base, p.compiled), "CS Project").single().slot.let { it.endMin - it.startMin }, "the whole block is kept and moved")
    }

    @Test fun YP2_an_event_that_leaves_no_room_shortens_the_work_with_the_reason_and_needs_the_wall() {
        val base = week(); val p = previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "Trip", "type" to "errand", "start" to "12:30", "end" to "19:30")), base, emptyList(), at(wed.minusDays(5), LocalTime.of(9, 0)))
        assertTrue(p.compiled.lines.any { it.contains("shortened") || it.contains("dropped") }, p.compiled.lines.toString()); assertTrue(p.wallNeeded)
    }

    @Test fun YP2_an_event_is_refused_over_a_commitment_sleep_the_morning_and_the_night_routine_and_when_it_is_already_over() {
        val base = week(); val now = at(wed.minusDays(5), LocalTime.of(9, 0))
        fun refuse(start: String, end: String, why: String) { val p = previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "X", "start" to start, "end" to end)), base, emptyList(), now); assertFalse(p.canApply, "$start-$end"); assertTrue(p.problems.any { it.contains(why) }, p.problems.toString()) }
        refuse("09:30", "10:30", "Randomness and Computation lecture"); refuse("23:05", "23:30", "Shutdown"); refuse("07:30", "08:00", "Wake"); refuse("22:50", "23:10", "Evening prayer")
        assertFalse(previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "", "start" to "15:00", "end" to "16:00")), base, emptyList(), now).canApply)
        assertFalse(previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "X", "start" to "16:00", "end" to "15:00")), base, emptyList(), now).canApply)
        assertFalse(previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "X", "start" to "10:00", "end" to "11:00")), base, emptyList(), nowAt(12)).canApply, "already over")
    }

    @Test fun YP2_an_event_may_take_protected_free_time_because_it_is_your_own_choice() {
        val base = day(); val p = previewOf(Instance(Preset.ADD_EVENT, wed, wed, mapOf("title" to "Cinema", "type" to "errand", "start" to "15:00", "end" to "16:00")), base, emptyList(), at(wed.minusDays(5), LocalTime.of(9, 0)))
        assertEquals(emptyList(), p.problems); assertTrue(find(applied(base, p.compiled), "Cinema").isNotEmpty())
    }

    /** The sweep: every kind of disturbance, on every day of four weeks, at many times. It never throws, never goes silent, and never moves what must not move. */
    @Test fun YP3_every_disturbance_on_every_day_either_plans_cleanly_or_says_why_not_and_never_moves_a_commitment_or_sleep() {
        val base = WeekB.slots; var planned = 0; var refused = 0
        for (offset in 0L..27L) {
            val date = LocalDate.of(2026, 11, 16).plusDays(offset)
            val cases = ArrayList<Pair<Instance, java.time.ZonedDateTime>>()
            for (h in listOf(7, 9, 11, 13, 15, 17, 19, 21)) {
                val n = at(date, LocalTime.of(h, 10))
                cases += Instance(Preset.LATE_START, date, date, mapOf("from_now" to "true")) to n
                for (m in listOf(15, 45, 120)) cases += Instance(Preset.RUN_OVER, date, date, mapOf("minutes" to "$m")) to n
            }
            for (w in listOf("07:00", "09:00", "11:00", "13:00", "15:00", "17:00")) cases += Instance(Preset.LATE_START, date, date, mapOf("wake" to w)) to at(date.minusDays(3), LocalTime.of(9, 0))
            for ((s, e) in listOf("09:00" to "10:00", "12:00" to "14:00", "15:00" to "17:30", "18:00" to "20:00", "19:00" to "22:00"))
                cases += Instance(Preset.ADD_EVENT, date, date, mapOf("title" to "Event", "type" to "errand", "start" to s, "end" to e)) to at(date.minusDays(3), LocalTime.of(9, 0))
            for ((inst, now) in cases) {
                val p = previewOf(inst, base, emptyList(), now)
                if (!p.canApply) { refused++; assertTrue(p.problems.isNotEmpty(), "${inst.preset} ${inst.settings} on $date at ${now.toLocalTime()} is refused with no reason"); continue }
                planned++; val after = applied(base, p.compiled)
                val before = slotsFor(base, date).filter { it.slot.type in commitmentTypes }.map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
                val now2 = slotsFor(after, date).map { Triple(it.slot.title, it.slot.startMin, it.slot.endMin) }
                before.forEach { b -> assertTrue(b in now2, "${b.first} moved by ${inst.preset} ${inst.settings} on $date at ${now.toLocalTime()}") }
            }
        }
        assertTrue(planned > 200, "planned $planned, refused $refused"); assertTrue(refused > 0)
    }

    // ---- YP4: the rest of the family, on the same engine and policy ----
    private val longAgo = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
    private fun prev(inst: Instance, now: java.time.ZonedDateTime = longAgo) = previewOf(inst, WeekB.slots, emptyList(), now)

    @Test fun YP4_a_cancelled_lecture_becomes_free_time_needs_the_wall_and_makes_no_work() {
        val d = LocalDate.of(2026, 11, 17); val p = prev(Instance(Preset.CANCEL, d, d, mapOf("what" to "Game Theory")))
        assertEquals(emptyList(), p.problems); assertTrue(p.wallNeeded); assertTrue(p.compiled.tasks.isEmpty())
        val a = applied(WeekB.slots, p.compiled); val day = slotsFor(a, d)
        assertTrue(day.none { it.slot.title.startsWith("Game Theory and Multicriteria Decision Making lecture") }); assertTrue(day.any { it.slot.title == "Free time (cancelled)" && it.slot.startMin == 600 })
        assertTrue(day.any { it.slot.type == "meal" } && day.any { it.slot.type == "sleep" }, "nothing else moved")
    }

    @Test fun YP4_a_cancellation_that_matches_nothing_or_names_nothing_is_refused_and_sleep_and_meals_cannot_be_cancelled() {
        val d = LocalDate.of(2026, 11, 17)
        assertFalse(prev(Instance(Preset.CANCEL, d, d, mapOf("what" to "Quantum"))).canApply)
        assertFalse(prev(Instance(Preset.CANCEL, d, d, mapOf("what" to ""))).canApply)
        assertFalse(prev(Instance(Preset.CANCEL, d, d, mapOf("what" to "Lunch"))).canApply, "a meal is not a commitment")
        assertFalse(prev(Instance(Preset.CANCEL, d, d.plusDays(1), mapOf("what" to "lecture"))).canApply, "one day")
    }

    @Test fun YP4_recovery_holds_work_to_a_small_amount_and_every_hour_set_aside_comes_back_as_work_after_the_last_day() {
        val s = LocalDate.of(2026, 11, 17); val p = prev(Instance(Preset.RECOVERY, s, s.plusDays(2), mapOf("work_h_max" to "2.0")))
        assertEquals(emptyList(), p.problems); assertTrue(p.wallNeeded)
        val after = applied(WeekB.slots, p.compiled)
        for (i in 0..2L) { val day = slotsFor(after, s.plusDays(i)).filter { it.slot.type in CUTTABLE_WORK }; assertTrue(day.sumOf { it.slot.endMin - it.slot.startMin } <= 120, "day $i") }
        val setAside = p.compiled.slots.filter { it.type == "free" && it.title == "Free time (recovery)" }.sumOf { it.endMin - it.startMin } / 60.0
        assertEquals(setAside, p.compiled.tasks.sumOf { it.hours }, 0.001); assertTrue(p.compiled.tasks.all { it.due.toLocalDate() > s.plusDays(2) })
        assertTrue(slotsFor(after, s).any { it.slot.type == "lecture" } && slotsFor(after, s).any { it.slot.type == "meal" } && slotsFor(after, s).any { it.slot.type == "routine" }, "lectures, meals and the routine stay")
    }

    @Test fun YP4_away_pauses_lectures_lightens_work_places_the_travel_and_what_is_missed_comes_back_unless_it_is_a_holiday() {
        val s = LocalDate.of(2026, 11, 17); val e = s.plusDays(2)
        val short = prev(Instance(Preset.AWAY, s, e, mapOf("lectures" to "running", "depart" to "09:00", "back" to "17:00", "travel_h" to "2.0")))
        assertEquals(emptyList(), short.problems, short.compiled.problems.toString()); assertTrue(short.wallNeeded)
        val after = applied(WeekB.slots, short.compiled)
        assertTrue((0..2L).all { slotsFor(after, s.plusDays(it)).none { b -> b.slot.type == "lecture" } }, "lectures paused")
        assertTrue(slotsFor(after, s).any { it.slot.title == "Travel out" && it.slot.startMin == 540 && it.slot.endMin == 660 }); assertTrue(slotsFor(after, e).any { it.slot.title == "Travel home" && it.slot.endMin == 1020 })
        assertTrue(short.compiled.tasks.isNotEmpty(), "missed lectures come back as work")
        val holiday = prev(Instance(Preset.AWAY, s, e, mapOf("lectures" to "none", "depart" to "09:00", "back" to "17:00", "travel_h" to "2.0")))
        assertTrue(holiday.compiled.tasks.none { it.title.contains("lecture", true) }, "a holiday brings nothing back from lectures")
    }

    @Test fun YP4_guests_get_preparation_before_they_arrive_the_visit_and_tidying_after() {
        val d = LocalDate.of(2026, 11, 21); val p = prev(Instance(Preset.GUESTS, d, d, mapOf("title" to "Anna and Tom", "from" to "18:00", "until" to "21:00", "lead_days" to "3", "after_min" to "20")))
        assertEquals(emptyList(), p.problems, p.compiled.problems.toString())
        val all = p.compiled.slots.filter { !it.cancelled }
        assertTrue(all.any { it.title == "Anna and Tom" && it.date == d && it.startMin == 1080 && it.endMin == 1260 }, "the visit")
        val prep = all.filter { it.title.startsWith("Anna and Tom: ") && it.type == "chore" && !it.title.endsWith("tidy up") }
        assertEquals(5, prep.size, "every preparation job is placed"); assertTrue(prep.all { it.date!! < d || (it.date == d && it.endMin <= 1080) }, "all of it before they arrive")
        assertTrue(all.any { it.title == "Anna and Tom: tidy up" && it.startMin == 1260 })
        assertEquals(emptyList(), checkPlan(WeekB.slots, p.compiled, Instance(Preset.GUESTS, d, d)), "every guard rail holds")
    }

    @Test fun YP4_guests_who_stay_have_a_block_each_day_and_a_job_that_has_no_room_is_said() {
        val s = LocalDate.of(2026, 11, 21); val p = prev(Instance(Preset.GUESTS, s, s.plusDays(2), mapOf("from" to "18:00", "until" to "12:00", "lead_days" to "0", "prep" to "Everything|900")))
        val visit = p.compiled.slots.filter { !it.cancelled && it.title == "Guests" }
        assertTrue(visit.any { it.date == s && it.startMin == 1080 }, "the arrival evening"); assertTrue(visit.none { it.startMin < 16 * 60 }, "never a whole day: you carry on with yours while they are there")
        assertTrue(visit.all { it.date!! in s..s.plusDays(2) })
        assertTrue(p.compiled.lines.any { it.contains("No room for") }, p.compiled.lines.toString())
        assertFalse(prev(Instance(Preset.GUESTS, s, s, mapOf("from" to "20:00", "until" to "19:00"))).canApply)
    }

    @Test fun YP4_revision_plans_each_exam_backwards_as_revision_sessions_and_holds_every_rule_the_assignment_planner_does() {
        val inst = Instance(Preset.REVISION, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 20), emptyMap(), listOf(
            mapOf("title" to "Game Theory exam", "due" to "2026-12-10", "due_time" to "09:30", "size_h" to "12.0"), mapOf("title" to "Neural Computation exam", "due" to "2026-12-14", "due_time" to "13:30", "size_h" to "12.0")))
        val p = prev(inst); assertEquals(emptyList(), p.problems)
        val sessions = p.compiled.slots.filter { it.type == "study" && it.title.endsWith(": revision") }
        assertEquals(24 * 60, sessions.sumOf { it.endMin - it.startMin }); assertTrue(sessions.map { it.title.substringBefore(":") }.toSet().size == 2, "both exams get sessions")
        assertTrue(sessions.filter { it.title.startsWith("Game Theory") }.all { it.date!!.atTime(it.endMin / 60, it.endMin % 60) <= LocalDate.of(2026, 12, 9).atTime(9, 30) }, "finished the day before")
        assertEquals(2, p.compiled.tasks.size); assertTrue(validate(inst.copy(items = emptyList()), LocalDate.of(2026, 11, 2)).any { it.contains("at least one exam") })
    }

    @Test fun YP4_the_whole_family_on_the_real_week_either_plans_cleanly_or_says_why() {
        val d = LocalDate.of(2026, 11, 19); var planned = 0
        for (offset in 0L..20L) {
            val day = d.plusDays(offset)
            val cases = listOf(
                Instance(Preset.CANCEL, day, day, mapOf("what" to "lecture")), Instance(Preset.RECOVERY, day, day.plusDays(1)), Instance(Preset.AWAY, day, day.plusDays(1)),
                Instance(Preset.GUESTS, day, day), Instance(Preset.GUESTS, day, day.plusDays(1), mapOf("until" to "12:00")))
            for (inst in cases) {
                val p = prev(inst); if (!p.canApply) { assertTrue(p.problems.isNotEmpty(), "${inst.preset} on $day is refused with no reason"); continue }
                planned++; val after = applied(WeekB.slots, p.compiled)
                for (x in inst.dates()) {
                    assertEquals(slotsFor(WeekB.slots, x).filter { it.slot.type == "sleep" }.map { it.slot.startMin }, slotsFor(after, x).filter { it.slot.type == "sleep" }.map { it.slot.startMin }, "${inst.preset} moved sleep on $x")
                    val meals = slotsFor(WeekB.slots, x).filter { it.slot.type == "meal" && "$x|${it.slot.title}" !in p.compiled.droppedMeals }.map { it.slot.title }; assertTrue(slotsFor(after, x).map { it.slot.title }.containsAll(meals), "${inst.preset} lost a meal on $x without saying so")
                }
            }
        }
        assertTrue(planned > 40, "planned $planned")
    }
    // ---- RF7: it holds on the real week, for any span ----
    @Test fun RF7_a_catch_up_span_of_any_length_keeps_every_commitment_and_never_overlaps() {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
        for (len in listOf(1, 3, 7, 10, 28)) for (startOffset in listOf(14, 15, 17)) {
            val start = LocalDate.of(2026, 11, 2).plusDays(startOffset.toLong()); val inst = Instance(Preset.CATCH_UP, start, start.plusDays((len - 1).toLong()))
            val p = previewOf(inst, WeekB.slots, listOf(DueWork("Backlog", 40.0, 8, at(start.plusDays(40), LocalTime.of(17, 0)))), now)
            assertEquals(emptyList(), p.problems, "$len days from $start"); val after = applied(WeekB.slots, p.compiled)
            for (date in inst.dates()) {
                val before = slotsFor(WeekB.slots, date).filter { it.slot.type in setOf("lecture", "tutorial", "match", "church", "mytutor", "training", "gym", "errand", "sleep", "study-standard", "study-light") }
                val now2 = slotsFor(after, date)
                before.forEach { b -> assertTrue(now2.any { it.slot.title == b.slot.title && it.slot.startMin == b.slot.startMin && it.slot.endMin == b.slot.endMin }, "${b.slot.title} on $date moved") }
            }
        }
    }

    @Test fun RF7_a_late_start_on_every_day_of_a_month_is_either_planned_cleanly_or_refused_with_a_reason() {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0)); var planned = 0; var refused = 0
        for (offset in 14L..41L) {
            val d = LocalDate.of(2026, 11, 2).plusDays(offset)
            for (wake in listOf("08:00", "10:00", "12:00", "13:30")) {
                val p = previewOf(Instance(Preset.LATE_START, d, d, mapOf("wake" to wake)), WeekB.slots, emptyList(), now)
                if (p.problems.isEmpty() && p.compiled.problems.isEmpty()) planned++ else { refused++; assertTrue(p.problems.isNotEmpty() || p.compiled.problems.isNotEmpty()) }
            }
        }
        assertTrue(planned > 0, "some of them plan ($planned planned, $refused refused)")
    }

    @Test fun RF7_the_same_span_gives_the_same_plan_every_time() {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0)); val inst = Instance(Preset.CATCH_UP, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22))
        assertEquals(previewOf(inst, WeekB.slots, emptyList(), now).compiled.slots, previewOf(inst, WeekB.slots, emptyList(), now).compiled.slots)
    }

    // ---- RF8: the numbers ----
    @Test fun RF8_every_preview_says_the_hours_a_day_the_cycles_and_the_notes_before_and_after() {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
        val p = previewOf(Instance(Preset.CATCH_UP, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18)), WeekB.slots, emptyList(), now)
        val s = p.summary!!; val text = s.lines().joinToString("\n")
        assertTrue(Regex("Study and work: [0-9.]+h over 3 days, about [0-9.]+h a day \\(it was [0-9.]+h\\)").containsMatchIn(text), text)
        assertTrue(text.contains("study cycle") && text.contains("of notes"), text); assertTrue(text.contains("Free time:"), text)
        assertTrue(text.contains("Mon 16 Nov:"), "each day is counted"); assertEquals(3, s.days.size); assertEquals(3, s.daysBefore.size)
        assertEquals(s.days.sumOf { it.workMin }, s.workMin); assertTrue(s.byModule.isNotEmpty())
    }

    @Test fun RF8_a_span_that_changes_nothing_is_not_offered_and_says_why() {
        val today = LocalDate.of(2026, 11, 16)
        val p = previewOf(Instance(Preset.LATE_START, today, today, mapOf("wake" to "10:00")), WeekB.slots, emptyList(), at(today, LocalTime.of(11, 0)))
        assertFalse(p.canApply); assertTrue(p.problems.single().contains("already passed"), p.problems.toString())
    }

    @Test fun RF8_a_lecture_free_week_shows_its_hours_too() {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(9, 0))
        val text = previewOf(Instance(Preset.LECTURE_FREE, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 13), mapOf("work_h_max" to "3.0", "cycles_per_day" to "2")), WeekB.slots, emptyList(), now).summary!!.lines().joinToString("\n")
        assertTrue(text.contains("Study and work:") && text.contains("a day"), text)
    }

    @Test fun RF8_the_steps_of_a_cycle_scale_to_the_length_of_the_block() {
        assertEquals(140, cycleSteps(140).sumOf { it.second }); assertEquals(60, notesMinutes(140))
        assertTrue(cycleSteps(150).sumOf { it.second } in 149..151); assertTrue(cycleStepsText(90).startsWith("Video or slides"))
    }

    // ---- RF9: a day laid out again is all or nothing when the calendar changes under it ----
    @Test fun RF9_if_part_of_a_reflowed_day_cannot_stand_the_whole_day_goes_back_to_the_plan_underneath() {
        val base = day(); val b = buildDay(base, wed, Rules(wake = 12 * 60 + 15))
        val import = PatternSlot(DayOfWeek.WEDNESDAY, 790, 850, "New event", "errand", "", false, "", date = wed)
        val r = reconcile(base + import, b.slots, allowOnDayOff = false)
        assertTrue(r.dropped > 0)
        assertTrue(r.kept.none { it.cancelled && it.type == "meal" || it.cancelled && it.type == "routine" }, "no meal or routine is left cancelled with nothing in its place")
    }
}
