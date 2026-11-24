package app.efa

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.EfaDb
import app.efa.data.META_ALARM_OFF
import app.efa.data.MetaRow
import efa.AlarmReason
import efa.at
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NA1: the alarm can be switched off for one morning (found on 24 November 2026: a wake much later than the 12:00 backstop). */
@RunWith(AndroidJUnit4::class)
class NoAlarmDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    @After fun clear() { runBlocking { EfaDb.get(ctx).meta().put(MetaRow(META_ALARM_OFF, "")) } }

    @Test fun NA1_the_wake_card_offers_No_alarm_for_this_morning_and_it_reports_the_tap() {
        var tapped = 0
        rule.setContent { EfaTheme { ChangeDoorScreen({}, {}, wake = WakeUi("Next alarm 12:00.", false, 10, 30), onNoAlarm = { tapped++ }) } }
        rule.onNodeWithContentDescription("No alarm for this morning: sleep until you wake by yourself").performScrollTo().performClick()
        assertEquals(1, tapped)
    }

    @Test fun NA1_once_it_is_off_the_card_says_so_offers_the_normal_alarm_back_and_no_second_No_alarm_button() {
        var cleared = 0
        rule.setContent { EfaTheme { ChangeDoorScreen({}, {}, wake = WakeUi("No alarm for Tue 24 Nov.", false, 10, 30, noAlarm = true), onWakeClear = { cleared++ }) } }
        rule.onAllNodesWithContentDescription("No alarm for this morning: sleep until you wake by yourself").assertCountEquals(0)
        rule.onNodeWithContentDescription("Use the normal alarm").performScrollTo().performClick()
        assertEquals(1, cleared)
    }

    @Test fun NA1_a_switched_off_alarm_is_skipped_and_the_next_one_is_the_day_after_and_it_can_be_brought_back() = runBlocking {
        val now = at(LocalDate.of(2026, 11, 4), LocalTime.of(5, 34))
        val normal = AlarmScheduler.planNow(ctx, now, useChosen = false, useNoAlarm = false)!!
        assertEquals(LocalDate.of(2026, 11, 4), normal.at.toLocalDate())
        AlarmScheduler.setNoAlarm(ctx, normal.at.toLocalDate(), now)
        val skipped = AlarmScheduler.planNow(ctx, now)!!
        assertEquals(LocalDate.of(2026, 11, 5), skipped.at.toLocalDate()); assertEquals(AlarmReason.BACKSTOP, skipped.reason)
        AlarmScheduler.setNoAlarm(ctx, null, now)
        assertNull(AlarmScheduler.noAlarmDate(ctx)); assertEquals(normal.at, AlarmScheduler.planNow(ctx, now)!!.at)
    }

    @Test fun NA1_an_alarm_that_fires_for_a_switched_off_morning_does_not_ring() = runBlocking {
        val day = LocalDate.of(2026, 11, 4)
        val noon = at(day, LocalTime.of(12, 0)).toInstant().toEpochMilli()
        EfaDb.get(ctx).meta().put(MetaRow(META_ALARM_OFF, day.toString())); AlarmSession.ringing = false
        AlarmReceiver().onReceive(ctx, Intent(AlarmScheduler.ACTION_FIRE).putExtra(AlarmScheduler.EXTRA_AT, noon).putExtra(AlarmScheduler.EXTRA_REASON, AlarmReason.BACKSTOP.name))
        Thread.sleep(2_500)
        assertFalse(AlarmSession.ringing, "a switched-off alarm does not ring"); assertTrue(EfaDb.get(ctx).mornings().get(day.toString()) == null, "and no morning is started")
    }
}
