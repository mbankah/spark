package app.efa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.EfaDb
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** SH1, SH2: Setup and Right now show a few headings, and everything else is one tap away. The other suites run with every shelf open (EfaTestRunner); these run with them folded, as in the app. */
@RunWith(AndroidJUnit4::class)
class ShelfTest {
    @get:Rule val rule = createComposeRule()
    @Before fun fold() { Shelves.startOpen = false }
    @After fun restore() { Shelves.startOpen = true }

    @Test fun SH1_a_shelf_starts_folded_says_what_is_inside_and_opens_and_folds_with_a_tap() {
        rule.setContent { EfaTheme { Column { Shelf("Reminders", "Photos and meals") { BasicText("A card inside") } } } }
        rule.onNodeWithText("Reminders").assertExists(); rule.onNodeWithText("Photos and meals").assertExists()
        rule.onNodeWithText("A card inside").assertDoesNotExist()
        rule.onNodeWithText("Reminders").performClick()
        rule.onNodeWithText("A card inside").assertExists()
        rule.onNodeWithText("Reminders").performClick()
        rule.onNodeWithText("A card inside").assertDoesNotExist()
    }

    @Test fun SH2_Setup_shows_its_five_shelves_and_the_Change_card_and_the_rest_is_inside_them() {
        val db = EfaDb.get(ApplicationProvider.getApplicationContext())
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, db = db, quietMode = true) } }
        rule.onNodeWithText("CHANGE THE PLAN").performScrollTo().assertExists()
        for (shelf in listOf("Permissions and health", "Reminders", "Morning and study", "Look and home", "Links and tools")) rule.onNodeWithText(shelf).performScrollTo().assertExists()
        rule.onNodeWithText("Quiet mode: on").assertDoesNotExist()
        rule.onNodeWithText("Reminders").performScrollTo().performClick()
        rule.onNodeWithText("Quiet mode: on").performScrollTo().assertExists()
        rule.onNodeWithText("YOUR REMINDERS").performScrollTo().assertExists()                 // your own reminders are with the others now
    }

    @Test fun SH2_the_permission_list_and_the_family_home_are_in_Setup_and_no_longer_on_Right_now() {
        val db = EfaDb.get(ApplicationProvider.getApplicationContext())
        Shelves.startOpen = true
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, db = db) } }
        rule.onNodeWithText("PERMISSIONS (AFTER A REINSTALL OR PHONE UPDATE)").performScrollTo().assertExists()
        rule.onNodeWithText("FAMILY HOME").performScrollTo().assertExists()
    }

    @Test fun SH2_Right_now_shows_what_is_true_now_and_keeps_the_rest_on_shelves() {
        val db = EfaDb.get(ApplicationProvider.getApplicationContext())
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.onAllNodesWithText("Right now").onFirst().assertExists()
        rule.onNodeWithText("LITE DAY").performScrollTo().assertExists()
        for (gone in listOf("EMERGENCY", "WHAT IF: SLEEP LATER OR EARLIER TONIGHT", "HOW ARE YOU?", "NFC TAGS (OPTIONAL)", "YOUR REMINDERS", "FAMILY HOME", "PERMISSIONS (AFTER A REINSTALL OR PHONE UPDATE)")) rule.onAllNodesWithText(gone).assertCountEquals(0)
        for (shelf in listOf("Change something", "Calm and notes", "Your data and help", "Emergency", "Plan tomorrow", "The coach", "Tools")) rule.onNodeWithText(shelf).performScrollTo().assertExists()
        rule.onNodeWithText("Emergency").performScrollTo().performClick()
        rule.onNodeWithText("I AM OUT").performScrollTo().assertExists()
    }
}
