package app.efa

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import efa.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** Voice quick-add on screen: V1, V2, V4, G7, E10. The phone's own recogniser is not run here (see DEVICE_TESTS E28 to E31). */
@RunWith(AndroidJUnit4::class)
class VoiceScreenTest {
    @get:Rule val rule = createComposeRule()
    private val now = at(LocalDate.of(2026, 11, 2), LocalTime.of(10, 0))

    private fun home(voice: Boolean, note: String? = null, onVoice: () -> Unit = {}) = rule.setContent {
        EfaTheme {
            HomeScreen(HomeState(date = "Mon 2 Nov", phone = "Phone closed", phoneOpen = false, time = "10:00", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null,
                freeToSpendPence = null, voice = voice, voiceNote = note), {}, {}, onVoice = onVoice)
        }
    }

    @Test fun V1_the_home_screen_offers_voice_and_opens_it_when_tapped() {
        var tapped = false
        home(true, onVoice = { tapped = true })
        rule.onNodeWithContentDescription("Voice quick add").performClick(); assertTrue(tapped)
    }

    @Test fun G7_E10_voice_is_hidden_where_it_is_off_and_typed_quick_add_is_still_there() {
        home(false)
        rule.onNodeWithContentDescription("Voice quick add").assertDoesNotExist()
        rule.onNodeWithContentDescription("Quick add").assertExists()
    }

    @Test fun V2_what_voice_could_not_do_is_said_plainly() {
        home(true, note = "This phone has no speech recogniser. Use the plus button to type it.")
        rule.onNodeWithText("This phone has no speech recogniser. Use the plus button to type it.").performScrollTo().assertExists()
    }

    @Test fun V2_the_card_shows_what_was_heard_and_what_it_was_read_as_before_anything_is_saved() {
        var saved: NewTask? = null
        val draft = NewTask("Assignment", at(LocalDate.of(2026, 11, 6), LocalTime.of(17, 0)), 8)
        rule.setContent { EfaTheme { QuickAddScreen(now, { "Buffer +20.0h" }, false, {}, { saved = it }, {}, prefill = draft, heard = "assignment due Friday, heavy") } }
        rule.onNodeWithText("I HEARD").assertExists(); rule.onNodeWithText("“assignment due Friday, heavy”").assertExists()
        rule.onNodeWithText("Heaviness 8 is about", substring = true).assertExists()
        assertNull(saved)                                                    // nothing saved by hearing it
    }

    @Test fun V2_the_person_can_save_what_was_heard_after_checking_it() {
        var saved: NewTask? = null
        val draft = NewTask("Assignment", at(LocalDate.of(2026, 11, 6), LocalTime.of(17, 0)), 8)
        rule.setContent { EfaTheme { QuickAddScreen(now, { "Buffer +20.0h" }, false, {}, { saved = it }, {}, prefill = draft, heard = "assignment due Friday, heavy") } }
        rule.onNode(hasText("Save", substring = true) and hasClickAction()).performScrollTo().performClick()
        assertEquals("Assignment", saved?.title); assertEquals(8, saved?.heaviness)
    }
}
