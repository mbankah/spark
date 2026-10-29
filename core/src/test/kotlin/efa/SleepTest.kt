package efa

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.*

class SleepTest {
    private val d = LocalDate.of(2026, 11, 3)                       // the evening the night is counted from
    private fun ms(day: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 11, day), LocalTime.of(h, m)).toInstant().toEpochMilli()
    private fun on(day: Int, h: Int, m: Int = 0) = SensorEvent(ms(day, h, m), SensorKind.SCREEN_ON)
    private fun off(day: Int, h: Int, m: Int = 0) = SensorEvent(ms(day, h, m), SensorKind.SCREEN_OFF)
    private val window get() = nightWindow(d)
    private fun night(vararg e: SensorEvent) = deriveNight(e.toList(), window.first, window.second)

    @Test fun `S2 sleep starts at the last screen-off before a long quiet stretch and ends at the first long screen-on`() {
        val n = night(on(3, 21), off(3, 23, 30), on(4, 7, 10))!!
        assertEquals(ms(3, 23, 30), n.startMs); assertEquals(ms(4, 7, 10), n.endMs); assertEquals(460, n.sleepMin)
    }

    @Test fun `S2 scrolling in bed is not sleep, because a short quiet stretch does not start the night`() {
        val n = night(off(3, 22, 0), on(3, 22, 40), off(3, 23, 50), on(4, 7, 50))!!       // 40 minutes off is not a quiet stretch
        assertEquals(ms(3, 23, 50), n.startMs); assertEquals(480, n.sleepMin)
    }

    @Test fun `F3 the quiet stretch is exactly 60 minutes`() {
        assertNotNull(night(off(3, 23, 0), on(4, 0, 0)))
        assertNull(night(off(3, 23, 0), on(3, 23, 59)))
    }

    @Test fun `S3 a night check under 15 minutes is joined into the night and its screen time is taken off`() {
        val n = night(off(3, 23, 30), on(4, 3, 0), off(4, 3, 8), on(4, 7, 10))!!
        assertEquals(1, n.checkCount); assertEquals(8, n.checkMin); assertEquals(ms(4, 7, 10), n.endMs)
        assertEquals(460 - 8, n.sleepMin)
    }

    @Test fun `S3 several checks are all joined`() {
        val n = night(off(3, 23, 30), on(4, 1, 0), off(4, 1, 5), on(4, 4, 0), off(4, 4, 10), on(4, 7, 0))!!
        assertEquals(2, n.checkCount); assertEquals(15, n.checkMin); assertEquals(450 - 15, n.sleepMin)
    }

    @Test fun `S2 a screen-on of 15 minutes or more ends the night, and a later nap is not part of it`() {
        val n = night(off(3, 23, 30), on(4, 5, 30), off(4, 6, 10), on(4, 8, 0))!!         // up for 40 minutes at 05:30
        assertEquals(ms(4, 5, 30), n.endMs); assertEquals(360, n.sleepMin)
        val edge = night(off(3, 23, 30), on(4, 3, 0), off(4, 3, 15), on(4, 7, 0))!!        // exactly 15 minutes: long
        assertEquals(ms(4, 3, 0), edge.endMs)
        val justUnder = night(off(3, 23, 30), on(4, 3, 0), off(4, 3, 14), on(4, 7, 0))!!
        assertEquals(ms(4, 7, 0), justUnder.endMs)
    }

    @Test fun `S2 a night with no quiet stretch gives no sleep, and so does no data`() {
        assertNull(night(off(3, 23, 0), on(3, 23, 30), off(3, 23, 45), on(4, 0, 20)))
        assertNull(night())
    }

    @Test fun `S2 a screen that is still off at noon ends the night at noon`() {
        val n = night(off(3, 23, 30))!!
        assertEquals(window.second, n.endMs)
    }

    @Test fun `S2 events outside the evening to noon window are ignored`() {
        assertNull(night(off(3, 15, 0), on(3, 17, 30)))                                    // an afternoon nap
    }

    @Test fun `S2 the order events arrive in does not matter`() {
        val a = listOf(on(3, 21), off(3, 23, 30), on(4, 3), off(4, 3, 5), on(4, 7))
        assertEquals(deriveNight(a, window.first, window.second), deriveNight(a.shuffled(Random(3)), window.first, window.second))
    }

    @Test fun `S2 a lone screen-on gives no night, because nothing says when the screen went off`() {
        assertNull(night(on(4, 6, 0)))
    }

    @Test fun `X4 the 25 hour night of the clock change is counted in real time`() {
        val day = LocalDate.of(2026, 10, 24)
        val start = at(day, LocalTime.of(23, 30)).toInstant().toEpochMilli()
        val end = at(day.plusDays(1), LocalTime.of(7, 30)).toInstant().toEpochMilli()
        val (from, until) = nightWindow(day)
        val n = deriveNight(listOf(SensorEvent(start, SensorKind.SCREEN_OFF), SensorEvent(end, SensorKind.SCREEN_ON)), from, until)!!
        assertEquals(9 * 60, n.sleepMin, "23:30 to 07:30 across the clock change is 9 hours, not 8")
        assertEquals(19 * 60L, Duration.ofMillis(until - from).toMinutes(), "18:00 to 12:00 is 18 wall-clock hours, 19 real hours on the night the clocks go back")
    }

    @Test fun `S5 charging, movement and light are summarised over the sleeping part of the night`() {
        val e = listOf(
            off(3, 23, 0), on(4, 7, 0),
            SensorEvent(ms(3, 22, 30), SensorKind.CHARGING_ON), SensorEvent(ms(4, 3, 0), SensorKind.CHARGING_OFF),
            SensorEvent(ms(4, 1, 0), SensorKind.MOTION), SensorEvent(ms(4, 5, 0), SensorKind.MOTION), SensorEvent(ms(3, 20, 0), SensorKind.MOTION),
            SensorEvent(ms(4, 2, 0), SensorKind.LIGHT, 0), SensorEvent(ms(4, 4, 0), SensorKind.LIGHT, 0), SensorEvent(ms(4, 6, 0), SensorKind.LIGHT, 2), SensorEvent(ms(4, 6, 30), SensorKind.LIGHT, 3),
        )
        val s = summariseNight(d, e)!!
        assertEquals(50, s.chargingPct, "charging 23:00 to 03:00 of a 08:00 night"); assertEquals(2, s.motionEvents, "the 20:00 movement was before bed")
        assertEquals(50, s.darkPct)
    }

    @Test fun `S5 there is no microphone signal at all`() {
        assertTrue(SensorKind.entries.none { it.name.contains("MIC", ignoreCase = true) || it.name.contains("AUDIO", ignoreCase = true) || it.name.contains("SOUND", ignoreCase = true) })
    }

    @Test fun `S7 a silence longer than 40 minutes in the night counts as a gap, and heartbeats prevent it`() {
        fun hb(h: Int, m: Int, day: Int = 4) = SensorEvent(ms(day, h, m), SensorKind.HEARTBEAT)
        val quiet = listOf(off(3, 23, 0), on(4, 7, 0))
        assertTrue(summariseNight(d, quiet)!!.gapCount >= 1, "no heartbeat for 8 hours is a gap")
        val beats = quiet + generateSequence(ms(3, 23, 0) + 900_000) { it + 900_000 }.takeWhile { it < ms(4, 7, 0) }.map { SensorEvent(it, SensorKind.HEARTBEAT) }.toList()
        assertEquals(0, summariseNight(d, beats)!!.gapCount)
        val missing = beats.filter { it.kind != SensorKind.HEARTBEAT || it.atMs !in ms(4, 2, 0)..ms(4, 3, 30) }
        assertEquals(1, summariseNight(d, missing)!!.gapCount)
        assertEquals(HEARTBEAT_EVERY, Duration.ofMinutes(15)); assertTrue(SERVICE_GAP > HEARTBEAT_EVERY)
    }

    @Test fun `S4 awake time in bed is the time from waking to the wake photo and never negative`() {
        assertEquals(Duration.ofMinutes(22), awakeInBed(ms(4, 7, 0), ms(4, 7, 22)))
        assertEquals(Duration.ZERO, awakeInBed(ms(4, 7, 22), ms(4, 7, 0)))
    }

    @Test fun `ST4 raw events older than 30 days are deleted and newer ones are kept`() {
        val now = at(LocalDate.of(2026, 12, 10), LocalTime.NOON).toInstant()
        val old = SensorEvent(now.minus(Duration.ofDays(31)).toEpochMilli(), SensorKind.SCREEN_OFF)
        val edge = SensorEvent(now.minus(Duration.ofDays(30)).plusSeconds(1).toEpochMilli(), SensorKind.SCREEN_OFF)
        assertEquals(listOf(old), rawEventsToDelete(listOf(old, edge), now))
    }

    @Test fun `ST4 an event before noon belongs to the night that began the evening before`() {
        assertEquals(LocalDate.of(2026, 11, 3), nightDateOf(ms(4, 3, 0)))
        assertEquals(LocalDate.of(2026, 11, 4), nightDateOf(ms(4, 13, 0)))
        assertEquals(LocalDate.of(2026, 11, 3), nightDateOf(ms(3, 22, 0)))
    }

    @Test fun `S5 light is stored as one of four buckets`() {
        assertEquals(listOf(0, 1, 2, 3), listOf(0.2f, 4f, 50f, 400f).map(::lightBucket))
        assertEquals(0, lightBucket(0f)); assertEquals(1, lightBucket(1f)); assertEquals(3, lightBucket(100f))
    }

    @Test fun `S7 reading a night never throws, whatever the events are`() {
        val rnd = Random(7)
        repeat(500) {
            val events = List(rnd.nextInt(0, 60)) { SensorEvent(window.first + rnd.nextLong(-3_600_000, 22L * 3_600_000), SensorKind.entries[rnd.nextInt(SensorKind.entries.size)], rnd.nextInt(0, 4)) }
            summariseNight(d, events)?.let { assertTrue(it.night.sleepMin >= 0 && it.night.endMs >= it.night.startMs); assertTrue(it.chargingPct in 0..100 && it.darkPct in 0..100) }
        }
    }

    @Test fun `S1 a night is derived from sensors alone, with no photo needed`() {
        assertNotNull(night(off(3, 23, 30), on(4, 7, 10)))
    }
}
