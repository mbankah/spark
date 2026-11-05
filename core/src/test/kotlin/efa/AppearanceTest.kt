package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class AppearanceTest {
    private fun t(h: Int, m: Int = 0, d: Int = 5) = at(LocalDate.of(2026, 11, d), LocalTime.of(h, m))

    @Test fun AP2_there_are_four_modes_and_auto() = assertEquals(listOf("Auto", "Light", "Dark", "Night", "Colourful"), AppearanceMode.entries.map { it.label })

    @Test fun AP8_text_has_a_contrast_of_at_least_4_5_to_1_in_the_colourful_mode_on_every_card() {
        val c = Colourful.tokens
        for ((kind, tint) in Colourful.tints) {
            assertTrue(contrast(c.text, tint) >= 4.5, "$kind text ${contrast(c.text, tint)}"); assertTrue(contrast(c.muted, tint) >= 4.5, "$kind muted ${contrast(c.muted, tint)}")
        }
        assertTrue(contrast(c.text, c.bg) >= 4.5 && contrast(c.text, c.card) >= 4.5 && contrast(c.muted, c.bg) >= 4.5)
        assertTrue(contrast(c.onAccent, c.accent) >= 4.5 && contrast(c.accentText, c.bg) >= 4.5)
    }

    @Test fun AP5_colourful_gives_the_four_cards_their_colours() {
        assertEquals(setOf(CardKind.BUFFER, CardKind.NOW, CardKind.NEXT, CardKind.SPEND), Colourful.tints.keys)
        assertEquals(4, Colourful.tints.values.toSet().size)
        assertEquals("#000000", Colourful.tokens.track)                                  // black outlines
    }

    @Test fun AP8_the_other_modes_keep_their_contrast() {
        for ((name, p) in Palettes.all) { assertTrue(contrast(p.text, p.bg) >= 4.5, "$name text"); assertTrue(contrast(p.muted, p.bg) >= 4.5 || name == "night", "$name muted") }
    }

    @Test fun AP4_auto_is_light_by_day_and_dark_in_the_evening() {
        assertEquals(AppearanceMode.LIGHT, autoMode(t(12), false)); assertEquals(AppearanceMode.LIGHT, autoMode(t(7), false)); assertEquals(AppearanceMode.DARK, autoMode(t(19), false)); assertEquals(AppearanceMode.DARK, autoMode(t(6, 59), false))
    }

    @Test fun AP4_auto_is_night_from_the_cutoff_until_the_alarm_is_stopped() {
        val w = NightWindow(t(0, 40), t(9, 0, 6))
        assertFalse(inNightMode(t(0, 39), w, null, null)); assertTrue(inNightMode(t(0, 40), w, null, null)); assertTrue(inNightMode(t(8, 0, 6), w, null, null))
        val stopped = t(8, 30, 6).toInstant().toEpochMilli()
        assertTrue(inNightMode(t(8, 29, 6), w, null, stopped)); assertFalse(inNightMode(t(8, 31, 6), w, null, stopped))
    }

    @Test fun AP4_the_shutdown_photo_starts_night_early_if_it_came_before_the_cutoff() {
        val w = NightWindow(t(0, 40, 6), t(9, 0, 6)); val shutdown = t(23, 10).toInstant().toEpochMilli()
        assertFalse(inNightMode(t(23, 9), w, shutdown, null)); assertTrue(inNightMode(t(23, 11), w, shutdown, null))
    }

    @Test fun AP4_a_night_with_no_stopped_alarm_still_ends_by_late_morning() {
        val w = NightWindow(t(0, 40), t(9, 0))
        assertTrue(inNightMode(t(14, 0), w, null, null)); assertFalse(inNightMode(t(15, 30), w, null, null))
    }

    @Test fun X4_the_clocks_going_back_do_not_confuse_the_night_window() {
        val oct = { h: Int, m: Int -> at(LocalDate.of(2026, 10, 25), LocalTime.of(h, m)) }
        assertTrue(inNightMode(oct(1, 30), NightWindow(oct(0, 40), at(LocalDate.of(2026, 10, 25), LocalTime.of(9, 0))), null, null))
    }

    @Test fun AP3_night_caps_the_brightness_at_about_a_fifth() = assertEquals(0.2f, NIGHT_BRIGHTNESS_CAP)
}
