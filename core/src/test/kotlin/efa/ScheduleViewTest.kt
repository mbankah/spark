package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** The second home page (SC1): the whole calendar from the same slots everything else reads. */
class ScheduleViewTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val now = at(today, LocalTime.of(10, 30))
    private val base = WeekB.slots

    @Test fun `SC1 the page covers the days asked for, today first, with Today and Tomorrow named`() {
        val v = scheduleView(base, emptyList(), null, now, days = 28)
        assertEquals(28, v.size)
        assertEquals(today, v.first().date); assertEquals("Today", v[0].tag); assertEquals("Tomorrow", v[1].tag); assertNull(v[2].tag)
        assertEquals(today.plusDays(27), v.last().date)
        assertEquals(v.map { it.date }.distinct(), v.map { it.date })
        assertEquals("Mon 2 Nov", v[0].heading)
    }

    @Test fun `SC1 every block of the calendar is on the page, in time order, none made up and none lost`() {
        val v = scheduleView(base, emptyList(), null, now, days = 14)
        for (d in v) {
            val expected = slotsFor(base, d.date, now.zone)
            assertEquals(expected.size, d.rows.size, "day ${d.date}")
            expected.zip(d.rows).forEach { (e, r) -> assertTrue(e.slot.title.startsWith(r.title), "${e.slot.title} / ${r.title}"); assertEquals(e.slot.startMin / 60, r.start.take(2).toInt() + if (r.nextDay) 24 else 0) }
            assertEquals(d.rows.map { it.nextDay to it.start }, d.rows.map { it.nextDay to it.start }.sortedWith(compareBy({ it.first }, { it.second })), "order on ${d.date}")
        }
    }

    @Test fun `SC1 a block that has finished is past, the one running is now and the rest are later`() {
        val day = scheduleView(base, emptyList(), null, now, days = 1).single()
        assertTrue(day.rows.any { it.state == RowState.PAST }, "something earlier than 10:30 should be past")
        assertTrue(day.rows.count { it.state == RowState.NOW } <= 1)
        val states = day.rows.map { it.state }
        assertEquals(states, states.sortedBy { it.ordinal }, "past, then now, then later")
        for (r in day.rows) when (r.state) {
            RowState.PAST -> assertTrue(r.end <= "10:30", "${r.title} ${r.end}")
            RowState.NOW -> assertTrue(r.start <= "10:30" && r.end > "10:30")
            RowState.LATER -> assertTrue(r.start > "10:30")
        }
        scheduleView(base, emptyList(), null, now, days = 3).drop(1).forEach { d -> assertTrue(d.rows.all { it.state == RowState.LATER }) }
    }

    @Test fun `SC1 a day off is marked and shows none of the work or lectures`() {
        val off = today.plusDays(2)
        val v = scheduleView(base + dayOffMarker(off), emptyList(), null, now, days = 5)
        val d = v.first { it.date == off }
        assertTrue(d.dayOff); assertEquals(0, d.workMin); assertTrue(d.rows.none { it.kind == RowKind.WORK || it.title.contains("lecture", true) })
        assertEquals(setOf(off), v.filter { it.dayOff }.map { it.date }.toSet())
    }

    @Test fun `SC1 deadlines appear on the day they fall due`() {
        val due = at(today.plusDays(3), LocalTime.of(17, 0))
        val v = scheduleView(base, listOf(OpenTask(1, "GT report", due, 120)), null, now, days = 7)
        assertEquals(listOf(DueRow("17:00", "GT report")), v.first { it.date == today.plusDays(3) }.due)
        assertEquals(1, v.sumOf { it.due.size })
    }

    @Test fun `SC1 work placed in a block is named in it and nowhere else`() {
        val tasks = listOf(OpenTask(1, "GT report", at(today.plusDays(4), LocalTime.of(17, 0)), 120))
        val alloc = allocateTasks(base, tasks, now)
        val v = scheduleView(base, tasks, alloc, now, days = 7)
        val withTask = v.flatMap { it.rows }.filter { it.detail.contains("GT report") }
        assertTrue(withTask.isNotEmpty(), "the task should be placed in a block")
        assertTrue(withTask.all { it.kind == RowKind.WORK })
        assertEquals(120, alloc.placed.sumOf { it.minutes })
    }

    @Test fun `SC1 the day's hours are the sums of its blocks`() {
        for (d in scheduleView(base, emptyList(), null, now, days = 14)) {
            assertEquals(d.rows.filter { it.kind == RowKind.WORK }.sumOf { it.minutes }, d.workMin)
            assertEquals(d.rows.filter { it.kind == RowKind.FIXED }.sumOf { it.minutes }, d.fixedMin)
            assertEquals(d.rows.filter { it.kind == RowKind.FREE }.sumOf { it.minutes }, d.freeMin)
            assertTrue(d.rows.all { it.minutes > 0 })
        }
    }

    @Test fun `SC1 the summary says only what there is`() {
        val none = ScheduleDay(today, "x", null, false, true, emptyList(), emptyList(), 0, 0, 0)
        assertEquals("", none.summary())
        assertEquals("1.5h work  2.0h fixed", none.copy(workMin = 90, fixedMin = 120).summary())
        assertEquals("1.5h work", none.copy(workMin = 90).summary())
    }

    @Test fun `SC1 block kinds follow the slot types`() {
        assertEquals(RowKind.WORK, rowKindOf("study-standard")); assertEquals(RowKind.WORK, rowKindOf("code"))
        assertEquals(RowKind.FIXED, rowKindOf("lecture")); assertEquals(RowKind.FIXED, rowKindOf("event"))
        assertEquals(RowKind.FREE, rowKindOf("free")); assertEquals(RowKind.MINOR, rowKindOf("travel"))
    }

    @Test fun `SC1 the page follows a modifier's sessions, since it reads the same slots`() {
        val inst = Instance(Preset.ASSIGNMENT, today, LocalDate.of(2026, 11, 13), emptyMap(), listOf(mapOf("title" to "GT", "due" to "2026-11-13", "due_time" to "17:00", "size_h" to "8.0")))
        val sessions = previewOf(inst, base, emptyList(), now).compiled.slots
        val v = scheduleView(base + sessions, emptyList(), null, now, days = 14)
        assertTrue(v.flatMap { it.rows }.any { it.title.startsWith("GT:") })
    }

    @Test fun `SC1 an imported title's room becomes its detail and is said once`() {
        val slot = PatternSlot(today.dayOfWeek, 14 * 60, 15 * 60, "Seminar - Room 2.14", "lecture", "Room 2.14", false, "")
        val other = PatternSlot(today.dayOfWeek, 16 * 60, 17 * 60, "Seminar", "lecture", "Room 2.14", false, "")
        val rows = scheduleView(listOf(slot, other), emptyList(), null, now, days = 1).single().rows
        assertEquals("Seminar", rows[0].title); assertEquals("Room 2.14", rows[0].detail)          // the room moves out of the title, and is said once
        assertEquals("Seminar", rows[1].title); assertEquals("Room 2.14", rows[1].detail)
    }
}
