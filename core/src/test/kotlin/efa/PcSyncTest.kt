package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class PcSyncTest {
    private val slots = WeekB.slots
    private val list = setOf("com.google.android.youtube", "com.instagram.android")
    private val owner = "com.google.android.youtube"
    private val monday = LocalDate.of(2026, 11, 2)

    private fun decide(t: java.time.ZonedDateTime) = decide(owner, t, slots, list)

    @Test fun A4_the_signature_matches_the_one_the_windows_tests_expect() {
        val body = "{\"v\":1,\"issuedAtUtc\":\"2026-11-02T10:00:00Z\",\"domains\":[\"youtube.com\"],\"windows\":[]}"
        assertEquals("9718e6f8a4c623d0f0b007ca4b1015f3e9d133bc76f8054245a826b4f4d55052", pcSignature("spark-test-key", body))
    }

    @Test fun A4_the_state_is_the_json_the_service_reads() {
        val w = PcWindow(Instant.parse("2026-11-02T09:00:00Z"), Instant.parse("2026-11-02T12:00:00Z"), "Work block")
        assertEquals("{\"v\":1,\"issuedAtUtc\":\"2026-11-02T10:00:00Z\",\"domains\":[\"reddit.com\",\"youtube.com\"],\"windows\":[{\"start\":\"2026-11-02T09:00:00Z\",\"end\":\"2026-11-02T12:00:00Z\",\"why\":\"Work block\"}]}",
            pcStateJson(Instant.parse("2026-11-02T10:00:00Z"), listOf("youtube.com", "reddit.com"), listOf(w)))
    }

    @Test fun A4_quotes_and_control_characters_in_a_reason_cannot_break_the_json() {
        val j = pcStateJson(Instant.EPOCH, emptyList(), listOf(PcWindow(Instant.EPOCH, Instant.EPOCH.plusSeconds(60), "He said \"go\"\n\\")))
        assertTrue(j.contains("He said \\\"go\\\" \\\\")); assertFalse(j.contains('\n'))
    }

    @Test fun K4_the_pc_blocks_exactly_when_the_phone_blocks() {
        val from = at(monday, LocalTime.of(8, 0)); val to = from.plusHours(14)
        val windows = pcWindows(from, to) { decide(it) }
        assertTrue(windows.isNotEmpty())
        var t = from
        while (t.isBefore(to)) {
            val inWindow = windows.any { !t.toInstant().isBefore(it.start) && t.toInstant().isBefore(it.end) }
            assertEquals(decide(t) is Decision.Block, inWindow, "at $t"); t = t.plusMinutes(7)
        }
    }

    @Test fun K4_windows_do_not_overlap_and_each_one_has_a_reason() {
        val windows = pcWindows(at(monday, LocalTime.of(0, 0)), at(monday, LocalTime.of(0, 0)).plusHours(PC_HOURS_AHEAD)) { decide(it) }
        windows.zipWithNext().forEach { (a, b) -> assertTrue(!a.end.isAfter(b.start)) }
        assertTrue(windows.all { it.why.isNotBlank() && it.end.isAfter(it.start) })
    }

    @Test fun K3_lunch_and_lectures_are_not_in_a_window() {
        val lunch = at(monday, LocalTime.of(12, 20)).toInstant()
        val windows = pcWindows(at(monday, LocalTime.of(8, 0)), at(monday, LocalTime.of(20, 0))) { decide(it) }
        assertTrue(windows.none { !lunch.isBefore(it.start) && lunch.isBefore(it.end) })
    }

    @Test fun K1_an_empty_block_list_gives_no_windows_and_no_domains() {
        val none = pcWindows(at(monday, LocalTime.of(8, 0)), at(monday, LocalTime.of(20, 0))) { decide(owner, it, slots, emptySet()) }
        assertTrue(none.isEmpty()); assertTrue(pcDomains(emptySet()).isEmpty())
    }

    @Test fun K9_a_phone_allowance_does_not_open_the_pc() {
        val t = at(monday, LocalTime.of(15, 30))
        val allowed = Allowances(appUntil = mapOf(owner to t.plusMinutes(20)))
        assertEquals(Decision.Allow, decide(owner, t, slots, list, allowed))
        // The PC windows are built from the phone's plan without the allowance, so the PC stays blocked.
        val w = pcWindows(t.minusMinutes(5), t.plusMinutes(5)) { decide(owner, it, slots, list) }
        assertTrue(w.any { !t.toInstant().isBefore(it.start) && t.toInstant().isBefore(it.end) })
    }

    @Test fun K10_the_domains_sent_are_those_of_the_listed_apps() = assertEquals(setOf("youtube.com", "youtu.be", "instagram.com"), pcDomains(list))

    @Test fun A4_the_pc_address_is_an_ip_or_name_with_an_optional_port() {
        assertEquals(PcAddress("192.168.1.20", 47831), parsePcAddress("192.168.1.20")); assertEquals(PcAddress("desktop-1", 5000), parsePcAddress("http://desktop-1:5000/"))
        assertNull(parsePcAddress("")); assertNull(parsePcAddress("a b")); assertNull(parsePcAddress("host:99999")); assertNull(parsePcAddress("host/path")); assertNull(parsePcAddress("-bad-"))
    }

    @Test fun X7_the_status_line_says_what_happened() {
        assertEquals("Not sent yet.", pcStatusText(null, null, 1_000_000)); assertEquals("Sent 3 minutes ago.", pcStatusText(1_000_000 - 180_000, null, 1_000_000))
        assertTrue(pcStatusText(null, "no route", 5).startsWith("Not reached: no route"))
    }
}
