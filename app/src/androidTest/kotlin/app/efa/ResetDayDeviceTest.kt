package app.efa

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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

/** NA2: Reset the day: the card in the Change door, and the question on Home. */
@RunWith(AndroidJUnit4::class)
class ResetDayDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val resetDesc = "Reset the day: switch off the next alarm and start the day from when you are up"

    @Test fun NA2_the_Change_door_has_a_Reset_the_day_card_with_its_button_and_the_wall_length_in_words() {
        var tapped = 0
        rule.setContent { EfaTheme { ChangeDoorScreen({}, {}, reset = ResetUi(true, "One a week. The wall is 100 characters."), onReset = { tapped++ }) } }
        rule.onNodeWithText("RESET THE DAY").performScrollTo().assertExists()
        rule.onNodeWithText("One a week. The wall is 100 characters.").assertExists()
        rule.onNodeWithContentDescription(resetDesc).performScrollTo().performClick()
        assertEquals(1, tapped)
    }

    @Test fun NA2_when_one_was_used_this_week_the_card_says_when_the_next_is_and_has_no_button() {
        rule.setContent { EfaTheme { ChangeDoorScreen({}, {}, reset = ResetUi(false, "Reset the day was used in the last 7 days. The next one is available from Mon 30 Nov 19:21.")) } }
        rule.onNodeWithText("The next one is available from", substring = true).performScrollTo().assertExists()
        rule.onAllNodesWithContentDescription(resetDesc).assertCountEquals(0)
    }

    @Test fun NA2_NA1_home_asks_once_whether_to_start_the_day_from_now_when_the_alarm_was_off() {
        val got = mutableListOf<String>()
        val s = HomeState(date = "Tue 24 Nov", phone = null, phoneOpen = true, time = "13:10", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null, resetAsk = true)
        rule.setContent { EfaTheme { HomeScreen(s, {}, {}, onReality = { got += it }) } }
        rule.onNodeWithText("YOU ARE UP?").assertExists()
        rule.onNodeWithContentDescription("Start the day from now").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Not now, I am not up yet").performScrollTo().performClick()
        assertEquals(listOf("late", "resetdismiss"), got)
    }

    @Test fun NA2_home_has_no_such_question_when_the_alarm_was_not_switched_off() {
        val s = HomeState(date = "Tue 24 Nov", phone = null, phoneOpen = true, time = "13:10", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null)
        rule.setContent { EfaTheme { HomeScreen(s, {}, {}) } }
        rule.onAllNodesWithText("YOU ARE UP?").assertCountEquals(0)
    }
}
