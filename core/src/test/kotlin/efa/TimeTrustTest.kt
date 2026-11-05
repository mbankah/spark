package efa

import kotlin.test.*

class TimeTrustTest {
    private val MIN = 60_000L

    @Test fun G10_a_clock_set_forward_is_caught_by_comparing_with_the_elapsed_counter() {
        val w = ClockWatch()
        assertNull(w.observe(1_000_000, 5_000, 1))
        val j = w.observe(1_000_000 + 60 * MIN + 60_000, 5_000 + 60_000, 1)            // a minute passed, the clock says an hour more
        assertNotNull(j); assertEquals("clock set forward", j.kind); assertEquals(60 * MIN, j.driftMs)
    }

    @Test fun G10_E14_a_clock_set_back_to_beat_a_cutoff_is_caught_too() {
        val w = ClockWatch(); w.observe(10_000_000, 1_000, 1)
        val j = w.observe(10_000_000 - 3 * 60 * MIN, 1_000 + 30_000, 1)
        assertEquals("clock set back", j?.kind); assertTrue(j!!.driftMs < 0)
    }

    @Test fun G10_ordinary_ticks_and_small_adjustments_are_not_tampering() {
        val w = ClockWatch(); w.observe(0, 0, 1)
        assertNull(w.observe(60_000, 60_000, 1)); assertNull(w.observe(120_000 + 90_000, 120_000, 1))      // 90 s of automatic time sync is inside the tolerance
    }

    @Test fun X4_a_reboot_starts_a_new_comparison_and_is_never_tampering() {
        val w = ClockWatch(); w.observe(5_000_000, 4_000_000, 1)
        assertNull(w.observe(5_000_000 + 10 * MIN, 30_000, 2))                          // the counter restarted, so the boot id changed
        assertNotNull(w.observe(5_000_000 + 10 * MIN + 5 * 60 * MIN, 30_000 + 60_000, 2))
    }

    @Test fun G10_the_clock_going_back_an_hour_for_daylight_saving_is_not_a_wall_clock_change() {
        // The wall clock is a UTC instant, so the clocks going back (X4) does not move it. Only a setting change does.
        val w = ClockWatch(); w.observe(1_000_000_000, 0, 1); assertNull(w.observe(1_000_000_000 + 3_600_000, 3_600_000, 1))
    }

    private fun reply(t2: Long, t3: Long, mode: Int = 4, stratum: Int = 2): ByteArray {
        val b = ByteArray(48); b[0] = mode.toByte(); b[1] = stratum.toByte()
        fun put(at: Int, ms: Long) { val sec = ms / 1000 + 2_208_988_800L; val frac = ((ms % 1000) shl 32) / 1000; for (i in 0 until 4) b[at + i] = (sec shr (8 * (3 - i))).toByte(); for (i in 0 until 4) b[at + 4 + i] = (frac shr (8 * (3 - i))).toByte() }
        put(32, t2); put(40, t3); return b
    }

    @Test fun G10_the_network_offset_is_read_from_an_sntp_reply() {
        // The phone's clock is 5 minutes behind the server's: the server received at server time T+5min when the phone said T.
        val t = 1_800_000_000_000L; val off = 5 * MIN
        assertTrue(kotlin.math.abs(ntpOffsetMs(reply(t + off + 20, t + off + 30), t, t + 50)!! - off) <= 1)
    }

    @Test fun G10_a_reply_that_is_not_an_answer_is_not_used() {
        assertNull(ntpOffsetMs(ByteArray(10), 0, 0)); assertNull(ntpOffsetMs(reply(1, 1, mode = 3), 0, 0)); assertNull(ntpOffsetMs(reply(1, 1, stratum = 0), 0, 0)); assertNull(ntpOffsetMs(ByteArray(48), 0, 0))
    }

    @Test fun G10_the_request_is_a_client_packet() { assertEquals(48, ntpRequest().size); assertEquals(0x1B, ntpRequest()[0].toInt()) }

    @Test fun G10_the_correction_applies_only_when_the_phone_is_more_than_two_minutes_out() {
        assertEquals(0, correctionMs(null)); assertEquals(0, correctionMs(90_000)); assertEquals(0, correctionMs(-90_000))
        assertEquals(10 * MIN, correctionMs(10 * MIN)); assertEquals(-3 * 60 * MIN, correctionMs(-3 * 60 * MIN))
    }
}
