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
