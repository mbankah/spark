package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.efa.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** LC1 on the phone: the setting, and setting the next reminder from the stored calendar. */
@RunWith(AndroidJUnit4::class)
class LectureCodeDeviceTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = EfaDb.get(ctx)

    @After fun back() = runBlocking<Unit> { db.meta().put(MetaRow(META_CODE_REMINDERS, "1")) }

    @Test fun LC1_reminders_are_on_by_default_and_the_setting_turns_them_off() = runBlocking<Unit> {
        val fresh = androidx.room.Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
        try { assertTrue(LectureCodeAlarm.enabled(fresh), "nothing stored means on") } finally { fresh.close() }
        db.meta().put(MetaRow(META_CODE_REMINDERS, "0")); assertFalse(LectureCodeAlarm.enabled(db))
        db.meta().put(MetaRow(META_CODE_REMINDERS, "1")); assertTrue(LectureCodeAlarm.enabled(db))
    }

    @Test fun LC1_the_alarm_is_set_for_the_next_lecture_hour_from_the_stored_calendar_and_cleared_when_switched_off() = runBlocking<Unit> {
        db.seedIfEmpty(System.currentTimeMillis(), efa.LONDON.id)
        db.meta().put(MetaRow(META_CODE_REMINDERS, "1"))
        LectureCodeAlarm.refresh(ctx)
        val next = efa.nextCodeReminder(PlanRepo(db).slots(), BlockingState.now())
        assertNotNull(next, "the seeded timetable has lectures")
        assertEquals(next.at.toInstant().toEpochMilli(), db.meta().get("code_alarm_at_ms")?.toLongOrNull(), "the alarm is for the next reminder")
        db.meta().put(MetaRow(META_CODE_REMINDERS, "0"))
        LectureCodeAlarm.refresh(ctx)
        assertEquals("0", db.meta().get("code_alarm_at_ms"), "switching it off clears the alarm")
    }

    @Test fun LC1_the_setup_screen_has_a_switch_for_the_reminders() {
        var set: Boolean? = null
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, codeReminders = true, onCodeReminders = { set = it }) } }
        rule.onNodeWithContentDescription("Turn the attendance code reminders off").performScrollTo().performClick()
        assertEquals(false, set)
    }
}
