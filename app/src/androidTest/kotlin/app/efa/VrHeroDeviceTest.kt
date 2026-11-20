package app.efa

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import efa.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.test.*

/** VR11: the running VRNPM cycle, big on the home screen, with its controls. */
@RunWith(AndroidJUnit4::class)
class VrHeroDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val study get() = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "study-standard" }
    private fun runAt(minutesIn: Long, phasesDone: Int = 0): VrRun {
        val now = System.currentTimeMillis(); val phases = vrPhases()
        var t = now - minutesIn * 60_000L - phases.take(phasesDone).sumOf { it.plannedMin } * 60_000L
        val b = ArrayList<Long>(); b += t
        for (i in 0 until phasesDone) { t += phases[i].plannedMin * 60_000L; b += t }
        return VrRun(45, b)
    }
    private fun home(vr: VrActive?) = HomeState(date = "Fri 20 Nov", phone = null, phoneOpen = true, time = "13:20", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, next = emptyList(), freeToSpendPence = null, vr = vr)

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getExternalFilesDir(null)!!
        File(dir, name).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun VR11_the_card_shows_the_ring_the_time_the_step_and_the_controls() {
        val got = ArrayList<VrAction>()
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(14, phasesDone = 4))) { got += it } } }
        rule.onNodeWithText("VRNPM").assertExists(); rule.onNodeWithText("N Notes").assertExists()
        rule.onNodeWithContentDescription("VRNPM. N Notes.", substring = true).assertExists()
        rule.onNodeWithText("Pause").performClick(); assertEquals(listOf(VrAction.PAUSE), got)
        rule.onNodeWithText("Page done").performClick(); assertEquals(listOf(VrAction.PAUSE, VrAction.PAGE), got)
        rule.onNodeWithContentDescription("VRNPM. N Notes.", substring = true).performClick(); assertEquals(VrAction.OPEN, got.last())
        rule.onNodeWithContentDescription("Hold to finish N", substring = true).assertExists()
        save("vrhero_running.png")
    }

    @Test fun VR11_a_paused_cycle_says_paused_and_offers_resume_and_an_overrun_says_over() {
        val now = System.currentTimeMillis()
        rule.setContent { EfaTheme { VrHero(VrActive(study, VrRun(45, listOf(now - 4 * 60_000L), emptyList(), null, listOf(now - 1_000L)))) {} } }
        rule.onNodeWithText("PAUSED").assertExists(); rule.onNodeWithText("Resume").assertExists()
        rule.onNodeWithContentDescription("Video, over now").assertDoesNotExist()
    }

    @Test fun VR11_a_step_past_its_time_shows_OVER_and_a_plus_time() {
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(14))) {} } }
        rule.onNodeWithText("OVER").assertExists(); rule.onNodeWithContentDescription("Video, over now").assertExists()
        save("vrhero_over.png")
    }

    @Test fun VR11_the_hold_ends_the_step() {
        val got = ArrayList<VrAction>()
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(3))) { got += it } } }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Hold to finish V", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(HOLD_END_MS + 600)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { VrAction.NEXT in got }
    }

    @Test fun VR11_the_home_screen_shows_the_card_first_when_a_cycle_is_running_and_not_otherwise() {
        rule.setContent { EfaTheme { HomeScreen(home(VrActive(study, runAt(3))), {}, {}) } }
        rule.onNodeWithText("VRNPM").assertExists(); rule.onNodeWithContentDescription("VRNPM. V Video.", substring = true).assertExists()
        save("vrhero_home.png")
    }

    @Test fun VR11_without_a_running_cycle_the_home_screen_has_no_card() {
        rule.setContent { EfaTheme { HomeScreen(home(null), {}, {}) } }
        rule.onNodeWithText("VRNPM").assertDoesNotExist()
    }

    @Test fun VR13_the_hold_starts_again_from_empty_after_it_completes() {
        val got = ArrayList<VrAction>()
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(3))) { got += it } } }
        rule.mainClock.autoAdvance = false
        for (round in 1..2) {
            rule.onNodeWithContentDescription("Hold to finish V", substring = true).performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(HOLD_END_MS + 700)
            rule.onRoot().performTouchInput { up() }
            rule.mainClock.advanceTimeBy(1_000)                       // the button waits a moment after a hold, then takes the next one
            assertEquals(round, got.count { it == VrAction.NEXT }, "hold $round counted")
        }
    }

    @Test fun VR16_the_card_is_linked_to_its_block_with_its_name_its_hours_and_where_the_cycle_stands_against_it() {
        val got = ArrayList<VrAction>()
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(3))) { got += it } } }
        val name = study.slot.title.substringBefore(" - ")
        rule.onNodeWithText(name).assertExists()
        rule.onNodeWithContentDescription("Study block. $name.", substring = true).assertExists()
        rule.onNodeWithText("The block ended at", substring = true).assertExists()            // the block in these tests is in the past: the cycle outlives it, and the card says so
        rule.onNodeWithContentDescription("Study block. $name.", substring = true).performClick(); assertEquals(VrAction.OPEN, got.last())
    }

    @Test fun VR16_a_cycle_in_the_block_that_is_on_now_is_the_Now_card_and_the_block_is_not_drawn_twice() {
        val ds = study
        val onNow = home(VrActive(ds, runAt(3))).copy(nowTitle = "Study block on now", nowLabel = "Now, until 12:15", nowKey = blockKey(ds))
        rule.setContent { EfaTheme { HomeScreen(onNow, {}, {}) } }
        rule.onNodeWithText("Study block on now").assertDoesNotExist()
        rule.onNodeWithContentDescription("VRNPM. V Video.", substring = true).assertExists()
    }

    @Test fun VR16_a_cycle_left_over_from_another_block_does_not_hide_the_block_that_is_on_now() {
        val onNow = home(VrActive(study, runAt(3))).copy(nowTitle = "A different block", nowLabel = "Now, until 12:15", nowKey = "some-other-block")
        rule.setContent { EfaTheme { HomeScreen(onNow, {}, {}) } }
        rule.onNodeWithText("A different block").assertExists()
        rule.onNodeWithContentDescription("VRNPM. V Video.", substring = true).assertExists()
    }

    @Test fun VR17_each_step_has_its_own_icon_and_a_break_has_one_too() {
        assertEquals(VrStep.V, vrStepOf(false, "V Video")); assertEquals(VrStep.R, vrStepOf(false, "R Read")); assertEquals(VrStep.N, vrStepOf(false, "N Notes"))
        assertEquals(VrStep.P, vrStepOf(false, "P Practice")); assertEquals(VrStep.M, vrStepOf(false, "M Marking")); assertNull(vrStepOf(true, "Break"))
        rule.setContent { EfaTheme { androidx.compose.foundation.layout.Row { VrStep.entries.forEach { VrIcon(it, 48.dp, androidx.compose.ui.graphics.Color.Black) }; VrIcon(null, 48.dp, androidx.compose.ui.graphics.Color.Black) } } }
        save("vr_icons.png")
    }

    @Test fun VR18_the_chips_carry_their_icons_and_the_state_each_is_in() {
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(14, phasesDone = 4))) {} } }
        rule.onNodeWithContentDescription("Video, done").assertExists(); rule.onNodeWithContentDescription("Notes, current", substring = true).assertExists()
        rule.onNodeWithContentDescription("Marking, upcoming").assertExists()
    }

    @Test fun VR13_the_card_says_what_to_do_in_the_step() {
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(3))) {} } }
        rule.onNodeWithText(VrStep.V.todo).assertExists()
    }
}
