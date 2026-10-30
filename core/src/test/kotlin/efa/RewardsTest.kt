package efa

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

private fun t(m: Int, d: Int, h: Int, min: Int = 0) = at(LocalDate.of(2026, m, d), LocalTime.of(h, min))

class WeekMoneyTest {
    private val slots = WeekB.slots
    private val monday = LocalDate.of(2026, 11, 2)
    private fun allCredited(fraction: Double = 1.0) = weekBlocks(slots, monday, emptyMap()).associate { it.key to (it.plannedMin * fraction).toInt() }

    @Test fun `Y4 work blocks and lecture attendance count and other fixed commitments do not`() {
        assertEquals(4, blockHeaviness("lecture")); assertEquals(4, blockHeaviness("tutorial"))
        assertEquals(6, blockHeaviness("study-standard")); assertEquals(3, blockHeaviness("study-light")); assertEquals(5, blockHeaviness("study")); assertEquals(5, blockHeaviness("code"))
        for (t in listOf("match", "mytutor", "training", "gym", "church", "meal", "free", "travel", "routine", "sleep", "errand", "chore")) assertNull(blockHeaviness(t), t)
        val types = weekBlocks(slots, monday, emptyMap()).map { it.type }.toSet()
        assertTrue("lecture" in types && "code" in types && "match" !in types && "mytutor" !in types)
    }

    @Test fun `R8 nothing confirmed pays nothing and everything confirmed pays the cap`() {
        assertEquals(0, weekResult(slots, monday, emptyMap()).money.freeToSpendPence)
        assertEquals(FREE_CAP_PENCE, weekResult(slots, monday, allCredited()).money.freeToSpendPence)
    }

    @Test fun `R7 R8 the ledger's table, row by row, with five equal blocks`() {
        val five = (0 until 5).map { PatternSlot(DayOfWeek.MONDAY, 600 + it * 120, 660 + it * 120, "Block $it", "study", "", false, "") }
        val keys = weekBlocks(five, monday, emptyMap()).map { it.key }
        val expected = mapOf(5 to (2500 to 1500), 4 to (2000 to 2000), 3 to (1500 to 2500), 2 to (1000 to 3000), 1 to (500 to 3500), 0 to (0 to 4000))
        for ((done, pair) in expected) {
            val r = weekResult(five, monday, keys.take(done).associateWith { 60 })
            assertEquals(pair, r.money.freeToSpendPence to r.money.savingsPence, "$done of 5")
        }
    }

    @Test fun `R7 at least 15 pounds is saved every week and free to spend never passes 25`() {
        for (f in listOf(0.0, 0.1, 0.33, 0.5, 0.77, 1.0, 1.5)) {
            val m = weekResult(slots, monday, allCredited(f)).money
            assertTrue(m.freeToSpendPence in 0..FREE_CAP_PENCE && m.savingsPence >= MIN_SAVED_PENCE, "fraction $f")
            assertEquals(OTHER_PENCE, m.freeToSpendPence + m.savingsPence)
        }
    }

    @Test fun `R6 food is 40 pounds whatever the work`() {
        for (f in listOf(0.0, 0.5, 1.0)) assertEquals(FOOD_PENCE, weekResult(slots, monday, allCredited(f)).money.foodPence)
    }

    @Test fun `R8 the payout is weighted by heaviness, so a heavy block pays more than a light one`() {
        val two = listOf(PatternSlot(DayOfWeek.MONDAY, 600, 660, "Heavy", "study-standard", "", false, ""), PatternSlot(DayOfWeek.MONDAY, 700, 760, "Light", "study-light", "", false, ""))
        val b = weekBlocks(two, monday, emptyMap())
        val heavy = weekResult(two, monday, mapOf(b[0].key to 60)).money.freeToSpendPence; val light = weekResult(two, monday, mapOf(b[1].key to 60)).money.freeToSpendPence
        assertTrue(heavy > light, "$heavy vs $light"); assertTrue(kotlin.math.abs(heavy - 1667) <= 1, "heavy $heavy")
    }

    @Test fun `R10 making every heaviness bigger changes nothing, because the planned total grows with it`() {
        val b = weekBlocks(slots, monday, allCredited(0.5))
        val doubled = b.map { it.copy(heaviness = it.heaviness * 2) }
        fun ratio(l: List<WeekBlock>) = l.sumOf { it.verifiedWeight } / l.sumOf { it.plannedWeight }
        assertEquals(ratio(b), ratio(doubled), 1e-9)
    }

    @Test fun `R8 more minutes credited than planned never pays more than the plan`() {
        val over = weekBlocks(slots, monday, emptyMap()).associate { it.key to it.plannedMin * 3 }
        assertEquals(FREE_CAP_PENCE, weekResult(slots, monday, over).money.freeToSpendPence)
    }

    @Test fun `R9 the week runs Monday to Sunday and the Sunday belongs to the week before the Monday`() {
        assertEquals(monday, weekStart(monday)); assertEquals(monday, weekStart(monday.plusDays(6))); assertEquals(monday.plusDays(7), weekStart(monday.plusDays(7)))
        assertEquals(LocalDate.of(2026, 10, 19), weekStart(LocalDate.of(2026, 10, 25)), "the day the clocks go back is a Sunday of the 19 October week")
    }

    @Test fun `X4 R9 the week of the clock change counts the same blocks as any other`() {
        val w = weekBlocks(slots, LocalDate.of(2026, 10, 19), emptyMap())
        assertEquals(weekBlocks(slots, monday, emptyMap()).size, w.size)
    }

    @Test fun `G6 R20-F5 money is live from the 15th day and not before`() {
        val first = t(11, 2, 9)                                            // after the clock change, so a day is 24 hours
        assertFalse(moneyLive(first, first.plusDays(13).plusHours(23))); assertTrue(moneyLive(first, first.plusDays(14)))
    }

    @Test fun `R8 the working shows food, each confirmed block, the sums and the payout`() {
        val five = (0 until 4).map { PatternSlot(DayOfWeek.MONDAY, 600 + it * 120, 660 + it * 120, "Block $it", "study", "", false, "") }
        val keys = weekBlocks(five, monday, emptyMap()).map { it.key }
        val w = weekResult(five, monday, keys.take(3).associateWith { 60 }).working()
        assertTrue(w.first().startsWith("Food: £40.00")); assertTrue(w.any { it.startsWith("Block 0: 5 x 1.0h = 5.0") })
        assertTrue(w.any { it == "Confirmed 15.0 of 20.0 planned" }); assertTrue(w.any { it.startsWith("Free to spend = £25.00 x 15.0 / 20.0 = £18.75") })
        assertTrue(w.last() == "Moved to savings: £21.25")
    }

    @Test fun `R4 progress reads as hours done of hours planned`() {
        val two = listOf(PatternSlot(DayOfWeek.MONDAY, 600, 720, "A", "study", "", false, ""), PatternSlot(DayOfWeek.MONDAY, 800, 920, "B", "study", "", false, ""))
        val keys = weekBlocks(two, monday, emptyMap()).map { it.key }
        assertEquals("2.0 of 4.0 hours done", weekResult(two, monday, mapOf(keys[0] to 120)).progress())
    }

    @Test fun `R8 an imported or converted block counts in the plan like any other`() {
        val before = weekResult(slots, monday, emptyMap()).plannedWeight
        val extra = PatternSlot(DayOfWeek.TUESDAY, 970, 1030, "Seminar", "tutorial", "", false, "", date = monday.plusDays(1))      // inside Tuesday's free time
        assertEquals(before + 4.0, weekResult(slots + extra, monday, emptyMap()).plannedWeight, 1e-9)
    }

    @Test fun `C5 a lecture cancelled in a file leaves the plan, so it cannot cost money`() {
        val lec = slots.first { it.day == DayOfWeek.MONDAY && it.type == "lecture" }
        val before = weekResult(slots, monday, emptyMap()).plannedWeight
        assertTrue(weekResult(slots + lec.copy(date = monday, cancelled = true), monday, emptyMap()).plannedWeight < before)
    }
}

class EarnedAccessTest {
    private val list = setOf("com.instagram.android")
    private val day = LocalDate.of(2026, 11, 4)
    private fun at(h: Int, m: Int = 0) = at(day, LocalTime.of(h, m))
    private val walk = PatternSlot(DayOfWeek.WEDNESDAY, 17 * 60, 17 * 60 + 30, "Walk", "travel", "", false, "", date = day)
    private val planning = PatternSlot(DayOfWeek.WEDNESDAY, 19 * 60, 19 * 60 + 30, "Planning", "planning", "", false, "", date = day)
    private val slots = listOf(walk, planning)
    private fun ms(h: Int, m: Int = 0) = at(h, m).toInstant().toEpochMilli()

    @Test fun `O3 R2 ten minutes of open phone per hour of confirmed work`() {
        assertEquals(10, earnedAccessMinutes(60)); assertEquals(20, earnedAccessMinutes(125)); assertEquals(0, earnedAccessMinutes(5)); assertEquals(15, earnedAccessMinutes(90))
    }

    @Test fun `R2 earned minutes open a transition, then run out`() {
        val e = listOf(Earning(ms(12), 20))
        assertFalse(earnedAccessActive(at(16, 59), e, slots), "not in a blocked stretch, so nothing is spent")
        assertTrue(earnedAccessActive(at(17, 0), e, slots)); assertTrue(earnedAccessActive(at(17, 19), e, slots))
        assertFalse(earnedAccessActive(at(17, 20), e, slots), "20 minutes earned, 20 spent")
    }

    @Test fun `R2 minutes are kept for later and spent only while the phone would be closed`() {
        val e = listOf(Earning(ms(9), 25))
        assertTrue(earnedAccessActive(at(17, 24), e, slots)); assertFalse(earnedAccessActive(at(17, 26), e, slots))
        assertFalse(earnedAccessActive(at(19, 5), e, slots), "the 25 minutes were spent on the walk")
    }

    @Test fun `R2 earnings add up`() {
        val e = listOf(Earning(ms(9), 10), Earning(ms(11), 10))
        assertTrue(earnedAccessActive(at(17, 19), e, slots)); assertFalse(earnedAccessActive(at(17, 21), e, slots))
    }

    @Test fun `R2 planning blocks can be lifted too`() {
        val e = listOf(Earning(ms(12), 40))                               // the walk spends 30 of them, so 10 are left for the planning block
        assertTrue(earnedAccessActive(at(19, 5), e, slots)); assertFalse(earnedAccessActive(at(19, 11), e, slots))
    }

    @Test fun `R2 what is not spent is gone at midnight`() {
        val e = listOf(Earning(ms(12), 60))
        val tomorrow = PatternSlot(DayOfWeek.THURSDAY, 17 * 60, 17 * 60 + 30, "Walk", "travel", "", false, "", date = day.plusDays(1))
        assertFalse(earnedAccessActive(at(day.plusDays(1), LocalTime.of(17, 5)), e, listOf(tomorrow)))
    }

    @Test fun `R2 no earnings means no access`() { assertFalse(earnedAccessActive(at(17, 5), emptyList(), slots)); assertFalse(earnedAccessActive(at(17, 5), listOf(Earning(ms(12), 0)), slots)) }

    private fun decideAt(pattern: List<PatternSlot>, now: java.time.ZonedDateTime, earned: Boolean) = decide("com.instagram.android", now, pattern, list, earnedAccess = earned)

    @Test fun `R2 earned access opens a blocked transition and a planning block`() {
        assertTrue(decideAt(slots, at(17, 5), false) is Decision.Block); assertEquals(Decision.Allow, decideAt(slots, at(17, 5), true))
        assertEquals(Decision.Allow, decideAt(slots, at(19, 5), true))
    }

    @Test fun `Y8 earned access never opens a work block`() {
        val work = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 10 * 60, 12 * 60, "Study", "study", "", false, "", date = day))
        assertTrue(decideAt(work, at(10, 30), true) is Decision.Block)
    }

    @Test fun `Y8 R2 earned access never opens the getting-ready block`() {
        val ready = listOf(PatternSlot(DayOfWeek.WEDNESDAY, 7 * 60 + 50, 8 * 60 + 20, "Breakfast, dress, bag", "routine", "", false, "", date = day))
        assertEquals(BlockReason.GETTING_READY, (decideAt(ready, at(8, 0), true) as Decision.Block).reason)
    }

    @Test fun `Y8 R5 earned access never opens the night cutoff, even inside a free slot`() {
        val wake = WeekB.slots
        val nextWake = nextWake(wake, at(12))!!
        val after = nightCutoff(nextWake).plusMinutes(25)
        assertTrue(decideAt(wake, after, true) is Decision.Block, "after the cutoff the list is closed whatever was earned")
        assertEquals(BlockReason.NIGHT, (decideAt(wake, after, true) as Decision.Block).reason)
    }

    @Test fun `Y8 earned access never opens the morning block`() {
        val m = MorningState(day, ringStartedMs = ms(7, 30), stoppedMs = ms(7, 33))
        val b = decide("com.instagram.android", at(8), emptyList(), list, morning = m, earnedAccess = true) as Decision.Block
        assertEquals(BlockReason.MORNING_READY, b.reason)
    }

    @Test fun `R5 late night is not a reward, and a good day only makes an override cheaper`() {
        val wake = at(7, 30).plusDays(1)
        assertEquals(45, nightOffer(wake.minusHours(7), wake, 0, true).minutes); assertEquals(30, nightOffer(wake.minusHours(7), wake, 0, false).minutes)
        assertEquals(nightOffer(wake.minusHours(7), wake, 0, true).wallLength, nightOffer(wake.minusHours(7), wake, 0, false).wallLength, "the same string, only cheaper in time")
    }

    @Test fun `R2 access never lifts anything when the flag is off, so the old rules are unchanged`() {
        for (h in listOf(7, 10, 17, 19, 23)) assertEquals(decide("com.instagram.android", at(h), WeekB.slots, list), decide("com.instagram.android", at(h), WeekB.slots, list, earnedAccess = false))
    }
}

class RewardTextTest {
    private val slots = WeekB.slots
    private fun at(h: Int, m: Int = 0) = at(LocalDate.of(2026, 11, 2), LocalTime.of(h, m))

    @Test fun `R1 finishing a block says how much it kept out of the Buffer`() {
        val cycle = PatternSlot(DayOfWeek.MONDAY, 610, 735, "Notes", "study-standard", "", false, "")
        assertEquals(STUDY_CYCLE_STANDARD_HOURS, reliefHours(cycle, 125)); assertEquals(1.0, reliefHours(PatternSlot(DayOfWeek.MONDAY, 540, 600, "L", "lecture", "", false, ""), 60))
        assertEquals(0.0, reliefHours(PatternSlot(DayOfWeek.MONDAY, 540, 600, "L", "meal", "", false, ""), 60))
        assertTrue("This kept 2.5h out of the Buffer." in verifiedLines(cycle, 125, 125, slots, at(12, 20)))
    }

    @Test fun `R2 a verified work block says how many minutes of open phone it earned`() {
        val cycle = PatternSlot(DayOfWeek.MONDAY, 610, 735, "Notes", "study-standard", "", false, "")
        assertTrue(verifiedLines(cycle, 125, 125, slots, at(12, 20)).any { it == "Earned 20 minutes of open phone for transitions and planning." })
    }

    @Test fun `R3 the reward is the next meal or free slot, with its time`() {
        val next = rewardNext(slots, at(12, 20))!!
        assertTrue(next.slot.type == "meal" || next.slot.type == "free"); assertTrue(next.start >= at(12, 20))
        assertTrue(verifiedLines(PatternSlot(DayOfWeek.MONDAY, 610, 735, "Notes", "study-standard", "", false, ""), 125, 125, slots, at(12, 20)).any { it.startsWith("Next: ") })
    }

    @Test fun `R3 with no meal or free slot left today there is no invented reward`() {
        val none = listOf(PatternSlot(DayOfWeek.MONDAY, 600, 660, "Study", "study", "", false, ""))
        assertNull(rewardNext(none, at(11)))
    }
}

class FreeSlotRulesTest {
    private val slots = WeekB.slots
    private val tuesday = LocalDate.of(2026, 11, 3)

    @Test fun `F1 a free slot has a start, a stop and a next block waiting`() {
        val info = freeSlotNow(slots, at(tuesday, LocalTime.of(16, 30)))!!
        assertEquals(LocalTime.of(16, 10), info.slot.start.toLocalTime()); assertEquals(LocalTime.of(18, 15), info.slot.end.toLocalTime()); assertNotNull(info.next)
        assertTrue(info.next!!.start >= info.slot.end)
    }

    @Test fun `F1 outside a free slot there is none`() { assertNull(freeSlotNow(slots, at(tuesday, LocalTime.of(10, 30)))) }

    @Test fun `F1 all apps are open during a free slot`() {
        assertEquals(Decision.Allow, decide("com.instagram.android", at(tuesday, LocalTime.of(16, 30)), slots, setOf("com.instagram.android")))
    }

    @Test fun `F5 late-night chat is free only inside a free slot, and after the cutoff the night rule applies`() {
        val wake = nextWake(slots, at(tuesday, LocalTime.of(12, 0)))!!
        val freeSlotDayTime = at(tuesday, LocalTime.of(16, 30))
        assertEquals(Decision.Allow, decide("com.instagram.android", freeSlotDayTime, slots, setOf("com.instagram.android")))
        val late = nightCutoff(wake).plusMinutes(25)
        assertEquals(BlockReason.NIGHT, (decide("com.instagram.android", late, slots, setOf("com.instagram.android")) as Decision.Block).reason)
    }

    @Test fun `F4 a window turned back is left alone by the planner`() {
        val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(12, 0))
        val work = listOf(DueWork("Big", 150.0, 8, at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0))))
        val first = planConversions(slots, work, now)
        assertTrue(first.conversions.isNotEmpty())
        val declined = first.conversions.map { it.window.plannedDay to it.slot.startMin }.toSet()
        val again = planConversions(slots, work, now, declined = declined)
        assertTrue(again.conversions.none { (it.window.plannedDay to it.slot.startMin) in declined })
    }
}

class ResetAndFootballTest {
    private val slots = WeekB.slots
    private val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(12, 0))
    private val big = listOf(DueWork("Big", 150.0, 8, at(LocalDate.of(2026, 11, 13), LocalTime.of(12, 0))))

    @Test fun `W4 every converted free slot ends with a 10 minute reset, and the work is the rest`() {
        val plan = planConversions(slots, big, now)
        assertTrue(plan.conversions.isNotEmpty())
        for (c in plan.conversions) {
            assertEquals("travel", c.reset.type); assertEquals(RESET_NOTE, c.reset.notes); assertEquals(RESET_MIN, c.reset.endMin - c.reset.startMin)
            assertEquals(c.slot.endMin, c.reset.startMin); assertEquals(c.window.slot.endMin, c.reset.endMin); assertEquals(c.window.slot.startMin, c.slot.startMin)
            assertTrue(c.slot.endMin - c.slot.startMin >= 30, "a worthwhile block, not a sliver")
        }
    }

    @Test fun `W4 the reset closes the phone as a transition and is not work`() {
        val plan = planConversions(slots, big, now); val c = plan.conversions.first()
        assertEquals(BlockReason.TRANSITION, firmReasonFor(c.reset.type)); assertNull(blockHeaviness(c.reset.type))
    }

    @Test fun `W4 a free slot too short to hold a reset and a block is never turned into work`() {
        val plan = planConversions(slots, big, now)
        assertTrue(plan.conversions.all { it.window.slot.endMin - it.window.slot.startMin >= MIN_CONVERTIBLE_MIN })
    }

    @Test fun `W4 protection is unchanged by the reset, so the protected 6 hours still hold`() {
        val plan = planConversions(slots, big, now, horizonDays = 28)
        val merged = slots + plan.conversions.flatMap { listOf(it.slot, it.reset) }
        for (w in 0 until 4) {
            val monday = LocalDate.of(2026, 11, 2).plusWeeks(w.toLong())
            val free = (0L..6L).flatMap { slotsFor(merged, monday.plusDays(it)) }.filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin }
            assertTrue(free >= PROTECTED_FREE_MIN_PER_WEEK, "week $w has $free")
        }
    }

    @Test fun `W4 every work block in the weekly plan is followed by a break, never straight by another work block`() {
        val work = setOf("study", "code", "study-standard", "study-light")
        val bad = ArrayList<String>()
        for (day in DayOfWeek.entries) {
            val l = slots.filter { it.day == day }.sortedBy { it.startMin }
            for ((i, s) in l.withIndex()) if (s.type in work) {
                val nx = l.getOrNull(i + 1)
                if (nx == null || nx.startMin != s.endMin || nx.type in work) bad += "${day.name} ${s.startMin} ${s.title.take(24)}"
            }
        }
        assertEquals(emptyList(), bad)
    }

    @Test fun `W4 the six resets that were added are ten minutes, close the phone as a transition, and took ten minutes from the block before`() {
        val resets = slots.filter { it.title == RESET_TITLE && it.date == null }
        assertEquals(6, resets.size)
        for (r in resets) {
            assertEquals(RESET_MIN, r.endMin - r.startMin); assertEquals(BlockReason.TRANSITION, firmReasonFor(r.type))
            val before = slots.single { it.day == r.day && it.endMin == r.startMin }
            assertTrue(before.type in setOf("study", "code", "study-standard", "study-light") && before.endMin - before.startMin >= 30, "${r.day} ${before.title}")
        }
    }

    private val monday = LocalDate.of(2026, 11, 2)

    @Test fun `F7 Y5 three hours of personal football a week are placed in free time, in sessions of 1 to 2 hours`() {
        val placed = placeFootball(slots, monday, at(monday, LocalTime.MIDNIGHT))
        assertEquals(FOOTBALL_WEEK_MIN, placed.sumOf { it.endMin - it.startMin })
        assertTrue(placed.all { it.endMin - it.startMin in FOOTBALL_MIN_SESSION..FOOTBALL_MAX_SESSION }); assertTrue(placed.all { it.type == "match" && isFootball(it) })
    }

    @Test fun `Y5 football is extra to the 6 protected hours, so at least 6 hours of free time are left that week`() {
        val placed = placeFootball(slots, monday, at(monday, LocalTime.MIDNIGHT))
        val merged = slots + placed
        val free = (0L..6L).flatMap { slotsFor(merged, monday.plusDays(it)) }.filter { it.slot.type == "free" }.sumOf { it.slot.endMin - it.slot.startMin }
        assertTrue(free >= PROTECTED_FREE_MIN_PER_WEEK, "$free minutes of free time left")
    }

    @Test fun `F7 football never overlaps another block and sits inside what was free time`() {
        val placed = placeFootball(slots, monday, at(monday, LocalTime.MIDNIGHT))
        for (g in placed) {
            val day = slotsFor(slots, g.date!!).filter { it.slot.type == "free" }
            assertTrue(day.any { it.slot.startMin <= g.startMin && it.slot.endMin >= g.endMin }, "${g.date} ${g.startMin}")
            assertTrue(slotsFor(slots, g.date!!).filter { it.slot.type != "free" }.none { it.slot.startMin < g.endMin && it.slot.endMin > g.startMin })
        }
    }

    @Test fun `F7 placing it again with the football already there adds nothing`() {
        val first = placeFootball(slots, monday, at(monday, LocalTime.MIDNIGHT))
        assertTrue(placeFootball(slots + first, monday, at(monday, LocalTime.MIDNIGHT)).isEmpty())
    }

    @Test fun `F7 football only starts from tomorrow, and what no longer fits is simply not placed`() {
        val placed = placeFootball(slots, monday, at(monday.plusDays(5), LocalTime.MIDNIGHT))
        assertTrue(placed.all { it.date!! >= monday.plusDays(5) })
        assertTrue(placed.sumOf { it.endMin - it.startMin } <= FOOTBALL_WEEK_MIN)
    }

    @Test fun `Y5 football is never turned into work and still needs its photo`() {
        val placed = placeFootball(slots, monday, at(monday, LocalTime.MIDNIGHT))
        val plan = planConversions(slots + placed, big, now)
        assertTrue(plan.conversions.none { c -> placed.any { it.date == c.window.plannedDay && it.startMin == c.slot.startMin } })
        assertEquals(Proof.WINDOW_PHOTO, proofFor(placed.first())); assertNull(firmReasonFor(placed.first().type), "the phone stays open")
    }

    @Test fun `Y4 Y5 personal football stays out of the money ratio`() {
        val placed = placeFootball(slots, monday, at(monday, LocalTime.MIDNIGHT))
        assertEquals(weekResult(slots, monday, emptyMap()).plannedWeight, weekResult(slots + placed, monday, emptyMap()).plannedWeight, 1e-9)
    }
}
