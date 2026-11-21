package app.efa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** CD1 to CD3: the door to every change, on its own and through the real app. */
@RunWith(AndroidJUnit4::class)
class ChangeDoorScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun CD1_every_change_is_said_in_its_own_words_and_a_tap_names_the_choice() {
        val got = mutableListOf<ChangeChoice>()
        rule.setContent { EfaTheme { ChangeDoorScreen(onChoose = { got += it }, onBack = {}) } }
        for (c in ChangeChoice.entries) { rule.onNodeWithText(c.title).performScrollTo().performClick() }
        assertEquals(ChangeChoice.entries.toList(), got)
        assertEquals(5, ChangeChoice.entries.size, "sleeping later tonight, a day off, a library period, a timetable file, and a week or a period; late, over and came up are the Now card (HS10) and a new deadline is Quick add (HA14)")
    }

    @Test fun CD2_the_wake_time_is_part_of_the_door_and_Back_goes_back() {
        var set: Pair<Int, Int>? = null; var back = false
        rule.setContent { EfaTheme { ChangeDoorScreen(onChoose = {}, onBack = { back = true }, wake = WakeUi("Next alarm 09:00.", false, 10, 30), onWakeSet = { h, m -> set = h to m }) } }
        rule.onNodeWithContentDescription("Set the wake time to 10:30").performScrollTo().performClick()
        assertEquals(10 to 30, set)
        rule.onNodeWithText("Back").performScrollTo().performClick(); assertTrue(back)
    }
}

/** CD1 through the real app: Setup no longer has the change cards, Setup leads to the door (Home has no Change button, HS5), and each choice opens its screen and comes back to the door. */
@RunWith(AndroidJUnit4::class)
class ChangeDoorE2ETest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun toDoor() {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Setup").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Setup").performClick()          // the gear is in the bottom row, not in the scrolling page (HA3)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("CHANGE THE PLAN").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Change the plan").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Pick what you want to change.", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun CD1_Setup_leads_to_the_door_and_the_door_leads_to_the_day_off_screen_and_back() {
        toDoor()
        rule.onNodeWithText("A day off or a weekend").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Plan a day off").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Back").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Pick what you want to change.", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun CD1_the_library_and_the_timetable_file_open_from_the_door() {
        toDoor()
        rule.onNodeWithText("A library period").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Plan a library period").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Back").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("A file from my timetable").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("A file from my timetable").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Import a timetable file").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun CD3_Setup_has_one_Change_the_plan_card_and_none_of_the_cards_that_moved() {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Setup").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Setup").performClick()          // the gear is in the bottom row, not in the scrolling page (HA3)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("CHANGE THE PLAN").fetchSemanticsNodes().isNotEmpty() }
        for (gone in listOf("Modify Schedule", "Plan a day off", "Plan a library period", "Import a timetable file", "Set this wake time")) rule.onAllNodesWithText(gone).assertCountEquals(0)
        rule.onNodeWithText("Change the plan").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Pick what you want to change.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Set this wake time").performScrollTo().assertExists()
    }
}
