package efa

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

class NotificationsTest {
    private val p = WeekB.slots
    private val list = setOf("com.instagram.android", "com.google.android.youtube")
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun dec(pkg: String, now: ZonedDateTime) = decide(pkg, now, p, list)
    private fun inc(pkg: String, category: String? = null, ongoing: Boolean = false, summary: Boolean = false) = Incoming(pkg, category, ongoing, summary)

    // ---- U8 ----
    @Test fun `U8 a notification from a blocked app is held while its block is on`() {
        assertTrue(shouldHold(inc("com.instagram.android"), dec("com.instagram.android", t(19, 10, 30))))
    }

    @Test fun `U8 a notification from an app that is not blocked comes through normally`() {
        assertFalse(shouldHold(inc("com.whatsapp"), dec("com.whatsapp", t(19, 10, 30))))
        assertFalse(shouldHold(inc("com.google.android.apps.messaging"), dec("com.google.android.apps.messaging", t(19, 10, 30))))
    }

    @Test fun `U8 a listed app is not held when the phone is open (a lecture or free time)`() {
        assertFalse(shouldHold(inc("com.instagram.android"), dec("com.instagram.android", t(19, 9, 30))))
        assertFalse(shouldHold(inc("com.instagram.android"), dec("com.instagram.android", t(19, 21, 0))))
    }

    @Test fun `U8 an app opened through the wall is not held for 30 minutes`() {
        val now = t(19, 10, 30)
        val allow = Allowances(appUntil = mapOf("com.instagram.android" to now.plusMinutes(30)))
        assertFalse(shouldHold(inc("com.instagram.android"), decide("com.instagram.android", now, p, list, allow)))
    }

    @Test fun `LA1 calls missed calls and alarms are never held`() {
        val d = dec("com.instagram.android", t(19, 10, 30))
        for (cat in listOf("call", "missed_call", "alarm")) assertFalse(shouldHold(inc("com.instagram.android", category = cat), d), cat)
    }

    @Test fun `LA1 authenticator banking and MyUoB packages are never held even if listed`() {
        val listed = setOf("com.google.android.apps.authenticator2", "com.monzo.android", "uk.ac.bham.myuob")
        val now = t(19, 10, 30)
        for (pkg in listed) assertFalse(shouldHold(inc(pkg), decide(pkg, now, p, listed)), pkg)
    }

    @Test fun `U8 ongoing notifications and group summaries are left alone`() {
        val d = dec("com.instagram.android", t(19, 10, 30))
        assertFalse(shouldHold(inc("com.instagram.android", ongoing = true), d))
        assertFalse(shouldHold(inc("com.instagram.android", summary = true), d))
    }

    // ---- LA2 ----
    private fun item(id: Long, pkg: String, atMs: Long) = HeldItem(id, pkg, pkg.split('.')[1], "title$id", "text$id", atMs)

    @Test fun `LA2 the digest is in time order with the app name`() {
        val now = t(19, 21, 0)                                          // free time: nothing blocked
        val items = listOf(item(3, "com.google.android.youtube", 3_000), item(1, "com.instagram.android", 1_000), item(2, "com.instagram.android", 2_000))
        val d = digest(items) { dec(it, now) }
        assertEquals(listOf(1L, 2L, 3L), d.ready.map { it.id })
        assertNull(d.waitingUntil)
        assertTrue(d.ready[0].line().contains("instagram: title1 - text1"), "line was: " + d.ready[0].line())
    }

    @Test fun `LA2 an item whose app is still blocked waits and the digest says until when`() {
        val now = t(19, 10, 30)                                         // still in the work block until 12:15
        val d = digest(listOf(item(1, "com.instagram.android", 1_000))) { dec(it, now) }
        assertTrue(d.ready.isEmpty()); assertEquals(t(19, 12, 15), d.waitingUntil)
    }

    @Test fun `LA2 nothing is lost between the hold and the digest`() {
        val items = (1L..200L).map { item(it, if (it % 2 == 0L) "com.instagram.android" else "com.google.android.youtube", it * 1_000) }
        val open = digest(items) { dec(it, t(19, 21, 0)) }
        assertEquals(200, open.ready.size)
        assertEquals((1L..200L).toList(), open.ready.map { it.id })
        val shut = digest(items) { dec(it, t(19, 10, 30)) }
        assertEquals(0, shut.ready.size + (if (shut.waitingUntil == null) 200 else 0))
        assertNotNull(shut.waitingUntil)
    }

    @Test fun `WG7 the digest carries no count`() {
        val items = listOf(item(1, "com.instagram.android", 1_000))
        val text = digest(items) { Decision.Allow }.toString()
        assertFalse(Regex("(?i)count|badge").containsMatchIn(text))
    }

    // ---- B2, LA11 ----
    @Test fun `B2 the persistent notice shows the Buffer`() {
        val r = BufferResult(17.5, listOf(WorkingLine("a", 5, 13.0, 1.0, 13.0)), false, "Sat 24 Oct 00:10")
        val n = bufferNotice(BufferState.Value(r), "Notes and problems, until 12:15", "12:15 Lunch", "14:02")
        assertEquals("Buffer +4.5h", n.title)
        assertEquals("To Sat 24 Oct 00:10", n.text)
        assertTrue("Now: Notes and problems, until 12:15" in n.big && "Next: 12:15 Lunch" in n.big && "Updated 14:02" in n.big)
    }

    @Test fun `AP6 a negative Buffer says NEGATIVE in the notice`() {
        val r = BufferResult(1.0, listOf(WorkingLine("a", 5, 5.0, 1.0, 5.0)), false, "Mon 19 Oct 19:00")
        assertEquals("Buffer NEGATIVE -4.0h", bufferNotice(BufferState.Value(r), null, null, "09:00").title)
    }

    @Test fun `D6 with no deadline the notice says so and never a wrong number`() {
        val n = bufferNotice(BufferState.NoDeadline, null, null, "09:00")
        assertEquals("Buffer: no deadline", n.title); assertFalse(Regex("[+-]\\d").containsMatchIn(n.title))
    }

    @Test fun `WG9 every notice says when it was last updated so a stale one is never taken as current`() {
        assertTrue("Updated 09:00" in bufferNotice(BufferState.NoDeadline, "x", "y", "09:00").big)
    }

    @Test fun `P6 the notice has no motivational wording and no held count`() {
        val r = BufferResult(10.0, listOf(WorkingLine("a", 5, 5.0, 1.0, 5.0)), false, "Mon 19 Oct 19:00")
        val n = bufferNotice(BufferState.Value(r), "Now", "Next", "09:00")
        val all = (n.title + n.text + n.big).lowercase()
        for (w in listOf("goal", "streak", "you can", "keep it up", "held", "waiting")) assertFalse(w in all, w)
    }
}
