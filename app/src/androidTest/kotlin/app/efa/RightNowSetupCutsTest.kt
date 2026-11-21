package app.efa

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import app.efa.data.EfaDb
import efa.Preset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The Right now and Setup audit (docs/CARD_AUDIT, round 5; ledger HA10 to HA13): one job, one button; each cut is a card that is not drawn any more, and the job is still there. */
@RunWith(AndroidJUnit4::class)
class RightNowSetupCutsTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: EfaDb
    @Before fun open() { Shelves.startOpen = true; db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build() }
    @After fun close() = db.close()

    @Test fun HA10_Right_now_does_not_repeat_the_three_Emergency_buttons_that_are_on_Home() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("TODAY LOOKS LIKE").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("I AM OUT").performScrollTo()
        rule.onAllNodesWithContentDescription("Emergency: Call 999").assertCountEquals(0)
        rule.onAllNodesWithText("EMERGENCY").assertCountEquals(0)
    }

    @Test fun HA10_the_winter_vacation_is_in_Modify_Schedule_and_opens_its_preview() {
        var got: efa.Instance? = null
        rule.setContent { EfaTheme { ModifyHomeScreen(emptyList(), LocalDate.of(2026, 11, 21), {}, {}, {}, {}, null, {}, onQuick = { got = it }) } }
        rule.onNodeWithText("WINTER VACATION, 14 DEC TO 8 JAN").performScrollTo()
        rule.onAllNodesWithContentDescription("Open the", substring = true).onFirst().performScrollTo().performClick()
        assertNotNull(got, "the winter span opens its preview")
    }

    @Test fun HA10_HA14_sleeping_later_tonight_is_a_choice_in_the_Change_door_and_the_four_duplicates_are_not() {
        val got = mutableListOf<ChangeChoice>()
        rule.setContent { EfaTheme { ChangeDoorScreen({ got += it }, {}) } }
        rule.onNodeWithText("Sleep later or earlier tonight").performScrollTo().performClick()
        assertEquals(listOf(ChangeChoice.NIGHT), got)
        for (gone in listOf("It is running over", "I started late", "Something came up", "A new deadline")) rule.onAllNodesWithText(gone).assertCountEquals(0)
    }

    @Test fun HA11_the_permission_list_does_not_repeat_the_Notifications_and_Blocking_cards_above_it() {
        rule.setContent { EfaTheme { PermissionsCard() } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Camera", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("Notifications", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("Blocking (accessibility)", substring = true).assertCountEquals(0)
    }

    @Test fun HA12_the_reminders_and_your_own_reminders_are_one_card() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, db = db) } }
        rule.onNodeWithText("REMINDERS AND QUIET").performScrollTo().assertExists()
        rule.onNodeWithText("YOUR REMINDERS").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Add the reminder").performScrollTo().assertExists()
    }

    @Test fun HA13_home_the_family_home_and_the_lecture_venues_are_one_Places_card() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, db = db) } }
        rule.onNodeWithText("PLACES").performScrollTo().assertExists()
        for (row in listOf("HOME", "FAMILY HOME", "LECTURE VENUES")) rule.onNodeWithText(row).performScrollTo().assertExists()
    }
}
