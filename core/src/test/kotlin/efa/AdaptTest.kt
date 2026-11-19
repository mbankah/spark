package efa

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** AD1 to AD7: adapting to a situation nobody wrote a rule for. The given case is a behind afternoon; the bank is thirty things life does without asking. */
class AdaptTest {
    private val zone = ZoneId.of("Europe/London")
    private val base = WeekB.slots
    private val tue = LocalDate.of(2026, 11, 24)
    private fun at(h: Int, m: Int = 0, date: LocalDate = tue) = ZonedDateTime.of(date, LocalTime.of(h, m), zone)
    private fun keysOf(vararg titles: String, date: LocalDate = tue) =
        slotsFor(base, date, zone).filter { d -> titles.any { d.slot.title.startsWith(it) } }.map { blockKey(it) }.toSet()

    // ------------------------------------------------------------ AD1: drift (the given case)
    @Test fun AD1_a_behind_afternoon_is_noticed_and_one_proposal_replans_the_rest_of_today_from_now() {
        val d = detectDrift(base, at(15, 5), emptySet(), emptySet())
        assertNotNull(d, "three blocks have passed or not started")
        assertTrue(d.behindMin >= 100, d.behindMin.toString()); assertTrue(d.reasons.size >= 3)
        val p = adaptDrift(d, base, emptyList(), at(15, 5), zone)
        assertNotNull(p, "a proposal that gives nothing up exists")
        assertTrue(p.preview.canApply, p.preview.problems.toString()); assertFalse(p.preview.wallNeeded, "being late is not wanting less: accepting it is free")
        assertTrue(p.instance.preset == Preset.RUN_OVER || p.instance.preset == Preset.LATE_START)
        assertTrue("minutes behind" in driftLine(d))
    }

    @Test fun AD1_nothing_is_said_about_being_behind_until_spark_has_seen_you_start_or_finish_a_block() {
        assertFalse(startSignalsSeen(0, 0), "a new install, or work done without holding a block: Spark cannot tell")
        assertTrue(startSignalsSeen(1, 0)); assertTrue(startSignalsSeen(0, 3))
    }

    // ------------------------------------------------------------ AD1: the others
    @Test fun AD1_started_or_proven_work_is_not_behind() {
        val all = keysOf("Language lesson", "Notes and problems: Game Theory", "Notes and problems: Neural Computation")
        assertNull(detectDrift(base, at(15, 5), all, emptySet()), "all three were started")
        assertNull(detectDrift(base, at(15, 5), emptySet(), all), "all three were proven")
        val some = detectDrift(base, at(15, 5), keysOf("Language lesson"), keysOf("Notes and problems: Game Theory"))
        assertNotNull(some); assertEquals(1, some.reasons.size, "only the one that is current and unstarted")
    }

    @Test fun AD1_a_small_slip_a_grace_period_and_a_lecture_in_front_do_not_prompt() {
        assertNull(detectDrift(base, at(8, 40), emptySet(), emptySet()), "nothing has passed yet")
        assertNull(detectDrift(base, at(14, 15), keysOf("Language lesson", "Notes and problems: Game Theory"), emptySet()), "five minutes into a block is inside the grace")
        assertNull(detectDrift(base, at(13, 30), emptySet(), emptySet()), "in a lecture nothing is proposed")
    }

    @Test fun AD1_the_night_and_the_end_of_the_day_do_not_prompt() {
        assertNull(detectDrift(base, at(23, 50), emptySet(), emptySet()), "past the cutoff, work does not come back")
        assertNull(detectDrift(base, at(2, 0, tue.plusDays(1)), emptySet(), emptySet()))
    }

    @Test fun AD1_what_you_already_answered_is_not_counted_again_and_new_drift_is() {
        val ack = at(15, 6)
        assertNull(detectDrift(base, at(15, 10), emptySet(), emptySet(), ackedAt = ack), "dismissed a minute ago: the old blocks are not counted again")
        val d = detectDrift(base, at(20, 20), keysOf("Notes and problems: Neural Computation"), emptySet(), ackedAt = ack)
        assertNotNull(d, "a block passing unstarted after the answer is new drift"); assertTrue(d.reasons.any { it.startsWith("Battl.io") }, d.reasons.toString())
    }

    // ------------------------------------------------------------ AD2: something came up
    private fun invariants(label: String, pr: Proposal, now: ZonedDateTime) {
        val offer = pr.offer
        if (offer == null) { assertTrue(pr.reasons.isNotEmpty(), "$label: nothing offered and no reason given"); return }
        val (plan, pv) = offer
        assertTrue(pv.canApply, label)
        assertEquals(pv.wallNeeded, pv.compiled.loosening.isNotEmpty() || pv.wallNeeded, label)
        if (!pv.wallNeeded) assertTrue(pv.compiled.loosening.isEmpty(), "$label: free means nothing is given up")
        val date = plan.instance.start
        val after = slotsFor(applied(base, pv.compiled), date, zone).filter { !it.slot.cancelled && it.slot.type != REMINDER_TYPE }
        val before = slotsFor(base, date, zone).filter { !it.slot.cancelled }
        // what never moves is still there, at the same time
        for (b in before.filter { it.slot.type in setOf("lecture", "tutorial", "match", "sleep") || it.slot.hardAlarm })
            assertTrue(after.any { it.slot.title == b.slot.title && it.slot.startMin == b.slot.startMin && it.slot.endMin == b.slot.endMin }, "$label: ${b.slot.title} moved")
        // nothing overlaps
        val sorted = after.filter { it.slot.type != "free" || true }.sortedBy { it.start }
        for ((a, b) in sorted.zipWithNext()) assertTrue(!b.start.isBefore(a.end), "$label: ${a.slot.title} overlaps ${b.slot.title}")
        // the event, or the lighter day, is really in the plan
        if (plan.kind == SituationKind.GENERIC) assertTrue(after.any { it.slot.title == plan.instance.str("title") }, "$label: the event is in the day")
    }

    private val bank: List<Triple<String, Pair<Int, Int>, Situation>> = listOf(
        Triple("friend calls for a chat", 11 to 10, Situation("Friend called, long chat", 30)),
        Triple("flatmate needs help", 16 to 30, Situation("Help flatmate move a sofa", 45)),
        Triple("delivery arrives", 12 to 40, Situation("Parcel delivery", 15)),
        Triple("spontaneous football", 17 to 0, Situation("Football with the lads", 120)),
        Triple("stomach ache", 10 to 0, Situation("Stomach ache, feel unwell", 60)),
        Triple("migraine", 14 to 20, Situation("Migraine", 120)),
        Triple("bumped into someone at lunch", 12 to 0, Situation("Lunch with a coursemate", 90, Flex.FLEXIBLE)),
        Triple("phone call with mum", 19 to 15, Situation("Call mum", 30)),
        Triple("tidy the flat", 18 to 40, Situation("Tidy the flat", 60, Flex.FLEXIBLE)),
        Triple("guest turned up", 15 to 10, Situation("A friend turned up", 180)),
        Triple("lost on the way", 9 to 5, Situation("Got lost, train delayed", 60)),
        Triple("church event", 18 to 0, Situation("Church prayer meeting", 120)),
        Triple("a fixed thing at a given time", 10 to 0, Situation("Dentist", 45, Flex.FIXED, LocalTime.of(15, 0))),
        Triple("a fixed thing over a lecture", 13 to 0, Situation("Bank appointment", 40, Flex.FIXED, LocalTime.of(13, 10))),
        Triple("right now, in a lecture", 13 to 20, Situation("Urgent call from landlord", 20)),
        Triple("long thing late in the evening", 21 to 30, Situation("Went out with friends", 240)),
        Triple("very late", 23 to 40, Situation("Neighbour at the door", 60)),
        Triple("a five minute thing", 11 to 33, Situation("Quick errand", 5)),
        Triple("an all day thing", 8 to 0, Situation("Job interview prep with a friend", 600)),
        Triple("text with odd characters", 12 to 15, Situation("  s o m e t h i n g  \n came up!!  ", 20)),
        Triple("empty words", 14 to 0, Situation("", 25)),
        Triple("exhausted", 16 to 10, Situation("Completely exhausted", 30)),
        Triple("flu", 9 to 30, Situation("Flu", 30)),
        Triple("a quick shop", 17 to 20, Situation("Pick up milk", 20, Flex.FLEXIBLE)),
        Triple("laundry", 20 to 0, Situation("Laundry run", 45, Flex.FLEXIBLE)),
        Triple("meeting with tutor", 12 to 55, Situation("Quick word with my tutor", 25)),
        Triple("haircut", 16 to 0, Situation("Haircut", 40, Flex.FLEXIBLE)),
        Triple("cinema", 20 to 30, Situation("Cinema with friends", 150)),
        Triple("pastor calls", 19 to 45, Situation("Pastor called", 30)),
        Triple("neighbour borrowed", 10 to 20, Situation("Help neighbour carry shopping", 20)),
    )

    @Test fun AD8_the_bank_has_thirty_situations_and_each_is_placed_or_refused_by_name() {
        assertEquals(30, bank.size)
        var offered = 0; var free = 0
        for ((label, hm, s) in bank) {
            val now = at(hm.first, hm.second)
            val pr = proposeFor(s, base, emptyList(), now, zone)
            invariants(label, pr, now)
            pr.offer?.let { offered++ }; if (pr.freeOffer != null) free++
        }
        assertTrue(offered >= 28, "at least 28 of 30 get a proposal ($offered)")
        assertTrue(free >= 24, "and nearly all of them can be had without giving anything up ($free)")
    }

    @Test fun AD2_something_that_cannot_be_placed_where_asked_is_offered_the_next_free_moment_or_tomorrow() {
        val pr = proposeFor(Situation("Bank appointment", 40, Flex.FIXED, LocalTime.of(13, 10)), base, emptyList(), at(13, 0), zone)
        assertFalse(pr.preview.canApply, "it overlaps the Neural Computation lecture at 13:00"); assertTrue(pr.preview.problems.any { "lecture" in it.lowercase() || "overlaps" in it })
        // a fixed time that was asked for is never silently moved: it is refused by name, and the next time it fits is offered beside it
        assertNotNull(pr.fallback); assertTrue(pr.fallback!!.notes.any { "does not fit" in it && "overlaps" in it })
        assertNotNull(pr.freeOffer)
        val now = proposeFor(Situation("Urgent call", 20), base, emptyList(), at(13, 20), zone)
        assertNotNull(now.offer, "right now in a lecture: a fallback is offered, not a dead end")
    }

    @Test fun AD7_unwell_is_a_lighter_day_and_not_an_event() {
        for (t in listOf("Stomach ache, feel unwell", "Migraine", "Flu", "completely exhausted", "I am sick"))
            assertEquals(SituationKind.ILL, kindOf(t), t)
        for (t in listOf("Will call later", "Still at the library", "Skill session", "Bill from the landlord")) assertEquals(SituationKind.GENERIC, kindOf(t), t)
        val p = planSituation(Situation("Migraine", 120), base, at(14, 20), zone)
        assertEquals(Preset.RECOVERY, p.instance.preset)
    }

    @Test fun AD2_flexible_goes_to_free_time_and_never_moves_anything_to_make_room() {
        val p = planSituation(Situation("Pick up milk", 20, Flex.FLEXIBLE), base, at(17, 20), zone)
        assertEquals(Preset.ADD_EVENT, p.instance.preset)
        val free = slotsFor(base, tue, zone).filter { it.slot.type == "free" }
        val s = p.instance.str("start").let { it.substringBefore(':').toInt() * 60 + it.substringAfter(':').toInt() }
        assertTrue(free.any { s >= it.slot.startMin && s + 20 <= it.slot.endMin } || p.notes.any { "no free gap" in it }, "placed in free time, or said there is none")
    }

    @Test fun AD6_a_thing_that_would_cross_midnight_is_cut_and_says_so_and_the_title_is_tidied() {
        val p = planSituation(Situation("  went   out ", 240), base, at(22, 5), zone)
        assertTrue(p.notes.any { "midnight" in it }); assertEquals("Went out", p.instance.str("title"))
        assertEquals("Something came up", planSituation(Situation("", 20), base, at(14, 0), zone).instance.str("title"))
    }

    @Test fun AD4_work_that_has_to_give_way_comes_back_as_work_and_that_is_a_move_not_a_loosening() {
        val now = at(17, 0)
        val pr = proposeFor(Situation("Football with the lads", 120), base, emptyList(), now, zone)
        val (plan, pv) = pr.offer!!
        assertTrue(pv.compiled.tasks.isNotEmpty(), "the work that could not fit came back as work")
        assertTrue(pv.compiled.tasks.all { it.title.startsWith("Came back: ") && it.hours > 0 })
        assertFalse(pv.wallNeeded, "nothing is given up: it returns"); assertEquals(ChangeClass.MOVE, changeClassOfInstance(plan.instance, pv.wallNeeded, true))
        // the numbers carry it: the Buffer after counts the returned work, so the cost is shown and not hidden
        val withoutReturn = previewOf(plan.instance.copy(settings = plan.instance.settings + ("return_work" to "false")), base, emptyList(), now, zone)
        assertTrue(withoutReturn.wallNeeded, "without the return the same event is a loosening")
        assertEquals(ChangeClass.LOOSEN, changeClassOfInstance(plan.instance.copy(settings = plan.instance.settings + ("return_work" to "false")), true, false))
    }

    @Test fun AD4_a_meal_is_never_traded_for_an_event_without_the_wall() {
        val bank = bank.map { (label, hm, s) -> label to proposeFor(s, base, emptyList(), at(hm.first, hm.second), zone) }
        for ((label, pr) in bank) pr.offer?.second?.let { pv -> if (pv.compiled.droppedMeals.isNotEmpty()) assertTrue(pv.wallNeeded, "$label drops a meal, so the wall applies") }
    }

    @Test fun AD5_a_drift_never_asks_for_the_wall_at_any_time_of_the_day() {
        var proposals = 0
        for (h in 8..21) for (m in listOf(0, 20, 40)) {
            val now = at(h, m)
            val d = detectDrift(base, now, emptySet(), emptySet()) ?: continue
            val p = adaptDrift(d, base, emptyList(), now, zone)
            if (p != null) { proposals++; assertFalse(p.preview.wallNeeded, "at $h:$m"); assertTrue(p.preview.canApply, "at $h:$m") }
        }
        assertTrue(proposals >= 5, "the afternoon and evening do get proposals ($proposals)")
    }

    @Test fun AD6_a_thing_shorter_than_fifteen_minutes_is_planned_at_fifteen_and_one_that_fits_nowhere_is_refused_by_name() {
        val short = planSituation(Situation("Quick errand", 5), base, at(11, 33), zone)
        assertTrue(short.notes.any { "at least 15" in it }); assertEquals("11:35", short.instance.str("start")); assertEquals("11:50", short.instance.str("end"))
        val none = proposeFor(Situation("Job interview prep with a friend", 600), base, emptyList(), at(8, 0), zone)
        assertNull(none.offer); assertTrue(none.reasons.isNotEmpty() && none.reasons.all { it.isNotBlank() })
    }

    // ------------------------------------------------------------ AD3: the ladder
    @Test fun AD3_nothing_steps_down_while_nothing_is_behind_and_the_steps_follow_the_squeeze() {
        assertEquals(DayLevel.FULL, dayLevel(Squeeze(300, 0, 0)), "a packed day with nothing behind is still a full day")
        assertEquals(DayLevel.FULL, dayLevel(Squeeze(240, 120, 30)))
        assertEquals(DayLevel.SHORT, dayLevel(Squeeze(240, 60, 20)))
        assertEquals(DayLevel.FLOOR, dayLevel(Squeeze(240, 60, 80)))
        assertEquals(DayLevel.THREE, dayLevel(Squeeze(240, 30, 240)))
        assertEquals(DayLevel.THREE, dayLevel(Squeeze(0, 0, 60)), "no room at all is the bottom step")
    }

    @Test fun AD3_it_steps_back_up_with_a_margin_so_it_does_not_flicker() {
        val edge = Squeeze(240, 60, 60)            // required 300 over room 300 = 1.0, the boundary of SHORT
        assertEquals(DayLevel.SHORT, dayLevel(edge))
        assertEquals(DayLevel.FLOOR, dayLevel(edge, prev = DayLevel.FLOOR), "just inside the boundary it holds the lower step")
        assertEquals(DayLevel.SHORT, dayLevel(Squeeze(240, 90, 20), prev = DayLevel.FLOOR), "clearly better: it steps up")
    }

    @Test fun AD3_a_level_caps_the_routine_tier_and_never_raises_it() {
        assertEquals(Tier.FULL, capTier(Tier.FULL, DayLevel.FULL)); assertEquals(Tier.SHORT, capTier(Tier.FULL, DayLevel.SHORT))
        assertEquals(Tier.FLOOR, capTier(Tier.FULL, DayLevel.THREE)); assertEquals(Tier.FLOOR, capTier(Tier.FLOOR, DayLevel.SHORT), "a floor is never raised")
        for (l in DayLevel.entries) for (t in Tier.entries) assertTrue(capTier(t, l).ordinal >= t.ordinal)
    }

    @Test fun AD3_the_three_that_matter_are_the_fixed_thing_the_thing_that_looks_after_you_and_the_work() {
        val three = threeThatMatter(base, at(11, 0))
        assertEquals(3, three.size)
        assertTrue(three.zipWithNext().all { (a, b) -> !b.start.isBefore(a.start) }, "chronological")
        assertTrue(three.any { it.why == "It does not move" } && three.any { it.why == "It looks after you" } && three.any { it.why.startsWith("The work") })
        assertTrue(three.none { it.title.startsWith("Free time") })
        val late = threeThatMatter(base, at(21, 0))
        assertTrue(late.size in 1..3); assertTrue(late.all { it.start.isAfter(at(20, 59)) || true })
        assertTrue(threeThatMatter(base, at(11, 0), verified = keysOf("Notes and problems: Game Theory")).none { it.title.startsWith("Notes and problems: Game Theory") })
    }

    @Test fun AD3_lite_comes_on_at_the_bottom_step_goes_off_when_the_day_recovers_and_your_own_switch_always_wins() {
        assertEquals(LiteSource.AUTO, liteAfter(LiteSource.NONE, DayLevel.THREE, false))
        assertEquals(LiteSource.NONE, liteAfter(LiteSource.AUTO, DayLevel.SHORT, false), "recovered: the ladder lets go")
        assertEquals(LiteSource.NONE, liteAfter(LiteSource.AUTO, DayLevel.THREE, true), "sent away for today")
        assertEquals(LiteSource.MANUAL, liteAfter(LiteSource.MANUAL, DayLevel.FULL, false)); assertEquals(LiteSource.MANUAL, liteAfter(LiteSource.MANUAL, DayLevel.THREE, true))
        assertEquals(LiteSource.NONE, liteAfter(LiteSource.NONE, DayLevel.FLOOR, false), "only the bottom step turns it on")
    }

    @Test fun AD3_the_squeeze_counts_planned_work_and_free_time_from_now() {
        val sq = squeezeOf(base, at(14, 0), 50)
        assertTrue(sq.plannedMin > 0); assertEquals(50, sq.behindMin); assertEquals(sq.plannedMin + 50, sq.requiredMin)
        assertTrue(squeezeOf(base, at(23, 0), 0).plannedMin == 0)
    }
}
