package app.efa

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import android.widget.TextView
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

private val WTODAY = LocalDate.of(2026, 11, 2)
private val WNOW = at(WTODAY, LocalTime.of(10, 0))

private fun wdata(updated: java.time.ZonedDateTime? = WNOW) = WidgetData(
    BufferState.Value(BufferResult(24.0, emptyList(), false, "Wed 4 Nov 17:00")), "Lecture, until 12:00", listOf("12:30 Lunch", "13:00 Lecture"), 1250, null, 12, listOf("Mon 2: 3 from 11:00"), updated)

/** Widget placements on a real database (WG1 to WG4, WG10). */
@RunWith(AndroidJUnit4::class)
class WidgetStorageTest {
    private lateinit var db: EfaDb
    private lateinit var repo: WidgetRepo

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        repo = WidgetRepo(db)
    }
    @After fun close() = db.close()

    @Test fun WG1_the_first_look_fills_in_the_default_four() = runBlocking {
        assertEquals(listOf(WidgetKind.BUFFER, WidgetKind.NOW_NEXT, WidgetKind.FREE_TO_SPEND, WidgetKind.DAY_PLAN), repo.placements(WTODAY).sortedBy { it.position }.map { it.kind })
    }

    @Test fun WG4_an_added_widget_is_marked_with_its_start_day_and_shows_tomorrow() = runBlocking {
        repo.save(addWidget(repo.placements(WTODAY), WidgetKind.SHOPPING, WidgetSize.TWO_BY_ONE, WTODAY))
        assertTrue(repo.rows(WTODAY).first { it.kind == WidgetKind.SHOPPING }.note!!.startsWith("Starts Tue 3 Nov"))
        assertTrue(repo.rows(WTODAY.plusDays(1)).first { it.kind == WidgetKind.SHOPPING }.note == null)
        assertTrue(shownOn(repo.placements(WTODAY).first { it.kind == WidgetKind.SHOPPING }, WTODAY.plusDays(1)).shown)
    }

    @Test fun WG4_a_removed_widget_is_gone_at_once() = runBlocking {
        repo.save(removeWidget(repo.placements(WTODAY), WidgetKind.DAY_PLAN))
        assertTrue(repo.placements(WTODAY).none { it.kind == WidgetKind.DAY_PLAN })
    }

    @Test fun WG4_an_enlargement_is_pending_until_tomorrow() = runBlocking {
        repo.save(resizeWidget(repo.placements(WTODAY), WidgetKind.NOW_NEXT, WidgetSize.TWO_BY_TWO, WTODAY))
        assertTrue(repo.rows(WTODAY).first { it.kind == WidgetKind.NOW_NEXT }.note!!.startsWith("Becomes 2x2"))
        assertEquals(WidgetSize.TWO_BY_TWO, repo.rows(WTODAY.plusDays(1)).first { it.kind == WidgetKind.NOW_NEXT }.size)
    }

    @Test fun WG3_the_order_is_kept() = runBlocking {
        repo.save(moveWidget(repo.placements(WTODAY), WidgetKind.DAY_PLAN, -1))
        assertEquals(listOf(WidgetKind.BUFFER, WidgetKind.NOW_NEXT, WidgetKind.DAY_PLAN, WidgetKind.FREE_TO_SPEND), repo.placements(WTODAY).sortedBy { it.position }.map { it.kind })
    }

    @Test fun WG10_the_placements_survive_the_database_being_closed_and_opened_again() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("wg10.db")
        var d = Room.databaseBuilder(ctx, EfaDb::class.java, "wg10.db").build()
        runBlocking { WidgetRepo(d).save(addWidget(WidgetRepo(d).placements(WTODAY), WidgetKind.WEEK_GLANCE, WidgetSize.TWO_BY_TWO, WTODAY)) }
        d.close()
        d = Room.databaseBuilder(ctx, EfaDb::class.java, "wg10.db").build()
        assertTrue(runBlocking { WidgetRepo(d).placements(WTODAY) }.any { it.kind == WidgetKind.WEEK_GLANCE })
        d.close(); ctx.deleteDatabase("wg10.db")
    }
}

@RunWith(AndroidJUnit4::class)
class WidgetMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_13_to_14_keeps_data_and_adds_the_widget_table() {
        helper.createDatabase("mig1314.db", 13).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig1314.db", 14, true, MIGRATION_13_14)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO widget_placement (kind,size,position,activeFrom) VALUES ('BUFFER','TWO_BY_ONE',0,'2026-11-02')")
    }
}

/** The Android widgets and the edit screen (B2, WG2, WG3, WG7, WG8, WG9). */
@RunWith(AndroidJUnit4::class)
class WidgetViewTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    private fun texts(kind: WidgetKind, data: WidgetData?): List<String> {
        val v = SparkWidget.views(ctx, kind, WidgetSize.TWO_BY_TWO, data, WNOW).apply(ctx, FrameLayout(ctx))
        return listOf(R.id.w0, R.id.w1, R.id.w2, R.id.w3, R.id.w4, R.id.w5, R.id.w6, R.id.w7).mapNotNull { id -> v.findViewById<TextView>(id)?.takeIf { it.visibility == android.view.View.VISIBLE }?.text?.toString() }
    }

    @Test fun B2_WG2_the_android_widgets_say_what_the_app_says() {
        assertEquals(listOf("Buffer +24.0h", "to Wed 4 Nov 17:00"), texts(WidgetKind.BUFFER, wdata()))
        assertEquals(listOf("Free to spend", "£12.50"), texts(WidgetKind.FREE_TO_SPEND, wdata()))
        assertEquals(listOf("Shopping list", "12 left to buy"), texts(WidgetKind.SHOPPING, wdata()))
    }

    @Test fun WG9_a_stale_android_widget_shows_its_age_and_no_number() {
        assertEquals(listOf("Free to spend", "stale since 09:49"), texts(WidgetKind.FREE_TO_SPEND, wdata(updated = WNOW.minusMinutes(11))))
    }

    @Test fun B2_all_seven_widgets_are_registered_with_the_system() {
        val found = ctx.packageManager.queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(ctx.packageName), 0).map { it.activityInfo.name.substringAfterLast('.') }.toSet()
        assertTrue(found.containsAll(setOf("BufferWidget", "NowNextWidget", "FreeToSpendWidget", "DayPlanWidget", "ShoppingWidget", "QuickAddWidget", "WeekGlanceWidget")), found.toString())
    }

    private fun rows() = listOf(WidgetRowUi(WidgetKind.BUFFER, WidgetSize.TWO_BY_ONE, null, true, false), WidgetRowUi(WidgetKind.DAY_PLAN, WidgetSize.TWO_BY_TWO, "Starts Tue 3 Nov", false, true))

    @Test fun WG3_the_edit_screen_adds_removes_moves_and_resizes() {
        val got = ArrayList<String>()
        rule.setContent { EfaTheme { EditWidgetsScreen(rows(), listOf(WidgetKind.SHOPPING), null, { got += "add ${it.label}" }, { got += "remove ${it.label}" }, { k, by -> got += "move ${k.label} $by" }, { k, s -> got += "size ${k.label} ${s.label}" }, {}) } }
        rule.onNodeWithText("Day plan, 2x2").assertExists(); rule.onNodeWithText("Starts Tue 3 Nov").assertExists()
        rule.onNodeWithContentDescription("Move Day plan up").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Remove Day plan").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Make Day plan 1x1").performScrollTo().performClick()
        rule.onNodeWithText("Add Shopping list").performScrollTo().performClick()
        assertEquals(listOf("move Day plan -1", "remove Day plan", "size Day plan 1x1", "add Shopping list"), got)
    }

    @Test fun WG3_the_Buffer_has_no_remove_button() {
        rule.setContent { EfaTheme { EditWidgetsScreen(rows(), emptyList(), null, {}, {}, { _, _ -> }, { _, _ -> }, {}) } }
        rule.onNodeWithContentDescription("Remove Buffer").assertDoesNotExist()
    }
}
