package efa

import java.time.Instant
import kotlin.test.*

class HealthSleepTest {
    private fun i(s: String) = Instant.parse(s)
    private val nightStart = i("2026-11-02T23:30:00Z"); private val nightEnd = i("2026-11-03T07:30:00Z")
    private fun s(a: String, b: String, asleep: Int) = HealthSession(i(a), i(b), asleep)

    @Test fun S6_the_watch_session_that_covers_the_phones_night_is_chosen() {
        val night = s("2026-11-02T23:45:00Z", "2026-11-03T07:20:00Z", 420); val nap = s("2026-11-03T13:00:00Z", "2026-11-03T14:00:00Z", 55)
        assertEquals(night, sessionForNight(listOf(nap, night), nightStart, nightEnd))
    }

    @Test fun S6_a_session_from_another_night_is_not_used() =
        assertNull(sessionForNight(listOf(s("2026-11-01T23:00:00Z", "2026-11-02T07:00:00Z", 400)), nightStart, nightEnd))

    @Test fun S6_the_longest_overlap_wins_when_there_are_two() {
        val a = s("2026-11-02T22:00:00Z", "2026-11-03T00:30:00Z", 140); val b = s("2026-11-03T00:45:00Z", "2026-11-03T07:00:00Z", 360)
        assertEquals(b, sessionForNight(listOf(a, b), nightStart, nightEnd))
    }

    @Test fun S6_the_line_shows_both_numbers_and_says_when_they_are_far_apart() {
        assertEquals("Your watch says 7h 0m. The phone says 6h 50m.", healthLine(410, s("2026-11-02T23:45:00Z", "2026-11-03T07:00:00Z", 420)))
        assertTrue(healthLine(300, s("2026-11-02T23:45:00Z", "2026-11-03T07:00:00Z", 420))!!.contains("They differ by 2h 0m"))
    }

    @Test fun X7_no_watch_no_line() = assertNull(healthLine(400, null))
}
