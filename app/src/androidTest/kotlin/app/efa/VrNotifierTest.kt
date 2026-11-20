package app.efa

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import efa.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.*

/** VR12: the running cycle on the lock screen. */
@RunWith(AndroidJUnit4::class)
class VrNotifierTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val ds get() = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "study-standard" }
    private fun now() = ctx.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == VrNotifier.ID }?.notification

    /** Posting is asynchronous, so the notification is waited for. */
    private fun shown(): Notification? { repeat(40) { now()?.let { return it }; Thread.sleep(100) }; return null }
    private fun gone(): Boolean { repeat(40) { if (now() == null) return true; Thread.sleep(100) }; return false }

    @After fun clean() { VrNotifier.cancel(ctx) }

    @Test fun VR12_a_running_cycle_is_public_on_the_lock_screen_with_a_live_countdown_and_a_Pause_button() {
        val now = System.currentTimeMillis()
        VrNotifier.post(ctx, ds, VrRun(45, listOf(now - 4 * 60_000L)), now)
        val n = shown(); assertNotNull(n, "the notification is up")
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility, "it shows on the lock screen")
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0, "it stays while the cycle runs")
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)); assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), "it counts down")
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TITLE).toString().startsWith("V Video"), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("V ●  R ○  N ○  P ○  M ○", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Pause", n.actions.single().title.toString())
        assertNotNull(n.contentIntent, "tapping it opens the app")
    }

    @Test fun VR12_a_paused_cycle_offers_Resume_and_stops_the_clock() {
        val now = System.currentTimeMillis()
        VrNotifier.post(ctx, ds, VrRun(45, listOf(now - 4 * 60_000L), emptyList(), null, listOf(now - 1_000L)), now)
        val n = shown(); assertNotNull(n)
        assertEquals("Resume", n.actions.single().title.toString()); assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER), "the clock is stopped")
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().startsWith("Paused, "))
    }

    @Test fun VR12_a_step_past_its_time_says_OVER_and_counts_up() {
        val now = System.currentTimeMillis()
        VrNotifier.post(ctx, ds, VrRun(45, listOf(now - 14 * 60_000L)), now)
        val n = shown(); assertNotNull(n)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)); assertFalse(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), "it counts up from when the step should have ended")
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().startsWith("OVER"))
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("V ●+"))
    }

    @Test fun VR12_it_goes_when_the_cycle_is_cancelled() {
        val now = System.currentTimeMillis()
        VrNotifier.post(ctx, ds, VrRun(45, listOf(now - 60_000L)), now); assertNotNull(shown())
        VrNotifier.cancel(ctx); assertTrue(gone(), "it is taken down")
    }

    @Test fun VR12_the_channel_is_quiet() {
        VrNotifier.post(ctx, ds, VrRun(45, listOf(System.currentTimeMillis())))
        val ch = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(VrNotifier.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, ch.importance)         // quiet: no sound, no pop-up. Each notification is itself public (VISIBILITY_PUBLIC), which is what the lock screen reads.
    }

    @Test fun VR13_the_reminder_makes_a_sound_over_the_lock_screen_and_says_what_is_next() {
        val now = System.currentTimeMillis()
        VrNotifier.alert(ctx, ds, VrRun(45, listOf(now - 10 * 60_000L - 5_000L)), now)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var n: Notification? = null
        repeat(40) { n = nm.activeNotifications.firstOrNull { it.id == VrNotifier.ALERT_ID }?.notification; if (n == null) Thread.sleep(100) }
        assertNotNull(n, "the reminder is up")
        assertEquals("V Video is up", n!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(n!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("Next: Break, 5 min."))
        assertEquals(Notification.VISIBILITY_PUBLIC, n!!.visibility)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel(VrNotifier.ALERT_CHANNEL).importance, "it is not the quiet clock channel")
        VrNotifier.cancel(ctx)
        repeat(40) { if (nm.activeNotifications.any { it.id == VrNotifier.ALERT_ID }) Thread.sleep(100) }
        assertFalse(nm.activeNotifications.any { it.id == VrNotifier.ALERT_ID }, "it goes with the cycle")
    }

    @Test fun VR13_no_reminder_is_made_for_a_cycle_that_is_paused() {
        val now = System.currentTimeMillis()
        VrNotifier.alert(ctx, ds, VrRun(45, listOf(now - 20 * 60_000L), emptyList(), null, listOf(now - 15 * 60_000L)), now)
        Thread.sleep(600)
        assertFalse(ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == VrNotifier.ALERT_ID })
    }
}
