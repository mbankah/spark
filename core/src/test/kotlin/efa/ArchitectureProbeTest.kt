package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** Scenario checks from the architecture review: does the combination make sense, not only each part. */
class ArchitectureProbeTest {
    private val base = WeekB.slots
    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(9, 0))

    @Test fun ARCH_a_holiday_week_off_brings_back_no_lectures() {
        val holiday = Instance(Preset.PERIOD_OFF, today.plusDays(1), today.plusDays(7))               // 7 days, auto reads it as a holiday
        val p = previewOf(holiday, base, emptyList(), now)
        assertTrue(p.compiled.noDemand)
        assertEquals(emptyList(), dayOffDemand(base, emptyList(), now))
        assertTrue(p.compiled.lines.any { it.contains("No lectures are running") })
        assertEquals(p.before is BufferState.Value, p.after is BufferState.Value)
    }

    @Test fun ARCH_a_short_period_off_in_term_still_brings_lectures_back() {
        val short = Instance(Preset.PERIOD_OFF, today.plusDays(1), today.plusDays(2))
        assertFalse(previewOf(short, base, emptyList(), now).compiled.noDemand)
        val forced = Instance(Preset.PERIOD_OFF, today.plusDays(1), today.plusDays(7), mapOf("lectures" to "running"))
        assertFalse(previewOf(forced, base, emptyList(), now).compiled.noDemand)
        assertTrue(lecturesRunning(forced))
    }

    @Test fun ARCH_a_holiday_week_is_no_worse_for_the_buffer_than_before() {
        val holiday = Instance(Preset.PERIOD_OFF, today.plusDays(1), today.plusDays(7))
        val running = holiday.copy(settings = mapOf("lectures" to "running"))
        val a = previewOf(holiday, base, emptyList(), now).after
        val b = previewOf(running, base, emptyList(), now).after
        if (a is BufferState.Value && b is BufferState.Value) assertTrue(a.result.hours >= b.result.hours)
    }

    @Test fun ARCH_the_alarm_is_not_set_on_a_day_off_and_returns_when_the_day_off_goes() {
        val day = today.plusDays(1)
        val off = base + dayOffMarker(day)
        assertTrue(isDayOff(off, day))
        assertFalse(isDayOff(base, day))
        assertNull(planAlarm(at(today, LocalTime.of(23, 0)), null, off, isDayOff = true))
        assertNotNull(planAlarm(at(today, LocalTime.of(23, 0)), null, base, isDayOff = false))
    }

    @Test fun ARCH_lecture_free_and_an_assignment_can_run_together_without_lectures_coming_back() {
        val lf = Instance(Preset.LECTURE_FREE, today.plusDays(1), today.plusDays(7))
        val c = previewOf(lf, base, emptyList(), now).compiled
        val after = applied(base, c)
        for (d in lf.dates()) assertTrue(slotsFor(after, d).none { it.slot.type == "lecture" })
        val asg = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 12, 31), emptyMap(), listOf(mapOf("title" to "GT", "due" to "2026-11-20", "due_time" to "17:00", "size_h" to "6.0")))
        val both = previewOf(asg, after, emptyList(), now)
        assertTrue(both.problems.isEmpty(), both.problems.joinToString())
        assertTrue(both.compiled.slots.isNotEmpty())
    }
}

class TermStateTest {
    private val base = WeekB.slots
    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(9, 0))
    private fun item(t: String, due: String) = mapOf("title" to t, "due" to due, "due_time" to "17:00", "size_h" to "6.0")

    @Test fun ARCH_lectures_none_combines_with_an_assignment() {
        val inst = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 13), mapOf("lectures" to "none"), listOf(item("GT", "2026-11-13")))
        val p = previewOf(inst, base, emptyList(), now)
        assertTrue(p.problems.isEmpty(), p.problems.joinToString())
        val after = applied(base, p.compiled)
        for (d in inst.dates()) assertTrue(slotsFor(after, d).none { it.slot.type == "lecture" || it.slot.type == "tutorial" }, "lectures on $d")
        assertTrue(p.compiled.loosening.any { it.contains("cancelled") })
        assertTrue(p.wallNeeded)
    }

    @Test fun ARCH_lectures_none_combines_with_catch_up_and_focus() {
        val cu = Instance(Preset.CATCH_UP, today.plusDays(1), today.plusDays(7), mapOf("lectures" to "none"))
        val p = previewOf(cu, base, emptyList(), now)
        for (d in cu.dates()) assertTrue(slotsFor(applied(base, p.compiled), d).none { it.slot.type == "lecture" })
        val fo = Instance(Preset.FOCUS, today, LocalDate.of(2026, 11, 14), mapOf("lectures" to "none"), listOf(item("CS", "2026-11-14") + ("size_h" to "20.0")))
        assertTrue(previewOf(fo, base, emptyList(), now).compiled.slots.any { it.cancelled })
    }

    @Test fun ARCH_lectures_default_to_running() {
        val inst = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 13), emptyMap(), listOf(item("GT", "2026-11-13")))
        assertEquals(emptyList(), lectureCuts(inst, base))
    }
}

class ModifierClashTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun po(a: Int, b: Int) = Instance(Preset.PERIOD_OFF, today.plusDays(a.toLong()), today.plusDays(b.toLong()))
    private fun asg(t: String) = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 20), emptyMap(), listOf(mapOf("title" to t, "due" to "2026-11-20")))

    @Test fun ARCH_the_same_instance_cannot_be_applied_twice() = assertTrue(modifierConflicts(po(1, 3), listOf(po(1, 3))).isNotEmpty())
    @Test fun ARCH_two_periods_off_cannot_share_days_but_can_follow_each_other() {
        assertTrue(modifierConflicts(po(3, 6), listOf(po(1, 4))).isNotEmpty())
        assertEquals(emptyList(), modifierConflicts(po(5, 6), listOf(po(1, 4))))
    }
    @Test fun ARCH_a_new_assignment_can_be_added_but_not_planned_twice() {
        assertEquals(emptyList(), modifierConflicts(asg("RC"), listOf(asg("GT"))))
        assertTrue(modifierConflicts(asg("GT"), listOf(asg("gt "))).isNotEmpty())
    }
}

class AdminDriftTest {
    private val base = WeekB.slots
    private val state = AdminState(LoadSet.STANDARD)

    @Test fun ARCH_admin_placed_for_a_day_does_not_change_as_the_week_goes_on() {
        val mon = LocalDate.of(2026, 11, 2)
        val fromMon = placeAdmin(base, state, mon, 14).slots.filter { it.date == mon.plusDays(4) }.map { Triple(it.title, it.startMin, it.endMin) }.sortedBy { it.second }
        val fromWed = placeAdmin(base, state, mon.plusDays(2), 14).slots.filter { it.date == mon.plusDays(4) }.map { Triple(it.title, it.startMin, it.endMin) }.sortedBy { it.second }
        assertEquals(fromMon, fromWed)
    }
}

class SessionSettingsTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun asg(s: Map<String, String>) = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 20), s, listOf(mapOf("title" to "GT", "due" to "2026-11-20", "due_time" to "17:00", "size_h" to "6.0")))

    @Test fun MG8_the_shortest_session_cannot_be_longer_than_the_longest_or_the_daily_cap() {
        assertTrue(validate(asg(mapOf("min_session" to "120", "max_session" to "45")), today).any { it.contains("cannot be longer than the longest") })
        assertTrue(validate(asg(mapOf("min_session" to "200", "max_session" to "240", "daily_cap_h" to "2.0")), today).any { it.contains("daily cap") })
        assertEquals(emptyList(), validate(asg(mapOf("min_session" to "45", "max_session" to "120")), today))
    }

    @Test fun MG10_hours_that_cannot_be_planned_are_said_on_screen_not_dropped() {
        val tight = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 4), mapOf("daily_cap_h" to "1.0"), listOf(mapOf("title" to "GT", "due" to "2026-11-04", "due_time" to "17:00", "size_h" to "40.0")))
        val p = previewOf(tight, WeekB.slots, emptyList(), at(today, LocalTime.of(9, 0)))
        assertTrue(p.compiled.lines.any { it.startsWith("Not fully planned") }, p.compiled.lines.joinToString())
    }

    @Test fun MG5_no_session_is_ever_planned_under_the_shortest_piece() {
        for (size in listOf("1.0", "1.25", "2.1", "3.2", "7.4")) {
            val p = previewOf(asg(mapOf("daily_cap_h" to "3.5")).copy(items = listOf(mapOf("title" to "GT", "due" to "2026-11-20", "due_time" to "17:00", "size_h" to size))), WeekB.slots, emptyList(), at(today, LocalTime.of(9, 0)))
            assertTrue(p.canApply, "$size: ${p.problems}")
            assertTrue(p.compiled.slots.all { it.endMin - it.startMin >= MIN_PIECE_MIN })
        }
    }
}
