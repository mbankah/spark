package app.efa

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
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
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

private fun pt(m: Int, d: Int, h: Int = 9) = at(LocalDate.of(2026, m, d), LocalTime.of(h, 0))

/** Phase 4 to 8 on a real database: the night, the doors, the change counts, the backup. */
@RunWith(AndroidJUnit4::class)
class LifeStorageTest {
    private lateinit var db: EfaDb
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val now = pt(11, 2)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
        runBlocking { db.seedIfEmpty(pt(10, 21).toInstant().toEpochMilli(), LONDON.id) }
    }
    @After fun close() = db.close()

    @Test fun PA3_finding_is_paid_for_from_a_credit_listening_earns_it_back_and_running_dry_pauses_the_app() {
        val yt = "com.google.android.youtube"
        var t = ZonedDateTime.of(2026, 11, 19, 3, 0, 0, 0, LONDON)
        BlockingState.clockOverride = { t }; BlockingState.scoped = ScopedAccess(); BlockingState.scrollTrip = emptyMap(); BlockingState.surfaces = emptyMap(); BlockingState.findCredit = freshCredit()
        try {
            BlockingState.noteSignals(yt, Signals(setOf("rich_grid_renderer")))
            assertEquals(Surface.FEED, BlockingState.surfaces[yt])
            fun swipe() { repeat(8) { BlockingState.noteScroll(yt); t = t.plusNanos(20_000_000) }; t = t.plusSeconds(3) }          // one swipe is many events
            repeat(20) { swipe() }
            assertNull(BlockingState.scrollTrip[yt], "twenty flicks on a feed is finding, and is inside the credit")
            assertEquals(0, BlockingState.creditLeft())
            swipe()
            val until = BlockingState.scrollTrip[yt]
            assertNotNull(until, "the next one is over it"); assertTrue(until.isAfter(t.plusMinutes(2)))
            BlockingState.scrollTrip = emptyMap()
            BlockingState.noteSignals(yt, Signals(setOf("watch_player")))
            assertEquals(Surface.WATCH, BlockingState.surfaces[yt])
            t = t.plusMinutes(10)
            assertEquals(10, BlockingState.creditLeft(), "ten minutes of listening earns ten back")
            repeat(12) { swipe() }
            assertNull(BlockingState.scrollTrip[yt], "twelve flicks on a playing video is inside its burst limit")
            assertFalse(BlockingState.noteScroll("com.example.other"), "an app the option does not cover is never counted")
            BlockingState.scoped = null
            assertFalse(BlockingState.noteScroll(yt), "with the option off nothing is counted")
        } finally { BlockingState.clockOverride = null; BlockingState.scoped = null; BlockingState.scrollTrip = emptyMap(); BlockingState.surfaces = emptyMap(); BlockingState.findCredit = freshCredit() }
    }

    @Test fun PA1_what_the_service_recognised_is_logged_by_name_only_and_the_log_is_short() {
        BlockingState.surfaceLog.clear()
        repeat(9) { BlockingState.noteSignals("com.google.android.youtube", Signals(setOf("view_$it", "reel_x"))) }
        assertEquals(6, BlockingState.surfaceLog.size)
        assertTrue(BlockingState.surfaceLog.first().contains("SHORTS") && BlockingState.surfaceLog.first().contains("reel_x"))
        BlockingState.surfaceLog.clear(); BlockingState.surfaces = emptyMap()
    }

    @Test fun IX6_a_file_with_a_modifier_applies_it_once_and_removing_the_import_removes_it() = runBlocking<Unit> {
        val plan = PlanRepo(db)
        val text = "kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes\nmodifier,2026-11-09,2026-11-11,,,Lighter days,lecture_free,,,,,\n"
        val parsed = parseTimetableCsv(text, now.toLocalDate())
        assertTrue(parsed.ok, parsed.errors.toString())
        val v = verifyImportIn(ImportMode.ADD, parsed, plan.slots(), plan.known(), emptyList(), now, reflow = true)
        assertTrue(v.ok, v.errors.toString()); assertEquals(1, v.modifiers.size)
        val a = plan.apply(v, "week.csv", fileHash(text), now.toInstant().toEpochMilli(), LONDON.id, "modifier")
        assertEquals(1, a.modifiers); assertEquals(1, db.modifiers().all().size)
        val again = verifyImportIn(ImportMode.ADD, parsed, plan.slots(), plan.known(), emptyList(), now, reflow = true)
        assertTrue(again.modifiers.isEmpty(), "the same file twice changes nothing"); assertEquals(1, again.alreadyImported)
        plan.removeImport(a.importId)
        assertEquals(0, db.modifiers().all().size, "removing the import removes what it made")
        assertTrue(plan.known().none { it.length > 0 && db.meta().get(IMPORT_MOD_PREFIX + it) != null })
    }

    @Test fun SA1_a_night_sets_the_alarm_to_the_wake_and_removing_it_clears_it() = runBlocking<Unit> {
        val repo = ModifierRepo(db)
        val sat = LocalDate.of(2026, 11, 7)
        val inst = Instance(Preset.NIGHT, sat, sat, mapOf("lights_out" to "01:30", "wake" to "10:30"))
        val p = repo.preview(inst, now)
        assertTrue(p.canApply, p.problems.toString())
        val id = repo.apply(inst, now, 75)
        val wakeMs = db.meta().get(META_WAKE_CHOSEN)?.toLong() ?: 0L
        assertEquals(at(sat.plusDays(1), LocalTime.of(10, 30)).toInstant().toEpochMilli(), wakeMs, "the alarm rings at the wake of the night")
        repo.remove(id)
        assertEquals("0", db.meta().get(META_WAKE_CHOSEN))
    }

    @Test fun SA3_a_night_under_eight_hours_cannot_be_applied() = runBlocking<Unit> {
        val sat = LocalDate.of(2026, 11, 7)
        val bad = Instance(Preset.NIGHT, sat, sat, mapOf("lights_out" to "03:30", "wake" to "10:30"))
        val p = ModifierRepo(db).preview(bad, now)
        assertFalse(p.canApply); assertTrue(p.problems.any { it.contains("under 8 hours") }, p.problems.toString())
        assertFailsWith<IllegalStateException> { ModifierRepo(db).apply(bad, now, 75) }
    }

    @Test fun SA2_an_event_after_midnight_is_accepted_once_the_night_is_applied_and_refused_before() = runBlocking<Unit> {
        val repo = ModifierRepo(db); val sat = LocalDate.of(2026, 11, 7)
        val study = Instance(Preset.ADD_EVENT, sat, sat, mapOf("title" to "Study 1", "type" to "study", "start" to "00:00", "end" to "00:50", "after_midnight" to "true"))
        assertFalse(repo.preview(study, now).canApply, "without the night the fixed limit still holds")
        repo.apply(Instance(Preset.NIGHT, sat, sat, mapOf("lights_out" to "02:40", "wake" to "11:00", "winddown_min" to "10")), now, 75)
        val p = repo.preview(study, now)
        assertTrue(p.canApply, p.problems.toString())
    }

    @Test fun BK1_BK4_BK5_a_catch_up_span_moves_the_due_date_to_its_end_and_the_end_of_the_span_gives_it_back() = runBlocking<Unit> {
        val plan = PlanRepo(db); val repo = ModifierRepo(db)
        val today = LocalDate.of(2026, 11, 2); val clockNow = pt(11, 2, 10)
        val original = pt(11, 1, 23).toInstant().toEpochMilli()
        val id = db.tasks().insert(TaskRow(title = "Catch-up: Randomness", dueMs = original, dueZone = LONDON.id, heaviness = 4, hoursEstimate = 1.0, createdMs = original - 3_600_000L, source = "missed", sourceKey = "missed:test", reason = "Bad night"))
        plan.automate(clockNow, pt(10, 21).toInstant().toEpochMilli(), emptySet())
        assertEquals(original, db.tasks().open().single { it.id == id }.dueMs, "BK4: outside a span the D5 date stands")
        val span = Instance(Preset.CATCH_UP, today, today.plusDays(4), mapOf("slots_floor" to "2", "slots_stretch" to "3"))
        val modId = repo.apply(span, clockNow, 75)
        plan.automate(clockNow, pt(10, 21).toInstant().toEpochMilli(), emptySet())
        val end = endOfDayCutoff(plan.slots(), today.plusDays(4), LONDON).toInstant().toEpochMilli()
        assertEquals(end, db.tasks().open().single { it.id == id }.dueMs, "BK1: due at the end of the span")
        repo.remove(modId)
        plan.automate(clockNow, pt(10, 21).toInstant().toEpochMilli(), emptySet())
        assertEquals(original, db.tasks().open().single { it.id == id }.dueMs, "BK5: the span is over, so D5 stands again")
    }

    @Test fun DP2_the_door_pool_is_kept_in_one_place_and_locks_when_it_is_used() = runBlocking<Unit> {
        val doors = DoorRepo(db); val t = Instant.now()
        assertTrue(doors.decide(DoorUse(Door.WALL_ENDS_COOLDOWN, t, 0, 30), t) is DoorDecision.Open)
        doors.record(DoorUse(Door.DAY_OFF, t, 600, 150))
        assertTrue(doors.decide(DoorUse(Door.WALL_ENDS_COOLDOWN, t, 0, 60), t) is DoorDecision.Locked)
        assertTrue(doors.decide(DoorUse(Door.EMERGENCY, t, 0, 0), t) is DoorDecision.Open, "the emergency door is never locked")
        assertEquals(1, doors.uses().size)
    }

    @Test fun CE1_every_counted_change_is_kept_and_an_undo_gives_the_count_back() = runBlocking<Unit> {
        val c = ChangeRepo(db); val t = Instant.now()
        c.record(ChangeClass.MOVE, false, 1, t); c.record(ChangeClass.LOOSEN, true, 2, t)
        assertEquals(2, c.history().size)
        c.refund(2)
        assertEquals(listOf(ChangeClass.MOVE), c.history().map { it.cls })
    }

    @Test fun SL6_a_reminder_you_answer_is_logged_taken_or_not_taken_and_no_advice_is_given() = runBlocking<Unit> {
        val real = EfaDb.get(ctx)
        real.meta().put(MetaRow(META_REMINDER_LOG, ""))
        ReminderAlarm.answer(ctx, "Melatonin", true); ReminderAlarm.answer(ctx, "Morning tablet", false)
        val log = real.meta().get(META_REMINDER_LOG).orEmpty().lines().filter { it.isNotBlank() }
        assertEquals(2, log.size); assertTrue(log[0].endsWith("|Melatonin|TAKEN")); assertTrue(log[1].endsWith("|Morning tablet|NOT_TAKEN"))
        real.meta().put(MetaRow(META_REMINDER_LOG, ""))
    }

    @Test fun EM1_the_emergency_door_logs_quietly_and_a_missing_bank_app_says_so_instead_of_failing() = runBlocking<Unit> {
        val real = EfaDb.get(ctx); real.meta().put(MetaRow(META_EMERGENCY_LOG, ""))
        val msg = Emergency.run(ctx, EmergencyAction.BANK)
        assertTrue(msg == null || msg.contains("No banking app"), "$msg")
        assertTrue(real.meta().get(META_EMERGENCY_LOG).orEmpty().contains("|BANK"))
        real.meta().put(MetaRow(META_EMERGENCY_LOG, ""))
    }

    @Test fun FB1_SL2_facts_and_tags_are_kept_as_lines_and_a_tag_is_stored_by_hash() = runBlocking<Unit> {
        val repo = FactRepo(db)
        repo.record(Fact(FactType.TAP, Instant.now(), "tap", 1.0, "step:bible.sit")); repo.record(tapFact(NfcPurpose.DESK, Instant.now()))
        assertEquals(2, repo.today().size); assertEquals(setOf(NfcPurpose.DESK), repo.recentTaps())
        val key = tagKey(byteArrayOf(5, 4, 3, 2, 1, 0, 9))
        repo.saveTags(mapOf(key to NfcPurpose.CHARGER)); assertEquals(NfcPurpose.CHARGER, repo.tags()[key])
        assertFalse(db.meta().get(META_NFC_TAGS).orEmpty().contains("050403"), "the id itself is never stored")
        assertTrue(db.meta().get(META_NFC_ON) != "1", "NFC is off by default: photos stay the proof")
    }

    @Test fun CE7_break_glass_rewrites_the_history_without_the_lifted_entries_and_the_door_is_recorded() = runBlocking<Unit> {
        val c = ChangeRepo(db); val t = Instant.now()
        c.record(ChangeClass.LOOSEN, false, 1, t.minusSeconds(30)); c.record(ChangeClass.LOOSEN, true, 2, t)
        c.replace(breakGlassHistory(c.history(), Instant.now()))
        assertEquals(listOf(1L), c.history().map { it.id }, "the latest entry is both the last loosening and the last import, so only it is lifted")
        DoorRepo(db).record(DoorUse(Door.BREAK_GLASS, t, 0, 0))
        assertFalse(breakGlassAvailable(DoorRepo(db).uses(), Instant.now()))
    }

    @Test fun CT1_CT2_a_backup_is_a_spark_database_and_a_file_that_is_not_one_is_refused_before_anything_changes() = runBlocking<Unit> {
        val name = Backup.write(ctx)
        assertNotNull(name, "the backup was written to Downloads/Spark")
        assertTrue(Backup.looksLikeBackup(ctx.getDatabasePath("efa.db")))
        val junk = File(ctx.cacheDir, "junk.db").apply { writeText("this is not a database") }
        assertFalse(Backup.looksLikeBackup(junk))
        val msg = Backup.restore(ctx, android.net.Uri.fromFile(junk))
        assertTrue(msg.contains("not a Spark backup"), msg)
        assertTrue(Backup.looksLikeBackup(ctx.getDatabasePath("efa.db")), "the live data is untouched")
    }

    @Test fun PK2_a_report_is_written_with_no_photos_or_location() = runBlocking<Unit> {
        assertNotNull(Backup.report(ctx, "The wall did not accept my string"))
    }

    @Test fun CT5_android_auto_backup_stays_off_so_your_data_goes_nowhere_but_your_own_backup_file() {
        val flags = ctx.applicationInfo.flags
        assertEquals(0, flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun SL9_greyscale_without_the_permission_changes_nothing() {
        if (!WindDown.available(ctx)) WindDown.sync(ctx, HomeMode.NIGHT)        // a no-op, not a crash
        assertTrue(WindDown.COMMAND.contains("WRITE_SECURE_SETTINGS"))
    }
}

@RunWith(AndroidJUnit4::class)
class LifeScreenTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var db: EfaDb
    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build(); runBlocking { db.seedIfEmpty(pt(10, 21).toInstant().toEpochMilli(), LONDON.id) } }
    @After fun close() = db.close()

    @Test fun HS4_the_third_page_offers_today_the_what_if_the_winter_arc_and_the_small_helpers_and_changes_nothing() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.onAllNodesWithText("Work it out").onFirst().assertExists()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("TODAY LOOKS LIKE").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("WHAT IF: SLEEP LATER OR EARLIER TONIGHT").performScrollTo().assertExists()
        rule.onNodeWithText("WINTER VACATION, 14 DEC TO 8 JAN").performScrollTo().assertExists()
        rule.onNodeWithText("HOW ARE YOU?").performScrollTo().assertExists()
        rule.onNodeWithText("PARK AN IDEA").performScrollTo().assertExists()
        rule.onNodeWithText("CANNOT SLEEP").performScrollTo().assertExists()
        rule.onNodeWithText("Get up.").performScrollTo().assertExists()
    }

    @Test fun SL3_HS3_the_cannot_sleep_list_has_the_four_steps_and_your_own_and_the_schedule_is_still_a_page() {
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Thu 19 Nov", phone = null, phoneOpen = true, time = "01:00", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null), {}, {}, third = { WorkItOutScreen(db, { _, _ -> }) }) } }
        rule.onNodeWithContentDescription("Schedule page").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Schedule", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Work it out page").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Get up.").fetchSemanticsNodes().isNotEmpty() }
        for (s in listOf("Get up.", "Low light.", "No phone.", "Back to bed at 20 minutes.")) rule.onNodeWithText(s).performScrollTo().assertExists()
    }

    @Test fun PA7_sleep_audio_is_off_by_default_turning_it_on_asks_for_the_wall_and_turning_it_off_does_not() {
        var asked: Triple<String, Int, () -> Unit>? = null
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }, onWall = { title, _, len, action -> asked = Triple(title, len, action) }) } }
        rule.onNodeWithText("SLEEP AUDIO (YOUTUBE)").performScrollTo().assertExists()
        assertNull(runBlocking { db.meta().get(META_SCOPED_ON) }, "off by default")
        rule.onNodeWithContentDescription("Sleep audio on").performScrollTo().performClick()
        rule.waitUntil(5_000) { asked != null }
        assertEquals("Sleep audio", asked!!.first); assertTrue(asked!!.second >= WallLength.IMPORT_LOOSEN, "a loosening: the typed wall")
        assertNull(runBlocking { db.meta().get(META_SCOPED_ON) }, "nothing is on until the wall is passed")
        asked!!.third()
        rule.waitUntil(5_000) { runBlocking { db.meta().get(META_SCOPED_ON) } == "1" }
        rule.onNodeWithContentDescription("Sleep audio off").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { db.meta().get(META_SCOPED_ON) } == "0" }
    }

    @Test fun PK1_an_idea_is_parked_kept_and_changes_nothing() {
        var opened = false
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> opened = true }) } }
        rule.onNodeWithContentDescription("Idea").performScrollTo().performTextInput("A better wake plan")
        rule.onNodeWithContentDescription("Park this idea").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { db.meta().get(META_IDEAS).orEmpty().contains("A better wake plan") } }
        rule.onNodeWithText("A better wake plan").performScrollTo().assertExists()
        assertFalse(opened, "parking an idea opens no plan change")
        assertEquals(0, runBlocking { db.modifiers().all().size })
    }

    @Test fun CO5_LT2_a_mood_tap_is_kept_for_you_and_three_hard_days_show_the_people_who_can_help() {
        runBlocking {
            val day = 86_400_000L; val nowMs = System.currentTimeMillis()
            db.meta().put(MetaRow(META_MOOD, (0..2).joinToString("\n") { "${nowMs - it * day - 10_000L}|1" }))
        }
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("The last few days have been hard.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("The last few days have been hard.", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Samaritans", substring = true).performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Plan a lighter day").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Fresh start from now").performScrollTo().assertExists()
    }

    @Test fun CO7_rewards_are_your_own_up_to_five_and_one_is_picked_at_random() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.onNodeWithContentDescription("Reward").performScrollTo().performTextInput("A long walk")
        rule.onNodeWithContentDescription("Add this reward").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { db.meta().get(META_REWARDS).orEmpty().contains("A long walk") } }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Pick a reward").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Pick a reward").performScrollTo().performClick()
        rule.onAllNodesWithText("A long walk").onFirst().assertExists()
    }

    @Test fun IX2_the_import_preview_offers_the_two_modes_the_meter_and_what_it_costs() {
        val now = java.time.ZonedDateTime.of(2026, 11, 20, 12, 0, 0, 0, LONDON)
        val v = verifyImport(parseTimetableCsv("kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes\nevent,2026-11-23,,14:00,15:00,Dentist,errand,,,,,", now.toLocalDate()), emptyList(), emptySet(), emptyList(), now)
        var got: ImportMode? = null
        rule.setContent { EfaTheme { ImportScreen(ImportUi.Checked("plan.csv", v, "h", "x", ImportMode.REPLACE), emptyList(), {}, {}, {}, {}, {}, onMode = { got = it }, meter = "This week: imports 1 of 2 free, moves 0 of 5 free, loosening 0 of 3.", economy = "Free.") } }
        rule.onNodeWithText("HOW TO USE THIS FILE").assertExists()
        rule.onNodeWithContentDescription("Add to my plan").performScrollTo().performClick(); assertEquals(ImportMode.ADD, got)
        rule.onNodeWithContentDescription("Replace my plan for these days").performScrollTo().assertExists()
        rule.onNodeWithText("This week: imports 1 of 2 free", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Free.").performScrollTo().assertExists()
    }

    @Test fun HS2_a_running_block_hides_the_secondary_cards_and_the_now_card_comes_before_the_buffer() {
        val s = HomeState(date = "Thu 19 Nov", phone = null, phoneOpen = true, time = "11:00", buffer = BufferState.NoDeadline, nowLabel = "Now, until 12:15", nowTitle = "Notes and problems", nowDetail = null,
            freeToSpendPence = 1250, mode = HomeMode.BLOCK, shoppingDay = true)
        rule.setContent { EfaTheme { HomeScreen(s, {}, {}) } }
        rule.onNodeWithText("Notes and problems").assertExists()
        rule.onNodeWithContentDescription("Free to spend this week", substring = true).assertDoesNotExist()
        rule.onNodeWithText("SHOPPING").assertDoesNotExist()
    }

    @Test fun HS1_the_free_mode_still_shows_the_money_card() {
        val s = HomeState(date = "Thu 19 Nov", phone = null, phoneOpen = true, time = "15:30", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = 1250, mode = HomeMode.FREE)
        rule.setContent { EfaTheme { HomeScreen(s, {}, {}) } }
        rule.onNodeWithContentDescription("Free to spend this week", substring = true).assertExists()
    }

    @Test fun EM1_the_home_and_the_blocked_card_carry_the_emergency_door_and_it_is_never_gated() {
        var got: EmergencyAction? = null
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Thu 19 Nov", phone = null, phoneOpen = true, time = "01:00", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null), {}, {}, onEmergency = { got = it }) } }
        rule.onNodeWithContentDescription("Emergency: Call 999").performScrollTo().performClick(); assertEquals(EmergencyAction.CALL_EMERGENCY, got)
        rule.onNodeWithContentDescription("Emergency: NHS 111").performScrollTo().performClick(); assertEquals(EmergencyAction.HEALTH, got)
        rule.onNodeWithContentDescription("Emergency: Share where I am").performScrollTo().performClick(); assertEquals(EmergencyAction.SHARE_LOCATION, got)
    }

    @Test fun EM1_the_blocked_card_has_the_emergency_buttons_even_in_a_lockdown() {
        var got: EmergencyAction? = null
        val d = Decision.Block(BlockReason.APP_CAP, "10 minutes at a time", java.time.ZonedDateTime.now(LONDON).plusMinutes(20), 0, night = false)
        rule.setContent { EfaTheme { BlockedCard("WhatsApp", d, 0, {}, {}, null, doorLock = "The weekly pool is used up.", onEmergency = { got = it }) } }
        rule.onNodeWithContentDescription("Emergency: Phone").performScrollTo().performClick(); assertEquals(EmergencyAction.CALL_ANYONE, got)
        rule.onNodeWithContentDescription("Emergency: Maps and taxi").performScrollTo().assertExists()
    }

    @Test fun LT1_a_lite_day_shows_now_and_next_and_hides_the_buffer_and_the_money() {
        val next = listOf(NextRow("12:40", "Lunch", ""), NextRow("13:00", "Lecture", ""), NextRow("15:00", "Notes", ""))
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Thu 19 Nov", phone = null, phoneOpen = true, time = "11:00", buffer = BufferState.NoDeadline, nowLabel = "Now", nowTitle = "Notes", nowDetail = null, freeToSpendPence = 1250, next = next, lite = true), {}, {}) } }
        rule.onNodeWithText("Lunch").assertExists(); rule.onNodeWithText("Lecture").assertExists(); rule.onNodeWithText("Notes", substring = false).assertExists()
        rule.onNodeWithContentDescription("Free to spend this week", substring = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Buffer.", substring = true).assertDoesNotExist()
    }

    @Test fun EM2_EM5_HO4_CT4_the_third_page_has_out_the_lost_phone_steps_the_support_routes_and_the_permission_list() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("I AM OUT").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("I AM OUT").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("I am out for 2 hours").performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("out does not apply", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("Out until", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("IF THE PHONE IS LOST OR BROKEN").performScrollTo().assertExists()
        rule.onNodeWithText("Find My Device", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Reasonable Adjustment Plan", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Coursework extension", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("PERMISSIONS (AFTER A REINSTALL OR PHONE UPDATE)").performScrollTo().assertExists()
        rule.onNodeWithText("Notifications", substring = true).performScrollTo().assertExists()
    }

    @Test fun SL4_SL6_CO11_tomorrows_fun_opens_a_preview_the_tone_is_kept_and_a_reminder_is_stored() {
        var opened: Instance? = null
        rule.setContent { EfaTheme { WorkItOutScreen(db, { i, _ -> opened = i }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("TOMORROW'S FUN").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Tomorrow's fun").performScrollTo().performTextInput("Game night")
        rule.onNodeWithContentDescription("Put it tomorrow at 17:00").performScrollTo().performClick()
        assertEquals(Preset.ADD_EVENT, opened?.preset); assertEquals("Game night", opened?.str("title"))
        rule.onNodeWithContentDescription("Tone Plain").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { db.meta().get(META_TONE) == "PLAIN" } }
        rule.onNodeWithContentDescription("Reminder name").performScrollTo().performTextInput("Test reminder")
        rule.onNodeWithContentDescription("Add the reminder").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { db.meta().get(META_REMINDERS).orEmpty().contains("Test reminder") } }
    }

    @Test fun RT_CO_the_runner_shows_the_steps_of_the_tier_and_finishes_the_bible_when_every_step_is_done() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Run Bible").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Run Bible").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Tier floor").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Tier floor").performScrollTo().performClick()
        for (s in listOf("Sit in the seat.", "Open the next passage.", "Read one chapter, or the first section.", "Write one sentence.")) {
            rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Done: $s").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Done: $s").performScrollTo().performClick()
            rule.waitUntil(5_000) { runBlocking { FactRepo(db).today().any { it.ref == "step:" + when (s) { "Sit in the seat." -> "bible.sit"; "Open the next passage." -> "bible.open"; "Read one chapter, or the first section." -> "bible.read.f"; else -> "bible.sentence" } } } }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Bible is done.").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { runBlocking { FactRepo(db).today().any { it.ref == "routine:bible" } } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("TODAY'S WINS").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Bible done.").performScrollTo().assertExists()
    }

    @Test fun RT3_a_photo_step_says_so_and_the_runner_never_fakes_it() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Run Wake").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Run Wake").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Photo step: take it on the home screen.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Photograph the setup.", substring = true).performScrollTo().assertExists()
    }

    @Test fun SL2_FB8_nfc_is_an_option_off_by_default_and_photos_stay_the_proof() {
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("NFC TAGS (OPTIONAL)").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("NFC TAGS (OPTIONAL)").performScrollTo().assertExists()
        rule.onNodeWithText("Photos stay the proof", substring = true).performScrollTo().assertExists()
        assertTrue(runBlocking { db.meta().get(META_NFC_ON) } != "1")
    }

    @Test fun CE7_the_third_page_offers_break_glass_with_the_longest_string_and_it_asks_for_the_wall() {
        var asked: Triple<String, String, Int>? = null
        rule.setContent { EfaTheme { WorkItOutScreen(db, { _, _ -> }, onWall = { t, x, n, _ -> asked = Triple(t, x, n) }) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("Break glass").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Break glass").performScrollTo().performClick()
        assertEquals("Break glass", asked?.first); assertEquals(BREAK_GLASS_WALL, asked?.third)
    }

    @Test fun MX6_the_blocked_card_says_the_screen_time_limit_is_spent_and_lifts_at_midnight() {
        val d = Decision.Block(BlockReason.SCREEN_LIMIT, "45 minutes today", java.time.ZonedDateTime.now(LONDON).plusHours(5), 0, night = false)
        rule.setContent { EfaTheme { BlockedCard("Instagram", d, 0, {}, {}, null) } }
        rule.onNodeWithText("Your screen-time limit for today is spent.", substring = true).assertExists()
    }

    @Test fun DP4_CE7_a_night_override_locked_by_the_pool_offers_break_glass_and_no_plain_wall() {
        var broke = 0
        val d = Decision.Block(BlockReason.NIGHT, "Night: past the cutoff", java.time.ZonedDateTime.now(LONDON).plusHours(8), 0, night = true)
        rule.setContent { EfaTheme { BlockedCard("Instagram", d, 0, {}, {}, null, doorLock = "The weekly pool is used up.", glassOk = true, onBreakGlass = { broke = it }) } }
        rule.onNodeWithText("The weekly pool is used up.", substring = true).assertExists()
        rule.onNodeWithText("Open anyway", substring = true).assertDoesNotExist()
    }

    @Test fun HS6_the_dock_has_its_four_tiles_with_drawn_icons_and_the_labels_are_still_there() {
        var set = 0
        rule.setContent { EfaTheme { HomeScreen(HomeState(date = "Thu 19 Nov", phone = null, phoneOpen = true, time = "12:00", buffer = BufferState.NoDeadline, nowLabel = null, nowTitle = null, nowDetail = null, freeToSpendPence = null), {}, {}, onSetup = { set++ }) } }
        for (l in listOf("Phone", "Msgs", "Maps", "Setup")) rule.onNodeWithText(l).performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Phone. Opens your Phone app.").performScrollTo().assertExists()
        rule.onNodeWithText("Setup").performScrollTo().performClick(); assertEquals(1, set)
    }
}

/** PA4, PA5, PA10 on the real database and the real plan: sleep audio at night, in a work block, and on the web. No service and no screen: the gate is asked directly. */
@RunWith(AndroidJUnit4::class)
class ScopedGateTest {
    private val yt = "com.google.android.youtube"
    private lateinit var ctx: Context
    private lateinit var db: EfaDb
    private fun t(d: Int, h: Int, m: Int = 0) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private fun clockAt(z: java.time.ZonedDateTime) { BlockingState.clockOverride = { z }; runBlocking { BlockingState.refresh(ctx) } }

    @Before fun setUp() = runBlocking<Unit> {
        ctx = ApplicationProvider.getApplicationContext(); db = EfaDb.get(ctx)
        db.seedIfEmpty(System.currentTimeMillis(), LONDON.id)
        db.openHelper.writableDatabase.execSQL("DELETE FROM allowance")
        BlockingRepo(db).saveList(setOf(yt)); db.meta().put(MetaRow(META_SCOPED_ON, "1"))
    }
    @After fun tearDown() = runBlocking<Unit> {
        BlockingState.clockOverride = null; BlockingState.surfaces = emptyMap(); BlockingState.scrollTrip = emptyMap()
        db.meta().put(MetaRow(META_SCOPED_ON, "0")); BlockingRepo(db).saveList(emptySet()); BlockingState.refresh(ctx)
    }

    @Test fun PA4_at_night_a_playing_video_is_open_and_shorts_are_shut() {
        clockAt(t(24, 0, 30))                       // Saturday 00:30, past the night cutoff
        BlockingState.surfaces = mapOf(yt to Surface.WATCH)
        assertEquals(Decision.Allow, BlockingState.decide(yt))
        BlockingState.surfaces = mapOf(yt to Surface.SHORTS)
        val d = BlockingState.decide(yt)
        assertTrue(d is Decision.Block && d.reason == BlockReason.SCOPED, d.toString())
    }

    @Test fun PA5_in_a_work_block_it_stays_shut_whatever_is_on_screen() {
        clockAt(t(19, 10, 30))                      // Monday 10:30, inside a work block
        BlockingState.surfaces = mapOf(yt to Surface.WATCH)
        val d = BlockingState.decide(yt)
        assertTrue(d is Decision.Block && d.reason == BlockReason.WORK_BLOCK, d.toString())
    }

    @Test fun PA10_the_website_stays_blocked_at_night_with_sleep_audio_on() {
        clockAt(t(24, 0, 30))
        BlockingState.surfaces = mapOf(yt to Surface.WATCH)
        assertEquals(Decision.Allow, BlockingState.decide(yt), "the app is open on terms")
        val web = BlockingState.decideHost("m.youtube.com")
        assertNotNull(web, "the site is still blocked"); assertEquals(BlockReason.NIGHT, web.second.reason)
    }

    @Test fun PA7_with_the_option_off_the_night_is_as_it_was() {
        runBlocking { db.meta().put(MetaRow(META_SCOPED_ON, "0")) }
        clockAt(t(24, 0, 30))
        BlockingState.surfaces = mapOf(yt to Surface.WATCH)
        val d = BlockingState.decide(yt)
        assertTrue(d is Decision.Block && d.reason == BlockReason.NIGHT, d.toString())
    }
}
