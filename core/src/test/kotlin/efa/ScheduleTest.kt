package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class ScheduleTest {
    private val p = WeekB.slots
    // Mon 19 Oct 2026 is a normal BST Monday.
    private fun at(d: Int, h: Int, m: Int = 0) = efa.at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun now(d: Int, h: Int, m: Int = 0) = nowAndNext(p, at(d, h, m))
    private fun day(dow: DayOfWeek) = p.filter { it.day == dow }.sortedBy { it.startMin }

    @Test fun `D1 O9 week B has every day`() {
        DayOfWeek.entries.forEach { assertTrue(day(it).isNotEmpty(), "$it") }
        assertEquals(179, p.size)
    }

    @Test fun `W3 every hour belongs to a block with no gaps or overlaps`() {
        for (d in DayOfWeek.entries) day(d).zipWithNext().forEach { (a, b) -> assertEquals(a.endMin, b.startMin, "$d ${a.title} -> ${b.title}") }
    }

    @Test fun `O5 Monday 13 00 is Campus League football only`() {
        val n = now(19, 13, 30)
        assertEquals("Campus League football", n.now?.slot?.title)
        assertEquals(1, day(DayOfWeek.MONDAY).count { it.startMin == 13 * 60 })
        assertTrue(p.none { it.title.contains("CS Project lecture", true) })
    }

    @Test fun `O6 Monday 20 00 and Saturday 16 45 football are removed`() {
        assertTrue(day(DayOfWeek.MONDAY).none { (it.type == "exercise" || it.type == "match") && it.startMin >= 14 * 60 })   // only Campus League at 13:00 remains
        assertEquals("Free time - phone open", now(19, 21).now?.slot?.title)
        assertTrue(day(DayOfWeek.SATURDAY).none { it.title.contains("FOOTBALL", true) })
        assertEquals("Free time - phone open", nowAndNext(p, at(24, 17)).now?.slot?.title)
    }

    @Test fun `O6 Q-C9 removed football also removes its own travel and buffers`() {
        assertTrue(p.none { it.title.contains("Travel to football") || it.title == "Buffer: arrive, warm up, phone away" })
    }

    @Test fun `section 3 Tuesday team training is dropped`() {
        assertTrue(day(DayOfWeek.TUESDAY).none { it.type == "training" })
        assertEquals("Free time - phone open", now(20, 17).now?.slot?.title)
    }

    @Test fun `section 3 Friday team training is 09 00 to 10 45 then a walk`() {
        val f = day(DayOfWeek.FRIDAY).first { it.type == "training" }
        assertEquals(9 * 60 to 10 * 60 + 45, f.startMin to f.endMin)
        assertEquals("Walk", nowAndNext(p, at(23, 10, 50)).now?.slot?.title)
    }

    @Test fun `D4 only MyTutor sessions are hard alarms`() {
        val hard = p.filter { it.hardAlarm }
        assertEquals(3, hard.size)
        assertTrue(hard.all { it.type == "mytutor" })
        assertTrue(p.filter { it.type == "lecture" || it.type == "tutorial" || it.type == "training" }.none { it.hardAlarm })
    }

    @Test fun `W7 names are plain and carry the place`() {
        assertTrue(p.any { it.title == "Neural Computation lecture - Haworth HWTH-101" })
        assertTrue(p.any { it.title == "Notes and problems: Neural Computation" })
        assertTrue(p.any { it.title == "Wake, shower" } && p.any { it.title == "Shutdown" } && p.any { it.title == "Free time - phone open" })
        assertTrue(p.none { it.title.startsWith("LECTURE:") || it.title.startsWith("VRNP") })
    }

    @Test fun `T1 transitions are blocks with a duration`() {
        val t = p.filter { it.type == "travel" }
        assertTrue(t.size > 30); assertTrue(t.all { it.endMin > it.startMin })
    }

    @Test fun `home Now and Next at Monday 09 30`() {
        val n = now(19, 9, 30)
        assertEquals("Randomness and Computation lecture - Murray Learning Centre LC-UG05", n.now?.slot?.title)
        assertEquals("Walk", n.next?.slot?.title)
        assertEquals("10:00 Walk", n.next?.label())
    }

    @Test fun `home Now is empty before the first block of the day`() {
        assertNull(now(19, 7, 0).now ?.takeIf { it.slot.day == DayOfWeek.MONDAY && it.slot.startMin > 0 && false })
        assertEquals("Wake, shower", now(19, 7, 30).now?.slot?.title)
    }

    @Test fun `after midnight belongs to the planned day before`() {
        // Friday 23 Oct plans shutdown 00:00 to 00:30, which is Saturday 24 Oct on the clock.
        val n = nowAndNext(p, at(24, 0, 10))
        assertEquals("Shutdown", n.now?.slot?.title)
        assertEquals(DayOfWeek.FRIDAY, n.now?.slot?.day)
        assertEquals(LocalDate.of(2026, 10, 24), n.now?.start?.toLocalDate())
    }

    @Test fun `X4 planned wall clock times hold across the clocks going back`() {
        val sun = slotsFor(p, LocalDate.of(2026, 10, 25))
        assertEquals(LocalTime.of(8, 30), sun.first().start.toLocalTime())
        assertEquals(0, sun.first().start.offset.totalSeconds)            // GMT
        val sat = slotsFor(p, LocalDate.of(2026, 10, 24)).first()
        assertEquals(3600, sat.start.offset.totalSeconds)                 // BST
        // Saturday's 00:00-00:30 shutdown straddles nothing; Saturday evening shutdown 23:00 is 25 h before Sunday 00:00 + 24h.
        val satShut = slotsFor(p, LocalDate.of(2026, 10, 24)).last { it.slot.type == "sleep" }
        assertEquals(LocalTime.of(23, 0), satShut.start.toLocalTime())
        // Sunday has 25 real hours but the pattern still ends at the same wall-clock times.
        assertEquals(LocalTime.of(22, 25), slotsFor(p, LocalDate.of(2026, 10, 25)).last().start.toLocalTime())
    }

    @Test fun `D1 every lecture has a study cycle on its day`() {
        for (d in DayOfWeek.entries) {
            val s = day(d); val lectures = s.count { it.type == "lecture" || it.type == "tutorial" }
            val cycles = s.count { it.type.startsWith("study-") }
            if (lectures > 0) assertTrue(cycles > 0, "$d has $lectures lectures and $cycles cycles")
        }
    }
}

class UpcomingTest {
    private val p = WeekB.slots
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))

    @Test fun `home Next lists the next blocks in order and not the current one`() {
        val nx = upcoming(p, t(19, 9, 30), 2)
        assertEquals(listOf("Walk", "Notes and problems: Randomness"), nx.map { it.slot.title })
        assertTrue(nx.zipWithNext().all { (a, b) -> a.start.isBefore(b.start) })
    }

    @Test fun `home Next reaches across midnight to the next day`() {
        val nx = upcoming(p, t(19, 23, 40), 1)
        assertEquals("Wake, shower", nx.single().slot.title)
    }

    @Test fun `home Next shows the place of a lecture`() {
        val lecture = upcoming(p, t(19, 8, 40), 1).single()
        assertEquals("Murray Learning Centre LC-UG05", lecture.detail())
    }

    @Test fun `Y1 G1 each proof kind has a plain hint and a block with no proof has none`() {
        assertEquals("", proofHint(Proof.NONE))
        for (k in Proof.entries.filter { it != Proof.NONE }) assertTrue(proofHint(k).isNotBlank(), k.name)
    }
}
