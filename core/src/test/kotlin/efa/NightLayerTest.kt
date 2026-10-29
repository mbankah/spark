package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class WakeDriftTest {
    private val d = LocalDate.of(2026, 11, 4)

    @Test fun `Z3 with no late mornings there is no drift`() {
        assertEquals(0, wakeDrift(emptyMap(), d))
    }

    @Test fun `Z3 a morning 60 minutes late moves the next target 60 later, then it comes back 15 minutes a day`() {
        val late = mapOf(d to 60)
        assertEquals(0, wakeDrift(late, d), "the late morning itself is not moved")
        assertEquals(listOf(60, 45, 30, 15, 0, 0), (1..6).map { wakeDrift(late, d.plusDays(it.toLong())) })
    }

    @Test fun `Z3 one night cannot move the target by more than the cap`() {
        assertEquals(DRIFT_CAP_MIN, wakeDrift(mapOf(d to 400), d.plusDays(1)))
        assertEquals(DRIFT_CAP_MIN - DRIFT_PULL_BACK_MIN, wakeDrift(mapOf(d to 400), d.plusDays(2)))
    }

    @Test fun `Z3 an early or on-time morning drifts nothing`() {
        assertEquals(0, wakeDrift(mapOf(d to 0), d.plusDays(1))); assertEquals(0, wakeDrift(mapOf(d to -30), d.plusDays(1)))
    }

    @Test fun `Z3 a second late morning sets the drift again and it never stacks past the cap`() {
        val two = mapOf(d to 90, d.plusDays(1) to 90)
        assertEquals(90, wakeDrift(two, d.plusDays(2)), "set again, not 180")
        val oversleepEveryDay = (0L..13L).associate { d.plusDays(it) to 120 }
        assertTrue(wakeDrift(oversleepEveryDay, d.plusDays(14)) <= DRIFT_CAP_MIN)
    }

    @Test fun `Z3 the pull-back is 15 minutes a day and the drift is never negative`() {
        for (l in listOf(10, 15, 16, 119)) for (k in 1..12) { val x = wakeDrift(mapOf(d to l), d.plusDays(k.toLong())); assertTrue(x >= 0 && x <= l) }
    }

    @Test fun `Z3 a late morning more than 14 days ago has no effect`() {
        assertEquals(0, wakeDrift(mapOf(d.minusDays(20) to 90), d))
    }

    private val slots = WeekB.slots
    private fun t(day: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 11, day), LocalTime.of(h, m))

    @Test fun `Z3 N1 a drifted wake moves the cutoff later by the same amount`() {
        val baseWake = nextWake(slots, t(4, 12))!!
        val probe = nightCutoff(baseWake).plusMinutes(5)
        val lateness = mapOf(baseWake.toLocalDate().minusDays(1) to 60)
        val base = nightWindow(slots, probe)!!
        assertNull(nightWindow(slots, probe, lateness = lateness), "5 minutes after the old cutoff the new one has not come yet")
        val drifted = nightWindow(slots, probe.plusMinutes(60), lateness = lateness)!!
        assertEquals(base.wake.plusMinutes(60), drifted.wake); assertEquals(base.cutoff.plusMinutes(60), drifted.cutoff)
    }

    private val list = setOf("com.instagram.android")

    @Test fun `Z3 N2 a late morning makes tonight's cutoff later, so the list is open until then`() {
        val lateness = mapOf(LocalDate.of(2026, 11, 3) to 60)                                          // Tuesday was 60 minutes late, so Wednesday's target moves
        // Wednesday 4 November: wake 07:30 planned, so the cutoff for Wednesday night is Thursday's wake. Probe the night into Wednesday morning instead.
        val baseWake = nextWake(slots, t(3, 23, 30))!!                                                  // Wednesday 07:30
        val cutoff = nightCutoff(baseWake)
        val justAfter = cutoff.plusMinutes(10)
        assertTrue(decide("com.instagram.android", justAfter, slots, list) is Decision.Block, "without drift the night block has started")
        assertEquals(Decision.Allow, decide("com.instagram.android", justAfter, slots, list, lateness = lateness), "with a 60 minute drift it has not")
        assertTrue(decide("com.instagram.android", cutoff.plusMinutes(70), slots, list, lateness = lateness) is Decision.Block)
    }

    @Test fun `N7 Z3 the hard stop moves with the drifted wake`() {
        val wake = t(4, 7, 30).plusMinutes(60)
        assertEquals(wake.minusHours(6), hardStop(wake)); assertTrue(overrideAllowed(wake.minusHours(6).minusMinutes(1), wake))
        assertFalse(overrideAllowed(wake.minusHours(6), wake))
    }

    @Test fun `Z3 the drifted wake is real time across the clock change on 25 October 2026`() {
        val w = driftedWake(slots, at(LocalDate.of(2026, 10, 24), LocalTime.of(23, 0)), mapOf(LocalDate.of(2026, 10, 24) to 60))
        val base = nextWake(slots, at(LocalDate.of(2026, 10, 24), LocalTime.of(23, 0)))!!
        assertNotNull(w); assertEquals(base.plusMinutes(60), w); assertEquals(base.toLocalTime().plusHours(1), w!!.toLocalTime())
    }
}

class FullyVerifiedDayTest {
    private val slots = WeekB.slots
    private val monday = LocalDate.of(2026, 11, 2)
    private fun at(h: Int, m: Int = 0) = at(monday, LocalTime.of(h, m))
    private val needing get() = slotsFor(slots, monday).filter { proofFor(it.slot) != Proof.NONE }
    private val evening get() = at(23, 30)

    @Test fun `N5 a day where every block that needs proof has it is fully verified`() {
        assertTrue(needing.size >= 3)
        assertTrue(fullyVerifiedDay(slots, monday, needing.map { blockKey(it) }.toSet(), evening))
    }

    @Test fun `N5 one block without its proof means the day is not fully verified`() {
        val all = needing.map { blockKey(it) }
        for (missing in all.indices) assertFalse(fullyVerifiedDay(slots, monday, (all - all[missing]).toSet(), evening), "missing ${all[missing]}")
    }

    @Test fun `N5 a block that has not finished yet is not verified`() {
        assertFalse(fullyVerifiedDay(slots, monday, needing.map { blockKey(it) }.toSet(), at(12, 0)))
    }

    @Test fun `N5 a day with nothing to prove is not called fully verified`() {
        val bare = listOf(PatternSlot(DayOfWeek.MONDAY, 600, 660, "Walk", "travel", "", false, ""))
        assertFalse(fullyVerifiedDay(bare, monday, emptySet(), evening))
    }

    @Test fun `N5 a lecture, a work block and a commitment all count`() {
        val types = needing.map { it.slot.type }.toSet()
        assertTrue("lecture" in types && types.any { it.startsWith("study") || it == "code" })
    }

    @Test fun `N5 an imported event and a converted free slot count too, because they carry the same proof`() {
        val extra = PatternSlot(DayOfWeek.MONDAY, 1200, 1260, "Seminar", "tutorial", "", false, "", date = monday)
        val conv = PatternSlot(DayOfWeek.MONDAY, 1155, 1250, CONVERTED_TITLE, "study", "", false, CONVERTED_NOTE, date = monday)
        val merged = slots + extra
        assertFalse(fullyVerifiedDay(merged, monday, needing.map { blockKey(it) }.toSet(), evening), "the new tutorial has no photo yet")
        assertTrue(proofFor(conv) == Proof.WORK_PHOTO)
    }

    @Test fun `N3 N5 the override buys 45 minutes only after such a day`() {
        assertEquals(45, overrideMinutes(true)); assertEquals(30, overrideMinutes(false))
        val wake = at(7, 30).plusDays(1)
        assertEquals(45, nightOffer(wake.minusHours(7), wake, 0, fullyVerifiedDay(slots, monday, needing.map { blockKey(it) }.toSet(), evening)).minutes)
        assertEquals(30, nightOffer(wake.minusHours(7), wake, 0, fullyVerifiedDay(slots, monday, emptySet(), evening)).minutes)
    }

    @Test fun `N5 the night belongs to the day before the wake it ends at`() {
        assertEquals(monday, dayOfNight(at(7, 30).plusDays(1)))
    }
}

class NightRulesNamedTest {
    private val wake = at(LocalDate.of(2026, 11, 4), LocalTime.of(7, 30))

    @Test fun `O2 the final hard stop is 6 hours before wake and no override is possible after it`() {
        assertEquals(wake.minusHours(6), hardStop(wake))
        assertTrue(overrideAllowed(wake.minusHours(6).minusSeconds(1), wake)); assertFalse(overrideAllowed(wake.minusHours(6), wake)); assertFalse(overrideAllowed(wake.minusHours(2), wake))
    }

    @Test fun `R20-F3 a long quiet stretch is at least 60 minutes of screen-off`() {
        assertEquals(60L, QUIET_STRETCH.toMinutes())
        val d = LocalDate.of(2026, 11, 3)
        val (from, until) = nightWindow(d)
        fun ms(h: Int, m: Int, day: Int = 3) = at(LocalDate.of(2026, 11, day), LocalTime.of(h, m)).toInstant().toEpochMilli()
        assertNotNull(deriveNight(listOf(SensorEvent(ms(23, 0), SensorKind.SCREEN_OFF), SensorEvent(ms(0, 0, 4), SensorKind.SCREEN_ON)), from, until))
        assertNull(deriveNight(listOf(SensorEvent(ms(23, 0), SensorKind.SCREEN_OFF), SensorEvent(ms(23, 59), SensorKind.SCREEN_ON)), from, until))
    }

    @Test fun `N1 the cutoff is 8 hours and 20 minutes before wake`() { assertEquals(wake.minusMinutes(8 * 60 + 20), nightCutoff(wake)) }
}

class ShutdownPhotoTest {
    private val d = LocalDate.of(2026, 11, 3)
    private fun ms(h: Int, m: Int = 0, day: Int = 3) = at(LocalDate.of(2026, 11, day), LocalTime.of(h, m)).toInstant().toEpochMilli()
    private val w get() = nightWindow(d)
    private fun e(kind: SensorKind, h: Int, m: Int = 0, day: Int = 3) = SensorEvent(ms(h, m, day), kind)

    @Test fun `S2 sleep starts at the last screen-off after the shutdown photo, so an earlier long off-stretch does not count`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 19), e(SensorKind.SCREEN_ON, 21), e(SensorKind.SCREEN_OFF, 23, 40), e(SensorKind.SCREEN_ON, 7, 10, 4))
        assertEquals(ms(19), deriveNight(events, w.first, w.second)!!.startMs, "no photo: the 19:00 stretch starts the night")
        assertEquals(ms(23, 40), deriveNight(events, w.first, w.second, shutdownPhotoMs = ms(21, 30))!!.startMs, "photo at 21:30: the 19:00 stretch is before it")
    }

    @Test fun `S1 a forgotten shutdown photo changes nothing, the sensors still give a night`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 23, 30), e(SensorKind.SCREEN_ON, 7, 10, 4))
        assertEquals(deriveNight(events, w.first, w.second), deriveNight(events, w.first, w.second, shutdownPhotoMs = null))
    }

    @Test fun `S2 a photo taken in the morning cannot be a shutdown photo`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 23, 30), e(SensorKind.SCREEN_ON, 7, 10, 4))
        assertEquals(ms(23, 30), deriveNight(events, w.first, w.second, shutdownPhotoMs = ms(7, 5, 4))!!.startMs)
    }

    @Test fun `S2 the wake photo ends the night even if the screen was never turned on`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 23, 30))
        val n = deriveNight(events, w.first, w.second, wakePhotoMs = ms(7, 30, 4))!!
        assertEquals(ms(7, 30, 4), n.endMs); assertEquals(480, n.sleepMin)
    }

    @Test fun `S2 a wake photo earlier than the first long screen-on is the end`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 23, 30), e(SensorKind.SCREEN_ON, 8, 0, 4))
        assertEquals(ms(7, 40, 4), deriveNight(events, w.first, w.second, wakePhotoMs = ms(7, 40, 4))!!.endMs)
    }

    @Test fun `S2 the photos never make a negative or backwards night`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 23, 30), e(SensorKind.SCREEN_ON, 7, 10, 4))
        for (shut in listOf(null, ms(18), ms(22), ms(2, 0, 4), ms(7, 0, 4), ms(23, 59))) for (wake in listOf(null, ms(6, 0, 4), ms(7, 10, 4), ms(11, 0, 4), ms(20, 0)))
            deriveNight(events, w.first, w.second, shut, wake)?.let { assertTrue(it.sleepMin >= 0 && it.endMs >= it.startMs, "$shut $wake") }
    }

    @Test fun `S4 the summary keeps the same night with the photos as bounds`() {
        val events = listOf(e(SensorKind.SCREEN_OFF, 23, 30), e(SensorKind.SCREEN_ON, 7, 10, 4))
        val s = summariseNight(d, events, shutdownPhotoMs = ms(23, 10), wakePhotoMs = ms(7, 32, 4))!!
        assertEquals(ms(23, 30), s.night.startMs); assertEquals(ms(7, 10, 4), s.night.endMs)
    }
}
