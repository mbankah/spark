package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.*

/** WT1 on the phone: the Setup card, and the chosen time read from storage by the alarm plan. */
@RunWith(AndroidJUnit4::class)
class WakeTimeDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = EfaDb.get(ctx)
    private val mon = LocalDate.of(2026, 11, 16)

    @After fun clean() = runBlocking<Unit> { db.meta().put(MetaRow(META_WAKE_CHOSEN, "0")) }

    @Test fun WT1_the_setup_card_says_the_next_alarm_and_sets_the_time_it_shows() {
        var set: Pair<Int, Int>? = null
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, wake = WakeUi("Next alarm 09:00, about 8 hours after you fell asleep.", false, 10, 30), onWakeSet = { h, m -> set = h to m }) } }
        rule.onNodeWithText("Next alarm 09:00, about 8 hours after you fell asleep.").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Wake time 10:30").assertExists()
        rule.onNodeWithContentDescription("Set the wake time to 10:30").performScrollTo().performClick()
        assertEquals(10 to 30, set)
    }

    @Test fun WT1_the_plus_and_minus_buttons_move_the_time_by_15_minutes_and_it_wraps_round_the_clock() {
        var set: Pair<Int, Int>? = null
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, wake = WakeUi("x", false, 10, 30), onWakeSet = { h, m -> set = h to m }) } }
        rule.onNodeWithContentDescription("Wake time 15 minutes later").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Wake time 10:45").assertExists()
        rule.onNodeWithContentDescription("Wake time 15 minutes earlier").performClick(); rule.onNodeWithContentDescription("Wake time 15 minutes earlier").performClick()
        rule.onNodeWithContentDescription("Wake time 10:15").assertExists()
        rule.onNodeWithContentDescription("Set the wake time to 10:15").performScrollTo().performClick(); assertEquals(10 to 15, set)
    }

    @Test fun WT1_going_back_to_the_normal_alarm_is_offered_only_when_a_time_was_chosen() {
        var cleared = false
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, wake = WakeUi("Next alarm 10:30, the time you chose.", true, 10, 30), onWakeClear = { cleared = true }) } }
        rule.onNodeWithText("Use the normal alarm").performScrollTo().performClick(); assertTrue(cleared)
    }

    @Test fun WT1_without_a_chosen_time_there_is_no_button_to_go_back() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, wake = WakeUi("x", false, 10, 30)) } }
        rule.onNodeWithText("Use the normal alarm").assertDoesNotExist()
    }

    @Test fun WT1_the_alarm_plan_uses_the_chosen_time_from_storage_and_forgets_it_once_it_is_long_past() = runBlocking<Unit> {
        db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
        val now = at(mon, LocalTime.of(7, 30)); val chosen = at(mon, LocalTime.of(10, 30))
        db.meta().put(MetaRow(META_WAKE_CHOSEN, chosen.toInstant().toEpochMilli().toString()))
        assertEquals(chosen, AlarmScheduler.chosenWake(ctx, now))
        val plan = AlarmScheduler.planNow(ctx, now)
        assertNotNull(plan); assertEquals(AlarmReason.CHOSEN, plan.reason); assertEquals(chosen, plan.at)
        assertNotEquals(AlarmReason.CHOSEN, AlarmScheduler.planNow(ctx, now, useChosen = false)?.reason, "without the choice it is Spark's own alarm")
        assertNull(AlarmScheduler.chosenWake(ctx, chosen.plusHours(7)), "a choice from yesterday is forgotten")
        assertEquals("0", db.meta().get(META_WAKE_CHOSEN))
    }
}
