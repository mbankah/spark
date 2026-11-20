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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime
import kotlin.test.*

/** CF1 to CF5, VR15, SR1 on the device: the store, the card, the notification, the handler for the block-end question, and the start reward. */
@RunWith(AndroidJUnit4::class)
class ConfirmDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val db: EfaDb get() = EfaDb.get(ctx)

    private fun studyToday(): DatedSlot {
        val today = ZonedDateTime.now(LONDON).toLocalDate()
        return (0L..6L).asSequence().map { today.plusDays(it) }.flatMap { d -> slotsFor(WeekB.slots, d).asSequence() }.first { it.slot.type == "study-standard" || it.slot.type == "study-light" }
    }

    @Before fun clean() {
        runBlocking { db.seedIfEmpty(System.currentTimeMillis(), LONDON.id) }          // the plan the block keys are looked up in
        val w = db.openHelper.writableDatabase
        w.execSQL("DELETE FROM meta WHERE `key` LIKE 'confirm:%'"); w.execSQL("DELETE FROM meta WHERE `key` LIKE 'vr:%'"); w.execSQL("DELETE FROM meta WHERE `key` LIKE 'run:%'")
    }

    @After fun after() { clean(); VrNotifier.cancel(ctx) }

    private val q = Confirm("t:1", "test", "Pick one", "Which?", listOf(ConfirmOption("a", "First"), ConfirmOption("b", "Second", true)), 1_000L)

    @Test fun CF2_the_store_raises_a_question_once_answers_it_once_and_keeps_it() = runBlocking {
        val repo = ConfirmRepo(db)
        assertNotNull(repo.raise(q)); assertNull(repo.raise(q))
        assertEquals(listOf("t:1"), repo.open().map { it.id })
        assertNull(repo.answer("t:1", "zzz", 5L))
        assertEquals("a", repo.answer("t:1", "a", 5L)!!.answer)
        assertNull(repo.answer("t:1", "b", 6L)); assertNull(repo.raise(q), "an answered question is not asked again")
        assertTrue(repo.open().isEmpty())
        repo.raise(q.copy(id = "t:2")); repo.withdraw("t:2", 9L)
        assertEquals(CONFIRM_WITHDRAWN, repo.get("t:2")!!.answer)
    }

    @Test fun CF1_the_card_shows_the_question_and_one_button_per_option() {
        var got: Pair<String, String>? = null
        rule.setContent { EfaTheme { ConfirmCard(q) { id, o -> got = id to o } } }
        rule.onNodeWithText("Pick one").assertExists(); rule.onNodeWithText("Which?").assertExists()
        rule.onNodeWithText("Second").performClick()
        assertEquals("t:1" to "b", got)
    }

    @Test fun CF1_the_notification_has_a_button_for_each_option() {
        val n = ConfirmNotifier.build(ctx, q)
        assertEquals("Pick one", n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertEquals(listOf("First", "Second"), n.actions.map { it.title.toString() })
    }

    @Test fun VR15_a_cycle_still_running_after_its_block_asks_once_and_stop_ends_it() = runBlocking {
        val ds = studyToday(); val end = ds.end.toInstant().toEpochMilli()
        VrRepo(db).start(ds, end - 10 * 60_000L)
        val run = VrRepo(db).get(ds)!!
        BlockEndAsk.check(ctx, ds, run, end + 60_000L); BlockEndAsk.check(ctx, ds, run, end + 120_000L)
        val open = ConfirmRepo(db).open()
        assertEquals(1, open.size, "asked once"); assertEquals(VR_BLOCKEND_KIND, open.single().kind)
        assertTrue(open.single().body.contains("What now?"))
        val out = ConfirmActions.answer(ctx, open.single().id, VR_BLOCKEND_STOP, end + 180_000L)
        assertEquals("The cycle is stopped.", out?.message)
        assertEquals(VrStatus.DONE, vrView(VrRepo(db).get(ds), end + 180_001L).status)
        assertTrue(ConfirmRepo(db).open().isEmpty())
        BlockEndAsk.check(ctx, ds, VrRepo(db).get(ds), end + 240_000L)
        assertTrue(ConfirmRepo(db).open().isEmpty(), "a stopped cycle is not asked about")
    }

    @Test fun VR15_move_hands_the_minutes_left_to_the_screen_and_run_on_changes_nothing() = runBlocking {
        val ds = studyToday(); val end = ds.end.toInstant().toEpochMilli()
        VrRepo(db).start(ds, end - 10 * 60_000L)
        BlockEndAsk.check(ctx, ds, VrRepo(db).get(ds), end + 60_000L)
        val id = ConfirmRepo(db).open().single().id
        val moved = ConfirmActions.answer(ctx, id, VR_BLOCKEND_MOVE, end + 90_000L)!!
        assertTrue(moved.then!!.startsWith("slide:") && moved.then!!.removePrefix("slide:").toInt() in 5..130, moved.then)
        assertEquals(VrStatus.RUNNING, vrView(VrRepo(db).get(ds), end + 90_001L).status, "the cycle is not stopped by asking to move the day")
    }

    @Test fun CF4_a_question_whose_cycle_is_no_longer_running_is_closed_by_the_sweep() = runBlocking {
        val ds = studyToday(); val end = ds.end.toInstant().toEpochMilli()
        VrRepo(db).start(ds, end - 10 * 60_000L)
        BlockEndAsk.check(ctx, ds, VrRepo(db).get(ds), end + 60_000L)
        assertEquals(1, ConfirmRepo(db).open().size)
        ConfirmActions.sweep(ctx, end + 61_000L); assertEquals(1, ConfirmRepo(db).open().size, "still running: still asked")
        VrRepo(db).stop(ds, end + 70_000L)
        ConfirmActions.sweep(ctx, end + 80_000L); assertTrue(ConfirmRepo(db).open().isEmpty())
    }

    @Test fun SR1_a_held_start_of_a_block_or_of_a_cycle_earns_two_minutes_and_one_run_record_holds_it() = runBlocking {
        val ds = studyToday(); val now = ZonedDateTime.now(LONDON)
        val before = RewardsRepo(db).earnings(now).sumOf { it.minutes }
        VrRepo(db).start(ds, now.toInstant().toEpochMilli() - 1_000L)
        assertNotNull(RunRepo(db).get(ds)?.startMs, "the cycle start is the block start: the Coach reads it")
        assertEquals(before + START_REWARD_MIN, RewardsRepo(db).earnings(now).sumOf { it.minutes })
        VrRepo(db).start(ds, now.toInstant().toEpochMilli())
        assertEquals(before + START_REWARD_MIN, RewardsRepo(db).earnings(now).sumOf { it.minutes }, "a second start of the same block pays nothing")
    }
}
