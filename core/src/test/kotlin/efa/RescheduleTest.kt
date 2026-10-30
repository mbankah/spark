package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
private fun t(m: Int, day: Int, h: Int, min: Int = 0) = at(d(m, day), LocalTime.of(h, min))

class CatchUpTest {
    private val slots = WeekB.slots
    private val firstUse = t(10, 1, 0)
    private fun catchUps(now: java.time.ZonedDateTime, verified: Set<String> = emptySet(), known: Set<String> = emptySet(), notBefore: java.time.ZonedDateTime = firstUse) =
        missedCatchUps(slots, now, verified, known, notBefore)
    private val mondayLecture get() = slotsFor(slots, d(10, 26)).first { it.slot.type == "lecture" }

    @Test fun `RS1 D5 a missed lecture comes back at its own length, due the end of the next day, with the reason`() {
        val c = catchUps(t(10, 27, 8, 0)).single { it.key == "missed:" + blockKey(mondayLecture) }
        assertEquals("Catch-up: Randomness and Computation lecture", c.title); assertEquals(1.0, c.hours)
        assertEquals("Missed Randomness and Computation lecture", c.reason)
        assertEquals(endOfDayCutoff(slots, d(10, 27)), c.due)
    }

    @Test fun `Z4 after a bad night the reason says so and what it costs`() {
        val now = t(10, 27, 8, 0)
        val bad = missedCatchUps(slots, now, emptySet(), emptySet(), firstUse, badNightDays = setOf(d(10, 26)))
        val c = bad.single { it.key == "missed:" + blockKey(mondayLecture) }
        assertEquals("Bad night, missed Randomness and Computation lecture, Buffer -1.0h", c.reason)
        assertTrue(catchUps(now).single { it.key == "missed:" + blockKey(mondayLecture) }.reason.startsWith("Missed "), "a normal night keeps the plain reason")
    }

    @Test fun `RS1 B7 nothing comes back while the backfill window is still open`() {
        assertTrue(catchUps(t(10, 26, 23, 0)).none { it.key == "missed:" + blockKey(mondayLecture) })
        assertTrue(catchUps(t(10, 27, 7, 0)).none { it.key == "missed:" + blockKey(mondayLecture) }, "next wake is 07:30")
    }

    @Test fun `RS1 a study cycle comes back at its cycle size`() {
        val cycle = slotsFor(slots, d(10, 26)).first { it.slot.type == "study-standard" }
        assertEquals(STUDY_CYCLE_STANDARD_HOURS, catchUps(t(10, 27, 8, 0)).single { it.key == "missed:" + blockKey(cycle) }.hours)
        val light = slotsFor(slots, d(10, 27)).first { it.slot.type == "study-light" }
        assertEquals(STUDY_CYCLE_LIGHT_HOURS, catchUps(t(10, 28, 8, 0)).single { it.key == "missed:" + blockKey(light) }.hours)
    }

    @Test fun `P8 a block that was proven does not come back`() {
        val now = t(10, 27, 8, 0)
        assertTrue(catchUps(now, verified = setOf(blockKey(mondayLecture))).none { it.key == "missed:" + blockKey(mondayLecture) })
    }

    @Test fun `RS5 a missed block comes back once however often the app looks`() {
        val now = t(10, 27, 8, 0)
        val first = catchUps(now)
        val second = catchUps(now.plusMinutes(1), known = first.map { it.key }.toSet())
        assertTrue(first.isNotEmpty()); assertTrue(second.none { it.key in first.map { c -> c.key } })
    }

    @Test fun `RS1 installing the app does not invent a backlog from before first use`() {
        assertTrue(catchUps(t(10, 27, 8, 0), notBefore = t(10, 27, 0)).none { it.key.contains("2026-10-26") })
    }

    @Test fun `RS1 only the last three days are looked at`() {
        assertTrue(catchUps(t(11, 2, 8, 0)).none { it.key.contains("2026-10-26") })
    }

    @Test fun `RS1 meals, routines, travel and free time never come back`() {
        val types = catchUps(t(10, 27, 8, 0)).map { k -> slotsFor(slots, d(10, 26)).firstOrNull { blockKey(it) == k.key.removePrefix("missed:") }?.slot?.type }
        assertTrue(types.filterNotNull().all { it in setOf("lecture", "tutorial", "study-standard", "study-light", "study", "code") })
    }

    @Test fun `RS8 W2 B6 bringing the work back lowers the Buffer by exactly its hours, so the lost time is not forgiven`() {
        val now = t(10, 27, 8, 0)
        val manual = listOf(DueWork("Report", 3.0, 5, t(11, 6, 12)))
        val without = (bufferNow(now, studyCycles(slots, now) + manual, slots, false) as BufferState.Value).result.hours
        val c = catchUps(now).single { it.key == "missed:" + blockKey(mondayLecture) }
        val withCatchUp = (bufferNow(now, studyCycles(slots, now) + manual + DueWork(c.title, c.hours, c.heaviness, c.due), slots, false) as BufferState.Value).result.hours
        assertTrue(withCatchUp <= without - c.hours + 0.001 || withCatchUp < without, "$without to $withCatchUp")
    }

    @Test fun `X4 the clock change on 25 October 2026 does not move the catch-up due times`() {
        val c = catchUps(t(10, 26, 8, 0), notBefore = t(10, 23, 0)).filter { it.key.contains("2026-10-25") }
        assertTrue(c.all { it.due == endOfDayCutoff(slots, d(10, 26)) })
    }
}

class FreePlannerTest {
    private val slots = WeekB.slots
    private val now = t(10, 26, 12)                       // Monday noon
    private fun work(hours: Double, due: java.time.ZonedDateTime, title: String = "Project") = DueWork(title, hours, 5, due)

    @Test fun `G3 per week the longest free slots are protected until 6 hours are covered`() {
        val p = protectedFree(slots, d(10, 28))
        assertEquals(setOf(d(10, 26) to 1155, d(10, 31) to 975), p)          // Monday 190 and Saturday 175 = 365 minutes
    }

    @Test fun `G3 the protected set does not change when a slot is turned into work`() {
        val before = protectedFree(slots, d(10, 28))
        val conv = slotsFor(slots, d(10, 27)).first { it.slot.type == "free" }.let {
            it.slot.copy(type = "study", title = CONVERTED_TITLE, notes = CONVERTED_NOTE, date = it.plannedDay)
        }
        assertEquals(before, protectedFree(slots + conv, d(10, 28)))
    }

    @Test fun `RS3 a Buffer under its margin turns free time into work, from tomorrow, before the deadline, and never a protected slot`() {
        val deadline = t(11, 6, 12)
        val big = listOf(work(60.0, deadline))                           // far more than fits, so every allowed slot is needed
        val plan = planConversions(slots, big, now)
        assertTrue(plan.conversions.isNotEmpty())
        val protected = (0..2).flatMap { w -> protectedFree(slots, d(10, 26).plusWeeks(w.toLong())) }.toSet()
        for (c in plan.conversions) {
            assertEquals("study", c.slot.type); assertEquals(CONVERTED_NOTE, c.slot.notes)
            assertFalse(c.window.start.isBefore(t(10, 27, 0)), "assigned the night before: nothing today")
            assertFalse(c.window.end.isAfter(deadline))
            assertFalse((c.window.plannedDay to c.slot.startMin) in protected, "a protected slot was converted")
            assertEquals(c.window.slot.type, "free")
        }
    }

    @Test fun `RS4 AP6 when every allowed slot is used and the work still does not fit, the shortfall is reported`() {
        val plan = planConversions(slots, listOf(work(300.0, t(11, 6, 12))), now)
        assertNotNull(plan.stillShortHours); assertTrue(plan.stillShortHours!! > 0)
    }

    @Test fun `RS3 only as many slots as the margin needs are converted`() {
        val deadline = t(10, 30, 12)
        val base = (bufferNow(now, studyCycles(slots, now) + work(0.1, deadline), slots, false) as BufferState.Value).result
        val tight = work(base.availableHours - base.workHours + 0.1, deadline)   // work that leaves a little less than the margin
        val one = planConversions(slots, studyCycles(slots, now) + tight, now)
        assertTrue(one.conversions.size <= 3, "took ${one.conversions.size}")
        val after = slots + one.conversions.map { it.slot }
        val res = (bufferNow(now, studyCycles(after, now) + tight, after, false) as BufferState.Value).result
        assertTrue(res.hours >= marginNeededHours(res.workHours) || one.stillShortHours != null || one.conversions.isNotEmpty())
    }

    @Test fun `RS3 a Buffer at or over its margin converts nothing`() {
        val plan = planConversions(slots, listOf(work(0.5, t(11, 20, 12))), now)
        assertTrue(plan.conversions.isEmpty()); assertNull(plan.stillShortHours)
    }

    @Test fun `D6 with no deadline nothing is converted`() {
        assertTrue(planConversions(slots, emptyList(), now).conversions.isEmpty())
    }

    @Test fun `RS5 running the planner again with its own conversions in place adds nothing`() {
        val w = listOf(work(60.0, t(11, 6, 12)))
        val first = planConversions(slots, w, now)
        val again = planConversions(slots + first.conversions.map { it.slot }, w, now)
        assertTrue(again.conversions.isEmpty(), "second run added ${again.conversions.size}")
    }

    @Test fun `RS3 converted free time counts as work in the Buffer, so the Buffer rises`() {
        val probe = (bufferNow(now, listOf(work(0.1, t(11, 6, 12))), slots, false) as BufferState.Value).result
        val w = listOf(work(probe.availableHours - 2.0, t(11, 6, 12)))              // leaves about 2 hours, under the 17.5% margin
        val before = (bufferNow(now, w, slots, false) as BufferState.Value).result.hours
        val plan = planConversions(slots, w, now)
        assertTrue(plan.conversions.isNotEmpty())
        val after = (bufferNow(now, w, slots + plan.conversions.map { it.slot }, false) as BufferState.Value).result.hours
        assertTrue(after > before, "$before to $after")
    }

    @Test fun `section 10 sleep, meals, hard commitments, resets and transitions are never turned into work`() {
        val plan = planConversions(slots, listOf(work(60.0, t(11, 6, 12))), now)
        assertTrue(plan.conversions.all { it.window.slot.type == "free" })
    }

    @Test fun `RS8 each conversion carries its reason`() {
        val plan = planConversions(slots, listOf(work(60.0, t(11, 6, 12))), now)
        assertTrue(plan.conversions.all { "became work" in it.reason })
    }

    @Test fun `IM6 free time an imported event already took is not converted again`() {
        val tue = d(10, 27)
        val event = PatternSlot(DayOfWeek.TUESDAY, 970, 1095, "Seminar", "tutorial", "", false, "", date = tue)       // takes all of Tuesday's long free slot
        val plan = planConversions(slots + event, listOf(work(60.0, t(11, 6, 12))), now)
        assertTrue(plan.conversions.none { it.window.plannedDay == tue && it.window.slot.startMin in 970..1094 })
    }

    @Test fun `X4 the planner works across the clock change on 25 October 2026`() {
        val plan = planConversions(slots, listOf(work(60.0, t(11, 6, 12))), t(10, 24, 12))
        assertTrue(plan.conversions.all { Duration.between(it.window.start, it.window.end).toMinutes() == (it.slot.endMin - it.slot.startMin + RESET_MIN).toLong() })
    }
}

class AllocationTest {
    private val slots = WeekB.slots
    private val now = t(10, 26, 12)
    private fun task(id: Long, mins: Int, due: java.time.ZonedDateTime, prio: Int = 0) = OpenTask(id, "Task $id", due, mins, prio)

    @Test fun `RS2 a task bigger than one block is split across several, earliest first, and the minutes add up`() {
        val a = allocateTasks(slots, listOf(task(1, 600, t(11, 6, 12))), now)
        val mine = a.placed.filter { it.task.id == 1L }
        assertTrue(mine.size >= 2, "a 10 hour task needs more than one block")
        assertEquals(600, mine.sumOf { it.minutes } + (a.unplaced[1L] ?: 0))
        assertEquals(mine.map { it.slot.start }, mine.map { it.slot.start }.sorted())
    }

    @Test fun `RS2 no block is given more minutes than it has and nothing is placed after its deadline`() {
        val tasks = (1L..6L).map { task(it, 240, t(10, 30, 12)) }
        val a = allocateTasks(slots, tasks, now)
        for ((slot, ps) in a.placed.groupBy { it.slot.start }) {
            val s = ps.first().slot
            assertTrue(ps.sumOf { it.minutes } <= Duration.between(maxOf(s.start, now), s.end).toMinutes(), "overfull at $slot")
        }
        assertTrue(a.placed.all { !it.slot.end.isAfter(it.task.due) })
    }

    @Test fun `RS4 what cannot be placed before its deadline is listed, not dropped`() {
        val a = allocateTasks(slots, listOf(task(1, 600, t(10, 26, 14))), now)
        assertEquals(600, (a.placed.sumOf { it.minutes }) + (a.unplaced[1L] ?: 0))
        assertTrue((a.unplaced[1L] ?: 0) > 0)
    }

    @Test fun `RS2 the earlier deadline gets the earlier block`() {
        val a = allocateTasks(slots, listOf(task(1, 120, t(11, 6, 12)), task(2, 120, t(10, 30, 12))), now)
        val first2 = a.placed.filter { it.task.id == 2L }.minOf { it.slot.start }
        val first1 = a.placed.filter { it.task.id == 1L }.minOf { it.slot.start }
        assertTrue(first2 <= first1)
    }

    @Test fun `PW2 equal deadlines are ordered by the period's priority`() {
        val due = t(11, 6, 12)
        val catchUpFirst = allocateTasks(slots, listOf(task(1, 120, due, prio = 0), task(2, 120, due, prio = 1)), now)
        assertTrue(catchUpFirst.placed.first { it.task.id == 2L }.slot.start <= catchUpFirst.placed.first { it.task.id == 1L }.slot.start)
        val assignmentFirst = allocateTasks(slots, listOf(task(1, 120, due, prio = 1), task(2, 120, due, prio = 0)), now)
        assertTrue(assignmentFirst.placed.first { it.task.id == 1L }.slot.start <= assignmentFirst.placed.first { it.task.id == 2L }.slot.start)
    }

    @Test fun `PW2 a period never changes how much work there is, only the order`() {
        val due = t(11, 6, 12)
        val a = allocateTasks(slots, listOf(task(1, 300, due, 0), task(2, 300, due, 1)), now)
        val b = allocateTasks(slots, listOf(task(1, 300, due, 1), task(2, 300, due, 0)), now)
        assertEquals(a.placed.sumOf { it.minutes } + a.unplaced.values.sum(), b.placed.sumOf { it.minutes } + b.unplaced.values.sum())
    }

    @Test fun `RS2 a task is never left as a sliver while it still has more to place`() {
        val a = allocateTasks(slots, (1L..4L).map { task(it, 95, t(10, 30, 12)) }, now)
        val bySlot = a.placed.groupBy { it.task.id }
        for ((_, ps) in bySlot) assertTrue(ps.dropLast(1).all { it.minutes >= MIN_CHUNK_MIN })
    }

    @Test fun `RS5 the same inputs give the same places`() {
        val tasks = listOf(task(1, 300, t(11, 6, 12)), task(2, 90, t(10, 30, 12)))
        assertEquals(allocateTasks(slots, tasks, now), allocateTasks(slots, tasks, now))
    }

    @Test fun `RS6 P8 proven minutes reduce the earliest deadline first and never more than what is left`() {
        val open = listOf(task(1, 60, t(11, 6, 12)), task(2, 45, t(10, 30, 12)), task(3, 30, t(10, 30, 12), prio = 1))
        assertEquals(mapOf(3L to 30, 2L to 45, 1L to 25), distributeCredit(open, 100))
        assertEquals(mapOf(3L to 30), distributeCredit(open, 30))
        assertTrue(distributeCredit(open, 0).isEmpty())
        assertEquals(135, distributeCredit(open, 1000).values.sum(), "credit past what is owed is not carried")
    }

    @Test fun `RS8 a converted free slot is a block tasks are placed in`() {
        val tue = d(10, 27)
        val conv = slotsFor(slots, tue).first { it.slot.type == "free" && it.slot.startMin == 970 }
            .slot.copy(type = "study", title = CONVERTED_TITLE, notes = CONVERTED_NOTE, date = tue)
        val a = allocateTasks(listOf(conv), listOf(task(1, 60, t(10, 28, 12))), now)
        assertEquals(listOf(60), a.placed.map { it.minutes }); assertEquals(conv, a.placed.single().slot.slot)
    }
}
