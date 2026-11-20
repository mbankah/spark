package app.efa

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test fun VR13_the_card_says_what_to_do_in_the_step() {
        rule.setContent { EfaTheme { VrHero(VrActive(study, runAt(3))) {} } }
        rule.onNodeWithText(VrStep.V.todo).assertExists()
    }
}
