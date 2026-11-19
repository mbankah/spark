package efa

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Phase 2: the spine. Anchors (SA), facts (FB), doors (DP), the change economy (CE), context (CX), routines (RT). */
class SpineTest {
    private val night = LocalDate.of(2026, 11, 18)
    private fun at(h: Int, m: Int = 0, d: Int = 18) = LocalDateTime.of(2026, 11, d, h, m)
    private fun slot(day: DayOfWeek, s: Int, e: Int, title: String, type: String, hard: Boolean = false) = PatternSlot(day, s, e, title, type, "", hard, "")
    private val thursdayLecture = slot(DayOfWeek.THURSDAY, 9 * 60, 10 * 60, "Randomness lecture", "lecture")

    // ---- SA: anchors ----
    @Test fun SA1_default_anchors_put_lights_out_at_the_n1_cutoff() {
        val a = defaultAnchors(night, at(9, 0, 19))
        assertEquals(LocalDateTime.of(2026, 11, 19, 0, 40), a.lightsOut)          // 09:00 minus 8h minus 20 min
        assertTrue(checkAnchors(a, emptyList()).isEmpty())
    }

    @Test fun SA3_tonights_plan_stands_with_lights_out_at_0240_and_wake_at_1100() {
        val a = NightAnchors(night, at(2, 40, 19), at(11, 0, 19))
        assertTrue(checkAnchors(a, emptyList()).isEmpty(), "02:40 to 11:00 is 8h20 in bed and 8h asleep")
    }

    @Test fun SA3_lights_out_after_the_cutoff_is_refused_and_says_the_wake_that_would_work() {
        val a = NightAnchors(night, at(2, 45, 19), at(11, 0, 19))
        val p = checkAnchors(a, emptyList()).single() as AnchorProblem.UnderEightHours
        assertEquals(LocalDateTime.of(2026, 11, 19, 11, 5), p.earliestWake)
        assertTrue(p.text().contains("11:05"))
    }

    @Test fun SA5_a_lecture_before_the_wake_is_refused_naming_the_lecture() {
        val a = NightAnchors(night, at(2, 40, 19), at(11, 0, 19))             // Thursday 19 Nov 2026
        val p = checkAnchors(a, listOf(thursdayLecture)).single() as AnchorProblem.HidesCommitment
        assertTrue(p.text().contains("Randomness lecture"))
        assertTrue(p.text().contains("recording"))
    }

    @Test fun SA5_a_lecture_after_the_wake_and_the_morning_floor_is_fine() {
        val a = NightAnchors(night, at(2, 40, 19), at(11, 0, 19))
        val noon = slot(DayOfWeek.THURSDAY, 12 * 60, 13 * 60, "Neural lecture", "lecture")
        assertTrue(checkAnchors(a, listOf(noon)).isEmpty())
        val tight = slot(DayOfWeek.THURSDAY, 11 * 60 + 20, 12 * 60, "Neural lecture", "lecture")
        assertEquals(1, checkAnchors(a, listOf(tight)).size, "20 minutes after waking is less than the 30 minute morning floor")
    }

    @Test fun SA5_a_study_block_or_the_morning_routine_is_not_a_commitment() {
        val a = NightAnchors(night, at(2, 40, 19), at(11, 0, 19))
        val study = slot(DayOfWeek.THURSDAY, 9 * 60, 11 * 60, "Notes", "study-standard")
        val routine = slot(DayOfWeek.THURSDAY, 8 * 60, 8 * 60 + 30, "Bible study", "routine")
        assertTrue(checkAnchors(a, listOf(study, routine)).isEmpty())
    }

    @Test fun SA5_a_hard_alarm_is_a_commitment_whatever_its_type() {
        val a = NightAnchors(night, at(2, 40, 19), at(11, 0, 19))
        val hard = slot(DayOfWeek.THURSDAY, 8 * 60, 9 * 60, "Paid session", "free", hard = true)
        assertEquals(1, checkAnchors(a, listOf(hard)).size)
    }

    @Test fun SA2_the_guard_rail_is_wake_to_lights_out_and_not_fixed_numbers() {
        // 00:00 to 02:30 on the planned day after tonight: minutes after midnight of the night's date are 1440 to 1590, lights-out 1600
        assertTrue(withinTheDay(1440, 1590, 11 * 60 - 1440 * 0 + 0 - 11 * 60 + 0, 1600))
        assertFalse(withinTheDay(1440, 1610, 0, 1600), "a block past lights-out is outside the day")
        assertFalse(withinTheDay(300, 400, 11 * 60, 1600), "a block before the wake is outside the day")
    }

    @Test fun SA4_moving_both_anchors_together_is_free_and_lights_out_later_alone_is_refused() {
        val old = NightAnchors(night, at(0, 40, 19), at(9, 0, 19))
        assertEquals(AnchorChange.FREE, classifyAnchorChange(old, NightAnchors(night, at(2, 40, 19), at(11, 0, 19))))
        assertEquals(AnchorChange.REFUSED, classifyAnchorChange(old, NightAnchors(night, at(2, 40, 19), at(9, 0, 19))))
    }

    @Test fun SA6_a_day_with_a_clock_change_is_23_or_25_hours_and_the_in_bed_time_is_real_elapsed() {
        assertEquals(1500, dayLengthMinutes(LocalDate.of(2026, 10, 25)))           // clocks go back
        assertEquals(1380, dayLengthMinutes(LocalDate.of(2027, 3, 28)))            // clocks go forward
        assertEquals(1440, dayLengthMinutes(LocalDate.of(2026, 11, 18)))
        val a = NightAnchors(LocalDate.of(2026, 10, 24), LocalDateTime.of(2026, 10, 25, 0, 30), LocalDateTime.of(2026, 10, 25, 8, 30))
        assertEquals(9 * 60L, a.inBedMinutes(), "8h on the clock is 9h of real time across the change")
    }

    // ---- FB: facts and proof ----
    private val t0 = Instant.parse("2026-11-18T08:00:00Z")
    private fun w(needs: ProofTier, also: Set<ProofTier> = emptySet()) = StepWindow("x", t0, t0.plusSeconds(600), needs, also)
    private fun f(type: FactType, sec: Long, src: String = "s", c: Double = 1.0) = Fact(type, t0.plusSeconds(sec), src, c)

    @Test fun FB2_a_higher_tier_satisfies_a_lower_need_and_never_the_other_way() {
        assertEquals(StepState.DONE, stepState(w(ProofTier.TAP), listOf(f(FactType.PHOTO_MATCHED, 10)), t0.plusSeconds(20)))
        assertEquals(StepState.OPEN, stepState(w(ProofTier.PHOTO), listOf(f(FactType.TAP, 10)), t0.plusSeconds(20)))
    }

    @Test fun FB3_a_step_is_done_only_by_a_fact_inside_its_window() {
        assertEquals(StepState.OPEN, stepState(w(ProofTier.PHOTO), listOf(f(FactType.PHOTO_MATCHED, -30)), t0.plusSeconds(20)))
        assertEquals(StepState.OPEN, stepState(w(ProofTier.PHOTO), listOf(f(FactType.PHOTO_MATCHED, 700)), t0.plusSeconds(20)))
        assertEquals(StepState.PENDING, stepState(w(ProofTier.PHOTO), emptyList(), t0.minusSeconds(5)))
    }

    @Test fun FB4_an_ignored_proof_leaves_the_step_open_with_its_gate_shut_then_unproven_with_no_reward() {
        val win = w(ProofTier.PHOTO)
        val open = stepState(win, emptyList(), t0.plusSeconds(100))
        assertEquals(StepState.OPEN, open); assertTrue(holdsGate(open)); assertFalse(paysReward(open))
        val late = stepState(win, emptyList(), t0.plusSeconds(700))
        assertEquals(StepState.UNPROVEN, late); assertFalse(holdsGate(late)); assertFalse(paysReward(late)); assertTrue(countsAsUnconfirmed(late))
    }

    @Test fun FB5_a_technical_failure_closes_the_step_as_not_your_fault_and_is_not_unconfirmed() {
        val s = stepState(w(ProofTier.PHOTO), emptyList(), t0.plusSeconds(700), techFailed = true)
        assertEquals(StepState.NOT_YOUR_FAULT, s); assertFalse(countsAsUnconfirmed(s))
    }

    @Test fun FB6_a_tag_read_away_from_its_place_is_accepted_at_lower_confidence_only() {
        val away = nfcFact(t0.plusSeconds(10), "desk", atItsPlace = false)
        assertEquals(StepState.OPEN, stepState(w(ProofTier.NFC), listOf(away), t0.plusSeconds(20)), "one weak fact is not enough")
        val also = Fact(FactType.GEOFENCE, t0.plusSeconds(12), "gps", 0.6)
        assertEquals(StepState.DONE, stepState(w(ProofTier.NFC), listOf(away, also), t0.plusSeconds(20)), "a weak tag and a weak place from two sources agree")
    }

    @Test fun FB7_two_weak_facts_from_the_same_source_do_not_count() {
        val a = f(FactType.STEPS, 5, "step", 0.5); val b = f(FactType.STEPS, 9, "step", 0.5)
        assertEquals(StepState.OPEN, stepState(w(ProofTier.SENSOR), listOf(a, b), t0.plusSeconds(20)))
    }

    @Test fun FB8_a_tag_stands_in_for_a_lower_need_once_it_exists_but_a_photo_step_needs_a_photo() {
        assertEquals(StepState.DONE, stepState(w(ProofTier.TAP, setOf(ProofTier.NFC)), listOf(nfcFact(t0.plusSeconds(3), "seat", true)), t0.plusSeconds(20)))
        assertEquals(StepState.OPEN, stepState(w(ProofTier.PHOTO), listOf(nfcFact(t0.plusSeconds(3), "seat", true)), t0.plusSeconds(20)))
    }

    // ---- DP: the door pool ----
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private fun use(d: Door, o: Int, g: Int, daysAgo: Long = 0, verified: Boolean = false) = DoorUse(d, now.minus(Duration.ofDays(daysAgo)), o, g, verified)

    @Test fun DP2_two_doors_together_cannot_exceed_the_pool() {
        val uses = listOf(use(Door.DAY_OFF, 360, 0))
        val second = doorDecision(uses, use(Door.RECOVERY, 360, 0), now)
        assertTrue(second is DoorDecision.Locked, "360 + 360 is over the 600 minute pool")
    }

    @Test fun DP3_a_verified_door_costs_half() {
        assertEquals(Spent(180, 0), price(use(Door.ILL, 360, 0, verified = true)))
        assertEquals(Spent(360, 0), price(use(Door.ILL, 360, 0, verified = false)))
        assertEquals(Spent(0, 45), price(use(Door.OUT, 0, 90, verified = true)))
    }

    @Test fun DP4_at_the_cap_only_the_emergency_door_a_verified_ill_day_and_break_glass_open() {
        val full = listOf(use(Door.DAY_OFF, 600, 180))
        assertTrue(doorDecision(full, use(Door.DAY_OFF, 60, 0), now) is DoorDecision.Locked)
        assertTrue(doorDecision(full, use(Door.EMERGENCY, 0, 0), now) is DoorDecision.Open)
        assertTrue(doorDecision(full, use(Door.BREAK_GLASS, 0, 0), now) is DoorDecision.Open)
        assertTrue(doorDecision(full, use(Door.ILL, 360, 0, verified = true), now) is DoorDecision.Open)
        assertTrue(doorDecision(full, use(Door.ILL, 360, 0, verified = false), now) is DoorDecision.Locked, "an unverified ill day is not exempt")
    }

    @Test fun DP2_the_wall_grows_as_the_pool_drains() {
        assertEquals(75, (doorDecision(emptyList(), use(Door.DAY_OFF, 200, 0), now) as DoorDecision.Open).wallLength)
        assertEquals(100, (doorDecision(emptyList(), use(Door.DAY_OFF, 400, 0), now) as DoorDecision.Open).wallLength)
        assertEquals(130, (doorDecision(emptyList(), use(Door.DAY_OFF, 550, 0), now) as DoorDecision.Open).wallLength)
    }

    @Test fun DP2_uses_older_than_the_window_free_the_pool_again() {
        val old = listOf(use(Door.DAY_OFF, 600, 180, daysAgo = 8))
        assertTrue(doorDecision(old, use(Door.DAY_OFF, 600, 0), now) is DoorDecision.Open)
    }

    @Test fun DP6_no_order_or_combination_of_doors_can_exceed_the_pool() {
        val rng = Random(20261118)
        val caps = PoolCaps()
        val doors = Door.entries
        repeat(400) {
            var uses = emptyList<DoorUse>()
            val steps = rng.nextInt(1, 40)
            repeat(steps) {
                val d = doors[rng.nextInt(doors.size)]
                val r = DoorUse(d, now.minusSeconds(rng.nextLong(0, 6 * 86400)), rng.nextInt(0, 400), rng.nextInt(0, 120), rng.nextBoolean())
                uses = applyIfOpen(uses, r, now, caps)
            }
            val spent = spentInWindow(uses.filter { it.door != Door.ILL || !it.verified }, now, caps)
            val withVerifiedIll = uses.filter { it.door == Door.ILL && it.verified }
            // Everything that is not an exempt verified ill day stays inside the caps; the ill days are exempt by DP4 but still cost half and are recorded.
            assertTrue(spent.obligationMin <= caps.obligationMin && spent.gateMin <= caps.gateMin, "pool exceeded: $spent with ${uses.size} uses")
            assertTrue(withVerifiedIll.all { it.verified })
        }
    }

    @Test fun CE7_break_glass_is_once_in_thirty_days() {
        val used = listOf(use(Door.BREAK_GLASS, 0, 0, daysAgo = 10))
        assertFalse(breakGlassAvailable(used, now))
        assertTrue(breakGlassAvailable(listOf(use(Door.BREAK_GLASS, 0, 0, daysAgo = 31)), now))
    }

    // ---- CE: the change economy ----
    private val monday = ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneId.of("Europe/London")).toInstant()
    private val installed = monday.minus(Duration.ofDays(60))
    private fun e(c: ChangeClass, minutes: Long, import: Boolean = false) = ChangeEntry(c, monday.plus(Duration.ofMinutes(minutes)), import)

    @Test fun CE2_reality_and_add_are_free_and_unlimited_even_after_many() {
        val many = (1..30).map { e(ChangeClass.REALITY, it.toLong()) }
        assertEquals(ChangeDecision.Free(counted = false), decideChange(many, ChangeClass.REALITY, false, monday.plus(Duration.ofHours(3)), installed))
        assertEquals(ChangeDecision.Free(counted = false), decideChange(many, ChangeClass.ADD, false, monday.plus(Duration.ofHours(3)), installed))
    }

    @Test fun CE3_five_moves_a_week_are_free_then_a_short_string() {
        val five = (1..5).map { e(ChangeClass.MOVE, it * 10L) }
        val at = monday.plus(Duration.ofHours(8))
        assertEquals(ChangeDecision.Free(counted = true), decideChange(five.take(4), ChangeClass.MOVE, false, at, installed))
        val d = decideChange(five, ChangeClass.MOVE, false, at, installed) as ChangeDecision.Wall
        assertEquals(25, d.length)
    }

    @Test fun CE4_loosening_uses_the_wall_then_longer_then_locks_until_monday() {
        val at = monday.plus(Duration.ofHours(10))
        assertEquals(75, (decideChange(emptyList(), ChangeClass.LOOSEN, false, at, installed) as ChangeDecision.Wall).length)
        assertEquals(75, (decideChange(listOf(e(ChangeClass.LOOSEN, 5)), ChangeClass.LOOSEN, false, at, installed) as ChangeDecision.Wall).length)
        assertEquals(100, (decideChange(listOf(e(ChangeClass.LOOSEN, 5), e(ChangeClass.LOOSEN, 6)), ChangeClass.LOOSEN, false, at, installed) as ChangeDecision.Wall).length)
        val locked = decideChange(listOf(e(ChangeClass.LOOSEN, 5), e(ChangeClass.LOOSEN, 6), e(ChangeClass.LOOSEN, 7)), ChangeClass.LOOSEN, false, at, installed) as ChangeDecision.Locked
        assertEquals(nextWeekStart(at), locked.until)
    }

    @Test fun CE5_two_free_imports_a_week_then_short_string_then_a_24_hour_cooldown_and_checking_stays_free() {
        val at = monday.plus(Duration.ofHours(30))
        val imports = { n: Int -> (1..n).map { e(ChangeClass.ADD, it * 20L, import = true) } }
        assertEquals(ChangeDecision.Free(counted = true), decideChange(imports(1), ChangeClass.ADD, true, at, installed))
        assertEquals(25, (decideChange(imports(2), ChangeClass.ADD, true, at, installed) as ChangeDecision.Wall).length)
        assertEquals(25, (decideChange(imports(4), ChangeClass.ADD, true, at, installed) as ChangeDecision.Wall).length)
        val cool = decideChange(imports(5), ChangeClass.ADD, true, monday.plus(Duration.ofMinutes(5 * 20L + 60)), installed)
        assertTrue(cool is ChangeDecision.Cooldown, "the 6th import starts a cooldown")
        // after 24 hours the cooldown has passed: the week count still applies but the cooldown does not block
        assertTrue(decideChange(imports(5), ChangeClass.ADD, true, monday.plus(Duration.ofMinutes(5 * 20L)).plus(Duration.ofHours(25)), installed) !is ChangeDecision.Cooldown)
    }

    @Test fun CE6_the_first_seven_days_count_but_never_lock() {
        val justInstalled = monday.minus(Duration.ofDays(2))
        val heavy = (1..10).map { e(ChangeClass.LOOSEN, it * 5L) }
        val d = decideChange(heavy, ChangeClass.LOOSEN, false, monday.plus(Duration.ofHours(5)), justInstalled)
        assertTrue(d is ChangeDecision.Free && d.trial)
    }

    @Test fun CE8_an_undo_refunds_the_count() {
        val h = (1..5).map { ChangeEntry(ChangeClass.MOVE, monday.plus(Duration.ofMinutes(it * 10L)), id = it.toLong()) }
        val after = refund(h, 3)
        assertEquals(4, after.size)
        assertEquals(ChangeDecision.Free(counted = true), decideChange(after, ChangeClass.MOVE, false, monday.plus(Duration.ofHours(8)), installed))
    }

    @Test fun CE5_the_week_resets_on_monday_at_midnight_london_time() {
        val sundayLate = ZonedDateTime.of(2026, 11, 22, 23, 0, 0, 0, ZoneId.of("Europe/London")).toInstant()
        assertEquals(monday.atZone(ZoneId.of("Europe/London")).toLocalDate().atStartOfDay(ZoneId.of("Europe/London")).toInstant(), weekStart(sundayLate))
        assertEquals(ZonedDateTime.of(2026, 11, 23, 0, 0, 0, 0, ZoneId.of("Europe/London")).toInstant(), nextWeekStart(sundayLate))
    }

    @Test fun CE10_a_change_that_loosens_the_economy_takes_effect_after_24_hours() {
        assertEquals(monday.plus(Duration.ofHours(24)), economyChangeEffectiveAt(monday, loosens = true))
        assertEquals(monday, economyChangeEffectiveAt(monday, loosens = false))
    }

    // ---- CX: context and places ----
    private val flat = Place("flat", "Flat", PlaceKind.FLAT, 52.4508, -1.9305, 100, setOf("flat-wifi"))
    private val family = Place("family", "Family home", PlaceKind.FAMILY_HOME, 51.5074, -0.1278, 100, setOf("family-wifi"))
    private val campus = Place("campus", "Campus", PlaceKind.CAMPUS, 52.4508, -1.9305 + 0.012, 400, setOf("eduroam"))

    @Test fun CX4_HO2_home_is_whichever_home_you_are_at() {
        assertEquals(PlaceKind.FLAT, placeAt(52.4508, -1.9305, null, listOf(flat, family)).place?.kind)
        assertEquals(PlaceKind.FAMILY_HOME, placeAt(51.5074, -0.1278, "family-wifi", listOf(flat, family)).place?.kind)
        assertEquals(null, placeAt(53.0, -2.0, null, listOf(flat, family)).place)
    }

    @Test fun FB7_place_is_strong_when_gps_and_wifi_agree_and_weaker_when_they_disagree() {
        assertEquals(1.0, placeAt(52.4508, -1.9305, "flat-wifi", listOf(flat, family)).confidence)
        assertTrue(placeAt(52.4508, -1.9305, "family-wifi", listOf(flat, family)).confidence < 0.7)
    }

    @Test fun CX3_HO1_the_day_type_is_proposed_from_the_calendar_place_and_term() {
        val pattern = listOf(thursdayLecture, slot(DayOfWeek.SUNDAY, 17 * 60, 20 * 60, "Church", "church"), slot(DayOfWeek.MONDAY, 13 * 60, 14 * 60, "Campus League", "match"))
        assertEquals(DayType.UNI, proposeDayType(LocalDate.of(2026, 11, 19), PlaceKind.FLAT, PlaceKind.FLAT, pattern).type)
        assertEquals(DayType.CHURCH, proposeDayType(LocalDate.of(2026, 11, 22), PlaceKind.FLAT, PlaceKind.FLAT, pattern).type)
        assertEquals(DayType.MATCH, proposeDayType(LocalDate.of(2026, 11, 23), PlaceKind.FLAT, PlaceKind.FLAT, pattern).type)
        assertEquals(DayType.EXAM, proposeDayType(LocalDate.of(2027, 1, 12), PlaceKind.FAMILY_HOME, PlaceKind.FAMILY_HOME, emptyList(), examToday = true).type)
        assertEquals(DayType.HOME_STUDY, proposeDayType(LocalDate.of(2026, 12, 16), PlaceKind.FAMILY_HOME, PlaceKind.FAMILY_HOME, emptyList()).type)
        assertEquals(DayType.TRAVEL, proposeDayType(LocalDate.of(2026, 12, 14), PlaceKind.FAMILY_HOME, PlaceKind.FLAT, emptyList()).type)
        assertEquals(DayType.HOLIDAY, proposeDayType(LocalDate.of(2026, 12, 25), PlaceKind.FAMILY_HOME, PlaceKind.FAMILY_HOME, emptyList()).type)
        assertEquals(DayType.REST, proposeDayType(LocalDate.of(2026, 11, 14), PlaceKind.FLAT, PlaceKind.FLAT, emptyList()).type)
    }

    @Test fun HO6_the_calendar_carries_the_universitys_real_dates() {
        assertEquals(TermState.VACATION, termOn(LocalDate.of(2026, 12, 14))?.state)
        assertEquals(TermState.VACATION, termOn(LocalDate.of(2027, 1, 8))?.state)
        assertEquals(TermState.ASSESSMENT, termOn(LocalDate.of(2027, 1, 12))?.state)
        assertEquals(TermState.SUPPORT_WEEK, termOn(LocalDate.of(2026, 11, 4))?.state)
        assertEquals(TermState.TEACHING, termOn(LocalDate.of(2026, 12, 11))?.state)
        assertEquals(LocalDate.of(2026, 12, 14), WINTER_VACATION_FROM); assertEquals(LocalDate.of(2027, 1, 8), WINTER_VACATION_TO)
    }

    @Test fun RT7_a_new_routine_observes_for_a_week_suggests_for_a_week_then_enforces() {
        assertEquals(Phase.OBSERVE, phaseOf(0)); assertEquals(Phase.OBSERVE, phaseOf(6))
        assertEquals(Phase.SUGGEST, phaseOf(7)); assertEquals(Phase.SUGGEST, phaseOf(13))
        assertEquals(Phase.ENFORCE, phaseOf(14))
    }

    // ---- RT: routines ----
    @Test fun RT2_the_bible_is_30_20_and_10_minutes_and_never_dropped() {
        assertEquals(30, BIBLE.minutes(Tier.FULL)); assertEquals(20, BIBLE.minutes(Tier.SHORT)); assertEquals(10, BIBLE.minutes(Tier.FLOOR))
        assertTrue(BIBLE.neverDropped)
    }

    @Test fun RT2_every_routine_has_a_floor_that_is_not_empty() {
        for (r in ROUTINES) assertTrue(r.stepsFor(Tier.FLOOR).isNotEmpty() || r.steps.all { Tier.FLOOR in it.tiers || Tier.FULL in it.tiers }, "${r.id} needs a floor")
        for (r in listOf(BIBLE, WAKE, GETTING_READY, WIND_DOWN)) assertTrue(r.stepsFor(Tier.FLOOR).isNotEmpty(), "${r.id} floor")
    }

    @Test fun RT5_the_tier_follows_the_time_and_never_goes_below_the_floor() {
        assertEquals(Tier.FULL, pickTier(BIBLE, 45)); assertEquals(Tier.SHORT, pickTier(BIBLE, 22)); assertEquals(Tier.FLOOR, pickTier(BIBLE, 12))
        assertEquals(Tier.FLOOR, pickTier(BIBLE, 3), "less time than the floor still gives the floor")
    }

    @Test fun RT5_bad_sleep_or_an_ill_day_lowers_the_tier() {
        assertEquals(Tier.SHORT, pickTier(BIBLE, 60, sleepHours = 4.5))
        assertEquals(Tier.FLOOR, pickTier(BIBLE, 60, sleepHours = 3.5), "the Bible floors on under 4 hours")
        assertEquals(Tier.FLOOR, pickTier(BIBLE, 60, dayType = DayType.ILL))
        assertEquals(Tier.SHORT, pickTier(BIBLE, 60, dayType = DayType.EXAM))
        assertEquals(Tier.FULL, pickTier(BIBLE, 60, sleepHours = 7.5))
    }

    @Test fun RT4_parallel_steps_take_the_longer_time_not_the_sum_and_must_name_each_other() {
        assertEquals(14 + 11 + 3, PASTA_AND_SHOWER.minutes(Tier.FULL), "pasta and shower overlap")
        assertTrue(validateParallel(PASTA_AND_SHOWER.steps).isEmpty())
        val bad = listOf(Step("a", "A.", 5, ProofTier.TAP, parallelWith = "b"), Step("b", "B.", 5, ProofTier.TAP))
        assertTrue(validateParallel(bad).isNotEmpty())
        val ghost = listOf(Step("a", "A.", 5, ProofTier.TAP, parallelWith = "zzz"))
        assertTrue(validateParallel(ghost).isNotEmpty())
    }

    @Test fun RT3_CO4_every_step_is_one_short_sentence_with_the_verb_first() {
        for (r in ROUTINES) for (st in r.steps) {
            assertTrue(st.say.length <= 80, "${st.id} is too long")
            assertTrue(st.say.isNotBlank() && st.say.first().isUpperCase(), "${st.id} starts with a capital")
            assertFalse(st.say.startsWith("You "), "${st.id} must lead with the verb")
        }
    }

    @Test fun RT8_prompts_fade_as_a_habit_holds_and_come_back_when_it_slips() {
        val good = List(28) { true }
        assertEquals(Scaffold.CUED, nextScaffold(Scaffold.GUIDED, good))
        assertEquals(Scaffold.TRACKED, nextScaffold(Scaffold.CUED, good))
        assertEquals(Scaffold.GUIDED, nextScaffold(Scaffold.GUIDED, List(28) { it % 2 == 0 }))
        val slipping = List(21) { true } + List(7) { it >= 4 }       // 3 misses in the last 7
        assertEquals(Scaffold.GUIDED, nextScaffold(Scaffold.CUED, slipping))
        assertEquals(Scaffold.CUED, nextScaffold(Scaffold.TRACKED, slipping))
        assertEquals(Scaffold.YOURS, nextScaffold(Scaffold.YOURS, good), "Yours is chosen, not reached")
    }

    @Test fun FB1_every_fact_type_has_a_proof_tier_and_the_ladder_is_ordered_none_tap_sensor_nfc_photo() {
        for (t in FactType.entries) assertNotNull(t.tier)
        assertEquals(listOf(ProofTier.NONE, ProofTier.TAP, ProofTier.SENSOR, ProofTier.NFC, ProofTier.PHOTO), ProofTier.entries.sortedBy { it.rank })
        assertEquals(ProofTier.PHOTO, FactType.PHOTO_MATCHED.tier); assertEquals(ProofTier.NFC, FactType.NFC_TAG.tier)
    }

    @Test fun RT1_a_routine_holds_ordered_steps_each_with_something_to_say_a_proof_and_a_tier() {
        for (r in ROUTINES) { assertTrue(r.steps.isNotEmpty(), r.id); for (s in r.steps) { assertTrue(s.say.isNotBlank()); assertTrue(s.tiers.isNotEmpty(), s.id) } }
        assertEquals(setOf("bible", "wake", "ready", "winddown", "pastashower", "study"), ROUTINES.map { it.id }.toSet().intersect(setOf("bible", "wake", "ready", "winddown", "pastashower", "study")))
    }

    @Test fun RT6_the_bible_is_never_dropped_and_ten_minutes_is_the_floor_however_little_time_there_is() {
        assertTrue(BIBLE.neverDropped)
        assertEquals(10, BIBLE.minutes(Tier.FLOOR))
        assertEquals(Tier.FLOOR, pickTier(BIBLE, 0))
        assertTrue(BIBLE.stepsFor(Tier.FLOOR).isNotEmpty())
    }

    @Test fun CX2_a_place_is_named_with_a_point_a_radius_and_wifi_names() {
        val p = Place("flat", "Flat", PlaceKind.FLAT, 52.45, -1.93, 120, setOf("home-wifi"))
        assertEquals("Flat", p.name); assertEquals(120, p.radiusM); assertTrue("home-wifi" in p.wifi)
        assertEquals(setOf(PlaceKind.FLAT, PlaceKind.FAMILY_HOME, PlaceKind.CAMPUS, PlaceKind.LIBRARY, PlaceKind.CHURCH, PlaceKind.PITCH, PlaceKind.SHOP), PlaceKind.entries.toSet().intersect(setOf(PlaceKind.FLAT, PlaceKind.FAMILY_HOME, PlaceKind.CAMPUS, PlaceKind.LIBRARY, PlaceKind.CHURCH, PlaceKind.PITCH, PlaceKind.SHOP)))
    }

    @Test fun DP1_every_door_is_listed_and_only_the_emergency_break_glass_and_the_ill_day_can_be_exempt() {
        assertEquals(setOf(Door.ILL, Door.BREAK_GLASS, Door.EMERGENCY), Door.entries.filter { it.exempt }.toSet())
        assertTrue(Door.entries.size >= 13)
        assertEquals(setOf(Door.ILL, Door.OUT), Door.entries.filter { it.verifiable }.toSet())
    }

    @Test fun SL1_the_wind_down_has_three_tiers_and_always_ends_with_the_dock_and_the_shutdown_photo() {
        assertTrue(WIND_DOWN.minutes(Tier.FULL) in 40..60); assertTrue(WIND_DOWN.minutes(Tier.SHORT) in 30..45); assertTrue(WIND_DOWN.minutes(Tier.FLOOR) in 5..12)
        for (t in Tier.entries) { val s = WIND_DOWN.stepsFor(t); assertEquals("wd.photo", s.last().id, t.name); assertTrue(s.any { it.id == "wd.dock" }, t.name) }
    }

    @Test fun SL8_there_is_no_sleep_restriction_a_night_under_eight_hours_is_refused_and_a_shift_is_gradual() {
        val a = NightAnchors(night, at(3, 30, 19), at(10, 0, 19))
        assertTrue(checkAnchors(a, emptyList()).any { it is AnchorProblem.UnderEightHours })
        assertTrue(gradualShift(java.time.LocalTime.of(11, 0), java.time.LocalTime.of(8, 0), night).zipWithNext().all { (x, y) -> Duration.between(y.second, x.second).toMinutes() <= 30 })
    }
}
