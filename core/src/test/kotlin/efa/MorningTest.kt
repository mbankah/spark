package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.*

/** A made-up room: soft blocks of light and dark, so it has structure like a desk and a window and not noise. */
private fun scene(seed: Int, w: Int = 64, h: Int = 48): IntArray {
    val r = Random(seed)
    val blocks = Array(6) { Array(8) { 40 + r.nextInt(180) } }
    return IntArray(w * h) { i -> blocks[(i / w) * 6 / h][(i % w) * 8 / w] }
}
private fun shifted(g: IntArray, w: Int, h: Int, dx: Int, dy: Int) = IntArray(w * h) { i -> g[((i / w + dy).coerceIn(0, h - 1)) * w + (i % w + dx).coerceIn(0, w - 1)] }
private fun brighter(g: IntArray, by: Int) = IntArray(g.size) { (g[it] + by).coerceIn(0, 255) }
private fun dimmer(g: IntArray, f: Double) = IntArray(g.size) { (g[it] * f).toInt() }
private fun noisy(g: IntArray, amp: Int, seed: Int) = Random(seed).let { r -> IntArray(g.size) { (g[it] + r.nextInt(-amp, amp + 1)).coerceIn(0, 255) } }
private fun sig(g: IntArray, w: Int = 64, h: Int = 48) = signatureOf(g, w, h)

class MatchTest {
    @Test fun `M2 the same scene matches itself`() {
        val s = sig(scene(1)); val r = matchScore(s, s)
        assertEquals(100, r.score); assertTrue(r.passed)
    }

    @Test fun `M2 M4 the same scene in a lighter or darker room, with sensor noise, still matches`() {
        val ref = sig(scene(2))
        for (live in listOf(brighter(scene(2), 40), dimmer(scene(2), 0.55), noisy(scene(2), 12, 5), noisy(dimmer(scene(2), 0.7), 10, 6))) {
            val r = matchScore(ref, sig(live)); assertTrue(r.passed, "score ${r.score}")
        }
    }

    @Test fun `M2 a small movement of the phone still matches`() {
        val g = scene(3); val ref = sig(g)
        for ((dx, dy) in listOf(2 to 0, 0 to 2, -2 to 1, 3 to 3)) assertTrue(matchScore(ref, sig(shifted(g, 64, 48, dx, dy))).passed, "shift $dx,$dy")
    }

    @Test fun `M2 M3 a different room does not match, across many pairs`() {
        val ref = sig(scene(10))
        for (seed in 11..60) assertFalse(matchScore(ref, sig(scene(seed))).passed, "scene $seed scored ${matchScore(ref, sig(scene(seed))).score}")
    }

    @Test fun `M3 a covered lens, a black room and a blank wall never match, even a reference that is also blank`() {
        val black = sig(IntArray(64 * 48) { 0 }); val wall = sig(IntArray(64 * 48) { 128 })
        val ref = sig(scene(4))
        assertFalse(matchScore(ref, black).passed); assertTrue(matchScore(ref, black).tooDark)
        assertFalse(matchScore(ref, wall).passed)
        assertFalse(matchScore(black, black).passed, "two blank photos are not a match"); assertFalse(matchScore(wall, wall).passed)
    }

    @Test fun `M4 the better of the latest reference and the standing reference counts`() {
        val live = sig(scene(20)); val latest = sig(scene(21)); val standing = sig(scene(20))
        assertFalse(matchAny(listOf(latest), live).passed); assertTrue(matchAny(listOf(latest, standing), live).passed)
        assertFalse(matchAny(emptyList(), live).passed)
    }

    @Test fun `M2 the signature is a fixed small size that cannot rebuild the photo`() {
        assertEquals(256, sig(scene(1, 640, 480), 640, 480).grey.size)
        assertEquals(sig(scene(1)), sig(scene(1)))
        assertFailsWith<IllegalArgumentException> { ImageSignature(ByteArray(10)) }
    }

    @Test fun `M2 any picture size works, including one smaller than the signature`() {
        assertEquals(256, signatureOf(IntArray(4) { it * 60 }, 2, 2).grey.size)
        assertEquals(256, signatureOf(IntArray(1), 1, 1).grey.size)
    }

    @Test fun `M2 scores stay between 0 and 100 for any input`() {
        val rnd = Random(9)
        repeat(300) { val a = sig(IntArray(64 * 48) { rnd.nextInt(256) }); val b = sig(IntArray(64 * 48) { rnd.nextInt(256) }); assertTrue(matchScore(a, b).score in 0..100) }
    }
}

class AlarmTimeTest {
    private val slots = WeekB.slots
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 11, d), LocalTime.of(h, m))
    private val evening = t(3, 21)

    @Test fun `G2 with no onset yet the alarm waits at the backstop, 12-00 the next day`() {
        val p = planAlarm(evening, null, slots)!!
        assertEquals(t(4, 12), p.at); assertEquals(AlarmReason.BACKSTOP, p.reason)
    }

    @Test fun `G2 before noon the next backstop is today`() {
        assertEquals(t(4, 12), planAlarm(t(4, 3), null, slots)!!.at)
        assertEquals(t(5, 12), planAlarm(t(4, 12, 1), null, slots)!!.at)
    }

    @Test fun `Z1 the alarm is onset plus 8 hours`() {
        val p = planAlarm(t(4, 0, 30), t(3, 23, 30), slots)!!
        assertEquals(t(4, 7, 30), p.at); assertEquals(AlarmReason.ONSET, p.reason)
    }

    @Test fun `Z1 a later onset rings later`() {
        assertEquals(t(4, 9, 15), planAlarm(t(4, 2), t(4, 1, 15), slots)!!.at)
    }

    @Test fun `G2 Y7 if onset plus 8 hours would be after 12-00 the backstop rings first, and the 3-hour floor does not hold it`() {
        val late = planAlarm(t(4, 6), t(4, 5), slots)!!                   // onset 05:00 -> 13:00, so the backstop
        assertEquals(t(4, 12), late.at); assertEquals(AlarmReason.BACKSTOP, late.reason)
        val veryLate = planAlarm(t(4, 10), t(4, 10), slots)!!             // onset 10:00: floor would be 13:00, backstop still rings at 12:00
        assertEquals(t(4, 12), veryLate.at)
    }

    @Test fun `Z2 no alarm rings sooner than 3 hours after onset unless it is a hard alarm`() {
        for (h in 0..11) { val onset = t(4, 0).plusHours(h.toLong()); val p = planAlarm(onset, onset, slots)!!; assertTrue(!p.at.isBefore(onset.plusHours(3)) || p.reason == AlarmReason.HARD || p.reason == AlarmReason.BACKSTOP, "onset $onset rings ${p.at} for ${p.reason}") }
    }

    private val hardMorning = PatternSlot(DayOfWeek.WEDNESDAY, 9 * 60, 11 * 60, "Exam", "mytutor", "", true, "")

    @Test fun `D4 Z2 a hard alarm rings early, 30 minutes before, even sooner than 3 hours after onset`() {
        val withHard = slots + hardMorning                                  // Wednesday 4 November 09:00
        val p = planAlarm(t(4, 3), t(4, 2), withHard)!!                     // onset 02:00 -> 10:00, floor 05:00
        assertEquals(t(4, 8, 30), p.at); assertEquals(AlarmReason.HARD, p.reason)
        val early = planAlarm(t(4, 6, 30), t(4, 6), withHard)!!             // onset 06:00: the exam is only 3 hours later
        assertEquals(t(4, 8, 30), early.at, "rings 2.5 hours after onset because it is hard")
    }

    @Test fun `D4 an evening hard commitment does not move the morning alarm, and a hard alarm after the ring time changes nothing`() {
        assertEquals(t(4, 7, 30), planAlarm(t(4, 0, 30), t(3, 23, 30), slots)!!.at)         // Week B has hard MyTutor sessions at 17:00 only
        assertNull(hardAlarmAt(slots, LocalDate.of(2026, 11, 4), LONDON))
        val p = planAlarm(t(4, 0, 30), t(3, 23, 30), slots + hardMorning)!!                 // exam 09:00 -> 08:30, later than 07:30
        assertEquals(t(4, 7, 30), p.at)
    }

    @Test fun `G2 a planned day off has no alarm`() { assertNull(planAlarm(evening, null, slots, isDayOff = true)) }

    @Test fun `X4 onset plus 8 hours is real time across the clock change on 25 October 2026`() {
        val onset = at(LocalDate.of(2026, 10, 24), LocalTime.of(23, 30))
        val p = planAlarm(onset.plusHours(1), onset, slots)!!
        assertEquals(onset.plusHours(8), p.at); assertEquals(LocalTime.of(6, 30), p.at.toLocalTime(), "8 real hours after 23:30 BST is 06:30 GMT")
    }

    @Test fun `Z1 the plan never throws for any onset`() {
        val rnd = Random(4)
        repeat(300) { val now = t(3, 12).plusMinutes(rnd.nextLong(0, 48 * 60)); val onset = if (rnd.nextBoolean()) now.minusMinutes(rnd.nextLong(0, 600)) else null; planAlarm(now, onset, slots) }
    }
}

class StopLadderTest {
    private fun offer(failed: Int, broken: Boolean = false, steps: Int = 0, stepsOk: Boolean = true, waitStart: Long? = null, now: Long = 0) = nextOffer(failed, broken, steps, stepsOk, waitStart, now)

    @Test fun `M2 the photo comes first, twice`() {
        assertEquals(Offer.Photo(1), offer(0)); assertEquals(Offer.Photo(2), offer(1))
    }

    @Test fun `M10 after two failed photos the steps are offered, counted from the start`() {
        assertEquals(Offer.Steps(0, STEPS_REQUIRED), offer(2)); assertEquals(Offer.Steps(25, STEPS_REQUIRED), offer(2, steps = 25))
    }

    @Test fun `M2 M3 steps taken while the photo is still allowed do not stop the alarm`() {
        assertEquals(Offer.Photo(1), offer(0, steps = 500)); assertEquals(Offer.Photo(2), offer(1, steps = 500))
    }

    @Test fun `M10 enough steps after the photo failed lets the alarm stop as the weaker check`() {
        assertEquals(Offer.CanStop(StopMethod.STEPS), offer(2, steps = STEPS_REQUIRED)); assertTrue(StopMethod.STEPS.isWeaker()); assertFalse(StopMethod.PHOTO.isWeaker())
    }

    @Test fun `M10 a broken camera goes straight to steps`() {
        assertEquals(Offer.Steps(0, STEPS_REQUIRED), offer(0, broken = true))
    }

    @Test fun `M10 with no step counter the last rung is a short wait of 3 minutes, then the alarm can stop`() {
        val o = offer(2, stepsOk = false, waitStart = 1_000, now = 1_000); assertEquals(Offer.Wait(1_000 + SHORT_WAIT.toMillis()), o)
        assertEquals(Offer.Wait(1_000 + SHORT_WAIT.toMillis()), offer(2, stepsOk = false, waitStart = 1_000, now = 1_000 + SHORT_WAIT.toMillis() - 1))
        assertEquals(Offer.CanStop(StopMethod.WAIT), offer(2, stepsOk = false, waitStart = 1_000, now = 1_000 + SHORT_WAIT.toMillis()))
        assertEquals(Offer.Wait(5_000 + SHORT_WAIT.toMillis()), offer(2, stepsOk = false, waitStart = null, now = 5_000), "the wait starts when it is first offered")
    }

    @Test fun `LA1 every state of the ladder has a way out`() {
        for (failed in 0..4) for (broken in listOf(false, true)) for (steps in listOf(0, 39, 40)) for (ok in listOf(false, true)) {
            var f = failed; var st = steps; var now = 0L
            var o = offer(f, broken, st, ok, 0, now)
            var guard = 0
            while (o !is Offer.CanStop && guard++ < 6) {
                when (o) {
                    is Offer.Photo -> f = PHOTO_ATTEMPTS             // the photo fails again
                    is Offer.Steps -> st = STEPS_REQUIRED            // the person walks
                    is Offer.Wait -> now = o.untilMs                 // the wait passes
                    else -> {}
                }
                o = offer(f, broken, st, ok, 0, now)
            }
            assertTrue(o is Offer.CanStop, "no way out at $failed $broken $steps $ok: $o")
        }
    }
}

class MorningPhaseTest {
    private val day = LocalDate.of(2026, 11, 4)
    private fun t(h: Int, m: Int = 0, d: Int = 4) = at(LocalDate.of(2026, 11, d), LocalTime.of(h, m))
    private fun ms(h: Int, m: Int = 0) = t(h, m).toInstant().toEpochMilli()

    @Test fun `M5 before the alarm fires there is no morning limit`() {
        assertEquals(MorningPhase.NONE, morningPhase(null, t(6))); assertEquals(MorningPhase.NONE, morningPhase(MorningState(day), t(6)))
    }

    @Test fun `M5 the limit starts when the alarm fires`() {
        assertEquals(MorningPhase.RINGING, morningPhase(MorningState(day, ringStartedMs = ms(7, 30)), t(7, 31)))
    }

    @Test fun `M6 M8 after the alarm stops social stays blocked until the ready photo, however long that takes`() {
        val m = MorningState(day, ringStartedMs = ms(7, 30), stoppedMs = ms(7, 32), stopMethod = StopMethod.PHOTO)
        assertEquals(MorningPhase.WAITING_FOR_READY, morningPhase(m, t(7, 33))); assertEquals(MorningPhase.WAITING_FOR_READY, morningPhase(m, t(11, 0)))
    }

    @Test fun `M8 the ready photo ends it at once, an event and not a timer`() {
        val m = MorningState(day, ringStartedMs = ms(7, 30), stoppedMs = ms(7, 32), stopMethod = StopMethod.PHOTO, readyMs = ms(8, 5), readyMethod = StopMethod.PHOTO)
        assertEquals(MorningPhase.DONE, morningPhase(m, t(8, 6)))
    }

    @Test fun `LA1 the block never runs all day, it ends at 14-00 if the ready photo never comes`() {
        val m = MorningState(day, ringStartedMs = ms(7, 30), stoppedMs = ms(7, 32))
        assertEquals(MorningPhase.WAITING_FOR_READY, morningPhase(m, t(13, 59))); assertEquals(MorningPhase.DONE, morningPhase(m, t(14, 0)))
    }

    @Test fun `LA1 an alarm stopped late still leaves 2 hours to get ready`() {
        val m = MorningState(day, ringStartedMs = ms(12, 0), stoppedMs = ms(13, 40))
        assertEquals(MorningPhase.WAITING_FOR_READY, morningPhase(m, t(15, 39))); assertEquals(MorningPhase.DONE, morningPhase(m, t(15, 40)))
    }

    @Test fun `S4 awake time in bed runs from the first long screen-on to the photo that stops the alarm`() {
        assertEquals(Duration.ofMinutes(22), awakeBeforeAlarmStopped(ms(7, 10), ms(7, 32)))
    }
}

class MorningBlockTest {
    private val slots = WeekB.slots
    private val list = setOf("com.instagram.android")
    private val day = LocalDate.of(2026, 11, 4)                 // a Wednesday
    private fun t(h: Int, m: Int = 0, d: Int = 4) = at(LocalDate.of(2026, 11, d), LocalTime.of(h, m))
    private fun ms(h: Int, m: Int = 0, d: Int = 4) = t(h, m, d).toInstant().toEpochMilli()
    private fun d(now: java.time.ZonedDateTime, m: MorningState?, pkg: String = "com.instagram.android", allow: Allowances = Allowances()) = decide(pkg, now, slots, list, allow, emptySet(), m)
    private val ringing = MorningState(day, ringStartedMs = ms(7, 30))
    private val waiting = MorningState(day, ringStartedMs = ms(7, 30), stoppedMs = ms(7, 33), stopMethod = StopMethod.PHOTO)
    private val ready = waiting.copy(readyMs = ms(8, 10), readyMethod = StopMethod.PHOTO)

    @Test fun `M5 while the alarm rings the listed apps are blocked, and the reason says so`() {
        val b = d(t(7, 31), ringing) as Decision.Block
        assertEquals(BlockReason.MORNING_ALARM, b.reason); assertTrue(b.reason.isMorning)
    }

    @Test fun `M6 after the alarm stops the listed apps stay blocked completely until the ready photo`() {
        for (time in listOf(t(7, 34), t(9, 0), t(13, 59))) { val b = d(time, waiting) as Decision.Block; assertEquals(BlockReason.MORNING_READY, b.reason) }
        assertEquals(readyReleaseAt(ms(7, 33), LONDON), (d(t(8), waiting) as Decision.Block).until)
    }

    @Test fun `M8 the ready photo ends it at once and the normal rules go on`() {
        val after = d(t(8, 11), ready)
        assertTrue(after !is Decision.Block || !(after as Decision.Block).reason.isMorning, "the block must be over, got $after")
    }

    @Test fun `M9 everything not on the list stays open during the alarm and the social block, so calls, maps, banking and music work`() {
        for (pkg in listOf("com.android.dialer", "com.google.android.apps.maps", "com.spotify.music", "com.barclays.android", "app.efa"))
            for (m in listOf(ringing, waiting)) assertEquals(Decision.Allow, d(t(7, 40), m, pkg), pkg)
    }

    @Test fun `M6 the wall cannot open a morning-blocked app, because an allowance never lifts the morning block`() {
        val allow = Allowances(appUntil = mapOf("com.instagram.android" to t(9)))
        assertTrue(d(t(7, 40), waiting, allow = allow) is Decision.Block); assertTrue(d(t(7, 31), ringing, allow = allow) is Decision.Block)
    }

    @Test fun `LA1 a social block that never gets its photo ends by itself at 14-00`() {
        assertTrue(d(t(13, 59), waiting) is Decision.Block)
        val late = d(t(14, 0), waiting); assertTrue(late !is Decision.Block || !(late as Decision.Block).reason.isMorning)
    }

    @Test fun `LA1 yesterday's unfinished morning never blocks today`() {
        val yesterday = MorningState(day.minusDays(1), ringStartedMs = ms(7, 30, 3), stoppedMs = ms(7, 33, 3))
        val b = d(t(7, 0), yesterday); assertTrue(b !is Decision.Block || !(b as Decision.Block).reason.isMorning)
    }

    @Test fun `X2 a morning the phone missed blocks nothing`() {
        val missed = MorningState(day)
        val b = d(t(9, 0), missed); assertTrue(b !is Decision.Block || !(b as Decision.Block).reason.isMorning)
        assertEquals(MorningPhase.NONE, morningPhase(missed, t(9)))
    }

    @Test fun `M5 with no morning state the rules are exactly what they were`() {
        for (h in listOf(7, 10, 15, 23)) assertEquals(decide("com.instagram.android", t(h), slots, list), decide("com.instagram.android", t(h), slots, list, morning = null))
    }

    @Test fun `M6 the morning block comes before a work block, so a person who has just woken is told the real reason`() {
        val workTime = at(LocalDate.of(2026, 11, 2), LocalTime.of(10, 30))                 // inside a Monday study block
        val m = MorningState(LocalDate.of(2026, 11, 2), ringStartedMs = ms(7, 30, 2), stoppedMs = ms(10, 0, 2))
        assertEquals(BlockReason.MORNING_READY, (decide("com.instagram.android", workTime, slots, list, Allowances(), emptySet(), m) as Decision.Block).reason)
    }
}
