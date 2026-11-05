package app.efa

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import efa.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** The appearance modes on the phone: AP2 to AP7. */
@RunWith(AndroidJUnit4::class)
class AppearanceScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun state() = HomeState(date = "Mon 2 Nov", phone = "Phone closed", phoneOpen = false, time = "10:00", buffer = BufferState.NoDeadline, nowLabel = "Now, until 11:00", nowTitle = "Study", nowDetail = null, freeToSpendPence = 1250)

    @Test fun AP5_colourful_gives_each_home_card_its_own_colour_and_the_other_modes_do_not() {
        val on = Colors(tokensFor(Mode.COLOURFUL, false), colourful = true); val off = Colors(tokensFor(Mode.LIGHT, false))
        assertEquals(4, CardKind.entries.map { on.tint(it) }.toSet().size)
        assertTrue(CardKind.entries.all { off.tint(it) == off.card })
        assertEquals(Color(0xFFFFE14D), on.tint(CardKind.BUFFER))
    }

    @Test fun AP7_every_mode_shows_the_same_cards_in_the_same_order() {
        var mode by androidx.compose.runtime.mutableStateOf(Mode.LIGHT)
        rule.setContent { EfaTheme(mode) { HomeScreen(state(), {}, {}) } }
        val seen = ArrayList<List<String>>()
        for (m in listOf(Mode.LIGHT, Mode.DARK, Mode.NIGHT, Mode.COLOURFUL)) {
            mode = m; rule.waitForIdle()
            seen += listOf("Buffer", "NOW", "Next", "Free to spend").map { label -> if (rule.onAllNodesWithContentDescription(label, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText(label, substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()) label else "missing $label" }
        }
        assertTrue(seen.all { it == seen.first() }, seen.toString()); assertTrue(seen.first().none { it.startsWith("missing") }, seen.first().toString())
    }

    @Test fun AP6_a_negative_buffer_says_NEGATIVE_in_every_mode() {
        val neg = state().copy(buffer = BufferState.Value(BufferResult(-2.8, emptyList(), false, "Wed 4 Nov 17:00")))
        var mode by androidx.compose.runtime.mutableStateOf(Mode.LIGHT)
        rule.setContent { EfaTheme(mode) { HomeScreen(neg, {}, {}) } }
        for (m in listOf(Mode.LIGHT, Mode.DARK, Mode.NIGHT, Mode.COLOURFUL)) { mode = m; rule.waitForIdle(); rule.onNodeWithText("NEGATIVE", substring = true, ignoreCase = true).assertExists() }
    }
}
