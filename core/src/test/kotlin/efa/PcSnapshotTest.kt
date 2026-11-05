package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

class PcSnapshotTest {
    private val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(10, 0))
    private fun data(b: BufferState = BufferState.Value(BufferResult(24.0, emptyList(), false, "Wed 4 Nov 17:00")), pence: Int? = 1250) =
        WidgetData(b, "Lecture, until 12:00", listOf("12:30 Lunch", "13:00 \"Quoted\" lecture"), pence, null, 12, listOf("Mon 2: 3 from 11:00"), now)

    @Test fun A6_the_snapshot_carries_the_buffer_the_blocks_the_money_and_the_week_as_text() {
        val j = pcSnapshotJson(Instant.parse("2026-11-02T10:00:00Z"), data(), listOf("Essay 6.3h", "Total 6.3h"))
        assertTrue(j.startsWith("{\"v\":1,\"issuedAtUtc\":\"2026-11-02T10:00:00Z\",\"buffer\":\"Buffer +24.0h\",\"until\":\"Wed 4 Nov 17:00\",\"negative\":false"))
        assertTrue(j.contains("\"working\":[\"Essay 6.3h\",\"Total 6.3h\"]")); assertTrue(j.contains("\"money\":\"£12.50\"")); assertTrue(j.contains("\"shopping\":12"))
        assertTrue(j.contains("13:00 \\\"Quoted\\\" lecture"))
    }

    @Test fun G6_before_money_starts_the_dashboard_says_recording() = assertTrue(pcSnapshotJson(Instant.EPOCH, data(pence = null), emptyList()).contains("\"money\":\"Recording\""))

    @Test fun B1_a_negative_buffer_is_flagged_so_the_page_can_say_so() {
        val j = pcSnapshotJson(Instant.EPOCH, data(BufferState.Value(BufferResult(-2.8, emptyList(), false, "Wed 4 Nov 17:00"))), emptyList())
        assertTrue(j.contains("\"buffer\":\"Buffer -2.8h\"")); assertTrue(j.contains("\"negative\":true"))
    }

    @Test fun ST6_the_snapshot_has_no_photo_location_or_sensor_fields() {
        val j = pcSnapshotJson(Instant.EPOCH, data(), emptyList()).lowercase()
        listOf("photo", "lat", "lon", "location", "sensor", "thumbnail", "fingerprint").forEach { assertFalse(j.contains("\"$it"), it) }
    }

    @Test fun X7_no_deadline_and_no_data_are_said_plainly() {
        assertTrue(pcSnapshotJson(Instant.EPOCH, data(BufferState.NoDeadline), emptyList()).contains("\"buffer\":\"Buffer: no deadline\",\"until\":null"))
    }

    @Test fun A6_the_snapshot_is_signed_the_same_way_as_the_state() {
        val j = pcSnapshotJson(Instant.EPOCH, data(), emptyList())
        assertEquals(pcSignature("k", j), pcSignature("k", j)); assertNotEquals(pcSignature("k", j), pcSignature("other", j))
    }
}
