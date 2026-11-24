package efa

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResetDayTest {
    private val now = Instant.parse("2026-11-24T05:40:00Z")
    private fun ago(days: Long) = now.minus(Duration.ofDays(days)).toEpochMilli()

    @Test fun `NA2 one reset a week, the next after seven days`() {
        assertTrue(resetAvailable(emptyList(), now)); assertNull(resetNextAt(emptyList(), now))
        val used = listOf(ago(2))
        assertFalse(resetAvailable(used, now))
        assertEquals(Instant.ofEpochMilli(ago(2)).plus(Duration.ofDays(7)), resetNextAt(used, now))
        assertTrue(resetAvailable(listOf(ago(8)), now), "a reset eight days ago is past the week")
    }

    @Test fun `NA2 the wall grows by 25 for each reset in the last 28 days`() {
        assertEquals(75, resetWallLength(emptyList(), now))
        assertEquals(100, resetWallLength(listOf(ago(10)), now))
        assertEquals(125, resetWallLength(listOf(ago(10), ago(20)), now))
        assertEquals(100, resetWallLength(listOf(ago(10), ago(40)), now), "a reset older than 28 days does not count")
    }

    @Test fun `NA2 the log round trips and a reset in the future is not counted`() {
        val log = listOf(ago(3), ago(9))
        assertEquals(log, decodeResetLog(encodeResetLog(log)))
        assertEquals(0, resetUses(listOf(now.plusSeconds(60).toEpochMilli()), now, 7))
        assertEquals(emptyList(), decodeResetLog(null))
    }
}
