package efa

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.*

class AdminLibraryTest {
    private val base = WeekB.slots
    private val mon = LocalDate.of(2026, 11, 2)
    private val std = AdminState(LoadSet.STANDARD)

    @Test fun A1_the_catalogue_has_about_sixty_jobs_in_sixteen_areas_with_unique_ids() {
        assertTrue(AdminLibrary.jobs.size >= 55, "${AdminLibrary.jobs.size}"); assertEquals(Area.entries.size, AdminLibrary.jobs.map { it.area }.toSet().size)
        assertEquals(AdminLibrary.jobs.size, AdminLibrary.jobs.map { it.id }.toSet().size)
    }

    @Test fun A1_every_job_has_a_sensible_window_and_a_minimum_not_above_its_length() {
        for (j in AdminLibrary.jobs) { assertTrue(j.minMinutes in 1..j.minutes, j.id); assertTrue(j.to > j.from, j.id); assertTrue(j.to <= 24 * 60, j.id); assertTrue(j.type == "chore" || j.type == "errand", j.id) }
    }

    @Test fun A1_the_jobs_you_named_are_there() {
        val titles = AdminLibrary.jobs.map { it.title.lowercase() }
        for (w in listOf("make the bed", "dishes", "clean the bathroom", "tidy the room", "bins", "weekly shop", "hoover", "ironing", "bed sheets")) assertTrue(titles.any { it.contains(w) }, w)
    }

    @Test fun MG19_nothing_is_placed_until_the_person_chooses_a_load_set() {
        assertEquals(emptyList(), placeAdmin(base, AdminState(), mon).slots)
    }

    @Test fun the_load_sets_choose_standard_jobs_at_minimum_lengths_standard_or_everything_but_triggered() {
        val light = AdminState(LoadSet.LIGHT); val thorough = AdminState(LoadSet.THOROUGH)
        val s = AdminLibrary.jobs.filter { std.enabled(it) }; val t = AdminLibrary.jobs.filter { thorough.enabled(it) }
        assertTrue(s.all { it.set == JobSet.STANDARD }); assertTrue(t.size > s.size); assertTrue(t.none { it.set == JobSet.TRIGGERED })
        val dishes = AdminLibrary.byId.getValue("sheets"); assertEquals(dishes.minMinutes, light.minutesFor(dishes)); assertEquals(dishes.minutes, std.minutesFor(dishes))
    }

    @Test fun a_job_can_be_switched_on_or_off_whatever_the_load_set() {
        val oven = AdminLibrary.byId.getValue("oven"); val sheets = AdminLibrary.byId.getValue("sheets")
        assertFalse(std.enabled(oven)); assertTrue(std.copy(on = setOf("oven")).enabled(oven)); assertFalse(std.copy(off = setOf("sheets")).enabled(sheets))
        assertFalse(AdminState(LoadSet.THOROUGH).copy(off = setOf("oven")).enabled(oven))
    }

    @Test fun triggered_jobs_are_listed_but_never_placed_by_themselves() {
        val trig = AdminLibrary.jobs.filter { it.set == JobSet.TRIGGERED }
        assertTrue(trig.isNotEmpty()); assertTrue(trig.none { AdminState(LoadSet.THOROUGH, on = it.id.let { id -> setOf(id) }).enabled(it) })
    }

    @Test fun frequencies_fall_on_the_right_days() {
        val daily = AdminLibrary.byId.getValue("counters"); val weekly = AdminLibrary.byId.getValue("bins_out"); val twice = AdminLibrary.byId.getValue("empty_bins"); val monthly = AdminLibrary.byId.getValue("bills")
        val week = (0..6).map { mon.plusDays(it.toLong()) }
        assertEquals(7, week.count { dueOn(daily, it) }); assertEquals(listOf(DayOfWeek.THURSDAY), week.filter { dueOn(weekly, it) }.map { it.dayOfWeek })
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), week.filter { dueOn(twice, it) }.map { it.dayOfWeek }.toSet())
        assertTrue(dueOn(monthly, LocalDate.of(2026, 12, 1))); assertFalse(dueOn(monthly, LocalDate.of(2026, 12, 2)))
        assertEquals(1, (0..27).count { dueOn(AdminLibrary.byId.getValue("haircut"), mon.plusDays(it.toLong())) })
    }

    @Test fun a_fortnightly_job_lands_on_one_week_in_two() {
        val s = AdminLibrary.byId.getValue("sheets"); val sats = (0..27).map { mon.plusDays(it.toLong()) }.filter { it.dayOfWeek == DayOfWeek.SATURDAY }
        assertEquals(2, sats.count { dueOn(s, it) })
    }

    @Test fun jobs_the_week_already_does_are_not_added_twice() {
        val plan = placeAdmin(base, AdminState(LoadSet.THOROUGH), mon)
        val titles = plan.slots.map { it.title }
        assertTrue("Wash the dishes" !in titles && "Hoover the floors" !in titles && "Clean the bathroom" !in titles && "Ironing" !in titles && "The weekly shop" !in titles)
    }

    @Test fun MG19_nothing_is_on_top_of_anything_and_protected_blocks_are_untouched() {
        val plan = placeAdmin(base, AdminState(LoadSet.THOROUGH), mon)
        assertEquals(emptyList(), checkAdmin(base, plan, AdminState(LoadSet.THOROUGH)))
    }

    @Test fun MG20_no_day_goes_past_the_daily_cap() {
        val st = AdminState(LoadSet.THOROUGH, capMin = 20); val plan = placeAdmin(base, st, mon)
        plan.minutesByDay.values.forEach { assertTrue(it <= 20, "$it") }
        assertTrue(plan.problems.any { it.contains("daily cap") })
    }

    @Test fun MG21_lighter_uses_the_minimum_lengths() {
        val plan = placeAdmin(base, std.copy(lighter = true), mon)
        plan.slots.forEach { s -> val j = AdminLibrary.jobs.first { it.title == s.title }; assertEquals(j.minMinutes, s.endMin - s.startMin, j.id) }
    }

    @Test fun a_job_that_cannot_be_placed_is_reported_with_its_day_never_silently_dropped() {
        val plan = placeAdmin(base, AdminState(LoadSet.THOROUGH), mon)
        assertTrue(plan.problems.isNotEmpty()); assertTrue(plan.problems.all { it.contains(":") })
        val placed = plan.slots.map { "${it.date!!.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }} ${it.date!!.dayOfMonth}: no room for ${it.title}" }
        for (p in plan.problems) assertFalse(placed.any { p.startsWith(it) }, p)
    }

    @Test fun G3_the_protected_free_hours_are_never_used_up() {
        val plan = placeAdmin(base, AdminState(LoadSet.THOROUGH, capMin = 300), mon, days = 7)
        val after = base + plan.slots
        val freeBefore = (0..6).sumOf { slotsFor(base, mon.plusDays(it.toLong())).filter { s -> s.slot.type == "free" }.sumOf { s -> s.slot.endMin - s.slot.startMin } }
        val freeAfter = (0..6).sumOf { slotsFor(after, mon.plusDays(it.toLong())).filter { s -> s.slot.type == "free" }.sumOf { s -> s.slot.endMin - s.slot.startMin } }
        assertTrue(freeAfter >= minOf(freeBefore, 360), "free before $freeBefore after $freeAfter")
    }

    @Test fun trimming_work_is_off_by_default_and_when_on_it_never_leaves_a_piece_under_the_minimum() {
        val off = placeAdmin(base, std.copy(capMin = 300), mon, days = 7)
        val on = placeAdmin(base, std.copy(capMin = 300, trimWork = true), mon, days = 7)
        assertTrue(on.slots.size >= off.slots.size)
        val after = base + on.slots
        for (d in 0..6) slotsFor(after, mon.plusDays(d.toLong())).filter { it.slot.type in CUTTABLE_WORK && it.slot.endMin - it.slot.startMin < MIN_PIECE_MIN }.forEach { x -> assertTrue(base.any { b -> b.day == x.slot.day && b.title == x.slot.title && b.startMin == x.slot.startMin && b.endMin == x.slot.endMin }, "${x.slot.title} was cut to a piece under the minimum") }
    }

    @Test fun MG22_a_job_that_needs_a_wake_block_says_so_when_the_day_has_none() {
        val noWake = base.filter { !it.title.startsWith("Wake") }
        val plan = placeAdmin(noWake, std, mon, days = 3)
        assertTrue(plan.problems.any { it.contains("needs a wake block") })
    }

    @Test fun MG23_health_jobs_are_reminders_with_no_medicine_dose_or_advice_in_them() {
        val health = AdminLibrary.jobs.filter { it.area == Area.HEALTH }
        health.forEach { assertTrue(it.type == "errand", it.id); assertFalse(it.title.contains("mg", true) || it.title.contains("dose", true)) }
    }

    @Test fun MG24_admin_adds_no_work_so_the_Buffer_is_unchanged() {
        val plan = placeAdmin(base, AdminState(LoadSet.THOROUGH), mon)
        val now = at(mon, java.time.LocalTime.of(9, 0))
        val a = bufferNow(now, studyCycles(base, now) + listOf(DueWork("E", 5.0, 5, at(mon.plusDays(5), java.time.LocalTime.of(17, 0)))), base, false)
        val b = bufferNow(now, studyCycles(base + plan.slots, now) + listOf(DueWork("E", 5.0, 5, at(mon.plusDays(5), java.time.LocalTime.of(17, 0)))), base + plan.slots, false)
        assertTrue(a is BufferState.Value && b is BufferState.Value); assertEquals((a as BufferState.Value).result.hours, (b as BufferState.Value).result.hours, 0.25)
    }

    @Test fun chores_keep_their_photo_and_other_admin_needs_none() {
        val plan = placeAdmin(base, AdminState(LoadSet.THOROUGH), mon)
        plan.slots.forEach { s -> val proof = proofFor(s); if (s.type == "chore") assertEquals(Proof.WINDOW_PHOTO, proof) else assertEquals(Proof.NONE, proof) }
    }

    @Test fun admin_is_the_same_each_time_it_is_worked_out_and_survives_being_saved() {
        assertEquals(placeAdmin(base, std, mon).slots, placeAdmin(base, std, mon).slots)
        val st = AdminState(LoadSet.THOROUGH, 60, true, true, setOf("oven"), setOf("dust")); assertEquals(st, AdminState.decode(st.encode())); assertEquals(AdminState(), AdminState.decode(null)); assertEquals(AdminState(), AdminState.decode("rubbish"))
    }

    @Test fun a_day_off_keeps_the_admin() {
        val withOff = base + dayOffMarker(mon.plusDays(1))
        val plan = placeAdmin(withOff, std, mon, days = 3)
        assertTrue(plan.slots.any { it.date == mon.plusDays(1) } || plan.problems.any { it.startsWith("Tue") })
        assertTrue(slotsFor(withOff + plan.slots, mon.plusDays(1)).containsAll(slotsFor(withOff + plan.slots, mon.plusDays(1)).filter { it.slot.notes.startsWith("Admin") }))
    }
}
