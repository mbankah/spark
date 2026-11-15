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

    @Test fun `AN1 SC1 today is shown on its own and the carousel pages are the next 7 days after it, then the 7 after, whatever the weekday`() {
        for (todayOffset in 0L..6L) {
            val start = today.plusDays(todayOffset); val n = at(start, LocalTime.of(10, 30))
            val days = scheduleView(base, emptyList(), null, n, days = 36, from = start)
            val weeks = scheduleWeeks(days)
            assertEquals(start, scheduleToday(days)?.date); assertEquals("Today", scheduleToday(days)?.tag)
            assertEquals(5, weeks.size, "from ${start.dayOfWeek}"); assertTrue(weeks.all { it.days.size == 7 })
            assertEquals(start.plusDays(1), weeks[0].days.first().date); assertEquals("Tomorrow", weeks[0].days.first().tag)
            assertEquals(start.plusDays(8), weeks[1].days.first().date)
            assertEquals(listOf("Next 7 days", "The 7 days after"), weeks.take(2).map { it.label })
            assertEquals("From ${java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.UK).format(start.plusDays(15))}", weeks[2].label)
            assertTrue(weeks.flatMap { it.days }.none { it.today }, "today is on its own at the top, in no window")
        }
    }

    @Test fun `AN1 SC1 every day after today is in exactly one window, in order, with the ranges named`() {
        val days = scheduleView(base, emptyList(), null, now, days = 36, from = today)
        val weeks = scheduleWeeks(days)
        assertEquals(days.drop(1).map { it.date }, weeks.flatMap { it.days }.map { it.date })
        assertEquals("3 to 9 Nov", weeks[0].range); assertEquals("1 to 7 Dec", weeks[4].range)
        assertEquals(emptyList(), scheduleWeeks(emptyList()))
        assertEquals(emptyList(), scheduleWeeks(scheduleView(base, emptyList(), null, now, days = 1, from = today)), "a page of only today has no windows")
        assertEquals(1, scheduleWeeks(scheduleView(base, emptyList(), null, now, days = 4, from = today)).size)
    }

    @Test fun `AN1 SC1 from a Wednesday, Wednesday is on top and the windows start on Thursday`() {
        val wed = at(today.plusDays(2), LocalTime.of(10, 30))
        val days = scheduleView(base, emptyList(), null, wed, days = 15, from = wed.toLocalDate())
        val weeks = scheduleWeeks(days)
        assertEquals(today.plusDays(2), scheduleToday(days)?.date)
        assertEquals(today.plusDays(3), weeks[0].days.first().date); assertEquals("Tomorrow", weeks[0].days.first().tag)
        assertEquals("5 to 11 Nov", weeks[0].range)
    }

    @Test fun `AN1 SC1 a week's summary counts its work, lectures and days off`() {
        val off = today.plusDays(1)
        val weeks = scheduleWeeks(scheduleView(base + dayOffMarker(off), emptyList(), null, now, days = 14))
        val w = weeks.first()
        assertEquals(w.days.sumOf { it.workMin }, w.workMin); assertEquals(1, w.daysOff)
        assertTrue(w.summary().contains("h work") && w.summary().contains("lecture") && w.summary().endsWith("1 day off"), w.summary())
        assertEquals("", ScheduleWeek(today, "x", "y", emptyList()).summary())
    }

    @Test fun `AN1 SC1 a day counts its lectures from the slot types`() {
        val d = scheduleView(base, emptyList(), null, now, days = 1).single()
        assertEquals(slotsFor(base, today).count { it.slot.type == "lecture" }, d.lectures)
    }

    @Test fun `SC3 what is running past midnight from yesterday's plan is on today's page and is the block you are in`() {
        val sat = today.plusDays(5)                                            // Friday's Shutdown starts at 24:00, so at 00:10 it is Saturday's calendar day
        val n = at(sat, LocalTime.of(0, 10))
        val days = scheduleView(base, emptyList(), null, n, days = 2, from = sat)
        val running = days.first().rows.firstOrNull { it.state == RowState.NOW }
        assertNotNull(running, "the block running at 00:10 belongs to Friday's plan"); assertEquals("Shutdown", running.title)
        assertEquals(running.title, homeBlocks(days).now?.title)
        assertEquals(nowAndNext(base, n).now?.slot?.title, running.title)
    }

    @Test fun `SC3 the home screen's Now and Next are the schedule's own rows, at every time of the week`() {
        var checked = 0
        for (d in 0L..6L) for (minute in 0 until 24 * 60 step 17) {
            val n = at(today.plusDays(d), LocalTime.of(minute / 60, minute % 60))
            val days = scheduleView(base, emptyList(), null, n, days = 3, from = n.toLocalDate())
            val hb = homeBlocks(days, 3)
            val nn = nowAndNext(base, n); val up = upcoming(base, n, 3)
            assertEquals(nn.now?.start, hb.now?.startAt, "now at $n"); assertEquals(nn.now?.end, hb.now?.endAt, "now end at $n")
            if (nn.now != null) assertTrue(nn.now!!.slot.title.startsWith(hb.now!!.title), "title at $n: ${nn.now!!.slot.title} / ${hb.now!!.title}")
            assertEquals(up.map { it.start }, hb.next.map { it.startAt }, "next at $n")
            assertEquals(up.map { it.slot.title.substringBefore(" - ") }, hb.next.map { it.title.substringBefore(" - ") }, "next titles at $n")
            checked++
        }
        assertTrue(checked > 500, "checked $checked times")
    }

    @Test fun `SC3 Now and Next leave out a reminder, which sits on top of a block and is never the block you are in`() {
        val slot = slotsFor(base, today).first { it.slot.type.startsWith("study") }
        val rem = PatternSlot(today.dayOfWeek, slot.slot.startMin + 5, slot.slot.startMin + 10, "Take the tablet", REMINDER_TYPE, "", false, "", date = today)
        val n = slot.start.plusMinutes(6)
        val hb = homeBlocks(scheduleView(base + rem, emptyList(), null, n, days = 1, from = today))
        assertEquals(slot.slot.title, hb.now?.title); assertTrue(hb.next.none { it.type == REMINDER_TYPE })
    }

    @Test fun `SC3 the work placed in a block reads the same on the schedule and on the home screen`() {
        val tasks = listOf(OpenTask(1, "GT report", at(today.plusDays(4), LocalTime.of(17, 0)), 120))
        val alloc = allocateTasks(base, tasks, now)
        val days = scheduleView(base, tasks, alloc, now, days = 5, from = today)
        val withTask = days.flatMap { it.rows }.filter { it.detail.contains("GT report") }
        assertTrue(withTask.isNotEmpty() && withTask.all { it.detail.contains("Includes: ") && Regex("GT report \\(\\d+ min\\)").containsMatchIn(it.detail) }, withTask.map { it.detail }.toString())
        assertEquals(120, alloc.placed.sumOf { it.minutes })
    }
}

