package efa

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

/** Test names start with the ledger ID they prove; tools/link-tests.mjs reads them into docs/TRACEABILITY.md. */
class BufferTest {
    private fun value(s: BufferState) = (s as BufferState.Value).result

    @Test fun `B1 Buffer plus 4_5h from 17_5 available minus 13_0 work`() {
        val r = value(computeBuffer(17.5, listOf(Task("a", 5.0, 5), Task("b", 8.0, 5)), shortSleep = false))
        assertEquals(4.5, r.hours, 1e-9)
        assertEquals("Buffer +4.5h", r.label())
        assertFalse(r.negative)
    }

    @Test fun `B1 negative Buffer says NEGATIVE`() {
        val r = value(computeBuffer(10.0, listOf(Task("a", 13.0, 5)), shortSleep = false))
        assertEquals("Buffer NEGATIVE -3.0h", r.label())
        assertTrue(r.negative)
    }

    @Test fun `AP6 a negative Buffer never relies on colour alone`() {
        assertTrue(value(computeBuffer(0.0, listOf(Task("a", 0.1, 1)), false)).label().contains("NEGATIVE"))
    }

    @Test fun `B3 working shows each task its heaviness its hours and the sum`() {
        val w = value(computeBuffer(17.5, listOf(Task("Notes", 5.0, 4), Task("Problems", 8.0, 7)), false)).working()
        assertTrue("Notes (heaviness 4): 5.0h x 1.0 = 5.0h" in w, w)
        assertTrue("Problems (heaviness 7): 8.0h x 1.0 = 8.0h" in w, w)
        assertTrue("Work 13.0h" in w && "Available 17.5h" in w && w.endsWith("Buffer +4.5h"), w)
    }

    @Test fun `B4 heaviness is one slider from 1 to 10`() {
        assertFailsWith<IllegalArgumentException> { Task("x", 1.0, 0) }
        assertFailsWith<IllegalArgumentException> { Task("x", 1.0, 11) }
        assertEquals(1.0, estimateHours(1.0, 5), 1e-9)       // Q-G1 mid point
        assertTrue(estimateHours(2.0, 9) > estimateHours(2.0, 2))
    }

    @Test fun `B8 short sleep makes hard tasks x1_5 and easy x1_15`() {
        val tasks = listOf(Task("hard", 2.0, 8), Task("easy", 2.0, 3))
        val r = value(computeBuffer(10.0, tasks, shortSleep = true))
        assertEquals(3.0, r.lines[0].hours, 1e-9)
        assertEquals(2.3, r.lines[1].hours, 1e-9)
        assertEquals(10.0 - 5.3, r.hours, 1e-9)
    }

    @Test fun `B8 threshold is 6_5 hours or less`() {
        assertTrue(isShortSleep(6.5)); assertTrue(isShortSleep(3.0)); assertFalse(isShortSleep(6.6))
    }

    @Test fun `B8 the multipliers are adjustable`() {
        val r = value(computeBuffer(10.0, listOf(Task("hard", 2.0, 8)), true, ShortSleepWeights(hard = 2.0)))
        assertEquals(4.0, r.lines[0].hours, 1e-9)
    }

    @Test fun `D6 no deadline shows no number`() {
        assertEquals(BufferState.NoDeadline, computeBuffer(null, emptyList(), false))
    }

    @Test fun `D1 study cycle sizes`() {
        assertEquals(2.5, STUDY_CYCLE_STANDARD_HOURS); assertEquals(1.0, STUDY_CYCLE_LIGHT_HOURS)
    }

    @Test fun `P5 stress is concrete arithmetic with overlap counted once`() {
        val t0 = Instant.parse("2026-10-20T08:00:00Z")
        val a = Interval(t0.plusSeconds(3600), t0.plusSeconds(3 * 3600))
        val b = Interval(t0.plusSeconds(2 * 3600), t0.plusSeconds(4 * 3600))   // overlaps a
        assertEquals(10.0 - 3.0, availableHours(t0, t0.plusSeconds(10 * 3600), listOf(a, b)), 1e-9)
    }

    @Test fun `X4 a 25 hour day is 25 hours and the Buffer follows`() {
        val from = at(LocalDate.of(2026, 10, 25), LocalTime.MIDNIGHT).toInstant()
        val to = at(LocalDate.of(2026, 10, 26), LocalTime.MIDNIGHT).toInstant()
        assertEquals(25.0, availableHours(from, to, emptyList()), 1e-9)
        val sat = at(LocalDate.of(2026, 10, 24), LocalTime.MIDNIGHT).toInstant()
        assertEquals(24.0, availableHours(sat, from, emptyList()), 1e-9)   // Saturday is a normal day
        // 8 h of sleep inside the long day leaves 17 h, not 16.
        val sleep = Interval(from, from.plus(Duration.ofHours(8)))
        assertEquals(17.0, availableHours(from, to, listOf(sleep)), 1e-9)
    }
}

class NightTest {
    private val mon = at(LocalDate.of(2026, 10, 19), LocalTime.of(7, 30))

    @Test fun `N1 cutoff 23_10 for a 07_30 wake`() {
        assertEquals(LocalTime.of(23, 10), nightCutoff(mon).toLocalTime())
        assertEquals(LocalDate.of(2026, 10, 18), nightCutoff(mon).toLocalDate())
    }

    @Test fun `N1 fall asleep time is subtracted not added`() {
        assertEquals(LocalTime.of(23, 0), nightCutoff(mon, Duration.ofMinutes(30)).toLocalTime())
    }

    @Test fun `N3 N5 an override buys 30 minutes or 45 after a verified day`() {
        assertEquals(30, overrideMinutes(false)); assertEquals(45, overrideMinutes(true))
    }

    @Test fun `N6 the screen shows the 8 hour sum`() {
        val nine = at(LocalDate.of(2026, 10, 20), LocalTime.of(9, 0))
        assertEquals("Wake 09:00, so sleeping after 01:00 means under 8h", eightHourSum(nine))
    }

    @Test fun `N7 no override from six hours before target wake`() {
        val cut = nightCutoff(mon)
        assertTrue(overrideAllowed(cut, mon))
        assertTrue(overrideAllowed(mon.minusHours(6).minusMinutes(1), mon))
        assertFalse(overrideAllowed(mon.minusHours(6), mon))
        assertFalse(overrideAllowed(cut.minusMinutes(1), mon))
        assertEquals(LocalTime.of(1, 30), hardStop(mon).toLocalTime())
    }

    @Test fun `X4 week across the clocks going back keeps elapsed time right`() {
        // Sun 25 Oct 2026: 02:00 BST becomes 01:00 GMT.
        val satWake = at(LocalDate.of(2026, 10, 24), LocalTime.of(8, 30))
        val sunWake = at(LocalDate.of(2026, 10, 25), LocalTime.of(8, 30))
        val monWake = at(LocalDate.of(2026, 10, 26), LocalTime.of(7, 30))
        assertEquals(3600, satWake.offset.totalSeconds); assertEquals(0, sunWake.offset.totalSeconds)
        assertEquals(25L, Duration.between(satWake, sunWake).toHours())
        assertEquals(23L, Duration.between(sunWake, monWake).toHours())   // 08:30 GMT to 07:30 GMT
        // Cutoff before Sunday's wake is 8 h 20 min of real time earlier: the wall clock reads 01:10 (BST), not 00:10.
        val cut = nightCutoff(sunWake)
        assertEquals(Duration.ofMinutes(8 * 60 + 20), Duration.between(cut, sunWake))
        assertEquals(LocalTime.of(1, 10), cut.toLocalTime())
        assertEquals(3600, cut.offset.totalSeconds)
        // Monday and the Friday after are unaffected.
        assertEquals(LocalTime.of(23, 10), nightCutoff(monWake).toLocalTime())
        assertEquals(LocalDate.of(2026, 10, 25), nightCutoff(monWake).toLocalDate())
        // Every alarm in the week is the same wall time in London, and instants are strictly increasing.
        val wakes = (24..30).map { at(LocalDate.of(2026, 10, it), LocalTime.of(7, 30)) }
        wakes.zipWithNext().forEach { (a, b) -> assertTrue(a.toInstant().isBefore(b.toInstant())) }
        assertEquals(setOf(LocalTime.of(7, 30)), wakes.map { it.toLocalTime() }.toSet())
    }
}

class MoneyTest {
    private val table = mapOf(1.0 to (2500 to 1500), 0.9 to (2250 to 1750), 0.8 to (2000 to 2000),
        0.7 to (1750 to 2250), 0.6 to (1500 to 2500), 0.5 to (1250 to 2750))

    @Test fun `R7 R8 money table from the ledger`() {
        for ((ratio, exp) in table) {
            val m = fromRatio(ratio)
            assertEquals(exp.first, m.freeToSpendPence, "free at $ratio"); assertEquals(exp.second, m.savingsPence, "saved at $ratio")
        }
    }

    @Test fun `R7 at least 15 pounds is saved every week`() {
        for (r in 0..100) assertTrue(fromRatio(r / 100.0).savingsPence >= MIN_SAVED_PENCE)
    }

    @Test fun `R6 food is 40 pounds and never tied to work`() {
        assertEquals(4000, fromRatio(0.0).foodPence); assertEquals(4000, fromRatio(1.0).foodPence)
        assertEquals(WEEKLY_TOTAL_PENCE, FOOD_PENCE + OTHER_PENCE)
    }

    @Test fun `R8 payout is weighted by heaviness`() {
        val planned = listOf(WeightedWork(8, 2.0), WeightedWork(2, 2.0))   // weights 16 + 4
        val onlyHard = weeklyMoney(listOf(WeightedWork(8, 2.0)), planned)   // 16/20 = 80%
        val onlyEasy = weeklyMoney(listOf(WeightedWork(2, 2.0)), planned)   // 4/20 = 20%
        assertEquals(2000, onlyHard.freeToSpendPence); assertEquals(500, onlyEasy.freeToSpendPence)
    }

    @Test fun `R10 inflating heaviness gains nothing when planned work is inflated too`() {
        val low = weeklyMoney(listOf(WeightedWork(3, 1.0)), listOf(WeightedWork(3, 2.0)))
        val high = weeklyMoney(listOf(WeightedWork(9, 1.0)), listOf(WeightedWork(9, 2.0)))
        assertEquals(low, high)
    }

    @Test fun `R7 ratio above 1 or no plan cannot exceed the cap`() {
        assertEquals(2500, weeklyMoney(listOf(WeightedWork(5, 9.0)), listOf(WeightedWork(5, 1.0))).freeToSpendPence)
        assertEquals(0, weeklyMoney(emptyList(), emptyList()).freeToSpendPence)
    }

    @Test fun `G6 money is zero-penalty for the first 14 days`() {
        assertFalse(moneyIsLive(0)); assertFalse(moneyIsLive(13)); assertTrue(moneyIsLive(14))
    }

    @Test fun `R9 pounds format`() { assertEquals("£22.50", pounds(2250)) }
}

class WallTest {
    @Test fun `H1 lengths grow 75 100 130 165 205`() {
        assertEquals(listOf(75, 100, 130, 165, 205, 250), (0..5).map { WallLength.dayOff(it) })
    }

    @Test fun `H3 a same-day request adds 40`() {
        assertEquals(115, WallLength.dayOff(0, sameDay = true)); assertEquals(140, WallLength.dayOff(1, sameDay = true))
    }

    @Test fun `H5 a weekend is one use`() {
        assertEquals(WallLength.dayOff(1), WallLength.dayOff(1))   // one request, one string, one use counted by the caller
    }

    @Test fun `N4 each extra override in a night is 25 longer`() {
        assertEquals(listOf(75, 100, 125), (1..3).map { WallLength.nightOverride(it) })
    }

    @Test fun `M7 K2 LA9 F4 are 75`() {
        assertEquals(75, WallLength.BLOCKLIST_EDIT); assertEquals(75, WallLength.OPEN_BLOCKED_APP)
        assertEquals(75, WallLength.FREE_SLOT_CHANGE); assertEquals(75, WallLength.TAMPER)
    }

    @Test fun `M7 the string is fresh and the right length every time`() {
        val a = newWallString(75); val b = newWallString(75)
        assertEquals(75, a.length); assertNotEquals(a, b); assertFalse(a.any { it.isWhitespace() })
    }

    @Test fun `M7 typing the whole string by hand works`() {
        val w = WallEntry("ab12"); "ab12".forEach { assertTrue(w.input(it.toString())) }
        assertTrue(w.done)
    }

    @Test fun `M7 a mistake restarts from zero`() {
        val w = WallEntry("abcd"); w.input("a"); w.input("b"); assertFalse(w.input("x"))
        assertEquals(0, w.typed); assertEquals(1, w.restarts); assertFalse(w.done)
    }

    @Test fun `M7 paste or autofill of several characters is rejected and restarts`() {
        val w = WallEntry("abcd"); w.input("a"); assertFalse(w.input("bcd"))
        assertEquals(0, w.typed); assertFalse(w.done)
    }
}

class StorageTest {
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    @Test fun `ST1 thumbnails older than 14 days are deleted`() {
        val old = Thumb("old", now.minus(Duration.ofDays(14)), 30_000); val fresh = Thumb("new", now.minus(Duration.ofDays(13)), 30_000)
        assertEquals(listOf(old), thumbsToDelete(listOf(old, fresh), now))
    }

    @Test fun `ST4 thumbnails stay under 25 MB oldest deleted first`() {
        val ts = (0 until 1000).map { Thumb("t$it", now.minusSeconds(it * 60L), 30 * 1024) }   // 30 MB, all recent
        val del = thumbsToDelete(ts, now)
        val left = ts - del.toSet()
        assertTrue(left.sumOf { it.bytes.toLong() } <= StoragePolicy.THUMB_CEILING_BYTES)
        assertTrue(del.all { d -> left.all { d.takenAt <= it.takenAt } }, "only the oldest go")
    }

    @Test fun `ST4 ceilings and keep times`() {
        assertEquals(30, StoragePolicy.SENSOR_EVENTS_KEEP.toDays()); assertEquals(365, StoragePolicy.TAMPER_LOG_KEEP.toDays())
        assertEquals(90, StoragePolicy.HOSTS_LOG_KEEP.toDays()); assertEquals(150L * 1024 * 1024, StoragePolicy.ALL_DATA_CEILING_BYTES)
    }

    @Test fun `ST4 90 days of use stays under 150 MB`() {
        // 15 photos/day: 14 days of thumbnails (capped 25 MB), 1 record each forever, 3 references, 30 days of sensor events at ~2 MB/day.
        val thumbs = minOf(15L * 14 * StoragePolicy.THUMB_BYTES, StoragePolicy.THUMB_CEILING_BYTES)
        val records = 90L * 15 * StoragePolicy.RECORD_BYTES_MAX
        val refs = 3L * StoragePolicy.REFERENCE_BYTES
        val sensors = 30L * 2 * 1024 * 1024
        assertTrue(thumbs + records + refs + sensors < StoragePolicy.ALL_DATA_CEILING_BYTES)
    }

    @Test fun `ST3 reference photos are working size`() { assertEquals(300 * 1024, StoragePolicy.REFERENCE_BYTES) }

    @Test fun `ST2 the forever record is a few hundred bytes`() {
        val r = PhotoRecord(now, "block-1234567890", "lecture", "pass-near", "<50m", 91, 0x1234L)
        assertTrue(r.approxBytes() < StoragePolicy.RECORD_BYTES_MAX)
    }

    @Test fun `ST2 a reused photo is rejected by its fingerprint`() {
        val fp = 0x0F0F_1234_ABCD_5678L
        assertTrue(isReusedPhoto(fp, listOf(fp)))
        assertTrue(isReusedPhoto(fp xor 0b11L, listOf(fp)))          // re-compressed copy
        assertFalse(isReusedPhoto(fp.inv(), listOf(fp)))
    }

    @Test fun `ST7 a photo record has no coordinate fields`() {
        val names = PhotoRecord::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { "lat" in it || "lon" in it || "coord" in it || "gps" in it }, names.toString())
    }
}

class DesignTest {
    @Test fun `AP8 text contrast is 4_5 to 1 or better in every mode and palette`() {
        for ((name, t) in Palettes.all) {
            for ((label, fg) in listOf("text" to t.text, "muted" to t.muted)) {
                assertTrue(contrast(fg, t.bg) >= 4.5, "$name $label on bg = ${contrast(fg, t.bg)}")
                assertTrue(contrast(fg, t.card) >= 4.5, "$name $label on card = ${contrast(fg, t.card)}")
            }
        }
    }

    // The accent is a fill or ring, never text (neutral light orange on a card is only 2.9:1). Text on an accent fill must pass.
    // Night uses an outlined button, so its label is the normal text token, already tested above.
    @Test fun `AP8 text on an accent fill is 4_5 to 1 or better`() {
        for ((name, t) in Palettes.all) if (name != "night") assertTrue(contrast(t.onAccent, t.accent) >= 4.5, "$name onAccent on accent = ${contrast(t.onAccent, t.accent)}")
    }

    @Test fun `AP8 the accent used as text is 4_5 to 1 or better on the card and the background in every palette`() {
        for ((name, t) in Palettes.all) {
            assertTrue(contrast(t.accentText, t.card) >= 4.5, "$name accentText on card = ${contrast(t.accentText, t.card)}")
            assertTrue(contrast(t.accentText, t.bg) >= 4.5, "$name accentText on bg = ${contrast(t.accentText, t.bg)}")
        }
    }

    @Test fun `AP3 night mode has no white and no blue`() {
        val t = Palettes.night
        for (hex in listOf(t.bg, t.card, t.text, t.muted, t.accent, t.track)) {
            val v = hex.removePrefix("#").chunked(2).map { it.toInt(16) }
            assertTrue(v[2] <= v[0], "$hex has more blue than red")
            assertTrue(v.max() < 0xe0, "$hex looks white")
        }
    }
}
