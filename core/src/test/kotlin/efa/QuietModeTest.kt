package efa

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** QM1: quiet mode holds every notification except essentials in a work block and at night, without breaking what the app and the phone need. */
class QuietModeTest {
    private val slots = WeekB.slots
    private val mon = LocalDate.of(2026, 11, 2)
    private val work get() = slotsFor(slots, mon).first { it.slot.type.startsWith("study") }
    private val allow = Decision.Allow
    private fun n(pkg: String, category: String? = null, ongoing: Boolean = false, summary: Boolean = false) = Incoming(pkg, category, ongoing, summary)

    @Test fun `QM1 with quiet mode on, an app that is not blocked is held too, and with it off it comes through as before`() {
        assertTrue(shouldHold(n("com.example.shop"), allow, quiet = true))
        assertFalse(shouldHold(n("com.example.shop"), allow, quiet = false), "U8 is unchanged with quiet mode off")
        assertFalse(shouldHold(n("com.example.shop"), allow))
        assertTrue(shouldHold(n("com.example.shop"), Decision.Block(BlockReason.WORK_BLOCK, "x", at(mon, LocalTime.of(12, 0)), 0, false)), "a blocked app is held as before")
    }

    @Test fun `QM1 an essential is never held, whatever quiet mode says`() {
        val essentials = ESSENTIAL_CORE + "com.example.bank2" + "com.example.maps"
        assertFalse(shouldHold(n("com.example.maps"), allow, quiet = true, essentials = essentials))
        assertFalse(shouldHold(n("com.example.bank2"), allow, quiet = true, essentials = essentials))
        for (e in ESSENTIAL_CORE) assertFalse(shouldHold(n(e), allow, quiet = true), e)
    }

    @Test fun `QM1 the phone's own packages and what the phone needs to work are never held`() {
        for (p in listOf("android", "com.android.systemui", "com.android.settings", "com.google.android.gms", "com.android.providers.downloads", "com.android.cellbroadcastreceiver", "com.google.android.permissioncontroller", "com.android.phone"))
            assertTrue(platformPackage(p) && !shouldHold(n(p), allow, quiet = true), p)
        assertFalse(platformPackage("com.android.shell"), "a bug report notice is not the phone working")
        assertFalse(platformPackage("com.example.androidtools") || platformPackage("androidx.app") || platformPackage("com.androidish"))
        for (c in QUIET_PASS_CATEGORIES) assertFalse(shouldHold(n("com.example.x", category = c), allow, quiet = true), c)
        for (c in listOf("navigation", "progress", "err", "sys", "service", "call", "alarm")) assertTrue(c in QUIET_PASS_CATEGORIES, c)
    }

    @Test fun `QM1 what LA1 protects, ongoing notifications and group summaries are never held, even blocked`() {
        val block = Decision.Block(BlockReason.WORK_BLOCK, "x", at(mon, LocalTime.of(12, 0)), 0, false)
        assertFalse(shouldHold(n("com.example.x", ongoing = true), allow, quiet = true)); assertFalse(shouldHold(n("com.example.x", summary = true), allow, quiet = true))
        assertFalse(shouldHold(n("com.google.android.apps.authenticator2"), block, quiet = true)); assertFalse(shouldHold(n("uk.co.mybanking.app"), allow, quiet = true))
        assertFalse(shouldHold(n("uk.ac.bham.app"), allow, quiet = true)); assertFalse(shouldHold(n("com.example.x", category = "call"), block, quiet = true))
        assertFalse(shouldHold(n("com.example.x", category = "missed_call"), allow, quiet = true))
    }

    @Test fun `QM1 the phone is quiet in a work block and at night, and open in free time, a lecture and a meal`() {
        val w = work
        val inWork = quietWindow(w.start.plusMinutes(10), slots)
        assertNotNull(inWork); assertEquals(BlockReason.WORK_BLOCK, inWork.reason); assertEquals(w.end, inWork.until)
        assertNull(quietWindow(w.end.plusMinutes(1), slots).takeIf { slotsFor(slots, mon).none { s -> s.start <= w.end.plusMinutes(1) && w.end.plusMinutes(1) < s.end && s.slot.type.startsWith("study") } })
        val lecture = slotsFor(slots, mon).first { it.slot.type == "lecture" }
        assertNull(quietWindow(lecture.start.plusMinutes(5), slots), "a lecture is not quiet")
        val free = slotsFor(slots, mon.plusDays(1)).first { it.slot.type == "free" }
        assertNull(quietWindow(free.start.plusMinutes(5), slots), "free time is open")
        val meal = slotsFor(slots, mon).first { it.slot.type == "meal" }
        assertNull(quietWindow(meal.start.plusMinutes(1), slots), "a meal is open")
    }

    @Test fun `QM1 getting ready, transitions and planning keep blocking listed apps but do not make the phone quiet`() {
        val d = slotsFor(slots, mon)
        for (type in listOf("routine", "travel", "planning")) {
            val s = d.firstOrNull { it.slot.type == type } ?: continue
            assertNull(quietWindow(s.start.plusMinutes(1), slots), type)
        }
    }

    @Test fun `QM1 the night is quiet from the cutoff to the wake, a night override lifts it, and daytime after the wake is open`() {
        val wake = slotsFor(slots, mon.plusDays(1)).first { it.slot.title == "Wake, shower" }.start
        val cutoff = nightCutoff(wake)
        assertNull(quietWindow(cutoff.minusMinutes(5), slots.filter { it.type != "sleep" }))
        val night = quietWindow(cutoff.plusMinutes(5), slots.filter { it.type != "sleep" })
        assertNotNull(night); assertEquals(BlockReason.NIGHT, night.reason); assertEquals(wake, night.until)
        assertNull(quietWindow(cutoff.plusMinutes(5), slots.filter { it.type != "sleep" }, Allowances(nightUntil = cutoff.plusMinutes(35))), "after a night override the phone is open for its minutes")
        assertNull(quietWindow(wake.plusMinutes(1), slots.filter { it.type != "sleep" }.filter { it.type != "study" }), "after the wake")
    }

    @Test fun `QM1 the digest waits for the end of the quiet window and then shows everything, in time order, with no count`() {
        val items = listOf(HeldItem(1, "com.example.a", "A", "one", "", 1000), HeldItem(2, "com.example.b", "B", "two", "", 2000))
        val until = at(mon, LocalTime.of(12, 15))
        val waiting = digest(items, until) { Decision.Allow }
        assertTrue(waiting.ready.isEmpty()); assertEquals(until, waiting.waitingUntil)
        val after = digest(items, null) { Decision.Allow }
        assertEquals(listOf(1L, 2L), after.ready.map { it.id }); assertNull(after.waitingUntil)
        val mixed = digest(items, until) { if (it.endsWith("a")) Decision.Block(BlockReason.WORK_BLOCK, "x", at(mon, LocalTime.of(11, 0)), 0, false) else Decision.Allow }
        assertEquals(at(mon, LocalTime.of(11, 0)), mixed.waitingUntil, "the sooner end is the one said")
    }

    @Test fun `QM1 quiet mode never holds anything the blocking rules would have let through when it is off`() {
        // With quiet off, shouldHold must equal the old rule: held only if blocked and not protected.
        val block = Decision.Block(BlockReason.NIGHT, "x", at(mon, LocalTime.of(23, 0)), 0, true)
        for (pkg in listOf("com.example.a", "com.android.systemui", "com.google.android.apps.authenticator2"))
            for (cat in listOf(null, "call", "msg", "navigation"))
                for (d in listOf(allow, block)) {
                    val old = d is Decision.Block && cat !in NEVER_HELD_CATEGORIES && !neverHeldPackage(pkg)
                    assertEquals(old, shouldHold(n(pkg, cat), d), "$pkg $cat $d")
                }
    }
}
