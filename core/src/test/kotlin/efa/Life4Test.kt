package efa

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** MX6, CE7, SL2, FB1, RT: limits, break glass, NFC as an option, facts as lines, the runner. */
class Life4Test {
    private val zone = ZoneId.of("Europe/London")
    private fun z(h: Int, m: Int = 0, d: Int = 21) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone)
    private val list = setOf("com.instagram.android")
    private val limit = ScreenLimit(list, 30)

    // ---- MX6 ----
    @Test fun MX6_the_blocked_apps_get_a_daily_budget_then_close_until_midnight() {
        val start = z(10)
        assertEquals(LimitPhase.OPEN, limitStatus(start, z(10, 29), 30).phase)
        val closed = limitStatus(start, z(10, 30), 30)
        assertEquals(LimitPhase.CLOSED, closed.phase); assertEquals(z(0, 0, 22), closed.until)
        assertEquals(LimitPhase.FREE, limitStatus(start, z(0, 0, 22), 30).phase, "a new day is free again")
        assertEquals(LimitPhase.FREE, limitStatus(null, z(12), 30).phase); assertEquals(LimitPhase.FREE, limitStatus(start, z(12), 0).phase)
    }

    @Test fun MX6_decide_closes_a_listed_app_when_the_budget_is_spent_and_the_wall_still_opens_it() {
        val base = emptyList<PatternSlot>()
        assertEquals(Decision.Allow, decide("com.instagram.android", z(10, 10), base, list, limit = limit, limitStart = mapOf("com.instagram.android" to z(10))))
        val d = decide("com.instagram.android", z(11), base, list, limit = limit, limitStart = mapOf("com.instagram.android" to z(10)))
        assertTrue(d is Decision.Block && d.reason == BlockReason.SCREEN_LIMIT, d.toString())
        val passed = Allowances(appUntil = mapOf("com.instagram.android" to z(11, 30)))
        assertEquals(Decision.Allow, decide("com.instagram.android", z(11), base, list, passed, limit = limit, limitStart = mapOf("com.instagram.android" to z(10))))
        assertEquals(Decision.Allow, decide("com.google.android.dialer", z(11), base, list, limit = limit, limitStart = mapOf("com.instagram.android" to z(10))), "calls are not on the list")
    }

    @Test fun MX6_out_and_an_essential_are_never_limited() {
        val start = mapOf("com.instagram.android" to z(10))
        assertEquals(Decision.Allow, decide("com.instagram.android", z(11), emptyList(), list, limit = limit, limitStart = start, outUntil = z(14), atHome = false))
        assertEquals(Decision.Allow, decide("com.instagram.android", z(11), emptyList(), list, limit = limit, limitStart = start, essentials = setOf("com.instagram.android")))
    }

    @Test fun MX6_a_period_off_carries_the_limit_as_a_setting_and_a_weekend_off_can_have_one() {
        val d = LocalDate.of(2026, 11, 28)
        val inst = Instance(Preset.PERIOD_OFF, d, d.plusDays(1), mapOf("screen_limit_min" to "45"))
        assertEquals(45, inst.int("screen_limit_min")); assertEquals(emptyList(), validate(inst, LocalDate.of(2026, 11, 20)))
        assertEquals(0, Instance(Preset.PERIOD_OFF, d, d).int("screen_limit_min"), "no limit by default")
        assertTrue(validate(inst.copy(settings = mapOf("screen_limit_min" to "900")), LocalDate.of(2026, 11, 20)).any { it.contains("Screen-time limit") })
    }

    // ---- CE7 ----
    @Test fun CE7_break_glass_takes_away_the_last_loosening_and_the_last_import_and_nothing_else() {
        val mon = ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, zone).toInstant()
        fun e(c: ChangeClass, m: Long, imp: Boolean = false, id: Long = m) = ChangeEntry(c, mon.plus(Duration.ofMinutes(m)), imp, id)
        val h = listOf(e(ChangeClass.LOOSEN, 1), e(ChangeClass.LOOSEN, 2), e(ChangeClass.LOOSEN, 3), e(ChangeClass.MOVE, 4), e(ChangeClass.ADD, 5, imp = true), e(ChangeClass.ADD, 6, imp = true))
        val after = breakGlassHistory(h, mon.plus(Duration.ofDays(2)))
        assertEquals(setOf(1L, 2L, 4L, 5L), after.map { it.id }.toSet())
        val locked = decideChange(h, ChangeClass.LOOSEN, false, mon.plus(Duration.ofDays(2)), mon.minus(Duration.ofDays(90)))
        assertTrue(locked is ChangeDecision.Locked)
        assertTrue(decideChange(after, ChangeClass.LOOSEN, false, mon.plus(Duration.ofDays(2)), mon.minus(Duration.ofDays(90))) !is ChangeDecision.Locked, "the lock is lifted")
        assertEquals(150, BREAK_GLASS_WALL)
    }

    @Test fun CE7_EM4_break_glass_is_once_in_thirty_days_and_never_costs_the_pool() {
        val now = Instant.now()
        assertTrue(breakGlassAvailable(emptyList(), now))
        val used = listOf(DoorUse(Door.BREAK_GLASS, now.minus(Duration.ofDays(3)), 0, 0))
        assertFalse(breakGlassAvailable(used, now))
        assertEquals(Spent(0, 0), price(DoorUse(Door.BREAK_GLASS, now, 500, 500)))
    }

    // ---- SL2: NFC is an option ----
    @Test fun SL2_a_tag_is_known_by_a_hash_never_its_id_and_the_registry_round_trips() {
        val k = tagKey(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        assertEquals(16, k.length); assertFalse(k.contains("01020304")); assertEquals(k, tagKey(byteArrayOf(1, 2, 3, 4, 5, 6, 7))); assertNotEquals(k, tagKey(byteArrayOf(9, 9, 9)))
        val m = mapOf(k to NfcPurpose.CHARGER, "abcd" to NfcPurpose.DESK)
        assertEquals(m, decodeTags(encodeTags(m))); assertTrue(decodeTags("junk|NOPE\nx").isEmpty())
    }

    @Test fun SL2_every_purpose_names_steps_that_exist_and_accept_a_tag() {
        val all = ROUTINES.flatMap { it.steps }.associateBy { it.id }
        for (p in NfcPurpose.entries) for (id in p.steps) { val s = all[id]; assertNotNull(s, "$id exists"); assertTrue(ProofTier.NFC in s.alsoAccepts || s.proof.rank <= ProofTier.NFC.rank, "$id accepts a tag") }
    }

    @Test fun SL2_FB8_with_nfc_off_a_tap_proves_nothing_and_photos_stay_the_proof() {
        val steps = runSteps(BIBLE, Tier.FULL, emptyList(), setOf(NfcPurpose.BIBLE_SEAT), nfcOn = false)
        assertFalse(steps.first { it.step.id == "bible.sit" }.done)
        val on = runSteps(BIBLE, Tier.FULL, emptyList(), setOf(NfcPurpose.BIBLE_SEAT), nfcOn = true)
        assertTrue(on.first { it.step.id == "bible.sit" }.done); assertEquals(NfcPurpose.BIBLE_SEAT, on.first { it.step.id == "bible.sit" }.tagPurpose)
    }

    @Test fun FB6_a_tap_away_from_its_place_is_a_weaker_fact() {
        val t = Instant.parse("2026-11-21T08:00:00Z")
        assertEquals(1.0, tapFact(NfcPurpose.DESK, t, true).confidence); assertEquals(0.5, tapFact(NfcPurpose.DESK, t, false).confidence)
        assertEquals("DESK", tapFact(NfcPurpose.DESK, t).ref)
    }

    // ---- FB1: facts as lines ----
    @Test fun FB1_a_fact_round_trips_as_a_line_and_a_bar_in_a_ref_cannot_break_it() {
        val f = Fact(FactType.TAP, Instant.parse("2026-11-21T08:00:00Z"), "tap", 0.9, "step:bible.sit|x")
        val back = decodeFact(encodeFact(f))!!
        assertEquals(FactType.TAP, back.type); assertEquals(0.9, back.confidence); assertEquals("step:bible.sit x", back.ref); assertNull(decodeFact("garbage"))
    }

    // ---- RT: the runner ----
    @Test fun RT5_RT6_the_runner_follows_the_tier_and_a_routine_is_done_when_every_non_photo_step_is_done() {
        val facts = BIBLE.stepsFor(Tier.FLOOR).map { Fact(FactType.TAP, Instant.now(), "tap", 1.0, "step:${it.id}") }
        val steps = runSteps(BIBLE, Tier.FLOOR, facts, emptySet(), false)
        assertTrue(routineDone(steps)); assertEquals(BIBLE.stepsFor(Tier.FLOOR).size, steps.size)
        assertFalse(routineDone(runSteps(BIBLE, Tier.FLOOR, facts.drop(1), emptySet(), false)))
    }

    @Test fun RT3_a_photo_step_is_shown_as_a_photo_step_and_does_not_hold_the_routine_open() {
        val ready = runSteps(GETTING_READY, Tier.FLOOR, emptyList(), emptySet(), false)
        assertTrue(ready.any { it.needsPhoto && it.step.id == "ready.bag" })
        val allButPhoto = ready.filter { !it.needsPhoto }.map { Fact(FactType.TAP, Instant.now(), "tap", 1.0, "step:${it.step.id}") }
        assertTrue(routineDone(runSteps(GETTING_READY, Tier.FLOOR, allButPhoto, emptySet(), false)))
    }
}
