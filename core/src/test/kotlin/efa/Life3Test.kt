package efa

import java.time.LocalDate
import kotlin.test.*

/** CO10, CO11, DP2: tone, identity and what a modifier costs. */
class Life3Test {
    @Test fun CO11_the_tone_changes_the_style_and_never_the_content() {
        val t = "Not proven. No reward. Next: study 10:10."
        assertEquals(t, toneLine(t, Tone.CALM))
        assertEquals("Not proven.", toneLine(t, Tone.PLAIN))
        assertEquals("$t Easy does it.", toneLine(t, Tone.LIGHT))
        assertEquals(emptyList(), voiceProblems(toneLine(t, Tone.LIGHT)))
        assertEquals(Tone.CALM, Tone.of(null)); assertEquals(Tone.PLAIN, Tone.of("PLAIN"))
    }

    @Test fun CO10_an_identity_sentence_is_quoted_back_only_with_a_fact() {
        val s = listOf("I keep my word to myself.", "I look after my body.", "")
        val line = identityLine(s, 2, 7)!!
        assertTrue(line.endsWith("That is 2 kept today.")); assertTrue(s.any { line.startsWith(it.trimEnd('.')) })
        assertNull(identityLine(s, 0, 7), "no fact, no quote"); assertNull(identityLine(emptyList(), 3, 7))
        assertEquals(line, identityLine(s, 2, 7))
    }

    @Test fun DP2_a_period_off_costs_four_hours_a_day_and_a_lighter_span_two_hours_a_day() {
        val d = LocalDate.of(2026, 12, 1)
        val off = Instance(Preset.PERIOD_OFF, d, d.plusDays(1))
        assertEquals(480, obligationMinutesOf(off, Compiled(dayOffDates = listOf(d, d.plusDays(1)))))
        assertTrue(480 <= PoolCaps().obligationMin, "a two day period off fits a fresh pool")
        assertEquals(360, obligationMinutesOf(Instance(Preset.RECOVERY, d, d.plusDays(2)), Compiled()))
        assertEquals(0, obligationMinutesOf(Instance(Preset.ASSIGNMENT, d, d), Compiled()))
        assertEquals(120, obligationMinutesOf(Instance(Preset.CANCEL, d, d), Compiled(loosening = listOf("Cancels a lecture"))))
    }
}
