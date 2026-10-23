package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class PlanTest {
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    // A tiny pattern with exact numbers: Monday 19 Oct has 8 h of study (10:00-14:00 and 15:00-19:00) and a wake at 07:30.
    private val small = listOf(
        PatternSlot(DayOfWeek.MONDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
        PatternSlot(DayOfWeek.MONDAY, 600, 840, "Project", "study", "", false, ""),
        PatternSlot(DayOfWeek.MONDAY, 900, 1140, "Project", "study", "", false, ""),
        PatternSlot(DayOfWeek.MONDAY, 840, 900, "Free time - phone open", "free", "", false, ""),
        PatternSlot(DayOfWeek.TUESDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
    )
    private fun value(s: BufferState) = (s as BufferState.Value).result

    @Test fun `D2 quick-add is title deadline and one slider with defaults`() {
        assertEquals(5, QUICK_ADD_DEFAULT_HEAVINESS)
        assertEquals(6.25, quickAddHours(5), 1e-9)
        assertEquals(12.5, quickAddHours(10), 1e-9)
        assertFailsWith<IllegalArgumentException> { quickAddHours(0) }
    }

    @Test fun `B1 Buffer is work-capable time before the deadline minus work`() {
        val due = DueWork("Report", quickAddHours(4), 4, t(19, 19))            // 5 h of work
        val r = value(bufferNow(t(19, 8), listOf(due), small, shortSleep = false))
        assertEquals(8.0, r.availableHours, 1e-9)                               // free time 14:00-15:00 is not available
        assertEquals(3.0, r.hours, 1e-9)
        assertEquals("Buffer +3.0h", r.label())
    }

    @Test fun `B3 working names the deadline each task and the sum`() {
        val w = value(bufferNow(t(19, 8), listOf(DueWork("Report", 5.0, 4, t(19, 19))), small, false)).working()
        assertTrue("To Mon 19 Oct 19:00" in w, w); assertTrue("Report (heaviness 4): 5.0h x 1.0 = 5.0h" in w, w)
        assertTrue("Available 8.0h" in w && "Work 5.0h" in w && w.endsWith("Buffer +3.0h"), w)
    }

    @Test fun `B1 only the time still ahead counts`() {
        val r = value(bufferNow(t(19, 16), listOf(DueWork("Report", 1.0, 4, t(19, 18))), small, false))
        assertEquals(2.0, r.availableHours, 1e-9)                               // 16:00-18:00 of the second block
    }

    @Test fun `B1 a deadline that cannot fit is NEGATIVE with the shortfall`() {
        val r = value(bufferNow(t(19, 8), listOf(DueWork("Exam prep", 12.5, 10, t(19, 19))), small, false))
        assertEquals("Buffer NEGATIVE -4.5h", r.label()); assertTrue(r.negative)
    }

    @Test fun `B9 the tightest deadline drives the number`() {
        val near = DueWork("Near", 6.0, 5, t(19, 14))      // 4 h available, 6 h of work: -2
        val far = DueWork("Far", 1.0, 5, t(19, 19))         // by 19:00 8 h available, 7 h work: +1
        val r = value(bufferNow(t(19, 10), listOf(near, far), small, false))
        assertEquals(-2.0, r.hours, 1e-9); assertEquals("Mon 19 Oct 14:00", r.deadlineLabel)
    }

    @Test fun `D6 no deadline at all shows no number`() {
        assertEquals(BufferState.NoDeadline, bufferNow(t(19, 8), emptyList(), small, false))
    }

    @Test fun `B8 a short night makes the same work heavier`() {
        val due = listOf(DueWork("Hard", 2.0, 8, t(19, 19)), DueWork("Easy", 2.0, 3, t(19, 19)))
        val normal = value(bufferNow(t(19, 8), due, small, false)).hours
        val short = value(bufferNow(t(19, 8), due, small, true)).hours
        assertEquals(8.0 - 4.0, normal, 1e-9); assertEquals(8.0 - (3.0 + 2.3), short, 1e-9)
    }

    @Test fun `B6 an overdue task is not hidden it counts against the Buffer`() {
        val r = value(bufferNow(t(19, 20), listOf(DueWork("Late", 2.0, 4, t(19, 18))), small, false))
        assertEquals(0.0, r.availableHours, 1e-9); assertTrue(r.negative)
    }

    @Test fun `D1 each lecture's study cycle is due that night's cutoff and counted`() {
        val cycles = studyCycles(WeekB.slots, t(19, 8))
        val mon = cycles.filter { it.title == "Notes and problems: Randomness" }.first()
        assertEquals(2.5, mon.hours)
        assertEquals(t(19, 23, 10), mon.due)                                    // Tue wake 07:30 - 8 h 20 min
        assertTrue(cycles.any { it.hours == 1.0 && it.heaviness == 3 })         // light cycles
    }

    @Test fun `D1 cycles already finished are not counted ahead`() {
        val after = studyCycles(WeekB.slots, t(19, 13))
        assertTrue(after.none { it.title == "Notes and problems: Randomness" && it.due == t(19, 23, 10) })
    }

    @Test fun `D1 with the real week the Buffer exists from the cycles alone`() {
        val s = bufferNow(t(19, 8), studyCycles(WeekB.slots, t(19, 8)), WeekB.slots, false)
        val r = value(s)
        assertNotNull(r.deadlineLabel); assertTrue(r.lines.isNotEmpty()); assertTrue(r.availableHours > 0)
    }

    @Test fun `F1 target wake is the planned wake time`() {
        assertEquals(LocalTime.of(7, 30), targetWake(WeekB.slots, LocalDate.of(2026, 10, 19))?.toLocalTime())
        assertEquals(LocalTime.of(8, 30), targetWake(WeekB.slots, LocalDate.of(2026, 10, 24))?.toLocalTime())
        assertEquals(LocalTime.of(23, 10), endOfDayCutoff(WeekB.slots, LocalDate.of(2026, 10, 19)).toLocalTime())
    }

    @Test fun `X4 work-capable hours on the 25 hour day`() {
        val p = listOf(PatternSlot(DayOfWeek.SUNDAY, 1380, 1500, "Late work", "study", "", false, ""))   // 23:00 to 01:00
        // Sunday 25 Oct plan 23:00 to Mon 01:00 wall clock is still 2 h; the day itself is 25 h long.
        assertEquals(2.0, workCapableHours(p, t(25, 22), t(26, 3)), 1e-9)
    }

    @Test fun `AP7 the dial fill is the share of due work that fits and is the same in every mode`() {
        assertEquals(1.0, value(bufferNow(t(19, 8), listOf(DueWork("a", 4.0, 4, t(19, 19))), small, false)).coverage, 1e-9)
        assertEquals(0.5, value(bufferNow(t(19, 8), listOf(DueWork("a", 16.0, 4, t(19, 19))), small, false)).coverage, 1e-9)
    }
}
