package efa

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.test.*

/** PA1 to PA7: part-of-an-app access. The given case is a podcast on YouTube at night with no scrolling; the others are the class it belongs to. */
class ScopedTest {
    private val zone = ZoneId.of("Europe/London")
    private val night = ZonedDateTime.of(2026, 11, 19, 3, 0, 0, 0, zone)           // a Thursday, inside the sleep block
    private val noon = ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, zone)
    private val yt = "com.google.android.youtube"
    private val ig = "com.instagram.android"
    private val pattern = listOf(
        PatternSlot(DayOfWeek.THURSDAY, 0, 540, "Sleep", "sleep", "", false, ""),
        PatternSlot(DayOfWeek.THURSDAY, 600, 900, "CS Project", "code", "", false, ""),
    )
    private val list = setOf(yt, ig)
    private val access = ScopedAccess()
    private fun at(s: Surface?, trip: ZonedDateTime? = null, now: ZonedDateTime = night, scoped: ScopedAccess? = access, pkg: String = yt, p: List<PatternSlot> = pattern, allow: Allowances = Allowances()) =
        decide(pkg, now, p, list, allow, scoped = scoped, surface = s, scrollTripUntil = trip)
    private fun ids(vararg i: String) = Signals(i.toSet())

    // ----- the given case -----
    @Test fun PA4_a_podcast_plays_at_night_and_shorts_do_not() {
        assertEquals(Decision.Allow, at(Surface.WATCH), "the video you are playing is open")
        val shorts = at(Surface.SHORTS) as Decision.Block
        assertEquals(BlockReason.SCOPED, shorts.reason); assertFalse(shorts.night)
    }

    // ----- the class: the other surfaces, the budget, the limits of the door -----
    @Test fun PA4_search_and_a_feed_are_open_but_on_a_scroll_budget_and_an_unknown_surface_is_too() {
        for (s in listOf(Surface.SEARCH, Surface.FEED, Surface.OTHER, null)) assertEquals(Decision.Allow, at(s), "$s")
        val tripped = at(Surface.FEED, trip = night.plusMinutes(3)) as Decision.Block
        assertEquals(BlockReason.SCOPED, tripped.reason); assertEquals("scrolling", tripped.label); assertEquals(night.plusMinutes(3), tripped.until)
        assertEquals(Decision.Allow, at(Surface.FEED, trip = night.minusMinutes(1)), "a trip that is over does not hold")
    }

    @Test fun PA5_the_door_opens_nothing_else_a_work_block_the_other_apps_and_the_default_stay_as_they_were() {
        assertEquals(BlockReason.WORK_BLOCK, (at(Surface.WATCH, now = noon) as Decision.Block).reason, "sleep audio never lifts a work block")
        assertEquals(BlockReason.NIGHT, (at(Surface.WATCH, pkg = ig) as Decision.Block).reason, "another listed app is still shut at night")
        assertEquals(BlockReason.NIGHT, (at(Surface.WATCH, scoped = null) as Decision.Block).reason, "off by default: the night is as it was")
        assertEquals(Decision.Allow, at(Surface.SHORTS, now = noon.plusHours(5)), "outside a block and the night the app is simply open")
    }

    @Test fun PA5_the_wall_allowance_still_works_the_old_way() {
        val opened = at(Surface.SHORTS, allow = Allowances(nightUntil = night.plusMinutes(30)))
        assertEquals(Decision.Allow, opened, "a night override is the wall, as before")
    }

    // ----- recognising the surface -----
    @Test fun PA1_a_surface_is_told_from_the_view_names_and_the_selected_tab() {
        assertEquals(Surface.SHORTS, classifySurface(yt, ids("reel_player_page_container", "watch_player")))
        assertEquals(Surface.SHORTS, classifySurface(yt, Signals(setOf("results"), setOf("Shorts"))), "the Shorts tab being selected is enough")
        assertEquals(Surface.WATCH, classifySurface(yt, ids("watch_player", "results")), "a playing video wins over the list under it")
        assertEquals(Surface.SEARCH, classifySurface(yt, ids("search_edit_text", "results")))
        assertEquals(Surface.FEED, classifySurface(yt, ids("rich_grid_renderer")))
        assertEquals(Surface.FEED, classifySurface(yt, Signals(emptySet(), setOf("Home"))))
        assertEquals(Surface.OTHER, classifySurface(yt, ids("something_new")))
        assertEquals(Surface.OTHER, classifySurface(yt, Signals(emptySet())))
    }

    @Test fun PA1_a_miniplayer_on_a_feed_is_the_feed_and_the_names_are_read_in_any_case() {
        assertEquals(Surface.FEED, classifySurface(yt, ids("watch_while_time_bar_view", "floaty_bar", "rich_grid")))
        assertEquals(Surface.SHORTS, classifySurface(yt, ids("REEL_Recycler")))
        assertEquals(Surface.OTHER, classifySurface("com.example.unknown", ids("reel_player")), "an app with no map is never guessed at")
    }

    // ----- counting the scrolling -----
    @Test fun PA3_one_swipe_is_one_gesture_however_many_events_it_makes() {
        val t = Instant.parse("2026-11-19T03:00:00Z")
        val oneSwipe = (0 until 12).map { t.plusMillis(it * 20L) }
        assertEquals(1, gestures(oneSwipe).size)
        assertEquals(2, gestures(oneSwipe + oneSwipe.map { it.plusSeconds(2) }).size)
        assertEquals(0, gestures(emptyList()).size)
    }

    @Test fun PA3_the_burst_limit_on_a_playing_video_counts_gestures_in_its_window_only() {
        val now = Instant.parse("2026-11-19T03:00:00Z")
        val watch = DEFAULT_BUDGETS.getValue(Surface.WATCH)
        fun swipes(n: Int, ago: Long = 0) = (0 until n).map { now.minusSeconds(ago + it * 4L) }
        assertFalse(scrollTripped(swipes(12), now, watch)); assertTrue(scrollTripped(swipes(13), now, watch))
        assertFalse(scrollTripped(swipes(13, ago = 90), now, watch), "swipes from a minute and a half ago are not counted")
    }

    @Test fun PA2_the_surfaces_are_open_on_a_burst_limit_or_paid_for_from_the_credit_and_shorts_is_closed() {
        assertFalse(isOpen(access, Surface.SHORTS)); assertNull(budgetOf(access, Surface.SHORTS)); assertNull(costOf(access, Surface.SHORTS))
        assertNotNull(budgetOf(access, Surface.WATCH)); assertNull(costOf(access, Surface.WATCH), "scrolling a playing video is not paid for from the credit")
        for (s in listOf(Surface.SEARCH, Surface.FEED, Surface.OTHER)) { assertNotNull(costOf(access, s), s.name); assertTrue(isOpen(access, s)) }
        assertTrue(costOf(access, Surface.SEARCH)!! < costOf(access, Surface.FEED)!!, "looking for the thing is the cheapest scrolling")
        assertTrue(costOf(access, Surface.OTHER)!! >= costOf(access, Surface.FEED)!!, "a surface nobody recognised is never cheaper than a feed")
    }

    // ----- the finding credit -----
    private val t0 = Instant.parse("2026-11-19T03:00:00Z")

    @Test fun PA2_finding_something_takes_a_while_and_that_is_fine() {
        // the given worry: it takes a while to find the video. Forty search gestures, or twenty on a feed, before anything is said.
        var c = freshCredit(access); var trip = false
        repeat(40) { val (n, t) = spendGesture(c, t0.plusSeconds(it * 5L), Surface.SEARCH, access); c = n; trip = trip || t }
        assertFalse(trip, "forty searches in a few minutes is finding, not a habit")
        c = freshCredit(access); trip = false
        repeat(20) { val (n, t) = spendGesture(c, t0.plusSeconds(it * 5L), Surface.FEED, access); c = n; trip = trip || t }
        assertFalse(trip, "twenty flicks on a feed")
        val (_, over) = spendGesture(c, t0.plusSeconds(200), Surface.FEED, access); assertTrue(over, "the next one is over")
    }

    @Test fun PA2_listening_earns_the_credit_back_and_scrolling_without_listening_does_not() {
        var c = freshCredit(access)
        repeat(20) { c = spendGesture(c, t0.plusSeconds(it * 5L), Surface.FEED, access).first }
        assertEquals(0.0, c.credit, 1e-9)
        val now = t0.plusSeconds(200)
        c = creditSurface(c, now, Surface.WATCH, access)                         // a video starts
        val later = creditAt(c, now.plusSeconds(10 * 60), access)               // and plays for ten minutes
        assertEquals(10.0, later.credit, 0.01)
        val (afterFeed, over) = spendGesture(later, now.plusSeconds(10 * 60), Surface.FEED, access)
        assertFalse(over); assertEquals(8.0, afterFeed.credit, 0.01)
        assertEquals(0.0, creditAt(creditSurface(c, now, Surface.FEED, access), now.plusSeconds(600), access).credit, 1e-9, "ten minutes on a feed earns nothing")
    }

    @Test fun PA2_the_credit_has_a_ceiling_and_a_gesture_on_a_playing_video_does_not_touch_it() {
        var c = creditSurface(freshCredit(access), t0, Surface.WATCH, access)
        assertEquals(40.0, creditAt(c, t0.plusSeconds(3 * 3600), access).credit, 1e-9, "three hours of listening never banks more than the ceiling")
        val (same, over) = spendGesture(c, t0.plusSeconds(5), Surface.WATCH, access)
        assertFalse(over); assertEquals(c.credit, same.credit, 1e-9)
        val (_, shortsOver) = spendGesture(c, t0, Surface.SHORTS, access); assertFalse(shortsOver, "shorts is closed by the surface, not by the credit")
    }

    @Test fun PA3_a_trip_leaves_the_credit_at_zero_so_a_flick_straight_after_is_over_again() {
        var c = freshCredit(access).copy(credit = 1.0)
        val (a, over) = spendGesture(c, t0, Surface.FEED, access); assertTrue(over); assertEquals(0.0, a.credit, 1e-9)
        assertTrue(spendGesture(a, t0.plusSeconds(1), Surface.FEED, access).second)
    }

    // ----- the invariants, over random cases -----
    @Test fun PA5_over_many_cases_shorts_never_opens_at_night_and_a_work_block_is_never_lifted() {
        val rnd = Random(19)
        repeat(3000) {
            val now = noon.plusMinutes(rnd.nextLong(-720, 720))
            val surface = Surface.entries[rnd.nextInt(Surface.entries.size)]
            val trip = if (rnd.nextBoolean()) now.plusMinutes(rnd.nextLong(-5, 5)) else null
            val d = decide(yt, now, pattern, list, scoped = access, surface = surface, scrollTripUntil = trip)
            val current = nowAndNext(pattern, now).now
            val firm = current?.let { firmReasonFor(it.slot.type) }
            if (firm != null) assertTrue(d is Decision.Block && d.reason == firm, "a work block holds at $now")
            val sleeping = current?.slot?.type == "sleep"
            if (sleeping && surface == Surface.SHORTS) assertTrue(d is Decision.Block, "Shorts at night at $now")
            if (sleeping && trip != null && trip.isAfter(now)) assertTrue(d is Decision.Block, "a trip holds at $now")
        }
    }

    // ----- the words -----
    @Test fun PA6_the_two_messages_say_what_happened_and_when_it_ends_and_use_no_banned_word() {
        val a = refusalLine(BlockReason.SCOPED, "Shorts", "03:01"); val b = refusalLine(BlockReason.SCOPED, "scrolling", "03:03")
        assertTrue("until" in a && "Shorts" in a); assertTrue("03:03" in b && "paused" in b)
        assertEquals(emptyList(), voiceProblems(a)); assertEquals(emptyList(), voiceProblems(b))
    }

    // ----- what is read, and failing safe -----
    @Test fun PA9_a_surface_nobody_recognised_costs_double_and_never_traps_the_phone() {
        val s = classifySurface(yt, ids("renamed_in_an_update"))
        assertEquals(Surface.OTHER, s)
        assertTrue(costOf(access, s)!! >= costOf(access, Surface.FEED)!!)
        assertEquals(Decision.Allow, at(s), "the phone is not trapped")
    }

    @Test fun PA8_only_the_apps_the_option_covers_are_looked_at_and_the_signals_hold_names_not_text() {
        assertEquals(setOf(yt), SCOPED_APPS)
        val sig = Signals(setOf("watch_player"), setOf("Home"))
        assertNull(surfaceMapFor("com.instagram.android"), "an app with no map is not read")
        assertEquals(setOf("watch_player"), sig.ids)
    }
}
