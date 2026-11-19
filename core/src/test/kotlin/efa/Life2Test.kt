package efa

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** EM1 to EM5, CO2, CO3, SL4, SL6, SL7, HO4, CX1, CE1: the rest of the life layer in core. */
class Life2Test {
    private val zone = ZoneId.of("Europe/London")
    private fun z(h: Int, m: Int = 0, d: Int = 19) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone)
    private fun p(d: DayOfWeek, s: Int, e: Int, title: String, type: String) = PatternSlot(d, s, e, title, type, "", false, "")
    private val pattern = listOf(
        p(DayOfWeek.THURSDAY, 450, 470, "Wake, shower", "routine"), p(DayOfWeek.THURSDAY, 615, 735, "Notes", "study-standard"), p(DayOfWeek.THURSDAY, 1340, 1380, "Shutdown", "sleep"),
        p(DayOfWeek.FRIDAY, 450, 470, "Wake, shower", "routine"),
    )

    @Test fun EM1_the_emergency_door_holds_999_the_phone_maps_the_bank_location_and_111_and_none_of_it_is_gated() {
        assertEquals(6, EmergencyAction.entries.size)
        assertTrue(EmergencyAction.entries.any { it.label == "Call 999" }); assertTrue(EmergencyAction.entries.any { it.label == "NHS 111" })
        assertEquals(Decision.Allow, decide("com.android.dialer", z(3), pattern, setOf("com.instagram.android")), "calls are never on the list")
        assertTrue(doorDecision(listOf(DoorUse(Door.DAY_OFF, Instant.now(), 600, 180)), DoorUse(Door.EMERGENCY, Instant.now(), 0, 0), Instant.now()) is DoorDecision.Open)
    }

    @Test fun EM2_out_is_honoured_only_when_the_phone_is_not_at_home_and_only_until_the_time() {
        val until = z(3)
        assertTrue(outActive(until, z(1), atHome = false))
        assertFalse(outActive(until, z(1), atHome = true), "at home the claim fails")
        assertFalse(outActive(until, z(1), atHome = null), "an unknown place opens nothing")
        assertFalse(outActive(until, z(3, 1), atHome = false), "after the time it ends")
        assertFalse(outActive(null, z(1), atHome = false))
    }

    @Test fun EM2_out_lifts_the_night_block_away_from_home_and_not_at_home() {
        val list = setOf("com.instagram.android")
        val night = z(2)
        assertTrue(decide("com.instagram.android", night, pattern, list) is Decision.Block, "the night rule closes it")
        assertEquals(Decision.Allow, decide("com.instagram.android", night, pattern, list, outUntil = z(4), atHome = false))
        assertTrue(decide("com.instagram.android", night, pattern, list, outUntil = z(4), atHome = true) is Decision.Block)
        assertTrue(decide("com.instagram.android", z(4, 30), pattern, list, outUntil = z(4), atHome = false) is Decision.Block, "after the time it is closed again")
    }

    @Test fun EM3_the_existing_override_is_unchanged_and_still_opens_a_night_for_its_allowance() {
        val list = setOf("com.instagram.android")
        val allow = Allowances(nightUntil = z(2, 30))
        assertEquals(Decision.Allow, decide("com.instagram.android", z(2, 10), pattern, list, allow))
        assertTrue(decide("com.instagram.android", z(2, 40), pattern, list, allow) is Decision.Block)
    }

    @Test fun EM4_EM5_the_lost_phone_screen_says_how_to_find_it_restore_and_set_the_permissions() {
        assertTrue(LOST_PHONE_STEPS.size >= 5)
        assertTrue(LOST_PHONE_STEPS.any { it.contains("Find My Device") }); assertTrue(LOST_PHONE_STEPS.any { it.contains("Restore from a backup file") })
        assertEquals(emptyList(), LOST_PHONE_STEPS.flatMap { voiceProblems(it) })
    }

    @Test fun CO2_a_prompt_goes_haptic_then_spoken_then_a_notification_then_the_gate_and_stops_there() {
        assertEquals(PromptLevel.HAPTIC, promptLevel(0)); assertEquals(PromptLevel.SPOKEN, promptLevel(1)); assertEquals(PromptLevel.NOTIFICATION, promptLevel(2))
        assertEquals(PromptLevel.GATE, promptLevel(3)); assertEquals(PromptLevel.GATE, promptLevel(40)); assertEquals(PromptLevel.HAPTIC, promptLevel(-2))
    }

    @Test fun CO3_every_haptic_has_a_meaning_and_a_distinct_pattern() {
        assertEquals(6, Haptic.entries.size)
        assertEquals(Haptic.entries.size, Haptic.entries.map { it.pattern.toList() }.toSet().size, "no two patterns are the same")
        for (h in Haptic.entries) { assertTrue(h.meaning.isNotBlank()); assertEquals(0L, h.pattern.first()); assertTrue(h.pattern.sum() < 2000, "short enough to feel, not to annoy") }
    }

    @Test fun SL7_the_nap_is_offered_once_after_a_night_under_four_hours_and_only_before_15_00() {
        assertTrue(napOffer(3.5, z(11))); assertFalse(napOffer(3.5, z(15))); assertFalse(napOffer(5.0, z(11))); assertFalse(napOffer(null, z(11)))
    }

    @Test fun SL4_tomorrows_fun_becomes_a_real_slot_tomorrow_at_1700() {
        val e = tomorrowsFun("Game night with friends", LocalDate.of(2026, 11, 19))
        assertEquals(Preset.ADD_EVENT, e.preset); assertEquals(LocalDate.of(2026, 11, 20), e.start)
        assertEquals("17:00", e.str("start")); assertEquals("Game night with friends", e.str("title"))
        assertEquals("Something fun", tomorrowsFun("  ", LocalDate.of(2026, 11, 19)).str("title"))
    }

    @Test fun SL6_your_own_reminders_round_trip_are_sorted_and_the_next_one_is_found_today_or_tomorrow() {
        val l = listOf(Reminder("Melatonin", LocalTime.of(1, 30)), Reminder("Morning tablet", LocalTime.of(8, 0)))
        assertEquals(l, decodeReminders(encodeReminders(l)))
        assertEquals("Morning tablet", nextReminder(l, z(7))!!.first.name)
        val next = nextReminder(l, z(9))!!
        assertEquals("Melatonin", next.first.name); assertEquals(z(1, 30, 20), next.second)
        assertNull(nextReminder(emptyList(), z(9))); assertTrue(decodeReminders("garbage").isEmpty())
        assertEquals(1, decodeReminders(encodeReminders(listOf(Reminder("A|B", LocalTime.of(9, 0))))).size, "a bar in a name cannot break the line")
    }

    @Test fun HO4_the_support_card_has_the_four_routes_with_the_rules_that_matter() {
        assertEquals(listOf("Coursework extension", "Extenuating circumstances", "Reasonable Adjustment Plan", "Your GP and NHS 111"), SUPPORT_ROUTES.map { it.name })
        assertTrue(SUPPORT_ROUTES[0].what.contains("Not for exams")); assertTrue(SUPPORT_ROUTES[0].when_.contains("Before"))
        assertTrue(SUPPORT_ROUTES[1].when_.contains("After")); assertTrue(SUPPORT_ROUTES[2].what.contains("0121 414 5130"))
    }

    @Test fun CX1_one_context_record_holds_where_and_what_state_and_is_the_same_each_time_it_is_asked() {
        val a = lifeContext(LocalDate.of(2026, 11, 19), z(11), PlaceKind.FLAT, pattern, 3, lite = false, outUntil = null, atHome = true)
        assertEquals(PlaceKind.FLAT, a.place); assertEquals(HomeMode.BLOCK, a.mode); assertEquals(Phase.OBSERVE, a.phase); assertFalse(a.out); assertFalse(a.lite)
        assertEquals(a, lifeContext(LocalDate.of(2026, 11, 19), z(11), PlaceKind.FLAT, pattern, 3, lite = false, outUntil = null, atHome = true))
        val out = lifeContext(LocalDate.of(2026, 11, 19), z(2), PlaceKind.FLAT, pattern, 20, lite = true, outUntil = z(4), atHome = false)
        assertTrue(out.out && out.lite); assertEquals(Phase.ENFORCE, out.phase); assertEquals(HomeMode.NIGHT, out.mode)
    }

    @Test fun CE1_a_modifier_that_reports_what_happened_is_reality_one_that_loosens_is_a_loosening_the_rest_add() {
        assertEquals(ChangeClass.REALITY, changeClassOfPreset(Preset.LATE_START, false)); assertEquals(ChangeClass.REALITY, changeClassOfPreset(Preset.NIGHT, false))
        assertEquals(ChangeClass.LOOSEN, changeClassOfPreset(Preset.PERIOD_OFF, true)); assertEquals(ChangeClass.LOOSEN, changeClassOfPreset(Preset.LATE_START, true))
        assertEquals(ChangeClass.ADD, changeClassOfPreset(Preset.ASSIGNMENT, false)); assertEquals(ChangeClass.ADD, changeClassOfPreset(Preset.GUESTS, false))
    }

    @Test fun DP3_DP4_every_door_use_prices_in_one_pool_so_a_night_override_and_a_day_off_count_together() {
        val t = Instant.now()
        val uses = listOf(obligationDoor(Door.DAY_OFF, t, 360), gateDoor(Door.OVERRIDE, t, 45), gateDoor(Door.OVERRIDE, t, 45))
        val s = spentInWindow(uses, t)
        assertEquals(360, s.obligationMin); assertEquals(90, s.gateMin)
        val big = listOf(obligationDoor(Door.DAY_OFF, t, 360), obligationDoor(Door.DAY_OFF, t, 240))
        assertTrue(doorDecision(big, gateDoor(Door.OVERRIDE, t, 30), t) is DoorDecision.Open, "gate minutes are a separate unit")
        assertTrue(doorDecision(big, obligationDoor(Door.DAY_OFF, t, 60), t) is DoorDecision.Locked)
    }

    @Test fun CT3_the_build_records_the_signing_key_and_refuses_a_different_one() {
        val gradle = java.io.File("../app/build.gradle.kts").takeIf { it.exists() } ?: java.io.File("app/build.gradle.kts")
        val fp = java.io.File(gradle.parentFile, "signing-fingerprint.txt")
        assertTrue(gradle.readText().contains("verifySigning") && gradle.readText().contains("finalizedBy(verifySigning)"), "the guard is wired to assembleDebug")
        assertTrue(fp.exists() && Regex("[0-9A-F]{64}").matches(fp.readText().trim()), "the fingerprint of the key is recorded")
        assertTrue(gradle.readText().contains("An update would not install over the old Spark"))
    }
}
