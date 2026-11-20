package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import java.time.ZonedDateTime
import kotlin.test.*

/** VR14: the real app, a cycle running in its database, and the home card driven by real holds. */
@RunWith(AndroidJUnit4::class)
class VrHomeE2ETest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val db: EfaDb get() = EfaDb.get(ctx)

    /** A study block of today in the seeded plan. */
    private fun studyToday(): DatedSlot {
        val today = ZonedDateTime.now(LONDON).toLocalDate()
        return (0L..6L).asSequence().map { today.plusDays(it) }.flatMap { d -> slotsFor(WeekB.slots, d).asSequence() }.first { it.slot.type == "study-standard" || it.slot.type == "study-light" }
            .let { found -> slotsFor(WeekB.slots, today).firstOrNull { it.slot.type.startsWith("study") } ?: found }
    }

    @Before fun prepare() {
        val w = db.openHelper.writableDatabase
        w.execSQL("DELETE FROM meta WHERE `key` LIKE 'vr:%'"); w.execSQL("DELETE FROM meta WHERE `key` = 'vrcfg'")
        w.execSQL("INSERT OR REPLACE INTO meta(`key`, value) VALUES ('${META_WALKTHROUGH_SEEN}', '1')")
        runBlocking { VrRepo(db).start(studyToday(), System.currentTimeMillis() - 3 * 60_000L) }      // V has run for 3 of its 10 minutes
    }

    @After fun clean() { db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE `key` LIKE 'vr:%'"); db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE `key` LIKE 'confirm:%'"); VrNotifier.cancel(ctx) }

    @Test fun CF1_a_question_waiting_shows_on_Home_and_a_tap_on_Stop_the_cycle_answers_it_and_ends_the_cycle() {
        val ds = studyToday()
        runBlocking { ConfirmRepo(db).raise(vrBlockEndConfirm(blockKey(ds), ds.slot.title, "12:15", VrRepo(db).get(ds)!!, System.currentTimeMillis())) }
        waitFor("Confirm. The study block has ended")
        rule.onNodeWithText("Stop the cycle").performScrollTo().performClick()
        rule.waitUntil(15_000) { runBlocking { VrRepo(db).get(studyToday()) }?.let { vrView(it, System.currentTimeMillis()).status == VrStatus.DONE } == true }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Stop the cycle").fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { ConfirmRepo(db).open() }.isEmpty())
    }

    private fun waitFor(text: String) = rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun VR14_the_home_card_shows_the_stored_cycle_and_a_hold_ends_the_step_and_then_the_next_one() {
        waitFor("Hold to finish V")
        rule.onNodeWithContentDescription("VRNPM. V Video.", substring = true).assertExists()

        // first hold: V ends, the break begins, and the button now says what it will do
        rule.onNodeWithContentDescription("Hold to finish V", substring = true).performTouchInput { down(center) }
        waitFor("Hold to start the next step")
        rule.onRoot().performTouchInput { up() }
        rule.onNodeWithContentDescription("VRNPM. Break.", substring = true).assertExists()
        assertEquals(2, runBlocking { VrRepo(db).get(studyToday()) }!!.boundaries.size, "V ended once")

        // the second hold works on the same button, from empty (a hold takes a full second, so a real second has passed)
        Thread.sleep(1_000)
        rule.onNodeWithContentDescription("Hold to start the next step", substring = true).performTouchInput { down(center) }
        waitFor("Hold to finish R")
        rule.onRoot().performTouchInput { up() }
        rule.onNodeWithContentDescription("VRNPM. R Read.", substring = true).assertExists()
        assertEquals(3, runBlocking { VrRepo(db).get(studyToday()) }!!.boundaries.size, "the break ended once")
    }

    @Test fun VR14_a_change_made_elsewhere_shows_on_the_home_card_within_a_second_or_two() {
        waitFor("Hold to finish V")
        runBlocking { VrRepo(db).pause(studyToday(), System.currentTimeMillis()) }                // as the lock screen's Pause does
        rule.waitUntil(15_000) { rule.onAllNodesWithText("PAUSED").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Resume").assertExists()
        runBlocking { VrRepo(db).resume(studyToday(), System.currentTimeMillis()) }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("PAUSED").fetchSemanticsNodes().isEmpty() }
    }
}
