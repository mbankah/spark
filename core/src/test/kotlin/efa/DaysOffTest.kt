package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
private fun t(m: Int, day: Int, h: Int, min: Int = 0) = at(d(m, day), LocalTime.of(h, min))

class DayOffCalendarTest {
    private val slots = WeekB.slots
    private val wed = d(11, 4)
    private val off = slots + dayOffMarker(wed)

    @Test fun `C5 a day off takes the work, lectures, planning and resets away`() {
        val before = slotsFor(slots, wed).map { it.slot.type }; val after = slotsFor(off, wed).map { it.slot.type }
        for (t in listOf("study", "code", "study-standard", "study-light", "lecture", "tutorial", "planning")) assertFalse(t in after, t)
        assertTrue(before.any { it in DAY_OFF_REMOVES }, "Wednesday has work to take away")
    }

    @Test fun `C5 a day off keeps sleep, meals, routines, free time and the commitments that are not university work`() {
        val kept = listOf("sleep", "meal", "routine", "free", "mytutor", "church", "gym", "match", "chore", "errand")
        for (type in kept) assertEquals(slotsFor(slots, wed).count { it.slot.type == type }, slotsFor(off, wed).count { it.slot.type == type }, type)
        assertTrue(slotsFor(off, wed).any { it.slot.title == "Wake, shower" }, "the wake time, and so the night cutoff, stays")
    }

    @Test fun `C5 the marker is never a block and other days are untouched`() {
        assertTrue(slotsFor(off, wed).none { it.slot.type == DAY_OFF_TYPE })
        for (i in listOf(-1L, 1L, 7L)) assertEquals(slotsFor(slots, wed.plusDays(i)).map { it.slot }, slotsFor(off, wed.plusDays(i)).map { it.slot })
        assertTrue(isDayOff(off, wed)); assertFalse(isDayOff(off, wed.plusDays(1)))
    }

    @Test fun `C5 a lecture imported onto a day off is also taken away`() {
        val extra = PatternSlot(wed.dayOfWeek, 1200, 1260, "Seminar", "tutorial", "", false, "", date = wed)
        assertTrue(slotsFor(slots + extra + dayOffMarker(wed), wed).none { it.slot.title == "Seminar" })
    }

    @Test fun `C5 nothing counts as lost, so a day off brings no catch-up work for its blocks`() {
        val now = t(11, 5, 9)
        val c = missedCatchUps(off, now, emptySet(), emptySet(), t(10, 1, 0))
        assertTrue(c.none { it.key.contains("2026-11-04") })
    }

    @Test fun `C5 the day's hours are left out of the weekly money ratio, so a day off does not cut the payout`() {
        val monday = d(11, 2)
        val full = weekResult(off, monday, emptyMap()); val plain = weekResult(slots, monday, emptyMap())
        assertTrue(full.plannedWeight < plain.plannedWeight)
        val credited = weekBlocks(off, monday, emptyMap()).associate { it.key to it.plannedMin }
        assertEquals(FREE_CAP_PENCE, weekResult(off, monday, credited).money.freeToSpendPence, "everything that was planned was done")
    }

    @Test fun `C5 the night cutoff and the sleep time are not touched`() {
        assertEquals(nextWake(slots, t(11, 4, 12)), nextWake(off, t(11, 4, 12)))
    }

    @Test fun `G2 no alarm rings on a planned day off, and the next one is for the next day`() {
        val evening = t(11, 3, 21)
        assertNull(planAlarmWithDaysOff(evening, null, off), "tonight's backstop would be tomorrow at noon, which is the day off")
        assertEquals(t(11, 5, 12), planAlarmWithDaysOff(t(11, 4, 13), null, off)!!.at)
        assertNotNull(planAlarmWithDaysOff(evening, null, slots))
    }

    @Test fun `C3 X4 a weekend off across the clock change on 25 October 2026 works`() {
        val sat = d(10, 24)
        val weekend = slots + dayOffMarker(sat) + dayOffMarker(sat.plusDays(1))
        for (day in listOf(sat, sat.plusDays(1))) assertTrue(slotsFor(weekend, day).none { it.slot.type in DAY_OFF_REMOVES })
        assertEquals(listOf(sat, sat.plusDays(1)), weekendOf(sat))
    }
}

class DayOffCostTest {
    private val sat = d(11, 7)
    private val small = listOf(
        PatternSlot(DayOfWeek.WEDNESDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
        PatternSlot(DayOfWeek.SATURDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
        PatternSlot(DayOfWeek.SATURDAY, 540, 720, "Project", "study", "", false, ""),
        PatternSlot(DayOfWeek.SATURDAY, 780, 960, "Project", "study", "", false, ""),
        PatternSlot(DayOfWeek.SUNDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
        PatternSlot(DayOfWeek.MONDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
    )
    private val now = t(11, 4, 9)
    private val work = listOf(DueWork("Report", 3.0, 5, t(11, 9, 12)))

    @Test fun `C4 the cost of a day off is shown before it is confirmed, for example Day off Sat Buffer minus 6 hours`() {
        val p = dayOffPreview(small, work, now, listOf(sat), emptyList(), sameDay = false)
        assertEquals(6.0, p.costHours!!, 1e-9); assertEquals("Day off Sat: Buffer -6.0h", p.lines.first())
    }

    @Test fun `C4 a weekend is one request and is named as one`() {
        val p = dayOffPreview(small, work, now, listOf(sat, sat.plusDays(1)), emptyList(), sameDay = false)
        assertTrue(p.lines.first().startsWith("Weekend Sat and Sun: Buffer -6.0h"), p.lines.first())
    }

    @Test fun `C4 when an earlier deadline is the tight one the line says what the day takes and why the Buffer did not move`() {
        val p = dayOffPreview(WeekB.slots, work, t(11, 3, 9), listOf(d(11, 4)), emptyList(), false)
        if (p.costHours!! <= 0.05) assertTrue("takes " in p.lines.first() && "out of time available" in p.lines.first(), p.lines.first())
        assertEquals(6.0, dayOffPreview(small, work, now, listOf(sat), emptyList(), false).removedHours, 1e-9)
    }

    @Test fun `C4 a day with no work to lose costs nothing`() {
        assertEquals(0.0, dayOffPreview(small, work, now, listOf(d(11, 8)), emptyList(), false).costHours!!, 1e-9)
    }

    @Test fun `C4 the lectures and study cycles of the day still have to be done, so they come back as work`() {
        val wed = d(11, 4)
        val p = dayOffPreview(WeekB.slots, work, t(11, 3, 9), listOf(wed), emptyList(), false)
        assertTrue(p.work.isNotEmpty(), "no work came back")
        assertTrue(p.work.any { it.title.startsWith("Catch-up: ") && it.hours > 0 }, p.work.toString()); assertTrue(p.work.any { it.title.startsWith("Study: ") }, p.work.toString())
        val nextDay = endOfDayCutoff(WeekB.slots + dayOffMarker(wed), wed.plusDays(1))
        assertTrue(p.work.all { it.due == nextDay }, "due by the end of the next working day")
        assertTrue(p.removedHours > 0.0, "the day takes work time away"); assertTrue(p.costHours!! >= 0.0)
        assertTrue(p.lines.first().startsWith("Day off Wed: "), p.lines.first())
    }

    @Test fun `C4 a day off cannot make work disappear, so the work that comes back is never less than the cycles removed`() {
        val wed = d(11, 4); val now2 = t(11, 3, 9)
        val cyclesOnWed = studyCycles(WeekB.slots, now2).filter { it.due == endOfDayCutoff(WeekB.slots, wed, now2.zone) }.sumOf { it.hours }
        val back = dayOffDemand(WeekB.slots, listOf(wed), now2).filter { it.title.startsWith("Study: ") }.sumOf { it.hours }
        assertEquals(cyclesOnWed, back, 1e-9)
    }

    @Test fun `H2 a day off that would push the Buffer negative says so and shows the working`() {
        val tight = listOf(DueWork("Report", 3.0, 5, t(11, 9, 12)), DueWork("Project", 6.0, 8, t(11, 9, 12)))
        val p = dayOffPreview(small, tight, now, listOf(sat), emptyList(), false)
        assertTrue(p.negative); assertTrue(p.lines.any { it.contains("NEGATIVE") }); assertTrue(p.lines.any { it.startsWith("Available ") }); assertTrue(p.lines.any { it.startsWith("Work ") })
    }

    @Test fun `H2 a day off that leaves the Buffer positive says nothing alarming`() {
        val roomy = small + PatternSlot(DayOfWeek.THURSDAY, 540, 780, "Project", "study", "", false, "")
        val p = dayOffPreview(roomy, listOf(DueWork("Small", 0.5, 2, t(11, 12, 12))), now, listOf(sat), emptyList(), false)
        assertFalse(p.negative); assertTrue(p.lines.none { it.contains("NEGATIVE") })
    }

    @Test fun `C4 with no deadline there is no Buffer to cost and it says so`() {
        val p = dayOffPreview(listOf(PatternSlot(DayOfWeek.SATURDAY, 450, 470, "Wake, shower", "routine", "", false, "")), emptyList(), now, listOf(sat), emptyList(), false)
        assertNull(p.costHours); assertTrue(p.lines.first().contains("no deadline"))
    }
}

class DayOffWallTest {
    private val now = t(11, 10, 12)
    private fun use(group: Long, daysAgo: Long) = DayOffUse(group, now.minusDays(daysAgo).toInstant().toEpochMilli())

    @Test fun `H1 the string gets longer with each day off used in the last 30 days`() {
        val lens = (0..5).map { n -> dayOffWall((1..n).map { use(it.toLong(), it.toLong()) }, now, false) }
        assertEquals(listOf(75, 100, 130, 165, 205, 250), lens)
    }

    @Test fun `H1 a use older than 30 days no longer counts`() {
        assertEquals(1, usesInLast30Days(listOf(use(1, 29)), now)); assertEquals(0, usesInLast30Days(listOf(use(1, 30)), now)); assertEquals(0, usesInLast30Days(listOf(use(1, 45)), now))
        assertEquals(75, dayOffWall(listOf(use(1, 40)), now, false))
    }

    @Test fun `H5 a weekend is one use however many days it covers`() {
        assertEquals(1, usesInLast30Days(listOf(use(7, 3), use(7, 3)), now)); assertEquals(100, dayOffWall(listOf(use(7, 3), use(7, 3)), now, false))
    }

    @Test fun `H3 a request made on the day adds 40 characters and one made ahead does not`() {
        assertEquals(75, dayOffWall(emptyList(), now, false)); assertEquals(115, dayOffWall(emptyList(), now, true)); assertEquals(140, dayOffWall(listOf(use(1, 2)), now, true))
    }

    @Test fun `H4 a night of 3 hours or less, with the logger alive, is a day off with no typing and it counts in the tally`() {
        assertTrue(autoDayOff(180, 0)); assertTrue(autoDayOff(60, 0)); assertFalse(autoDayOff(181, 0))
        assertEquals(100, dayOffWall(listOf(use(99, 1)), now, false), "the automatic one made the next planned day off cost more")
    }

    @Test fun `H4 a night the logger missed part of cannot grant a day off`() { assertFalse(autoDayOff(120, 1)) }

    @Test fun `H1 the same use is never counted twice, and uses in the future are ignored`() {
        assertEquals(1, usesInLast30Days(listOf(use(1, 2), use(1, 2), use(1, 2)), now)); assertEquals(0, usesInLast30Days(listOf(DayOffUse(1, now.plusDays(1).toInstant().toEpochMilli())), now))
    }
}

class DayOffClaimTest {
    private val due = 1_000_000L

    @Test fun `H6 a claim for a day with nothing missed holds at once`() { assertEquals(ClaimState.HELD, resolveClaim(emptyList(), due + 5)) }

    @Test fun `H6 the claim waits while the work is still to do and not yet due`() {
        assertEquals(ClaimState.PENDING, resolveClaim(listOf(ClaimTask(due, null)), due - 1))
        assertEquals(ClaimState.PENDING, resolveClaim(listOf(ClaimTask(due, due - 10), ClaimTask(due, null)), due - 1))
    }

    @Test fun `H6 the claim holds when every task was done by its time`() {
        assertEquals(ClaimState.HELD, resolveClaim(listOf(ClaimTask(due, due - 5), ClaimTask(due + 10, due)), due + 100))
        assertEquals(ClaimState.HELD, resolveClaim(listOf(ClaimTask(due, due)), due + 1), "done exactly on time")
    }

    @Test fun `H6 the claim fails the moment a task is overdue and stays failed`() {
        assertEquals(ClaimState.FAILED, resolveClaim(listOf(ClaimTask(due, null)), due + 1))
        assertEquals(ClaimState.FAILED, resolveClaim(listOf(ClaimTask(due, due + 1)), due + 100), "done late is not done by its time")
        assertEquals(ClaimState.FAILED, resolveClaim(listOf(ClaimTask(due, due - 5), ClaimTask(due, null)), due + 1))
    }

    @Test fun `H6 only the last 7 days before today can be claimed`() {
        val today = d(11, 10)
        assertTrue(claimable(d(11, 9), today)); assertTrue(claimable(d(11, 3), today))
        assertFalse(claimable(d(11, 2), today)); assertFalse(claimable(today, today)); assertFalse(claimable(d(11, 11), today))
    }
}
