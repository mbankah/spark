package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.Preset
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** The Modify Schedule screens reached through the real app: Setup, Modify Schedule, a preset, a preview, and the wall in front of a loosening apply. */
@RunWith(AndroidJUnit4::class)
class ModifyFlowTest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun clean() {
        val w = EfaDb.get(ApplicationProvider.getApplicationContext<Context>()).openHelper.writableDatabase
        w.execSQL("DELETE FROM modifier"); w.execSQL("DELETE FROM day_off"); w.execSQL("DELETE FROM task WHERE source IN ('modifier','dayoff')")
        w.execSQL("DELETE FROM meta WHERE `key` IN ('change_log','door_uses')")          // CE, DP: earlier tests counted changes and door uses in this same database
    }

    private fun openModify() {
        rule.onNodeWithText("Setup").performScrollTo().performClick()
        rule.onNodeWithText("Modify Schedule").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("PRESETS").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun C1_Setup_leads_to_Modify_Schedule_with_every_built_preset() {
        openModify()
        Preset.entries.forEach { rule.onNodeWithText(it.label).performScrollTo().assertExists() }
    }

    @Test fun C4_a_period_off_previews_its_cost_and_asks_for_the_wall_before_it_applies() {
        openModify()
        rule.onNodeWithContentDescription("Set up Period off").performScrollTo().performClick()
        rule.onNodeWithText("Preview what it does").performScrollTo().performClick()
        rule.waitUntil(8_000) { rule.onAllNodesWithText("Apply, with the wall").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Buffer with this:", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Apply, with the wall").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Apply Period off").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("Cancel").fetchSemanticsNodes().isNotEmpty())
        val w = EfaDb.get(ApplicationProvider.getApplicationContext<Context>()).openHelper.writableDatabase
        w.query("SELECT COUNT(*) FROM modifier").use { it.moveToFirst(); assertTrue(it.getInt(0) == 0, "nothing was applied before the wall") }
    }

    @Test fun MG1_Essentials_opens_from_Modify_Schedule_and_needs_the_wall_to_add() {
        openModify()
        rule.onNodeWithText("Essentials: apps never blocked").performScrollTo().performClick()
        rule.onNodeWithText("Apps that are never blocked").assertExists()
        rule.onNodeWithContentDescription("Open the add window. Needs the typed wall.").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Add to the essentials").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun MG19_the_Admin_library_opens_and_places_nothing_until_a_load_is_chosen() {
        openModify()
        rule.onNodeWithText("Admin library: upkeep jobs").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Not set up: nothing is placed.").fetchSemanticsNodes().isNotEmpty() }
    }
}
