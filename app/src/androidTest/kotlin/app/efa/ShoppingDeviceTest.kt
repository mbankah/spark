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
import kotlin.test.*

/** The shopping list on a real database and its screen: SH1 to SH5. */
@RunWith(AndroidJUnit4::class)
class ShoppingStorageTest {
    private lateinit var db: EfaDb
    private lateinit var repo: ShoppingRepo

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build()
        repo = ShoppingRepo(db)
    }
    @After fun close() = db.close()

    @Test fun SH1_the_list_is_filled_from_the_master_plan_once() = runBlocking {
        assertEquals(29, repo.items().size); assertEquals(29, repo.items().size)
    }

    @Test fun SH4_SH5_the_totals_on_the_phone_are_the_ledger_totals() = runBlocking {
        val items = repo.items()
        assertEquals(3642, items.filter { !it.pantry }.sumOf { it.pricePence }); assertEquals(2576, items.filter { it.pantry }.sumOf { it.pricePence })
    }

    @Test fun SH1_ticks_stay_and_a_new_shop_unticks_the_weekly_basket_but_not_the_pantry() = runBlocking {
        val items = repo.items()
        repo.tick(items.first { !it.pantry }); repo.tick(items.first { it.pantry })
        assertEquals(2, repo.items().count { it.ticked })
        repo.newShop()
        assertEquals(listOf(true), repo.items().filter { it.ticked }.map { it.pantry })
    }

    @Test fun SH3_a_price_from_a_receipt_replaces_the_estimate_and_stops_being_marked_as_one() = runBlocking {
        val est = repo.items().first { it.estimate }
        repo.price(est, 480)
        val after = repo.items().first { it.id == est.id }
        assertEquals(480, after.pricePence); assertFalse(after.estimate)
        repo.price(after, 0); assertEquals(480, repo.items().first { it.id == est.id }.pricePence)       // not a price
    }
}

@RunWith(AndroidJUnit4::class)
class ShoppingMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_12_to_13_keeps_data_and_adds_the_shopping_table() {
        helper.createDatabase("mig1213.db", 12).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig1213.db", 13, true, MIGRATION_12_13)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO shopping_item (name,pricePence,estimate,pantry,ticked) VALUES ('Eggs',265,0,0,0)")
    }
}

@RunWith(AndroidJUnit4::class)
class ShoppingScreenTest {
    @get:Rule val rule = createComposeRule()
    private val items = listOf(ShoppingRow(1, "Eggs", 265, false, false), ShoppingRow(2, "Wraps", 220, true, false), ShoppingRow(3, "Rice", 579, false, true))

    @Test fun SH4_the_screen_shows_the_basket_total_and_the_room_left_in_the_food_budget() {
        rule.setContent { EfaTheme { ShoppingScreen(items, {}, { _, _ -> }, {}, {}) } }
        rule.onNodeWithText("Weekly basket £4.85").assertExists(); rule.onNodeWithText("£35.15 left of the £40.00 food budget.").assertExists()
    }

    @Test fun SH3_an_estimate_is_labelled_and_the_pantry_is_a_separate_one_off_cost() {
        rule.setContent { EfaTheme { ShoppingScreen(items, {}, { _, _ -> }, {}, {}) } }
        rule.onNodeWithText("£2.20  ESTIMATE").assertExists(); rule.onNodeWithText("Pantry £5.79. A one-off cost, not part of the weekly budget.").performScrollTo().assertExists()
    }

    @Test fun SH1_ticking_and_changing_a_price_report_back() {
        var ticked: Long? = null; var priced: Pair<Long, Int>? = null
        rule.setContent { EfaTheme { ShoppingScreen(items, { ticked = it.id }, { r, p -> priced = r.id to p }, {}, {}) } }
        rule.onNodeWithContentDescription("Tick off Eggs").performScrollTo().performClick(); assertEquals(1L, ticked)
        rule.onNodeWithContentDescription("Price up 10 pence for Eggs").performScrollTo().performClick(); assertEquals(1L to 275, priced)
    }
}
