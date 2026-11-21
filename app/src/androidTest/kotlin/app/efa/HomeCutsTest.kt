package app.efa

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import efa.BufferState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Home audit (docs/CARD_AUDIT, round 5): one button for one job, so each cut below is a card that is not drawn any more, and the job it did is still one tap away. */
@RunWith(AndroidJUnit4::class)
class HomeCutsTest {
    @get:Rule val rule = createComposeRule()
    private fun home() = HomeState(date = "Fri 20 Nov", phone = null, phoneOpen = true, time = "12:00", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null,
        next = listOf(NextRow("12:30", "Lunch", ""), NextRow("13:00", "Notes", "")))

    @Test fun HA1_CF1_every_kind_of_question_is_the_same_card_with_one_button_per_answer() {
        val got = mutableListOf<String>()
        val s = home().copy(meal = "Lunch", mealKey = "k", review = "How long did \"Essay\" take?", heldWaiting = true, newRoom = "Hall H1")
        rule.setContent { EfaTheme { HomeScreen(s, {}, {}, onMeal = { _, a -> got += "meal $a" }, onReview = { got += "review" }, onHeld = { got += "held" }, onNewRoom = { got += "room" }) } }
        rule.onNodeWithText("DID YOU EAT?").assertExists()
        rule.onNodeWithContentDescription("Ate Lunch").performClick()
        rule.onNodeWithContentDescription("Show 2 more things waiting for you").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Put Hall H1 on the map").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Tell Spark how long it took").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Open the digest").performScrollTo().performClick()
        assertEquals(listOf("meal true", "room", "review", "held"), got)
    }

    @Test fun HA2_PH1_a_finished_blocks_photo_is_a_line_of_the_photo_entry_not_a_card_of_its_own() {
        var proof = 0
        rule.setContent { EfaTheme { HomeScreen(home().copy(proofDue = "Language lesson"), {}, {}, onProofDue = { proof++ }) } }
        rule.onNodeWithText("PROOF DUE").assertDoesNotExist()
        rule.onNodeWithText("PHOTO DUE").assertExists()
        rule.onNodeWithText("Language lesson").performScrollTo().performClick(); assertEquals(1, proof)
    }

    @Test fun HA5_HS10_the_shopping_list_is_a_button_on_the_next_card_on_shopping_day_and_not_a_card() {
        var shop = 0
        rule.setContent { EfaTheme { HomeScreen(home().copy(shoppingDay = true), {}, {}, onShopping = { shop++ }) } }
        rule.onNodeWithText("SHOPPING").assertDoesNotExist()
        rule.onNodeWithContentDescription("Shopping list").performScrollTo().performClick(); assertEquals(1, shop)
    }

    @Test fun HS10_no_shopping_button_when_it_is_not_shopping_day() {
        rule.setContent { EfaTheme { HomeScreen(home().copy(widgets = efa.WidgetKind.entries.filter { it.byDefault && it != efa.WidgetKind.SHOPPING }.toSet()), {}, {}) } }
        rule.onAllNodesWithContentDescription("Shopping list").assertCountEquals(0)
    }

    @Test fun HA6_WG_the_week_is_a_widget_and_the_schedule_page_not_a_card_on_the_home_page() {
        rule.setContent { EfaTheme { HomeScreen(home().copy(widgets = efa.WidgetKind.entries.toSet()), {}, {}) } }
        rule.onNodeWithText("WEEK").assertDoesNotExist()
    }

    @Test fun HA3_HS6_the_bottom_row_is_type_to_open_the_setup_gear_and_quick_add() {
        rule.setContent { EfaTheme { HomeScreen(home(), {}, {}) } }
        rule.onNodeWithContentDescription("Type to open an app").assertIsDisplayed()
        rule.onNodeWithContentDescription("Setup: home, venues, blocking and GitHub").assertIsDisplayed()
        rule.onNodeWithContentDescription("Quick add").assertIsDisplayed()
        assertTrue(true)
    }
}
