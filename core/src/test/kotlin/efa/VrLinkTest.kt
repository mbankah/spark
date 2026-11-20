package efa

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

/** VR16: a cycle is linked to its block. */
class VrLinkTest {
    private fun ms(h: Int, m: Int) = ZonedDateTime.of(LocalDate.of(2026, 11, 20), LocalTime.of(h, m), LONDON).toInstant().toEpochMilli()

    @Test fun VR16_the_link_says_the_blocks_hours_and_how_far_through_it_is() {
        val s = ms(10, 0); val e = ms(12, 30)
        assertEquals(VrBlockLink("10:00 to 12:30", "The block ends in 90 min", false), vrBlockLink(s, e, ms(11, 0)))
        assertEquals("The block ends in 2h 30min", vrBlockLink(s, e, ms(10, 0)).status); assertEquals("The block ends in 2h", vrBlockLink(s, e, ms(10, 30)).status)
        assertEquals("The block ends in 5 min", vrBlockLink(s, e, ms(12, 25)).status)
        assertEquals("The block starts in 10 min", vrBlockLink(s, e, ms(9, 50)).status)
    }

    @Test fun VR16_once_the_block_has_ended_the_link_says_so_and_how_long_ago() {
        val l = vrBlockLink(ms(10, 0), ms(12, 30), ms(12, 45))
        assertTrue(l.ended); assertEquals("The block ended at 12:30, 15 min ago", l.status)
        assertFalse(vrBlockLink(ms(10, 0), ms(12, 30), ms(12, 29)).ended)
        assertEquals("The block ended at 12:30, 5 days ago", vrBlockLink(ms(10, 0), ms(12, 30), ms(12, 30) + 5 * 86_400_000L + 3_600_000L).status)
    }

    @Test fun VR16_the_current_cycle_is_the_one_in_the_block_that_is_on_now_else_the_one_that_ended_last() {
        val old = VrCandidate(ms(8, 0), ms(10, 0)); val now = VrCandidate(ms(10, 30), ms(12, 30)); val older = VrCandidate(ms(6, 0), ms(7, 0))
        assertEquals(1, vrCurrent(listOf(old, now, older), ms(11, 0)), "the block that is on now")
        assertEquals(0, vrCurrent(listOf(old, older), ms(10, 15)), "none on now: the one that ended last")
        assertNull(vrCurrent(emptyList(), ms(11, 0)))
        assertEquals(1, vrCurrent(listOf(VrCandidate(ms(10, 0), ms(12, 0)), VrCandidate(ms(11, 0), ms(13, 0))), ms(11, 30)), "two on now: the later one")
    }
}
