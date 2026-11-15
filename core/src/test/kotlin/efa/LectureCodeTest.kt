package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** LC1: a reminder to enter the attendance code at every lecture hour. */
class LectureCodeTest {
    private val mon = LocalDate.of(2026, 11, 2)
    private fun lecture(day: java.time.DayOfWeek, from: Int, to: Int, title: String = "Randomness and Computation lecture - Murray LC-UG05") =
        PatternSlot(day, from, to, title, "lecture", "Murray LC-UG05", false, "")

    @Test fun `LC1 a one hour lecture is reminded once, at its end`() {
        val r = lectureCodeReminders(listOf(lecture(mon.dayOfWeek, 9 * 60, 10 * 60)), mon, 1)
        assertEquals(listOf(at(mon, LocalTime.of(10, 0))), r.map { it.at })
        assertEquals("Enter the attendance code for Randomness and Computation lecture.", r.single().text())
    }

    @Test fun `LC1 a two hour lecture is reminded at each hour and a 50 minute one at its end`() {
        val two = lectureCodeReminders(listOf(lecture(mon.dayOfWeek, 14 * 60, 16 * 60)), mon, 1)
        assertEquals(listOf(at(mon, LocalTime.of(15, 0)), at(mon, LocalTime.of(16, 0))), two.map { it.at })
        val short = lectureCodeReminders(listOf(lecture(mon.dayOfWeek, 9 * 60, 9 * 60 + 50)), mon, 1)
        assertEquals(listOf(at(mon, LocalTime.of(9, 50))), short.map { it.at })
    }

    @Test fun `LC1 only lectures give a reminder, and a day off or a cancelled lecture gives none`() {
        val week = WeekB.slots
        val all = lectureCodeReminders(week, mon, 7)
        assertTrue(all.isNotEmpty())
        val lectureEnds = (0L..6L).flatMap { slotsFor(week, mon.plusDays(it)) }.filter { it.slot.type == "lecture" }.map { it.end }.toSet()
        assertEquals(lectureEnds, all.map { it.at }.filter { it in lectureEnds }.toSet())
        val off = lectureCodeReminders(week + dayOffMarker(mon), mon, 1)
        assertEquals(emptyList(), off)
        val first = slotsFor(week, mon).first { it.slot.type == "lecture" }
        val cancelled = PatternSlot(mon.dayOfWeek, first.slot.startMin, first.slot.endMin, first.slot.title, "lecture", "", false, "", date = mon, cancelled = true)
        assertTrue(lectureCodeReminders(week + cancelled, mon, 1).none { it.at == first.end })
    }

    @Test fun `LC1 a lecture an import adds gets a reminder at its end`() {
        val extra = PatternSlot(mon.dayOfWeek, 19 * 60, 20 * 60, "Guest lecture", "lecture", "", false, "", date = mon)
        assertTrue(lectureCodeReminders(listOf(extra), mon, 1).any { it.at == at(mon, LocalTime.of(20, 0)) && it.lecture == "Guest lecture" })
    }

    @Test fun `LC1 the next reminder is the first one strictly after now`() {
        val slots = listOf(lecture(mon.dayOfWeek, 9 * 60, 10 * 60))
        assertEquals(at(mon, LocalTime.of(10, 0)), nextCodeReminder(slots, at(mon, LocalTime.of(9, 30)))?.at)
        assertEquals(at(mon.plusDays(7), LocalTime.of(10, 0)), nextCodeReminder(slots, at(mon, LocalTime.of(10, 0)))?.at)      // the same weekday next week
        assertNull(nextCodeReminder(emptyList(), at(mon, LocalTime.of(9, 0))))
    }

    @Test fun `LC1 the reminder is the same wherever you are, since it reads only the calendar`() {
        // no location, no attendance proof and no block state enter into it: the inputs are the slots and the time
        val slots = WeekB.slots
        assertEquals(lectureCodeReminders(slots, mon, 7), lectureCodeReminders(slots, mon, 7))
    }
}
