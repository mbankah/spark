package efa

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class BlockTest {
    private fun t(h: Int, m: Int = 0, d: Int = 19) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private val start = t(10); private val end = t(12)
    private val deadline = t(7, 30, 20)

    private fun verify(proof: Proof, now: java.time.ZonedDateTime, hold: Boolean = true, dup: Boolean = false,
                       loc: LocationResult? = null, recovered: Int = 0) =
        verifyProof(proof, start, end, now, hold, dup, loc, deadline, recovered)

    private fun slot(type: String, title: String = "x") = PatternSlot(DayOfWeek.MONDAY, 600, 720, title, type, "", false, "")

    @Test fun `G1 proof kinds by block type`() {
        assertEquals(Proof.GIT_COMMIT, proofFor(slot("code")))
        assertEquals(Proof.WORK_PHOTO, proofFor(slot("study-standard")))
        assertEquals(Proof.WORK_PHOTO, proofFor(slot("study")))
        assertEquals(Proof.ATTENDANCE, proofFor(slot("lecture")))
    }

    @Test fun `Y6 tutorials count as lectures for attendance`() { assertEquals(Proof.ATTENDANCE, proofFor(slot("tutorial"))) }

    @Test fun `Y1 each fixed commitment other than a lecture needs one photo in its window`() {
        for (type in listOf("training", "match", "gym", "church", "mytutor", "chore")) assertEquals(Proof.WINDOW_PHOTO, proofFor(slot(type)), type)
    }

    @Test fun `Y1 meals are not photographed and routines carry no proof`() {
        for (type in listOf("meal", "routine", "travel", "free", "sleep", "planning", "errand")) assertEquals(Proof.NONE, proofFor(slot(type)), type)
    }

    @Test fun `Y1 every pattern slot gets exactly one proof rule and no meal asks for a photo`() {
        assertTrue(WeekB.slots.filter { it.type == "meal" }.all { proofFor(it) == Proof.NONE })
        assertTrue(WeekB.slots.filter { it.type == "chore" || it.type == "gym" || it.type == "church" }.all { proofFor(it) == Proof.WINDOW_PHOTO })
    }

    // ---- W1: timer, hold, photo ----
    @Test fun `W1 a one second hold must be unbroken`() {
        val h = HoldTracker()
        h.down(0); assertFalse(h.up(999)); assertFalse(h.completed)
        h.down(5_000); assertTrue(h.up(6_000)); assertTrue(h.completed)
    }

    @Test fun `W1 letting go early restarts the hold`() {
        val h = HoldTracker(); h.down(0); h.cancel(); assertFalse(h.up(5_000)); assertEquals(0.0, h.progress(10_000))
        h.down(10_000); assertEquals(0.5, h.progress(10_500), 1e-9)
    }

    @Test fun `W1 the photo does not count while the timer is running`() {
        assertEquals(Reason.TIMER_RUNNING, verify(Proof.WORK_PHOTO, t(11, 59)).reason)
    }

    @Test fun `W1 the photo does not count before the hold`() {
        assertEquals(Reason.NEEDS_HOLD, verify(Proof.WORK_PHOTO, t(12, 1), hold = false).reason)
    }

    @Test fun `W1 timer then hold then photo verifies the whole block`() {
        val v = verify(Proof.WORK_PHOTO, t(12, 2))
        assertTrue(v.verified); assertEquals(120, v.creditedMinutes); assertFalse(v.recovered)
    }

    @Test fun `W2 an unproven block counts as lost`() {
        val v = verify(Proof.WORK_PHOTO, t(12, 1), hold = false)
        assertFalse(v.verified); assertEquals(0, v.creditedMinutes)
    }

    @Test fun `G5 one capture decides with no review step`() {
        // verifyProof takes the single photo's facts and returns the final verdict at once.
        assertEquals(Reason.OK, verify(Proof.WORK_PHOTO, t(12, 5)).reason)
    }

    // ---- B7 backfill ----
    @Test fun `B7 a late proof is marked recovered and capped at one hour`() {
        val v = verify(Proof.WORK_PHOTO, t(13, 0))                       // 60 min late
        assertTrue(v.verified); assertTrue(v.recovered); assertEquals(60, v.creditedMinutes)
    }

    @Test fun `B7 recovery credit is what is left of the hour`() {
        val v = verify(Proof.WORK_PHOTO, t(13, 0), recovered = 45)
        assertEquals(15, v.creditedMinutes)
    }

    @Test fun `B7 no recovery left means no credit`() {
        assertEquals(Reason.BACKFILL_CAP, verify(Proof.WORK_PHOTO, t(13, 0), recovered = 60).reason)
    }

    @Test fun `B7 nothing can be recovered after the next morning`() {
        assertEquals(Reason.OUTSIDE_WINDOW, verify(Proof.WORK_PHOTO, t(7, 31, 20)).reason)
    }

    // ---- Y1 / Y3 window photos ----
    @Test fun `Y1 a window photo counts only inside the window`() {
        assertEquals(Reason.TOO_EARLY, verify(Proof.WINDOW_PHOTO, t(9, 59)).reason)
        assertTrue(verify(Proof.WINDOW_PHOTO, t(10, 0)).verified)
        assertTrue(verify(Proof.WINDOW_PHOTO, t(12, 0)).verified)
    }

    @Test fun `Y3 a fixed commitment without its photo in the window is lost`() {
        val v = verify(Proof.WINDOW_PHOTO, t(12, 1))
        assertFalse(v.verified); assertEquals(Reason.OUTSIDE_WINDOW, v.reason)
    }

    // ---- L1 to L3 attendance ----
    @Test fun `L1 attendance needs the in-window photo`() {
        assertEquals(Reason.OUTSIDE_WINDOW, verify(Proof.ATTENDANCE, t(12, 1), loc = LocationResult.NEAR_VENUE).reason)
    }

    @Test fun `L2 near the named building is full proof`() {
        val v = verify(Proof.ATTENDANCE, t(10, 20), loc = LocationResult.NEAR_VENUE)
        assertTrue(v.verified); assertFalse(v.weaker)
    }

    @Test fun `L2 far enough from home is weaker proof and marked`() {
        val v = verify(Proof.ATTENDANCE, t(10, 20), loc = LocationResult.AWAY_FROM_HOME)
        assertTrue(v.verified); assertTrue(v.weaker)
    }

    @Test fun `L3 inside the home radius the proof is rejected`() {
        assertEquals(Reason.REJECTED_NEAR_HOME, verify(Proof.ATTENDANCE, t(10, 20), loc = LocationResult.REJECTED_NEAR_HOME).reason)
    }

    @Test fun `L3 no location fix means no attendance`() {
        assertEquals(Reason.NO_LOCATION, verify(Proof.ATTENDANCE, t(10, 20), loc = null).reason)
    }

    @Test fun `L2 location classes by distance`() {
        assertEquals(LocationResult.NEAR_VENUE, classifyLocation(120.0, 3000.0))
        assertEquals(LocationResult.NEAR_VENUE, classifyLocation(120.0, 50.0))        // O4: a venue next to home passes on the building check
        assertEquals(LocationResult.NEAR_VENUE, classifyLocation(150.0, 3000.0))
        assertEquals(LocationResult.AWAY_FROM_HOME, classifyLocation(151.0, 300.0))
        assertEquals(LocationResult.REJECTED_NEAR_HOME, classifyLocation(400.0, 100.0))
        assertEquals(LocationResult.REJECTED_NEAR_HOME, classifyLocation(400.0, 249.0))
        assertEquals(LocationResult.AWAY_FROM_HOME, classifyLocation(400.0, 250.0))
        assertEquals(LocationResult.VENUE_UNKNOWN, classifyLocation(null, 900.0))
        assertEquals(LocationResult.REJECTED_NEAR_HOME, classifyLocation(null, 100.0))
        assertEquals(LocationResult.HOME_UNSET, classifyLocation(400.0, null))
    }

    // Real campus points from the generated directory (Murray Learning Centre) and a made-up home 1.5 km away.
    private val murray = Venues.find("Murray Learning Centre LC-UG05")!!.let { Pair(it.lat, it.lon) }
    private val home = Pair(52.4620, -1.9100)
    private fun fix(lat: Double, lon: Double, acc: Double = 20.0) = Fix(lat, lon, acc)

    @Test fun `L2 a fix at the lecture building passes as full proof`() {
        assertEquals(LocationResult.NEAR_VENUE, locate(fix(murray.first + 0.0005, murray.second), murray, home))   // about 55 m
    }

    @Test fun `L2 a fix in the car park 200 m from the building and far from home is weaker proof`() {
        assertEquals(LocationResult.AWAY_FROM_HOME, locate(fix(murray.first + 0.0018, murray.second), murray, home))
    }

    @Test fun `L3 a fix at home is rejected`() {
        assertEquals(LocationResult.REJECTED_NEAR_HOME, locate(fix(home.first, home.second), murray, home))
    }

    @Test fun `E11 an imprecise or missing fix is no fix`() {
        assertEquals(LocationResult.NO_FIX, locate(fix(murray.first, murray.second, acc = 400.0), murray, home))
        assertEquals(LocationResult.NO_FIX, locate(null, murray, home))
    }

    @Test fun `L2 with no home set only the building check can pass`() {
        assertEquals(LocationResult.NEAR_VENUE, locate(fix(murray.first, murray.second), murray, null))
        assertEquals(LocationResult.HOME_UNSET, locate(fix(52.5, -1.8), murray, null))
        assertEquals(Reason.HOME_NOT_SET, verify(Proof.ATTENDANCE, t(10, 20), loc = LocationResult.HOME_UNSET).reason)
    }

    @Test fun `L8 home is saved at about 100 m not a house`() {
        assertEquals(Pair(52.462, -1.91), coarseHome(52.46234, -1.90988))
        assertTrue(haversineM(52.46234, -1.90988, 52.462, -1.91) < 120)
    }

    @Test fun `O4 venues within the home radius are listed so they use the building check alone`() {
        val nearHome = Pair(murray.first + 0.0005, murray.second)
        assertTrue(venuesNearHome(nearHome, Venues.buildings).any { it.alias == "Murray Learning Centre" })
        assertTrue(venuesNearHome(home, Venues.buildings).isEmpty())
    }

    @Test fun `L4 L5 every lecture and tutorial venue on the timetable is matched or listed as unmatched`() {
        val venues = WeekB.slots.filter { it.type == "lecture" || it.type == "tutorial" }.map { it.venue }.toSet()
        assertTrue(venues.size >= 7)
        for (v in venues) assertTrue(Venues.find(v) != null || v in Venues.unmatched, "$v is neither matched nor reported")
    }

    @Test fun `L4 directory points are on the Edgbaston campus`() {
        assertTrue(Venues.buildings.isNotEmpty())
        for (b in Venues.buildings) assertTrue(b.lat in 52.44..52.46 && b.lon in -1.95..-1.92, "${b.alias} ${b.lat},${b.lon}")
    }

    @Test fun `L5 every directory building keeps its OpenStreetMap source`() {
        assertTrue(Venues.buildings.all { it.osmWayId > 0 && it.osmName.isNotBlank() })
    }

    @Test fun `L2 distance between two points`() {
        // Birmingham Aston Webb to Library is a few hundred metres; a degree of latitude is about 111 km.
        assertEquals(111_195.0, haversineM(52.0, -1.9, 53.0, -1.9), 200.0)
        assertEquals(0.0, haversineM(52.45, -1.93, 52.45, -1.93), 1e-6)
    }

    @Test fun `ST2 location is kept as a band never coordinates`() {
        assertEquals("<50m", distanceBand(12.0)); assertEquals("<250m", distanceBand(200.0)); assertEquals(">=1km", distanceBand(5000.0))
    }

    // ---- ST2 duplicate ----
    @Test fun `ST2 a reused photo is rejected`() {
        assertEquals(Reason.DUPLICATE, verify(Proof.WORK_PHOTO, t(12, 5), dup = true).reason)
    }

    @Test fun `ST2 difference hash is stable under brightness change and differs between scenes`() {
        val a = IntArray(72) { (it * 37) % 255 }
        val brighter = IntArray(72) { minOf(255, a[it] + 10) }
        val other = IntArray(72) { (it * 91 + 13) % 255 }
        assertEquals(differenceHash(a), differenceHash(brighter))
        assertTrue(java.lang.Long.bitCount(differenceHash(a) xor differenceHash(other)) > 4)
    }

    @Test fun `G1 a code block cannot be verified until the commit check exists`() {
        val v = verify(Proof.GIT_COMMIT, t(12, 5))
        assertFalse(v.verified); assertEquals(Reason.COMMIT_CHECK_UNAVAILABLE, v.reason)
        assertTrue(v.reason.text().contains("cannot be verified"))
    }

    @Test fun `G1 a block that needs no photo is not verified by one`() {
        assertEquals(Reason.NOT_REQUIRED, verify(Proof.NONE, t(11)).reason)
    }

    @Test fun `X4 a block window across the clocks going back is its real length`() {
        val s = at(LocalDate.of(2026, 10, 25), LocalTime.of(0, 30)); val e = at(LocalDate.of(2026, 10, 25), LocalTime.of(3, 0))
        assertEquals(Duration.ofMinutes(210), Duration.between(s, e))   // 02:00 BST becomes 01:00 GMT: 2.5 h on the clock, 3.5 h real
    }

    @Test fun `W2 W3 finished work blocks without proof are listed until the next wake`() {
        // Mon 19 Oct 12:30: the standard cycle 10:10-12:15 is over and unproven.
        val due = proofDue(WeekB.slots, t(12, 30), emptySet())
        assertTrue(due.any { it.slot.title == "Notes and problems: Randomness" })
        // Once proven it leaves the list.
        val key = blockKey(due.first { it.slot.title == "Notes and problems: Randomness" })
        assertTrue(proofDue(WeekB.slots, t(12, 30), setOf(key)).none { it.slot.title == "Notes and problems: Randomness" })
        // Tuesday after the 07:30 wake the Monday block can no longer be proved.
        assertTrue(proofDue(WeekB.slots, t(7, 31, 20), emptySet()).none { it.plannedDay == LocalDate.of(2026, 10, 19) })
    }

    @Test fun `B7 the next wake after a block is the planned wake`() {
        assertEquals(t(7, 30, 20), nextWake(WeekB.slots, t(12, 15)))
        assertEquals(t(8, 30, 24), nextWake(WeekB.slots, t(20, 0, 23)))
    }

    @Test fun `W1 a block key is stable and unique per day`() {
        val a = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "study-standard" }
        val b = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 26)).first { it.slot.type == "study-standard" }
        assertEquals(blockKey(a), blockKey(a)); assertNotEquals(blockKey(a), blockKey(b))
    }
}
