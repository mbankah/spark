package app.efa

import android.content.Context
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

private val TUE = LocalDate.of(2026, 11, 24)
private fun tue(h: Int, m: Int = 0): ZonedDateTime = ZonedDateTime.of(TUE, LocalTime.of(h, m), LONDON)

/** AD1, AD3 on a real database: drift, the answer to it, the ladder and where Lite comes from. */
@RunWith(AndroidJUnit4::class)
class AdaptStateTest {
    private lateinit var db: EfaDb
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build(); runBlocking { db.seedIfEmpty(tue(8).toInstant().toEpochMilli(), LONDON.id) } }
    @After fun close() = db.close()

    private suspend fun pattern() = PlanRepo(db).slots()
    /** AD1: Spark has seen you start a block this week, so it can tell when one is not started. */
    private suspend fun evidence() { val d = tue(9).minusDays(2); db.blocks().upsert(BlockRow(key = "seen|1", title = "Seen", type = "study", startMs = d.toInstant().toEpochMilli(), endMs = d.plusHours(1).toInstant().toEpochMilli(), zone = LONDON.id, holdAtMs = d.toInstant().toEpochMilli() + 60_000)) }

    @Test fun AD1_a_behind_afternoon_makes_a_free_proposal_and_answering_it_clears_it() = runBlocking<Unit> {
        evidence()
        AdaptState.refresh(db, pattern(), emptySet(), tue(15, 5))
        val dbg = pattern().let { pt -> "cur=" + nowAndNext(pt, tue(15, 5)).now?.slot?.let { it.title + "/" + it.type } + " night=" + (nightWindow(pt, tue(15, 5)) != null) + " movable=" + slotsFor(pt, TUE, LONDON).filter { it.slot.type in MOVABLE_WORK }.map { it.slot.title.take(12) + "@" + it.slot.startMin } + " n=" + pt.size }
        assertNotNull(AdaptState.drift, "three blocks have passed or not started: $dbg"); val p = AdaptState.proposal
        assertNotNull(p, "and there is a way to replan that gives nothing up"); assertFalse(p.preview.wallNeeded)
        assertTrue(AdaptState.view().driftLine!!.contains("minutes behind"))
        AdaptState.acknowledge(db, tue(15, 6).toInstant().toEpochMilli())
        assertNull(AdaptState.proposal)
        AdaptState.refresh(db, pattern(), emptySet(), tue(15, 10))
        assertNull(AdaptState.drift, "what you answered is not counted again")
    }

    @Test fun AD1_with_no_start_ever_seen_nothing_is_said_and_the_day_stays_full() = runBlocking<Unit> {
        AdaptState.refresh(db, pattern(), emptySet(), tue(17, 30))
        assertNull(AdaptState.drift); assertNull(AdaptState.proposal); assertEquals(DayLevel.FULL, AdaptState.level); assertFalse(AdaptState.liteAuto); assertNull(db.meta().get(META_LITE_AUTO))
    }

    @Test fun AD1_a_block_you_started_is_not_behind() = runBlocking<Unit> {
        val started = slotsFor(pattern(), TUE, LONDON).filter { it.slot.title.startsWith("Language lesson") || it.slot.title.startsWith("Notes and problems: Game Theory") || it.slot.title.startsWith("Notes and problems: Neural") }
        for (d in started) db.blocks().upsert(BlockRow(key = blockKey(d), title = d.slot.title, type = d.slot.type, startMs = d.start.toInstant().toEpochMilli(), endMs = d.end.toInstant().toEpochMilli(), zone = LONDON.id, holdAtMs = d.start.toInstant().toEpochMilli() + 60_000))
        AdaptState.refresh(db, pattern(), emptySet(), tue(15, 5))
        assertNull(AdaptState.drift)
    }

    @Test fun AD3_the_bottom_step_turns_lite_on_by_itself_and_back_to_the_full_day_turns_it_off_for_today() = runBlocking<Unit> {
        evidence()
        AdaptState.refresh(db, pattern(), emptySet(), tue(17, 30))
        assertEquals(DayLevel.THREE, AdaptState.level)
        assertEquals("1", db.meta().get(META_LITE_AUTO)); assertTrue(AdaptState.liteAuto); assertEquals(3, AdaptState.three.size)
        AdaptState.fullDay(db, TUE)
        AdaptState.refresh(db, pattern(), emptySet(), tue(17, 35))
        assertFalse(AdaptState.liteAuto, "sent away for today"); assertEquals("0", db.meta().get(META_LITE_AUTO))
    }

    @Test fun AD3_your_own_lite_switch_is_never_overruled_and_a_recovered_day_lets_the_ladder_go() = runBlocking<Unit> {
        evidence()
        db.meta().put(MetaRow(META_LITE, "1"))
        AdaptState.refresh(db, pattern(), emptySet(), tue(17, 30))
        assertNull(db.meta().get(META_LITE_AUTO), "your switch wins: the ladder wrote nothing")
        db.meta().put(MetaRow(META_LITE, "0"))
        AdaptState.refresh(db, pattern(), emptySet(), tue(17, 30)); assertEquals("1", db.meta().get(META_LITE_AUTO))
        AdaptState.refresh(db, pattern(), emptySet(), tue(8, 0))
        assertEquals(DayLevel.FULL, AdaptState.level); assertEquals("0", db.meta().get(META_LITE_AUTO), "nothing behind: the ladder lets go")
    }
}

/** AD1 to AD3 on the screens: the banner, the chips, the three things, and Something came up. */
@RunWith(AndroidJUnit4::class)
class AdaptScreenTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: EfaDb
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build(); runBlocking { db.seedIfEmpty(tue(8).toInstant().toEpochMilli(), LONDON.id) } }
    @After fun close() { BlockingState.clockOverride = null; db.close() }

    private fun home(adapt: AdaptView) = HomeState(date = "Tue 24 Nov", phone = null, phoneOpen = true, time = "15:05", buffer = BufferState.NoDeadline, nowLabel = "Now, until 15:10", nowTitle = "Notes and problems", nowDetail = null, freeToSpendPence = null, adapt = adapt)

    @Test fun AD1_the_banner_names_how_far_behind_and_has_one_proposal_and_one_way_out() {
        val got = ArrayList<String>()
        rule.setContent { EfaTheme { HomeScreen(home(AdaptView(driftLine = "You are 50 minutes behind. Replan the rest of today from now?", driftDetail = "Language lesson (65 minutes) passed without a start.")), {}, {}, onAdapt = { got += it }) } }
        rule.onNodeWithText("YOU ARE BEHIND").assertExists(); rule.onNodeWithText("You are 50 minutes behind. Replan the rest of today from now?").assertExists()
        rule.onNodeWithContentDescription("Replan the rest of today from now").performClick(); rule.onNodeWithContentDescription("Not now").performClick()
        assertEquals(listOf("drift", "dismiss"), got)
    }

    @Test fun AD3_the_level_line_and_the_three_things_show_and_back_to_the_full_day_is_one_tap() {
        val got = ArrayList<String>()
        val a = AdaptView(levelLine = "Three things: only what matters shows until you catch up.", three = listOf("18:30  Dinner. It looks after you.", "19:10  Battl.io. The work that matters most now."), liteAuto = true)
        rule.setContent { EfaTheme { HomeScreen(home(a), {}, {}, onAdapt = { got += it }) } }
        rule.onNodeWithText("THREE THINGS THAT MATTER").performScrollTo().assertExists(); rule.onNodeWithText("18:30  Dinner. It looks after you.").performScrollTo().assertExists()
        rule.onNodeWithText("Three things: only what matters shows until you catch up.").assertExists()
        rule.onNodeWithContentDescription("Back to the full day").performScrollTo().performClick(); assertEquals(listOf("full"), got)
    }

    @Test fun AD2_something_came_up_is_one_chip_from_the_home_and_there_is_no_banner_when_nothing_is_behind() {
        val got = ArrayList<String>()
        rule.setContent { EfaTheme { HomeScreen(home(AdaptView()), {}, {}, onReality = { got += it }) } }
        rule.onNodeWithText("YOU ARE BEHIND").assertDoesNotExist()
        rule.onNodeWithContentDescription("Something came up: say what happened and Spark finds a place for it").performScrollTo().performClick()
        assertEquals(listOf("cameup"), got)
    }

    @Test fun AD2_say_what_happened_and_spark_places_it_shows_what_it_costs_and_hands_the_plan_to_the_normal_preview() {
        BlockingState.clockOverride = { tue(14, 0) }
        var plan: Instance? = null
        rule.setContent { EfaTheme { CameUpScreen(db, { plan = it }, {}) } }
        rule.onNodeWithContentDescription("What happened").performTextInput("A friend turned up")
        rule.onNodeWithContentDescription("Find a place").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("WHAT SPARK FOUND").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Buffer with this:", substring = true).performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Use this plan").performScrollTo().performClick()
        assertNotNull(plan); assertEquals(Preset.ADD_EVENT, plan!!.preset); assertEquals("A friend turned up", plan!!.str("title"))
    }

    @Test fun AD2_unwell_becomes_a_lighter_day_and_a_thing_with_no_place_says_so_by_name() {
        BlockingState.clockOverride = { tue(14, 0) }
        var plan: Instance? = null
        rule.setContent { EfaTheme { CameUpScreen(db, { plan = it }, {}) } }
        rule.onNodeWithContentDescription("What happened").performTextInput("Migraine, feeling unwell")
        rule.onNodeWithContentDescription("Find a place").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("WHAT SPARK FOUND").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Use this plan").performScrollTo().performClick()
        assertEquals(Preset.RECOVERY, plan!!.preset)
    }
}

/** HS10: the home has one order, and nothing waiting for you pushes Now off the first screen. */
@RunWith(AndroidJUnit4::class)
class HomeLayoutTest {
    @get:Rule val rule = createComposeRule()
    private val next = listOf(NextRow("15:10", "Pre-football meal", "Away from the desk"), NextRow("16:10", "Campus League football", "Fixed: match time"), NextRow("17:30", "CS Project", "Shortened by the match"))
    private fun busy() = HomeState(date = "Tue 24 Nov", phone = "Phone closed", phoneOpen = false, time = "15:05", buffer = BufferState.NoDeadline, nowLabel = "Now, until 15:10", nowTitle = "Notes and problems: Neural Computation",
        nowDetail = "Recall 10", nowOpensBlock = true, next = next, freeToSpendPence = 1840, nowFraction = 0.9f, nowLeftMin = 5, mode = HomeMode.FREE, proofDue = "Language lesson", meal = "Lunch", mealKey = "k",
        moved = listOf("Pre-football meal moved 20 minutes later."), adapt = AdaptView(driftLine = "You are 55 minutes behind. Replan the rest of today from now?", driftDetail = "Language lesson passed without a start."))

    private fun y(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().positionInRoot.y

    @Test fun HS10_now_comes_first_then_next_then_what_waits_and_now_is_on_the_first_screen_whatever_is_waiting() {
        rule.setContent { EfaTheme { HomeScreen(busy(), {}, {}) } }
        val screen = rule.onRoot().fetchSemanticsNode().size.height
        assertTrue(y("Notes and problems: Neural Computation") < y("NEXT"), "Now above Next"); assertTrue(y("NEXT") < y("DID YOU EAT?"), "Next above what waits")
        assertTrue(y("Notes and problems: Neural Computation") < screen / 2, "Now is in the top half of the first screen")
        for (a in listOf("Running over: this is taking longer", "Started late: start the day from now", "Something came up: say what happened and Spark finds a place for it")) rule.onNodeWithContentDescription(a).assertExists()
    }

    @Test fun HS10_two_things_waiting_show_and_the_rest_are_one_tap_away_and_moved_for_you_is_not_on_the_home_page() {
        rule.setContent { EfaTheme { HomeScreen(busy(), {}, {}) } }
        rule.onNodeWithText("DID YOU EAT?").assertExists(); rule.onNodeWithText("YOU ARE BEHIND").assertExists(); rule.onNodeWithText("PROOF DUE").assertDoesNotExist()
        rule.onNodeWithContentDescription("Show 1 more things waiting for you").performScrollTo().performClick()
        rule.onNodeWithText("PROOF DUE").assertExists()
        rule.onNodeWithText("MOVED FOR YOU").assertDoesNotExist()
    }

    @Test fun HS10_next_lists_the_next_three_things_in_time_order() {
        rule.setContent { EfaTheme { HomeScreen(busy(), {}, {}) } }
        assertTrue(y("Pre-football meal") < y("Campus League football") && y("Campus League football") < y("CS Project"))
    }
}
