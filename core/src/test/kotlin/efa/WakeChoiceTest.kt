package efa

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

/** CW1: a chosen wake moves the morning. */
class WakeChoiceTest {
    private fun at(h: Int, m: Int): ZonedDateTime = ZonedDateTime.of(LocalDate.of(2026, 11, 28), LocalTime.of(h, m), LONDON)

    @Test fun CW1_a_wake_later_than_the_Wake_block_is_a_Late_start_for_that_date_tagged_as_the_wake_choice() {
        val inst = wakeChoiceInstance(8 * 60, at(11, 0))!!
        assertEquals(Preset.LATE_START, inst.preset)
        assertEquals(LocalDate.of(2026, 11, 28), inst.start); assertEquals(inst.start, inst.end)
        assertEquals("11:00", inst.str("wake")); assertTrue(isWakeChoice(inst))
        assertTrue(validate(inst, LocalDate.of(2026, 11, 20)).isEmpty(), "it is a valid Late start")
    }

    @Test fun CW1_a_wake_that_is_not_later_changes_nothing_and_neither_does_a_day_with_no_Wake_block() {
        assertNull(wakeChoiceInstance(8 * 60, at(8, 0)))
        assertNull(wakeChoiceInstance(8 * 60, at(8, WAKE_CHOICE_SLACK_MIN)), "inside the slack it is the same morning")
        assertNull(wakeChoiceInstance(8 * 60, at(7, 0)), "an earlier alarm leaves the blocks where they are")
        assertNull(wakeChoiceInstance(null, at(11, 0)))
        assertNotNull(wakeChoiceInstance(8 * 60, at(8, WAKE_CHOICE_SLACK_MIN + 1)))
    }

    @Test fun CW1_only_the_tagged_Late_start_counts_as_a_wake_choice() {
        val own = Instance(Preset.LATE_START, LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 28), mapOf("wake" to "11:00"))
        assertFalse(isWakeChoice(own), "a Late start you made yourself is yours")
        assertFalse(isWakeChoice(null))
        assertFalse(isWakeChoice(own.copy(preset = Preset.RUN_OVER, settings = mapOf("src" to WAKE_CHOICE_SRC))))
    }

    @Test fun CW1_the_planned_wake_is_read_from_the_calendar_day() {
        val sat = LocalDate.of(2026, 11, 28)
        val m = plannedWakeMin(WeekB.slots, sat)
        assertNotNull(m, "a weekend has a Wake block")
        assertTrue(m in 4 * 60..14 * 60, "$m")
    }
}
