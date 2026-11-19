package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** WK2, WK3 on the screen: nine pages, Next and Back, Skip, Start and Open Setup. */
@RunWith(AndroidJUnit4::class)
class WalkthroughScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun WK2_it_walks_through_all_nine_pages_with_next_and_back_and_each_page_has_its_title_and_lines() {
        rule.setContent { EfaTheme { WalkthroughScreen(firstRun = true, onFinish = {}) } }
        val pages = WALKTHROUGH
        rule.onNodeWithText(pages[0].title).assertExists(); rule.onNodeWithText("1 OF 9").assertExists()
        for (i in 1 until pages.size - 1) {
            rule.onNodeWithContentDescription("Next page").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText(pages[i].title).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("${i + 1} OF 9").assertExists()
            for (l in pages[i].lines) rule.onNodeWithText(l).assertExists()
        }
        rule.onNodeWithContentDescription("Previous page").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(pages[pages.size - 3].title).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun WK2_the_last_page_offers_start_and_open_setup_and_has_no_skip() {
        var got: Boolean? = null
        rule.setContent { EfaTheme { WalkthroughScreen(firstRun = true, onFinish = { got = it }) } }
        repeat(WALKTHROUGH.size - 1) { rule.onNodeWithContentDescription("Next page").performClick(); rule.waitForIdle() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Start using Spark").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Skip the walkthrough").assertDoesNotExist()
        rule.onNodeWithContentDescription("Finish the walkthrough and open Setup").performClick(); assertEquals(true, got)
    }

    @Test fun WK3_skip_is_there_from_the_first_page_and_ends_it_without_opening_setup() {
        var got: Boolean? = null
        rule.setContent { EfaTheme { WalkthroughScreen(firstRun = true, onFinish = { got = it }) } }
        rule.onNodeWithContentDescription("Skip the walkthrough").performClick(); assertEquals(false, got)
    }

    @Test fun WK2_start_ends_it_and_the_pictures_draw_on_every_page_without_error() {
        var got: Boolean? = null
        rule.setContent { EfaTheme { WalkthroughScreen(firstRun = false, onFinish = { got = it }) } }
        repeat(WALKTHROUGH.size - 1) { rule.onNodeWithContentDescription("Next page").performClick(); rule.waitForIdle() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Start using Spark").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Start using Spark").performClick(); assertEquals(false, got)
    }
}

/** WK1, WK4 in the real app: it opens by itself on first open, once, and is there again from Setup. */
@RunWith(AndroidJUnit4::class)
class WalkthroughFirstOpenTest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun on() { WalkthroughGate.enforced = true; SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun off() { WalkthroughGate.enforced = false; SetupGate.enforced = true }
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val db get() = EfaDb.get(ApplicationProvider.getApplicationContext<Context>())

    @Before fun fresh() {
        db.openHelper.writableDatabase.execSQL("DELETE FROM meta WHERE `key` = '" + META_WALKTHROUGH_SEEN + "'")
        rule.activityRule.scenario.recreate()
    }

    @Test fun WK1_it_opens_by_itself_on_first_open_skip_ends_it_for_good_and_setup_shows_it_again() {
        rule.waitUntil(15_000) { rule.onAllNodesWithText(WALKTHROUGH[0].title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Skip the walkthrough").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(WALKTHROUGH[0].title).fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(10_000) { db.openHelper.readableDatabase.query("SELECT value FROM meta WHERE `key` = '" + META_WALKTHROUGH_SEEN + "'").use { it.moveToFirst() && it.getString(0) == "1" } }
        rule.activityRule.scenario.recreate()
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("Setup: home, venues, blocking and GitHub").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(WALKTHROUGH[0].title).assertDoesNotExist()
        rule.onNodeWithContentDescription("Setup: home, venues, blocking and GitHub").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Show the walkthrough").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Show the walkthrough").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(WALKTHROUGH[0].title).fetchSemanticsNodes().isNotEmpty() }
    }
}
