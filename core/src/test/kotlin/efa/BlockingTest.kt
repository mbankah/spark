package efa

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

class BlockingTest {
    private val p = WeekB.slots
    private val social = setOf("com.instagram.android", "com.google.android.youtube")
    private val insta = "com.instagram.android"
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun decide(pkg: String, now: ZonedDateTime, allow: Allowances = Allowances(), verified: Set<String> = emptySet()) =
        decide(pkg, now, p, social, allow, verified)
    private fun blocked(d: Decision) = d as Decision.Block

    // ---- K3: which block types close the phone ----
    @Test fun `K3 a work block blocks the social list`() {
        val d = blocked(decide(insta, t(19, 10, 30)))        // Mon 10:10-12:15 notes and problems
        assertEquals(BlockReason.WORK_BLOCK, d.reason); assertEquals("Notes and problems: Randomness", d.label); assertEquals(t(19, 12, 15), d.until)
    }

    @Test fun `K3 the phone is open in lectures training matches church gym meals and free time`() {
        assertEquals(Decision.Allow, decide(insta, t(19, 9, 30)))    // lecture
        assertEquals(Decision.Allow, decide(insta, t(19, 13, 30)))   // Campus League match
        assertEquals(Decision.Allow, decide(insta, t(19, 12, 30)))   // lunch
        assertEquals(Decision.Allow, decide(insta, t(19, 21, 0)))    // free time
        assertEquals(Decision.Allow, decide(insta, t(22, 18, 30)))   // gym
        assertEquals(Decision.Allow, decide(insta, t(25, 18, 45)))   // church
    }

    @Test fun `K3 the wake-up and getting-ready window blocks`() {
        assertEquals(BlockReason.GETTING_READY, blocked(decide(insta, t(19, 7, 40))).reason)   // Mon wake and shower
        assertEquals(BlockReason.GETTING_READY, blocked(decide(insta, t(19, 8, 0))).reason)    // Bible study
    }

    @Test fun `T3 K3 transitions block the phone`() {
        assertEquals(BlockReason.TRANSITION, blocked(decide(insta, t(19, 10, 2))).reason)      // walk after the lecture
    }

    @Test fun `K1 YouTube blocks in a work block like the social apps`() {
        assertEquals(BlockReason.WORK_BLOCK, blocked(decide("com.google.android.youtube", t(19, 10, 30))).reason)
    }

    @Test fun `X1 LA1 an app not on the list is never blocked`() {
        for (pkg in listOf("com.android.dialer", "com.google.android.apps.maps", "com.monzo.android", "com.google.android.apps.authenticator2"))
            assertEquals(Decision.Allow, decide(pkg, t(19, 10, 30)), pkg)
    }

    // ---- N2: the night rule ----
    @Test fun `N2 after the cutoff the list is blocked even in a free slot`() {
        // Mon 19 Oct 23:10 is the cutoff for a Tue 07:30 wake; Monday free time ends earlier, so use Friday: free slot 22:35-23:25, cutoff 00:10.
        val d = decide(insta, t(24, 0, 20))                    // after Friday's cutoff (Sat wake 08:30, cutoff 00:10)
        assertTrue(d is Decision.Block && d.night)
    }

    @Test fun `N2 before the cutoff the night rule blocks nothing`() {
        assertEquals(Decision.Allow, decide(insta, t(23, 23, 0)))       // Fri free slot, before 00:10
    }

    @Test fun `integration 1 after the cutoff work blocks getting-ready and transitions still block as usual`() {
        // Study blocks end well before the cutoff, so check the firm reasons beat the night rule at an instant that is both:
        // a pattern with a work block that runs past the cutoff.
        val late = listOf(
            PatternSlot(java.time.DayOfWeek.MONDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
            PatternSlot(java.time.DayOfWeek.MONDAY, 1380, 1440, "Late project", "code", "", false, ""),       // 23:00-24:00, past the 23:10 cutoff
            PatternSlot(java.time.DayOfWeek.TUESDAY, 450, 470, "Wake, shower", "routine", "", false, ""),
        )
        val now = t(19, 23, 30)
        val d = decide(insta, now, late, social, Allowances(nightUntil = now.plusMinutes(30)), emptySet())
        assertEquals(BlockReason.WORK_BLOCK, (d as Decision.Block).reason)       // a night override does not lift a work block
        assertFalse(d.night)
    }

    @Test fun `N3 a night override opens the list for its window and then it closes again`() {
        val now = t(24, 0, 20)
        assertEquals(Decision.Allow, decide(insta, now, Allowances(nightUntil = now.plusMinutes(30))))
        assertTrue(decide(insta, now.plusMinutes(31), Allowances(nightUntil = now.plusMinutes(30))) is Decision.Block)
    }

    @Test fun `N7 an override is offered only until six hours before the wake`() {
        val wake = t(24, 8, 30)
        assertTrue(nightOffer(t(24, 0, 20), wake, 0, false).allowed)
        assertTrue(nightOffer(t(24, 2, 29), wake, 0, false).allowed)
        assertFalse(nightOffer(t(24, 2, 30), wake, 0, false).allowed)
    }

    @Test fun `N3 N4 N5 the offer carries its minutes and its wall length`() {
        val wake = t(24, 8, 30)
        val first = nightOffer(t(24, 0, 20), wake, 0, false)
        assertEquals(30, first.minutes); assertEquals(75, first.wallLength)
        val third = nightOffer(t(24, 0, 20), wake, 2, true)
        assertEquals(45, third.minutes); assertEquals(125, third.wallLength)
    }

    // ---- LA9 ----
    @Test fun `LA9 passing the wall opens that one app for 30 minutes`() {
        val now = t(19, 10, 30)
        val allow = Allowances(appUntil = mapOf(insta to now.plusMinutes(APP_ALLOWANCE_MINUTES)))
        assertEquals(Decision.Allow, decide(insta, now, allow))
        assertTrue(decide("com.google.android.youtube", now, allow) is Decision.Block, "only that app opens")
        assertTrue(decide(insta, now.plusMinutes(31), allow) is Decision.Block, "then it blocks again")
    }

    @Test fun `LA9 W2 the card says how many unproven minutes opening the app would lose`() {
        val d = blocked(decide(insta, t(19, 10, 30)))
        assertEquals(125, d.unprovenMinutes)
        assertEquals(0, blocked(decide(insta, t(19, 10, 30), verified = setOf(blockKey(nowAndNext(p, t(19, 10, 30)).now!!)))).unprovenMinutes)
    }

    @Test fun `LA9 outside a work block opening costs no block`() {
        assertEquals(0, blocked(decide(insta, t(19, 7, 40))).unprovenMinutes)       // getting ready
    }

    @Test fun `U7 every reason has a plain label`() {
        assertEquals("Notes and problems: Randomness", blocked(decide(insta, t(19, 10, 30))).label)
        assertEquals("Wake, shower", blocked(decide(insta, t(19, 7, 40))).label)
        assertTrue(blocked(decide(insta, t(24, 0, 20))).label.startsWith("Night"))
    }

    @Test fun `X4 the night window across the clocks going back follows real time`() {
        // Sun 25 Oct wake 08:30 GMT, cutoff 8 h 20 min earlier is 00:10 GMT, which is 01:10 BST on the clock.
        val w = nightWindow(p, at(LocalDate.of(2026, 10, 25), LocalTime.of(1, 30)))   // the first 01:30, in BST: 00:30 GMT, after the 00:10 GMT cutoff
        assertNotNull(w); assertEquals(0, w.wake.offset.totalSeconds)
        assertEquals(8L * 60 + 20, java.time.Duration.between(w.cutoff, w.wake).toMinutes())
    }
}

class KnownAppsTest {
    @Test fun `K1 the default list is the installed social and video apps`() {
        val installed = listOf("com.instagram.android", "com.google.android.youtube", "com.android.chrome", "com.monzo.android", "com.whatsapp")
        assertEquals(setOf("com.instagram.android", "com.google.android.youtube"), defaultBlockList(installed))
    }

    @Test fun `K1 YouTube is in the list`() { assertTrue("com.google.android.youtube" in SOCIAL_AND_VIDEO) }

    @Test fun `X1 banking calls maps and authenticator are never preselected`() {
        assertTrue(defaultBlockList(NEVER_PRESELECTED).isEmpty())
        assertTrue(SOCIAL_AND_VIDEO.keys.none { it in NEVER_PRESELECTED })
    }

    @Test fun `Q-C8 games and shopping are not preselected`() {
        assertTrue(defaultBlockList(listOf("com.valvesoftware.steam", "com.amazon.mShop.android.shopping", "com.ebay.mobile")).isEmpty())
    }
}
