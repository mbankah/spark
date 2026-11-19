package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.*

/** PL1 to PL4: Away has a time zone and exempts place checks. */
class AwayTest {
    private val d = LocalDate.of(2026, 12, 20)
    private fun away(zone: String = "") = Instance(Preset.AWAY, d, d.plusDays(3), if (zone.isEmpty()) emptyMap() else mapOf("zone" to zone))

    @Test fun PL1_an_away_span_sets_the_zone_for_its_dates_and_london_elsewhere() {
        val a = away("America/New_York")
        assertEquals(ZoneId.of("America/New_York"), zoneFor(d.plusDays(1), listOf(a)))
        assertEquals(LONDON, zoneFor(d.minusDays(1), listOf(a)))
        assertEquals(LONDON, zoneFor(d.plusDays(1), listOf(away())), "a blank zone is London")
        assertEquals(LONDON, zoneFor(d.plusDays(1), listOf(away("Not/AZone"))), "a bad zone falls back to London and validate refuses it")
    }

    @Test fun PL1_a_zone_that_is_not_a_zone_is_refused_with_an_example() {
        val p = validate(away("Not/AZone"), d.minusDays(1))
        assertTrue(p.any { it.contains("America/New_York") }, p.toString())
        assertTrue(validate(away("Asia/Tokyo"), d.minusDays(1)).none { it.contains("zone") })
    }

    @Test fun PL2_place_checks_are_exempt_on_away_dates_and_the_exemption_is_said() {
        val a = away()
        assertTrue(placeChecksExempt(d, listOf(a))); assertTrue(placeChecksExempt(d.plusDays(3), listOf(a)))
        assertFalse(placeChecksExempt(d.plusDays(4), listOf(a)))
        assertNotNull(placeExemptionNote(d, listOf(a)))
        assertNull(placeExemptionNote(d.plusDays(5), listOf(a)))
    }

    @Test fun PL3_an_alarm_is_an_instant_so_it_does_not_move_when_the_zone_changes() {
        val inLondon = alarmInstant(d, LocalTime.of(8, 0), LONDON)
        val inNy = alarmInstant(d, LocalTime.of(8, 0), ZoneId.of("America/New_York"))
        assertEquals(Instant.parse("2026-12-20T08:00:00Z"), inLondon)
        assertEquals(Instant.parse("2026-12-20T13:00:00Z"), inNy)
        assertEquals(inLondon, inLondon.atZone(ZoneId.of("Asia/Tokyo")).toInstant(), "the instant is the same whatever zone shows it")
    }

    @Test fun PL4_the_clock_change_rule_holds_in_every_zone() {
        assertEquals(1500, dayLengthIn(LocalDate.of(2026, 11, 1), ZoneId.of("America/New_York")))      // US clocks go back on 1 Nov 2026
        assertEquals(1500, dayLengthIn(LocalDate.of(2026, 10, 25), LONDON))
        assertEquals(1440, dayLengthIn(LocalDate.of(2026, 12, 20), ZoneId.of("Asia/Tokyo")))
    }
}
