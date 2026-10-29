package app.efa

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

private const val SHELL = "com.android.shell"      // notifications posted with `cmd notification post` come from this package

/**
 * Held notifications on the emulator, end to end: a real notification listener, real notifications posted into the shade, and the
 * real database. The test and the listener share one process, so the test moves the clock the policy reads.
 */
@RunWith(AndroidJUnit4::class)
class HeldNotificationsE2ETest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private lateinit var ctx: Context
    private lateinit var db: EfaDb
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun clockAt(z: ZonedDateTime) { BlockingState.clockOverride = { z }; runBlocking { BlockingState.refresh(ctx) } }
    private fun post(tag: String, title: String, text: String) { screen.shell("cmd notification post -S bigtext -t $title $tag $text") }
    private fun inShade(tag: String) = screen.shell("dumpsys notification --noredact").contains("tag=$tag")
    private fun heldCount() = runBlocking { db.held().unreviewed().size }

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = EfaDb.get(ctx)
        runBlocking {
            db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
            db.openHelper.writableDatabase.execSQL("DELETE FROM held_notification")
            BlockingRepo(db).saveList(setOf(SHELL))
        }
        screen.shell("cmd notification allow_listener app.efa/app.efa.NotifHolder")
        screen.shell("cmd notification cancel_all 2>/dev/null")
        clockAt(t(19, 10, 30))                                  // Monday 10:30: inside a work block
        Thread.sleep(2_000)                                     // let the listener bind
    }

    @After fun tearDown() {
        BlockingState.clockOverride = null
        runBlocking { BlockingRepo(db).saveList(emptySet()); db.openHelper.writableDatabase.execSQL("DELETE FROM held_notification") }
        screen.shell("cmd notification disallow_listener app.efa/app.efa.NotifHolder")
    }

    @Test fun U8_a_notification_from_a_blocked_app_is_held_while_its_block_is_on() {
        post("held1", "Alex", "free-tonight")
        assertTrue(screen.waitFor(15_000) { heldCount() == 1 }, "the notification was not held")
        val row = runBlocking { db.held().unreviewed() }.single()
        assertEquals(SHELL, row.pkg); assertEquals("Alex", row.title); assertTrue(row.text.contains("free-tonight"), row.text)
        assertTrue(screen.waitFor(10_000) { !inShade("held1") }, "it should be gone from the shade once stored")
    }

    @Test fun U8_a_notification_from_an_app_that_is_not_blocked_comes_through_normally() {
        runBlocking { BlockingRepo(db).saveList(setOf("com.example.other")) }
        clockAt(t(19, 10, 30))
        post("open1", "Sam", "hello")
        Thread.sleep(4_000)
        assertEquals(0, heldCount())
        assertTrue(inShade("open1"), "it should stay in the shade")
    }

    @Test fun U8_a_listed_app_is_not_held_when_the_phone_is_open() {
        clockAt(t(19, 21, 0))                                   // free time
        post("free1", "Sam", "hello")
        Thread.sleep(4_000)
        assertEquals(0, heldCount()); assertTrue(inShade("free1"))
    }

    @Test fun LA2_nothing_is_lost_when_many_arrive_together_and_the_digest_is_in_time_order() {
        for (i in 1..15) post("bulk$i", "Chat$i", "message$i")
        assertTrue(screen.waitFor(40_000) { heldCount() == 15 }, "only ${heldCount()} of 15 were held")
        clockAt(t(19, 21, 0))
        val d = digest(runBlocking { HeldRepo(db).unreviewed() }) { BlockingState.decide(it) }
        assertEquals(15, d.ready.size); assertNull(d.waitingUntil)
        assertEquals(d.ready.map { it.atMs }.sorted(), d.ready.map { it.atMs })
        assertEquals((1..15).map { "Chat$it" }.toSet(), d.ready.map { it.title }.toSet())
    }

    @Test fun LA2_while_the_block_is_still_on_the_digest_waits_and_says_until_when() {
        post("wait1", "Alex", "hi")
        assertTrue(screen.waitFor(15_000) { heldCount() == 1 })
        val d = digest(runBlocking { HeldRepo(db).unreviewed() }) { BlockingState.decide(it) }
        assertTrue(d.ready.isEmpty()); assertEquals(t(19, 12, 15), d.waitingUntil)
    }

    @Test fun LA2_marking_the_digest_seen_keeps_the_rows_for_30_days_then_removes_them() {
        post("seen1", "Alex", "hi")
        assertTrue(screen.waitFor(15_000) { heldCount() == 1 })
        val repo = HeldRepo(db)
        runBlocking {
            val ids = repo.unreviewed().map { it.id }
            repo.markReviewed(ids, 1_000L)
            assertTrue(repo.unreviewed().isEmpty())
            assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM held_notification").use { it.moveToFirst(); it.getInt(0) }, "kept after being seen")
            repo.markReviewed(emptyList(), 1_000L + 31L * 24 * 3_600_000)
            assertEquals(0, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM held_notification").use { it.moveToFirst(); it.getInt(0) }, "removed after 30 days")
        }
    }

    @Test fun LA2_an_unseen_notification_is_never_deleted_however_old() {
        runBlocking {
            val repo = HeldRepo(db)
            repo.add(SHELL, "Shell", "old", "very old", 1_000L)
            repo.markReviewed(emptyList(), System.currentTimeMillis() + 400L * 24 * 3_600_000)
            assertEquals(1, repo.unreviewed().size)
        }
    }

    @Test fun U8_the_listener_ignores_Sparks_own_notifications() {
        screen.shell("pm grant app.efa android.permission.POST_NOTIFICATIONS")
        runBlocking { BlockingRepo(db).saveList(setOf(ctx.packageName)) }
        clockAt(t(19, 10, 30))
        BufferNotifier.update(ctx)                                  // Spark's own notification, while Spark itself is "on the list"
        Thread.sleep(4_000)
        assertEquals(0, heldCount())
        ctx.getSystemService(NotificationManager::class.java).cancel(BufferNotifier.ID)
    }
}

/** The persistent Buffer notification (B2, LA11). */
@RunWith(AndroidJUnit4::class)
class BufferNoticeE2ETest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val screen = TestScreen(inst)
    private lateinit var ctx: Context
    private lateinit var db: EfaDb
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = EfaDb.get(ctx)
        runBlocking { db.seedIfEmpty(System.currentTimeMillis(), LONDON.id); db.openHelper.writableDatabase.execSQL("DELETE FROM task") }
        screen.shell("pm grant app.efa android.permission.POST_NOTIFICATIONS")
        BlockingState.clockOverride = { t(19, 10, 30) }
        runBlocking { BlockingState.refresh(ctx) }
        ctx.getSystemService(NotificationManager::class.java).cancel(BufferNotifier.ID)
    }

    @After fun tearDown() { BlockingState.clockOverride = null; ctx.getSystemService(NotificationManager::class.java).cancel(BufferNotifier.ID) }

    private fun posted(): android.service.notification.StatusBarNotification? =
        ctx.getSystemService(NotificationManager::class.java).activeNotifications.firstOrNull { it.id == BufferNotifier.ID }

    @Test fun B2_LA11_the_Buffer_notification_is_posted_and_persistent_with_a_Quick_add_action() {
        BufferNotifier.update(ctx)
        assertTrue(screen.waitFor(10_000) { posted() != null }, "no notification")
        val n = posted()!!.notification
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0, "it must not be dismissable")
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertTrue(title.startsWith("Buffer "), title)
        assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Now: Notes and problems: Randomness, until 12:15"))
        assertEquals("Quick add", n.actions.single().title.toString())
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
    }

    @Test fun LA11_the_channel_is_silent_with_no_badge() {
        BufferNotifier.update(ctx)
        val ch = ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(BufferNotifier.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_LOW, ch.importance)
        assertFalse(ch.canShowBadge()); assertNull(ch.sound); assertFalse(ch.shouldVibrate())
    }

    @Test fun WG9_the_notification_says_when_it_was_updated_and_is_replaced_not_stacked() {
        BufferNotifier.update(ctx)
        BlockingState.clockOverride = { t(19, 10, 31) }; BufferNotifier.update(ctx)
        fun mine() = ctx.getSystemService(NotificationManager::class.java).activeNotifications.filter { it.id == BufferNotifier.ID }
        // The system posts it a moment after the call returns, so wait for the new text rather than reading the old one.
        assertTrue(screen.waitFor(10_000) { mine().singleOrNull()?.notification?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.contains("Updated 10:31") == true }, mine().toString())
        assertEquals(1, mine().size)
    }

    @Test fun AP6_a_negative_Buffer_says_NEGATIVE_in_the_notification() {
        runBlocking { db.tasks().insert(TaskRow(title = "Huge", dueMs = t(19, 18).toInstant().toEpochMilli(), dueZone = "Europe/London", heaviness = 10, hoursEstimate = 40.0, createdMs = 1)) }
        runBlocking { BlockingState.refresh(ctx) }
        BufferNotifier.update(ctx)
        assertTrue(screen.waitFor(10_000) { posted() != null })
        assertTrue(posted()!!.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().startsWith("Buffer NEGATIVE"))
        runBlocking { db.openHelper.writableDatabase.execSQL("DELETE FROM task") }
    }

    @Test fun X7_without_notification_permission_nothing_is_posted_and_nothing_breaks() {
        // Revoking the real permission would kill this process, so the permission check is switched off for this test.
        BufferNotifier.allowedOverride = { false }
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(BufferNotifier.ID)
            Thread.sleep(500)
            BufferNotifier.update(ctx)
            Thread.sleep(1_000)
            assertNull(posted())
        } finally { BufferNotifier.allowedOverride = null }
    }

    @Test fun LA11_tapping_Quick_add_on_the_notification_opens_quick_add() {
        BufferNotifier.update(ctx)
        assertTrue(screen.waitFor(10_000) { posted() != null })
        posted()!!.notification.actions.single().actionIntent.send()
        assertTrue(screen.waitFor(15_000) { screen.has("Quick add") && screen.has("TITLE") }, "quick add did not open")
    }

    @Test fun P6_WG7_the_notification_has_no_motivational_text_and_no_held_count() {
        BufferNotifier.update(ctx)
        assertTrue(screen.waitFor(10_000) { posted() != null })
        val n = posted()!!.notification
        val all = (n.extras.getCharSequence(Notification.EXTRA_TITLE).toString() + n.extras.getCharSequence(Notification.EXTRA_TEXT) + n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)).lowercase()
        for (w in listOf("goal", "streak", "keep it up", "held", "waiting")) assertFalse(w in all, w)
    }
}

/** The digest and home screens. */
@RunWith(AndroidJUnit4::class)
class DigestScreenTest {
    @get:Rule val rule = createComposeRule()
    private fun item(id: Long, label: String, title: String, atMs: Long) = HeldItem(id, "pkg.$id", label, title, "body $id", atMs)

    @Test fun LA2_the_digest_shows_time_order_with_the_app_name() {
        val d = Digest(listOf(item(1, "Instagram", "Alex", 1_000), item(2, "YouTube", "New video", 2_000)), null)
        rule.setContent { EfaTheme { DigestScreen(d, LONDON, {}, {}, {}) } }
        rule.onNodeWithText("Alex").assertExists(); rule.onNodeWithText("New video").assertExists()
        rule.onNodeWithText("02:00  Instagram", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("01:00, Instagram", substring = true).assertExists()
    }

    @Test fun LA2_a_digest_whose_apps_are_still_blocked_says_until_when() {
        rule.setContent { EfaTheme { DigestScreen(Digest(emptyList(), at(LocalDate.of(2026, 10, 19), LocalTime.of(12, 15))), LONDON, {}, {}, {}) } }
        rule.onNodeWithText("Still blocked until 12:15.").assertExists()
        rule.onNodeWithText("Seen").assertDoesNotExist()
    }

    @Test fun WG7_the_digest_and_the_home_card_show_no_count() {
        val d = Digest((1L..7L).map { item(it, "Instagram", "Msg $it", it * 1_000) }, null)
        rule.setContent { EfaTheme { DigestScreen(d, LONDON, {}, {}, {}) } }
        rule.onNodeWithText("Held notifications").assertExists()
        rule.onAllNodesWithText("7").assertCountEquals(0)
    }

    @Test fun LA2_Seen_marks_them_and_Back_leaves() {
        var seen = false; var back = false
        rule.setContent { EfaTheme { DigestScreen(Digest(listOf(item(1, "Instagram", "Alex", 1_000)), null), LONDON, {}, { seen = true }, { back = true }) } }
        rule.onNodeWithText("Seen").performClick(); rule.onNodeWithText("Back").performClick()
        assertTrue(seen && back)
    }
}

@RunWith(AndroidJUnit4::class)
class HomeLookTest {
    @get:Rule val rule = createComposeRule()
    private fun result(avail: Double, work: Double) = BufferResult(avail, listOf(WorkingLine("t", 5, work, 1.0, work)), false, "Sat 24 Oct 00:10")
    private fun state(b: BufferState, held: Boolean = false) = HomeState(
        date = "Fri 23 Oct", phone = "Phone open", phoneOpen = true, time = "11:20", buffer = b,
        nowLabel = "Now, until 12:00", nowTitle = "Neural Computation lecture", nowDetail = "Haworth HWTH-101. Attendance photo due.", nowOpensBlock = true,
        next = listOf(NextRow("12:00", "Lunch", ""), NextRow("13:00", "Randomness lecture", "Arts ARTS-LR2")),
        freeToSpendPence = null, heldWaiting = held,
    )

    @Test fun the_bundled_fonts_load() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(ctx.resources.getFont(R.font.dm_sans)); assertNotNull(ctx.resources.getFont(R.font.dm_mono_regular)); assertNotNull(ctx.resources.getFont(R.font.dm_mono_medium))
    }

    @Test fun home_follows_the_drawing_date_phone_clock_dial_now_next() {
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.Value(result(17.5, 13.0))), {}, {}) } }
        rule.onNodeWithText("11:20").assertExists()
        rule.onNodeWithText("FRI 23 OCT").assertExists(); rule.onNodeWithText("PHONE OPEN").assertExists()
        rule.onNodeWithText("+4.5h").assertExists(); rule.onNodeWithText("Available 17.5h").assertExists(); rule.onNodeWithText("Work 13.0h").assertExists()
        rule.onNodeWithText("NOW, UNTIL 12:00").assertExists(); rule.onNodeWithText("Neural Computation lecture").assertExists()
        rule.onNodeWithText("Haworth HWTH-101. Attendance photo due.").assertExists()
        rule.onNodeWithText("Lunch").assertExists(); rule.onNodeWithText("Randomness lecture").assertExists(); rule.onNodeWithText("Arts ARTS-LR2").assertExists()
        rule.onNodeWithText("Phone").assertExists(); rule.onNodeWithText("Msgs").assertExists(); rule.onNodeWithText("Maps").assertExists()
        rule.onNodeWithContentDescription("Quick add").assertExists(); rule.onNodeWithText("Setup").assertExists()
    }

    @Test fun AP6_a_negative_Buffer_dial_says_NEGATIVE() {
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.Value(result(1.0, 4.0))), {}, {}) } }
        rule.onNodeWithText("NEGATIVE").assertExists(); rule.onNodeWithText("-3.0h").assertExists()
    }

    @Test fun D6_with_no_deadline_the_dial_says_so_and_is_not_tappable() {
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.NoDeadline), {}, {}) } }
        rule.onNodeWithText("No deadline.").assertExists()
        rule.onNodeWithText("Tap dial for the sum").assertDoesNotExist()
    }

    @Test fun WG7_held_notifications_appear_as_a_card_with_no_count() {
        var opened = false
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.NoDeadline, held = true), {}, {}, onHeld = { opened = true }) } }
        rule.onNodeWithText("Waiting for you").performScrollTo().performClick()
        assertTrue(opened)
    }

    @Test fun no_held_card_when_nothing_is_held() {
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.NoDeadline), {}, {}) } }
        rule.onNodeWithText("Waiting for you").assertDoesNotExist()
    }

    @Test fun U6_the_essentials_report_which_one_was_tapped() {
        val got = mutableListOf<String>()
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.NoDeadline), {}, {}, onEssential = { got += it }) } }
        rule.onNodeWithText("Phone").performScrollTo().performClick(); rule.onNodeWithText("Msgs").performClick(); rule.onNodeWithText("Maps").performClick()
        assertEquals(listOf("phone", "messages", "maps"), got)
    }

    @Test fun X7_an_unreadable_database_says_so_and_shows_nothing_wrong() {
        rule.setContent { EfaTheme { HomeScreen(state(BufferState.NoDeadline).copy(nowTitle = null, unavailable = true), {}, {}) } }
        rule.onNodeWithText("Schedule unavailable").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class HeldMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_5_to_6_also_adds_held_notifications() {
        helper.createDatabase("mig56h.db", 5).close()
        val db = helper.runMigrationsAndValidate("mig56h.db", 6, true, MIGRATION_5_6)
        db.execSQL("INSERT INTO held_notification (pkg,label,title,text,atMs) VALUES ('a','A','t','x',1)")
        db.query("SELECT COUNT(*) FROM held_notification WHERE reviewedAtMs IS NULL").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
