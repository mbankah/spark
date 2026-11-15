package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** WT1: choosing when the next alarm rings. */
class WakeTimeTest {
    private val mon = LocalDate.of(2026, 11, 16)
    private fun t(h: Int, m: Int = 0, day: LocalDate = mon) = at(day, LocalTime.of(h, m))
    private val slots = WeekB.slots

    @Test fun `WT1 with no sleep yet, a chosen 10 30 replaces the 12 00 backstop`() {
        val p = planAlarm(t(7, 30), null, slots, chosen = t(10, 30))
        assertEquals(t(10, 30), p?.at); assertEquals(AlarmReason.CHOSEN, p?.reason)
        assertEquals(AlarmReason.BACKSTOP, planAlarm(t(7, 30), null, slots)?.reason, "without a choice the backstop stands")
    }

    @Test fun `WT1 once asleep, a chosen time later than onset plus 8 hours is used and an earlier one never rings before the 3 hour floor`() {
        val onset = t(1, 0)
        assertEquals(t(9, 0), planAlarm(t(7, 30), onset, slots)?.at)
        assertEquals(t(10, 30), planAlarm(t(7, 30), onset, slots, chosen = t(10, 30))?.at)
        val early = planAlarm(t(2, 0), onset, slots, chosen = t(3, 0))
        assertEquals(t(4, 0), early?.at, "no sooner than 3 hours after sleep began"); assertEquals(AlarmReason.CHOSEN, early?.reason)
    }

    @Test fun `WT1 a chosen time past the 12 00 backstop, or already past, is ignored`() {
        assertEquals(AlarmReason.BACKSTOP, planAlarm(t(7, 30), null, slots, chosen = t(12, 30))?.reason)
        assertEquals(t(12, 0), planAlarm(t(7, 30), null, slots, chosen = t(12, 30))?.at)
        assertEquals(AlarmReason.BACKSTOP, planAlarm(t(7, 30), null, slots, chosen = t(7, 0))?.reason)
        assertEquals(t(12, 0), planAlarm(t(7, 30), null, slots, chosen = t(12, 0))?.at.takeIf { planAlarm(t(7, 30), null, slots, chosen = t(12, 0))?.reason == AlarmReason.CHOSEN } ?: t(12, 0))
    }

    @Test fun `WT1 a hard alarm earlier than the chosen time still rings first`() {
        val hard = slots + PatternSlot(mon.dayOfWeek, 10 * 60, 11 * 60, "MyTutor session - online", "mytutor", "", true, "", date = mon)
        val p = planAlarm(t(7, 30), null, hard, chosen = t(10, 30))
        assertEquals(AlarmReason.HARD, p?.reason); assertEquals(t(9, 30), p?.at)
    }

    @Test fun `WT1 a planned day off has no alarm, chosen time or not`() {
        val off = slots + dayOffMarker(mon)
        assertNull(planAlarmWithDaysOff(t(7, 30), null, off, chosen = t(10, 30)))
        assertEquals(t(10, 30), planAlarmWithDaysOff(t(7, 30), null, slots, chosen = t(10, 30))?.at)
    }

    @Test fun `WT1 the next clock time is today if it is ahead and tomorrow if it has passed`() {
        assertEquals(t(10, 30), nextClockTime(t(7, 30), 10, 30))
        assertEquals(t(10, 30, mon.plusDays(1)), nextClockTime(t(11, 0), 10, 30))
        assertEquals(t(10, 30, mon.plusDays(1)), nextClockTime(t(10, 30), 10, 30), "the same minute has passed")
    }

    @Test fun `WT1 the problems with a choice are said plainly`() {
        assertNull(wakeChoiceProblem(t(7, 30), null, t(10, 30)))
        assertEquals("The latest alarm is 12:00.", wakeChoiceProblem(t(7, 30), null, t(12, 30)))
        assertEquals("That time has passed.", wakeChoiceProblem(t(7, 30), null, t(7, 0)))
        assertNull(wakeChoiceProblem(t(23, 0), null, t(10, 30, mon.plusDays(1))), "in the evening the next morning is the one chosen for")
        assertEquals("The latest alarm is 12:00.", wakeChoiceProblem(t(23, 0), null, t(13, 0, mon.plusDays(1))))
    }

    @Test fun `WT1 only a wake time later than the one Spark would have chosen needs the wall`() {
        val normal = planAlarm(t(7, 30), t(1, 0), slots)
        assertTrue(wakeNeedsWall(normal, planAlarm(t(7, 30), t(1, 0), slots, chosen = t(10, 30))), "10:30 is later than 09:00")
        assertFalse(wakeNeedsWall(normal, planAlarm(t(7, 30), t(1, 0), slots, chosen = t(8, 30))), "earlier only tightens")
        assertFalse(wakeNeedsWall(normal, planAlarm(t(7, 30), t(1, 0), slots, chosen = t(9, 0))), "the same time is no change")
        assertFalse(wakeNeedsWall(null, normal)); assertFalse(wakeNeedsWall(normal, null))
        assertEquals(75, WallLength.WAKE_LATER)
    }

    @Test fun `WT1 a chosen time never moves the alarm later than the backstop, whatever the onset`() {
        for (hour in 0..11) for (minute in listOf(0, 30)) {
            val p = planAlarm(t(1, 0), t(hour, minute), slots, chosen = t(11, 59))
            assertTrue(p != null && !p.at.isAfter(ZonedDateTimeAtNoon(p.at.toLocalDate())), "onset $hour:$minute gave ${p?.at}")
        }
    }

    private fun ZonedDateTimeAtNoon(d: LocalDate) = at(d, LocalTime.NOON)
}
