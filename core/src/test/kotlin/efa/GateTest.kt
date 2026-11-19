package efa

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

/** GT1, GT2: one gate, with the WhatsApp cap. MX1 to MX3: five effects, recipes on them. */
class GateTest {
    private val zone = ZoneId.of("Europe/London")
    private val noon = ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, zone)
    private val wa = "com.whatsapp"
    private fun decideWa(now: ZonedDateTime, start: ZonedDateTime?, call: Boolean = false, allow: Allowances = Allowances()) =
        decide(wa, now, emptyList(), emptySet(), allow, essentials = setOf(wa), cap = AppCap(), capStart = if (start == null) emptyMap() else mapOf(wa to start), inCall = call)

    @Test fun GT2_whatsapp_is_open_for_ten_minutes_then_a_thirty_minute_cooldown_then_free_again() {
        assertEquals(Decision.Allow, decideWa(noon.plusMinutes(9), noon))
        val d = decideWa(noon.plusMinutes(10), noon) as Decision.Block
        assertEquals(BlockReason.APP_CAP, d.reason); assertEquals(noon.plusMinutes(40), d.until)
        assertTrue(decideWa(noon.plusMinutes(39), noon) is Decision.Block)
        assertEquals(Decision.Allow, decideWa(noon.plusMinutes(40), noon), "after the cooldown it is free; the next open starts a new window")
    }

    @Test fun GT2_nothing_is_blocked_before_the_first_open() {
        assertEquals(Decision.Allow, decideWa(noon, null))
        assertEquals(CapPhase.FREE, capStatus(null, noon).phase)
    }

    @Test fun GT2_an_active_call_is_never_cut_off_even_in_the_cooldown() {
        assertEquals(Decision.Allow, decideWa(noon.plusMinutes(20), noon, call = true))
    }

    @Test fun GT2_only_the_wall_ends_a_cooldown_early_and_it_opens_that_app_for_the_allowance() {
        val passed = Allowances(appUntil = mapOf(wa to noon.plusMinutes(50)))
        assertEquals(Decision.Allow, decideWa(noon.plusMinutes(20), noon, allow = passed))
    }

    @Test fun GT2_other_apps_and_sms_are_not_capped() {
        val d = decide("com.google.android.apps.messaging", noon.plusMinutes(20), emptyList(), emptySet(), cap = AppCap(), capStart = mapOf(wa to noon))
        assertEquals(Decision.Allow, d)
    }

    @Test fun GT2_the_cap_is_off_when_no_cap_is_given_so_existing_behaviour_is_unchanged() {
        assertEquals(Decision.Allow, decide(wa, noon.plusMinutes(20), emptyList(), emptySet(), essentials = setOf(wa)))
    }

    @Test fun GT2_the_window_and_cooldown_lengths_are_settings() {
        val c = AppCap(windowMin = 5, cooldownMin = 60)
        assertEquals(CapPhase.OPEN, capStatus(noon, noon.plusMinutes(4), c).phase)
        assertEquals(CapPhase.COOLDOWN, capStatus(noon, noon.plusMinutes(64), c).phase)
        assertEquals(CapPhase.FREE, capStatus(noon, noon.plusMinutes(65), c).phase)
    }

    @Test fun MX1_every_preset_is_made_of_effects_and_all_five_effects_lead_some_preset() {
        for (p in Preset.entries) assertTrue(effectsOf(p).isNotEmpty(), "${p.id} has an effect")
        for (e in Effect.entries) assertTrue(presetsFor(e).isNotEmpty(), "${e.name} leads at least one recipe")
    }

    @Test fun MX2_the_thirteen_presets_stay_as_named_recipes_and_the_night_is_the_fourteenth() {
        assertEquals(14, Preset.entries.size)
        assertEquals(Effect.ANCHOR, effectsOf(Preset.NIGHT).first()); assertEquals(Effect.ANCHOR, effectsOf(Preset.LATE_START).first())
        assertEquals(Effect.DEMAND, effectsOf(Preset.CATCH_UP).first()); assertEquals(Effect.SUPPLY, effectsOf(Preset.RECOVERY).first())
        assertEquals(Effect.EVENT, effectsOf(Preset.GUESTS).first()); assertEquals(Effect.POLICY, effectsOf(Preset.LECTURE_FREE).first())
    }

    @Test fun MX3_an_effect_leads_to_the_recipes_that_do_it() {
        assertTrue(presetsFor(Effect.SUPPLY).containsAll(listOf(Preset.RECOVERY, Preset.PERIOD_OFF, Preset.AWAY)))
        assertTrue(presetsFor(Effect.ANCHOR).containsAll(listOf(Preset.LATE_START, Preset.RUN_OVER, Preset.NIGHT)))
        assertEquals(Preset.entries.toSet(), Effect.entries.flatMap { presetsFor(it) }.toSet(), "every recipe is reachable from one effect")
    }

    @Test fun MX5_stored_instances_still_decode_and_the_night_round_trips() {
        val d = java.time.LocalDate.of(2026, 11, 20)
        val night = Instance(Preset.NIGHT, d, d, mapOf("lights_out" to "02:40", "wake" to "11:00", "winddown_min" to "10"))
        assertEquals(night, decodeInstance(encodeInstance(night)))
        val old = Instance(Preset.ADD_EVENT, d, d, mapOf("title" to "Dentist", "type" to "errand", "start" to "10:00", "end" to "11:00"))
        val back = decodeInstance(encodeInstance(old))!!
        assertFalse(back.bool("after_midnight"), "an event stored before the setting existed is not after midnight")
        assertEquals(java.time.LocalDate.of(2026, 11, 21), nightAnchorsOf(night)!!.wakeDate)
    }
}
