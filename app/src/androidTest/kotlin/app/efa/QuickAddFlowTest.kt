package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.efa.data.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Instrumented UI flow on a device or emulator. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class QuickAddFlowTest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun clean() {
        // Start each test with no stored tasks; the seeded pattern stays.
        val w = EfaDb.get(ApplicationProvider.getApplicationContext<Context>()).openHelper.writableDatabase
        w.execSQL("DELETE FROM task"); w.execSQL("DELETE FROM overlay_item")
        // Missed blocks since first use come back as work (RS1), and that would change which deadline the Buffer is measured to. Start as if first used now.
        w.execSQL("UPDATE meta SET value = '${System.currentTimeMillis()}' WHERE key = 'first_use_epoch_ms'")
    }

    @Test fun D2_B3_quick_add_title_then_save_gives_a_real_Buffer_with_working() {
        rule.onNodeWithContentDescription("Quick add").performClick()
        rule.onNodeWithContentDescription("Title").performTextInput("Neural Computation coursework")
        rule.onNodeWithContentDescription("Heaviness 8 of 10").performClick()
        // Due tonight at 23:59, so this task is always the deadline the Buffer is measured to, whatever the time of day the test runs.
        rule.onNodeWithText("Today").performClick(); rule.onNodeWithText("23:59").performClick()
        rule.onNodeWithText("Save").performClick()
        // Back home, the Buffer card is a real number and opens its working.
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Tap dial for the sum").fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(SemanticsMatcher("is the Buffer card") { n ->
            (androidx.compose.ui.semantics.SemanticsProperties.ContentDescription in n.config && n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].firstOrNull()?.startsWith("Buffer.") == true)
        }).performClick()
        rule.onNodeWithText("Buffer working").assertExists()
        rule.onNodeWithText("Neural Computation coursework (heaviness 8): 10.0h x 1.0 = 10.0h", substring = true).assertExists()
    }

    @Test fun D2_saving_needs_a_title() {
        rule.onNodeWithContentDescription("Quick add").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Add a title first.").assertExists()
    }

    @Test fun D3_check_canvas_says_so_when_the_app_is_missing() {
        rule.onNodeWithContentDescription("Quick add").performClick()
        rule.onNodeWithText("Check Canvas").performClick()
        val installed = rule.activity.packageManager.getLaunchIntentForPackage("com.instructure.candroid") != null
        if (!installed) rule.onNodeWithText("The Canvas app is not installed on this phone.").assertExists()
    }

    @Test fun G7_typed_quick_add_needs_no_microphone_permission() {
        val granted = rule.activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        rule.onNodeWithContentDescription("Quick add").performClick()
        rule.onNodeWithContentDescription("Title").assertExists()
        assertEquals(granted, rule.activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO))
    }
}

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_1_to_2_keeps_the_pattern_and_adds_tasks() {
        helper.createDatabase("mig.db", 1).apply {
            execSQL("INSERT INTO pattern_slot (day,startMin,endMin,title,type,venue,hardAlarm,notes,edited) VALUES (1,450,470,'Wake, shower','routine','',0,'',0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig.db", 2, true, MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM pattern_slot").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('x',1,'Europe/London',5,6.25,1)")
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertTrue(it.getInt(0) == 1) }
    }

    @Test fun X4_migration_2_to_3_keeps_tasks_and_adds_blocks_and_photos() {
        helper.createDatabase("mig23.db", 2).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('keep me',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig23.db", 3, true, MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO block (key,title,type,startMs,endMs,zone,verified,creditedMin,recovered,weaker) VALUES ('k','t','study',1,2,'Europe/London',0,0,0,0)")
        db.execSQL("INSERT INTO photo (blockKey,atMs,type,locationResult,distanceBand,matchScore,fingerprint,thumbBytes) VALUES ('k',1,'study','not-required','unknown',-1,5,100)")
        db.query("SELECT COUNT(*) FROM block").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
