package app.efa

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
import kotlin.test.*

/** The block list, the restore screen and the storage behind blocking. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class BlockSetupScreenTest {
    @get:Rule val rule = createComposeRule()

    private val apps = listOf(AppInfo("com.instagram.android", "Instagram"), AppInfo("com.android.chrome", "Chrome"), AppInfo("com.google.android.youtube", "YouTube"))

    @Test fun K1_M6_the_installed_social_apps_start_selected_and_others_do_not() {
        var saved: Set<String>? = null
        rule.setContent { EfaTheme { BlockListScreen(apps, null, { saved = it }, {}) } }
        rule.onNodeWithContentDescription("Instagram, blocked").assertExists()
        rule.onNodeWithContentDescription("YouTube, blocked").assertExists()
        rule.onNodeWithContentDescription("Chrome, not blocked").assertExists()
        rule.onNodeWithText("Save list").performClick()
        assertEquals(setOf("com.instagram.android", "com.google.android.youtube"), saved)
    }

    @Test fun K2_the_person_can_add_and_remove_apps_before_saving() {
        var saved: Set<String>? = null
        rule.setContent { EfaTheme { BlockListScreen(apps, null, { saved = it }, {}) } }
        rule.onNodeWithContentDescription("Chrome, not blocked").performClick()
        rule.onNodeWithContentDescription("Instagram, blocked").performClick()
        rule.onNodeWithText("Save list").performClick()
        assertEquals(setOf("com.android.chrome", "com.google.android.youtube"), saved)
    }

    @Test fun X1_the_screen_says_calls_maps_banking_and_authenticator_are_never_closed() {
        rule.setContent { EfaTheme { BlockListScreen(apps, null, {}, {}) } }
        rule.onNodeWithText("never closed", substring = true).assertExists()
    }

    @Test fun K2_M7_changing_a_saved_list_needs_the_wall_of_75_characters() {
        rule.setContent { EfaTheme { ListWallGate({}, {}) } }
        rule.onNodeWithText("TYPE THIS EXACTLY, BY HAND (0 OF 75)").assertExists()
    }

    @Test fun K2_M7_passing_the_list_wall_by_hand_continues() {
        var passed = false
        rule.setContent { EfaTheme { ListWallGate({ passed = true }, {}) } }
        val target = rule.onNodeWithContentDescription("Characters to type", substring = true).fetchSemanticsNode().config
            .let { it[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first().removePrefix("Characters to type: ") }
        assertEquals(75, target.length)
        val f = rule.onNodeWithContentDescription("Type the characters here")
        target.forEach { f.performTextInput(it.toString()) }
        rule.waitUntil(5_000) { passed }
    }

    @Test fun U4_the_restore_screen_says_blocking_is_off_and_how_to_fix_it() {
        var opened = false
        rule.setContent { EfaTheme { RestoreScreen({ opened = true }, {}) } }
        rule.onNodeWithText("Blocking is off").assertExists()
        rule.onNodeWithText("Switch it back on", substring = true).assertExists()
        rule.onNodeWithText("Open accessibility settings").performClick()
        assertTrue(opened)
    }

    @Test fun U1_setup_shows_whether_the_service_is_on_and_offers_the_settings_page_when_it_is_off() {
        var settings = false
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, blocking = BlockingStatus(false, null), onOpenAccessibility = { settings = true }) } }
        rule.onNodeWithText("Off. Blocking does nothing", substring = true).assertExists()
        rule.onNodeWithText("Open accessibility settings").performClick()
        assertTrue(settings)
        rule.onNodeWithText("Block list: not chosen yet.").assertExists()
    }

    @Test fun U1_setup_says_on_and_shows_the_list_size_and_that_changing_it_costs_75_characters() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, blocking = BlockingStatus(true, 3)) } }
        rule.onNodeWithText("On. Spark can see which app is in front.").assertExists()
        rule.onNodeWithText("Block list: 3 apps. Fixed, so changing it takes 75 typed characters.").assertExists()
        rule.onNodeWithText("Open accessibility settings").assertDoesNotExist()
    }
}

@RunWith(AndroidJUnit4::class)
class BlockingStorageTest {
    private lateinit var db: EfaDb
    private lateinit var repo: BlockingRepo
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        repo = BlockingRepo(db)
    }
    @After fun close() = db.close()

    @Test fun K2_the_list_is_saved_and_read_back() = runBlocking {
        assertNull(repo.list())
        repo.saveList(setOf("b.pkg", "a.pkg")); assertEquals(setOf("a.pkg", "b.pkg"), repo.list())
    }

    @Test fun LA9_an_app_opened_through_the_wall_is_open_for_30_minutes_then_blocked_again() = runBlocking {
        val now = t(19, 10, 30)
        repo.openApp("a.pkg", now, 75, null, BlockRepo(ApplicationProvider.getApplicationContext(), db, WeekB.slots))
        assertEquals(now.plusMinutes(30), repo.allowances(now).appUntil["a.pkg"])
        assertTrue(repo.allowances(now.plusMinutes(31)).appUntil.isEmpty())
    }

    @Test fun LA9_W2_opening_an_app_in_a_work_block_breaks_that_block_and_keeps_the_log() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val blocks = BlockRepo(ctx, db, WeekB.slots)
        val ds = nowAndNext(WeekB.slots, t(19, 10, 30)).now!!
        repo.openApp("a.pkg", t(19, 10, 30), 75, ds, blocks)
        assertNotNull(blocks.load(ds)?.brokenAtMs)
        assertEquals(1, db.allowances().active(0).size)
    }

    @Test fun LA9_opening_an_app_outside_a_work_block_breaks_nothing() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val blocks = BlockRepo(ctx, db, WeekB.slots)
        val ds = nowAndNext(WeekB.slots, t(19, 7, 40)).now!!        // getting ready
        repo.openApp("a.pkg", t(19, 7, 40), 75, ds, blocks)
        assertNull(blocks.load(ds))
    }

    @Test fun N3_N4_night_overrides_are_counted_so_the_next_string_is_longer() = runBlocking {
        val cutoff = t(24, 0, 10)
        assertEquals(0, repo.nightUsesSince(cutoff))
        repo.nightOverride(t(24, 0, 30), 30, 75)
        repo.nightOverride(t(24, 1, 10), 30, 100)
        assertEquals(2, repo.nightUsesSince(cutoff))
        assertEquals(3, repo.nightUsesSince(cutoff).plus(1))
        assertEquals(125, WallLength.nightOverride(repo.nightUsesSince(cutoff) + 1))
    }

    @Test fun N3_a_night_override_opens_all_listed_apps_until_it_ends() = runBlocking {
        repo.nightOverride(t(24, 0, 30), 30, 75)
        val a = repo.allowances(t(24, 0, 31))
        assertEquals(t(24, 1, 0), a.nightUntil)
        assertNull(repo.allowances(t(24, 1, 1)).nightUntil)
    }

    @Test fun X5_U4_switching_blocking_off_is_logged_once_and_the_restore_once() = runBlocking {
        repo.noteServiceState(listSet = true, serviceOn = false, nowMs = 1_000L)
        repo.noteServiceState(listSet = true, serviceOn = false, nowMs = 2_000L)         // still off: no second row
        repo.noteServiceState(listSet = true, serviceOn = true, nowMs = 3_000L)
        repo.noteServiceState(listSet = true, serviceOn = true, nowMs = 4_000L)          // still on: no second row
        assertEquals(listOf("blocking_off", "blocking_restored"), db.tamper().all().map { it.kind })
    }

    @Test fun X5_with_no_list_chosen_an_off_service_is_not_tamper() = runBlocking {
        repo.noteServiceState(listSet = false, serviceOn = false, nowMs = 1_000L)
        assertTrue(db.tamper().all().isEmpty())
    }

    @Test fun ST4_the_tamper_log_keeps_one_year() = runBlocking {
        val year = StoragePolicy.TAMPER_LOG_KEEP.toMillis()
        db.tamper().insert(TamperRow(atMs = 1_000L, kind = "blocking_off", detail = "old"))
        repo.noteServiceState(listSet = true, serviceOn = false, nowMs = 1_000L + year + 5_000)
        assertTrue(db.tamper().all().none { it.detail == "old" }, "the old row should be deleted")
        assertEquals(1, db.tamper().all().size)
    }
}

@RunWith(AndroidJUnit4::class)
class BlockMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_5_to_6_keeps_blocks_and_adds_allowances_and_the_tamper_log() {
        helper.createDatabase("mig56.db", 5).apply {
            execSQL("INSERT INTO block (key,title,type,startMs,endMs,zone,verified,creditedMin,recovered,weaker) VALUES ('k','t','study',1,2,'Europe/London',1,60,0,0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig56.db", 6, true, MIGRATION_5_6)
        db.query("SELECT COUNT(*), MAX(brokenAtMs) FROM block").use { it.moveToFirst(); assertEquals(1, it.getInt(0)); assertTrue(it.isNull(1)) }
        db.execSQL("INSERT INTO allowance (kind,pkg,untilMs,atMs,wallLength) VALUES ('app','a.pkg',2,1,75)")
        db.execSQL("INSERT INTO tamper (atMs,kind,detail) VALUES (1,'blocking_off','x')")
    }
}
