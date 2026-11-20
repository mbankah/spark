package app.efa

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.efa.data.*
import efa.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

internal fun jpeg(seed: Int, gps: Boolean = false): ByteArray {
    val bmp = Bitmap.createBitmap(1280, 960, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp); c.drawColor(Color.WHITE)
    val p = Paint()
    val rnd = java.util.Random(seed.toLong())
    repeat(60) { p.color = Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)); c.drawRect(rnd.nextInt(1200).toFloat(), rnd.nextInt(900).toFloat(), rnd.nextInt(1280).toFloat(), rnd.nextInt(960).toFloat(), p) }
    val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
    val bytes = out.toByteArray()
    if (!gps) return bytes
    val f = File.createTempFile("gps", ".jpg"); f.writeBytes(bytes)
    ExifInterface(f.absolutePath).apply { setLatLong(52.4508, -1.9305); saveAttributes() }
    return f.readBytes().also { f.delete() }
}

/** Repository rules on a device: Room, bitmaps and files are real; the clock is passed in. */
@RunWith(AndroidJUnit4::class)
class BlockRepoTest {
    private lateinit var db: EfaDb
    private lateinit var repo: BlockRepo
    private lateinit var ctx: Context
    private val day = LocalDate.of(2026, 10, 19)
    private val cycle get() = slotsFor(WeekB.slots, day).first { it.slot.title == "Notes and problems: Randomness" }   // 10:10 to 12:15
    private fun t(h: Int, m: Int) = at(day, LocalTime.of(h, m))

    @Before fun open() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
        repo = BlockRepo(ctx, db, WeekB.slots)
        File(ctx.filesDir, "thumbs").deleteRecursively()
    }
    @After fun close() = db.close()

    @Test fun W1_photo_before_the_timer_ends_is_refused_and_stores_nothing() = runBlocking {
        val v = repo.submitPhoto(cycle, jpeg(1), t(11, 0))
        assertEquals(Reason.TIMER_RUNNING, v.reason)
        assertEquals(0, db.photos().fingerprints().size); assertNull(repo.load(cycle))
    }

    @Test fun W1_photo_before_the_hold_is_refused() = runBlocking {
        assertEquals(Reason.NEEDS_HOLD, repo.submitPhoto(cycle, jpeg(1), t(12, 20)).reason)
    }

    @Test fun W1_timer_then_hold_then_photo_verifies_and_keeps_only_a_thumbnail() = runBlocking {
        repo.recordHold(cycle, 1L)
        val v = repo.submitPhoto(cycle, jpeg(2), t(12, 20))
        assertTrue(v.verified); assertEquals(125, v.creditedMinutes)
        assertTrue(repo.load(cycle)!!.verified)
        val photo = db.photos().withThumbs().single()
        val f = File(photo.thumbPath!!)
        assertTrue(f.exists()); assertTrue(f.length() <= StoragePolicy.THUMB_BYTES, "thumbnail is ${f.length()} bytes")   // ST1
        assertEquals(1, File(ctx.filesDir, "thumbs").listFiles()!!.size, "only the thumbnail exists, not the full photo")
        assertEquals("not-required", photo.locationResult); assertEquals(-1, photo.matchScore)
    }

    @Test fun ST7_the_thumbnail_carries_no_location() {
        val p = ProofProcessor.process(jpeg(3, gps = true))
        val f = File.createTempFile("thumb", ".jpg"); f.writeBytes(p.thumb)
        val exif = ExifInterface(f.absolutePath)
        assertNull(exif.latLong); assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)); f.delete()
    }

    @Test fun ST2_a_photo_reused_for_another_block_is_rejected() = runBlocking {
        repo.recordHold(cycle, 1L); val same = jpeg(4)
        assertTrue(repo.submitPhoto(cycle, same, t(12, 20)).verified)
        val other = slotsFor(WeekB.slots, day).first { it.slot.title == "Notes and problems: Randomness" }.let { slotsFor(WeekB.slots, LocalDate.of(2026, 10, 23)).first { s -> s.slot.type == "study-standard" } }
        repo.recordHold(other, 1L)
        val v = repo.submitPhoto(other, same, at(LocalDate.of(2026, 10, 23), LocalTime.of(17, 45)))
        assertEquals(Reason.DUPLICATE, v.reason); assertFalse(repo.load(other)!!.verified)
    }

    @Test fun B7_a_late_proof_is_recovered_and_capped() = runBlocking {
        repo.recordHold(cycle, 1L)
        val v = repo.submitPhoto(cycle, jpeg(5), t(14, 0))      // 1 h 45 after the end
        assertTrue(v.verified && v.recovered); assertEquals(60, v.creditedMinutes)
        assertTrue(repo.load(cycle)!!.recovered)
    }

    @Test fun ST1_ST4_expired_thumbnails_are_deleted_and_verification_stays() = runBlocking {
        repo.recordHold(cycle, 1L); repo.submitPhoto(cycle, jpeg(6), t(12, 20))
        val photo = db.photos().withThumbs().single(); val file = File(photo.thumbPath!!)
        val later = Instant.ofEpochMilli(photo.atMs).plus(java.time.Duration.ofDays(15))
        assertEquals(1, repo.cleanThumbs(later))
        assertFalse(file.exists()); assertTrue(db.photos().withThumbs().isEmpty())
        assertTrue(repo.load(cycle)!!.verified, "deleting a thumbnail never changes verification (ST5)")
        assertEquals(1, db.photos().fingerprints().size, "the forever record stays (ST2)")
    }

    @Test fun ST5_clear_thumbnails_keeps_the_records_and_verification() = runBlocking {
        repo.recordHold(cycle, 1L); repo.submitPhoto(cycle, jpeg(7), t(12, 20))
        assertEquals(1, repo.clearAllThumbs())
        assertTrue(repo.load(cycle)!!.verified); assertEquals(1, db.photos().fingerprints().size)
    }

    @Test fun G1_a_code_block_cannot_be_verified_with_a_photo() = runBlocking {
        val code = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "code" }
        repo.recordHold(code, 1L)
        val v = repo.submitPhoto(code, jpeg(8), code.end.plusMinutes(1))
        assertEquals(Reason.COMMIT_CHECK_UNAVAILABLE, v.reason); assertNull(repo.load(code)?.takeIf { it.verified })
    }

    @Test fun Y1_a_gym_photo_counts_only_inside_its_window() = runBlocking {
        val gym = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 22)).first { it.slot.type == "gym" }      // Thursday 18:20 to 19:05
        assertEquals(Reason.OUTSIDE_WINDOW, repo.submitPhoto(gym, jpeg(9), gym.end.plusMinutes(1)).reason)
        assertTrue(repo.submitPhoto(gym, jpeg(10), gym.start.plusMinutes(10)).verified)
    }

    @Test fun G5_an_undecodable_capture_fails_plainly_and_changes_nothing() = runBlocking {
        repo.recordHold(cycle, 1L)
        assertFailsWith<CaptureFailed> { repo.submitPhoto(cycle, byteArrayOf(1, 2, 3), t(12, 20)) }
        assertFalse(repo.load(cycle)!!.verified)
    }
}

/** VR1 to VR6 on a device: the cycle is kept in the database, and a finished cycle books its weekly remedial once. */
@RunWith(AndroidJUnit4::class)
class VrRepoTest {
    private lateinit var db: EfaDb
    private val day = LocalDate.of(2026, 11, 20)
    private val ds get() = slotsFor(WeekB.slots, day).first { it.slot.type == "study-standard" }

    @Before fun open() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), EfaDb::class.java).build() }
    @After fun close() { db.close() }

    @Test fun VR1_a_cycle_is_kept_and_continues_where_it_was() = runBlocking {
        val repo = VrRepo(db); val z = ds.start.zone
        assertNull(repo.get(ds)); repo.start(ds, 1_000L)
        assertEquals(listOf(1_000L), repo.get(ds)!!.boundaries)
        repo.start(ds, 5_000L); assertEquals(listOf(1_000L), repo.get(ds)!!.boundaries, "starting again changes nothing")
        repo.advance(ds, 2_000L, z); assertEquals(listOf(1_000L, 2_000L), repo.get(ds)!!.boundaries)
    }

    @Test fun VR6_a_finished_cycle_books_30_minutes_of_remedial_once_due_on_the_sunday() = runBlocking {
        val repo = VrRepo(db); val z = ds.start.zone
        repo.start(ds, 0L)
        var t = 0L
        repeat(vrPhases().size) { t += 60_000L; repo.advance(ds, t, z) }
        val tasks = db.tasks().open().filter { it.source == "remedial" }
        assertEquals(1, tasks.size, tasks.toString())
        assertEquals(REMEDIAL_TITLE, tasks.single().title); assertEquals(0.5, tasks.single().hoursEstimate, 0.0001)
        assertEquals(remedialDue(day, null, z).toInstant().toEpochMilli(), tasks.single().dueMs)
        repo.advance(ds, t + 60_000L, z)
        assertEquals(1, db.tasks().open().count { it.source == "remedial" }, "a finished cycle is not booked twice")
    }

    @Test fun VR8_with_the_Red_Engine_on_a_finished_cycle_books_no_remedial_and_open_ones_are_removed() = runBlocking {
        val repo = VrRepo(db); val z = ds.start.zone
        repo.start(ds, 0L); var t = 0L
        repeat(vrPhases().size) { t += 60_000L; repo.advance(ds, t, z) }
        assertEquals(1, db.tasks().open().count { it.source == "remedial" })
        db.tasks().insertOnce(TaskRow(title = REMEDIAL_TITLE, dueMs = 1L, dueZone = z.id, heaviness = 3, hoursEstimate = 0.5, createdMs = 0L, source = "remedial", sourceKey = "remedial:begun", doneMin = 10))
        assertEquals(1, repo.removeOpenRemedial(), "the one not started goes; the one with minutes done stays")
        assertEquals(1, db.tasks().open().count { it.source == "remedial" })
        repo.saveConfig(repo.config().copy(redEngine = true))
        val other = slotsFor(WeekB.slots, LocalDate.of(2026, 11, 21)).first { it.slot.type.startsWith("study") }
        repo.start(other, 0L); t = 0L
        repeat(vrPhases().size) { t += 60_000L; repo.advance(other, t, other.start.zone) }
        assertEquals(1, db.tasks().open().count { it.source == "remedial" }, "no new remedial is booked")
    }

    @Test fun VR10_a_cycle_started_with_the_override_begins_at_notes_and_a_running_cycle_keeps_its_plan() = runBlocking {
        val repo = VrRepo(db); val z = ds.start.zone
        repo.start(ds, 1_000L)
        val normal = repo.get(ds)!!
        assertEquals("", normal.overridden)
        repo.saveConfig(VrConfig.SESSION)
        assertEquals(normal, repo.get(ds), "a cycle already running is not changed")
        val other = slotsFor(WeekB.slots, LocalDate.of(2026, 11, 21)).first { it.slot.type.startsWith("study") }
        repo.start(other, 2_000L)
        val run = repo.get(other)!!
        assertEquals("VR", run.overridden); assertEquals(5, run.boundaries.size); assertEquals(vrPlan(VrConfig.SESSION), run.planMinutes)
        assertEquals("N Notes", vrView(run, 3_000L).title)
        var t = 2_000L
        for (p in vrPhases(run.planMinutes).drop(4)) { t += p.plannedMin * 60_000L; repo.advance(other, t, z) }
        assertEquals(VrStatus.DONE, vrView(repo.get(other), t).status); assertEquals(150, vrView(repo.get(other), t).actualMin)
    }

    @Test fun VR9_a_pause_is_kept_in_the_database() = runBlocking {
        val repo = VrRepo(db)
        repo.start(ds, 1_000L); repo.pause(ds, 5_000L)
        assertTrue(repo.get(ds)!!.paused); assertEquals(listOf(5_000L), repo.get(ds)!!.pauses)
        repo.resume(ds, 9_000L)
        assertFalse(repo.get(ds)!!.paused); assertEquals(listOf(5_000L, 9_000L), repo.get(ds)!!.pauses)
    }

    @Test fun VR7_the_settings_are_kept_and_a_cycle_uses_them() = runBlocking {
        val repo = VrRepo(db)
        assertEquals(VrConfig(), repo.config())
        repo.saveConfig(VrConfig.IDEAL.copy(capMin = 105))
        repo.start(ds, 0L)
        val run = repo.get(ds)!!
        assertEquals(vrPlan(VrConfig.IDEAL.copy(capMin = 105)), run.planMinutes); assertTrue(run.planMinutes.filterIndexed { i, _ -> i % 2 == 0 }.sum() <= 105)
        repo.saveConfig(VrConfig.OPTIMAL)
        assertEquals(run.planMinutes, repo.get(ds)!!.planMinutes, "a cycle running keeps its own plan")
    }

    @Test fun VR4_timed_pages_set_the_N_time_for_the_next_cycle() = runBlocking {
        val repo = VrRepo(db); val z = ds.start.zone
        assertEquals(45, repo.plannedN())
        repo.start(ds, 0L)
        var t = 0L
        for (p in vrPhases().take(4)) { t += p.plannedMin * 60_000L; repo.advance(ds, t, z) }
        val nStart = t
        repo.page(ds, nStart + 15 * 60_000L); repo.page(ds, nStart + 30 * 60_000L)
        repeat(5) { t += 60 * 60_000L; repo.advance(ds, nStart + (it + 1) * 40 * 60_000L, z) }
        assertEquals(60, repo.plannedN(), "four pages at 15 minutes is 60")
    }
}

/** The block screen with a finished timer, driven by real touch gestures and the real camera. */
@RunWith(AndroidJUnit4::class)
class BlockScreenTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val past: DatedSlot = run {
        val d = LocalDate.of(2026, 10, 19)
        slotsFor(WeekB.slots, d).first { it.slot.title == "Notes and problems: Randomness" }
    }

    private class Fake : BlockActions {
        var hold: Long? = null; var photos = 0; var lastSize = 0
        override suspend fun load(ds: DatedSlot) = hold?.let { BlockRow(key = "k", title = ds.slot.title, type = ds.slot.type, startMs = 0, endMs = 0, zone = "Europe/London", holdAtMs = it) }
        override suspend fun recordHold(ds: DatedSlot, atMs: Long) { hold = atMs }
        var fixAsked = 0; var lastFix: Fix? = null; var permission = true
        var fixToGive: Fix? = Fix(52.4507, -1.9356, 15.0)
        override suspend fun submitPhoto(ds: DatedSlot, jpeg: ByteArray, now: ZonedDateTime, fix: Fix?): Verdict { photos++; lastSize = jpeg.size; lastFix = fix; return Verdict(true, Reason.OK, 125) }
        override suspend fun currentFix(): Fix? { fixAsked++; return fixToGive }
        override fun hasLocationPermission() = permission
        var commitChecks = 0
        var rec: RunRecord? = null; var skipped: String? = null
        var vr: VrRun? = null
        override suspend fun vrRun(ds: DatedSlot) = vr
        var cfg = VrConfig()
        override suspend fun vrConfig() = cfg
        override suspend fun vrStart(ds: DatedSlot, nowMs: Long) { vr = efa.vrStart(45, nowMs) }
        override suspend fun vrAdvance(ds: DatedSlot, nowMs: Long) { vr = vr?.let { efa.vrAdvance(it, nowMs) } }
        override suspend fun vrPage(ds: DatedSlot, nowMs: Long) { vr = vr?.let { efa.vrPage(it, nowMs) } }
        override suspend fun vrPause(ds: DatedSlot, nowMs: Long) { vr = vr?.let { efa.vrPause(it, nowMs) } }
        override suspend fun vrResume(ds: DatedSlot, nowMs: Long) { vr = vr?.let { efa.vrResume(it, nowMs) } }
        override suspend fun runRecord(ds: DatedSlot) = rec
        override suspend fun runStart(ds: DatedSlot, nowMs: Long) { rec = RunRecord(startMs = nowMs) }
        override suspend fun runFinish(ds: DatedSlot, nowMs: Long) { rec = (rec ?: RunRecord(startMs = nowMs)).copy(endMs = nowMs) }
        override suspend fun runSkip(ds: DatedSlot, reason: String) { skipped = reason; rec = RunRecord(skippedReason = reason) }
        override suspend fun checkCommit(ds: DatedSlot, now: ZonedDateTime): Verdict { commitChecks++; return Verdict(true, Reason.OK, 70) }
    }

    @Test fun ST1_a_routine_block_has_a_Start_button_that_hands_its_routine_to_the_runner() {
        var got: String? = null
        val wake = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.title.startsWith("Wake") }
        rule.setContent { EfaTheme { BlockScreen(wake, Fake(), onStartRoutine = { got = it }) {} } }
        rule.onNodeWithText("START").assertExists(); rule.onNodeWithText("Start getting ready").performClick()
        assertEquals(GETTING_READY.id, got)
    }

    @Test fun ST1_a_study_block_can_be_started_too_and_a_lecture_has_no_Start_button() {
        var got: String? = null
        rule.setContent { EfaTheme { BlockScreen(past, Fake(), onStartRoutine = { got = it }) {} } }
        rule.onNodeWithText("Start study block").performClick(); assertEquals(STUDY_BLOCK.id, got)
        val lecture = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "lecture" }
        assertTrue(routineForSlot(lecture.slot) == null, "a lecture is not a routine")
    }

    private val wakeSlot get() = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.title.startsWith("Wake") }

    @Test fun ST2_a_block_with_no_proof_is_started_by_a_hold_as_long_as_its_length_asks_for() {
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(wakeSlot, fake) {} } }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Hold to start", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(holdMillis(20) / 2)
        rule.onNodeWithContentDescription("Hold to start", substring = true).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(500)
        assertNull(fake.rec, "letting go before it is full starts nothing")
        rule.onNodeWithContentDescription("Hold to start", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(holdMillis(20) + 300)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { fake.rec?.startMs != null }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Hold to finish", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithContentDescription("Hold to finish", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun ST2_a_block_that_did_not_happen_asks_why_and_records_it() {
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(wakeSlot, fake) {} } }
        rule.onNodeWithText("It did not happen").performScrollTo().performClick()
        rule.onNodeWithText("Too tired").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.skipped == "Too tired" }
        rule.onNodeWithText("Did not happen: Too tired.").assertExists()
    }

    @Test fun ST2_a_late_start_offers_to_slide_the_rest_of_the_day_by_the_minutes_it_was_late() {
        val fake = Fake(); var shifted = 0
        val ds = wakeSlot
        fake.rec = RunRecord(startMs = ds.start.toInstant().toEpochMilli() + 12 * 60_000L)
        rule.setContent { EfaTheme { BlockScreen(ds, fake, onShift = { shifted = it }) {} } }
        rule.onNodeWithText("Started late: slide the rest of today by 15 min").performScrollTo().performClick()
        assertEquals(15, shifted)
    }

    private val studySlot get() = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "study-standard" }

    @Test fun VR1_a_study_block_has_the_VRNPM_timer_and_a_hold_starts_the_cycle() {
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(studySlot, fake) {} } }
        rule.onNodeWithText("VRNPM").assertExists()
        for (name in listOf("Video", "Read", "Notes", "Practice", "Marking")) rule.onNodeWithContentDescription(name, substring = true).assertExists()
        rule.onNodeWithContentDescription("Hold to start the cycle", substring = true).performScrollTo()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Hold to start the cycle", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(holdMillis(130) + 800)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { fake.vr != null }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("V Video").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun VR3_a_step_past_its_time_shows_OVER_and_counts_up_and_does_not_stop() {
        val fake = Fake(); val now = System.currentTimeMillis()
        fake.vr = VrRun(45, listOf(now - 14 * 60_000L))
        rule.setContent { EfaTheme { BlockScreen(studySlot, fake) {} } }
        rule.onNodeWithText("OVER", substring = true).performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Video, over now").assertExists()
        rule.onNodeWithContentDescription("Hold to finish V", substring = true).performScrollTo().assertExists()
    }

    @Test fun VR4_notes_has_a_page_timer() {
        val fake = Fake(); val now = System.currentTimeMillis(); val t0 = now - 26 * 60_000L
        fake.vr = VrRun(45, listOf(t0, t0 + 10 * 60_000L, t0 + 15 * 60_000L, t0 + 20 * 60_000L, t0 + 25 * 60_000L))
        rule.setContent { EfaTheme { BlockScreen(studySlot, fake) {} } }
        rule.onNodeWithText("N Notes").performScrollTo().assertExists()
        rule.onNodeWithText("Page done").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.vr?.pages?.size == 1 }
    }

    @Test fun VR7_the_timer_shows_the_times_from_the_settings() {
        val fake = Fake(); fake.cfg = VrConfig.IDEAL
        rule.setContent { EfaTheme { BlockScreen(studySlot, fake) {} } }
        rule.onNodeWithText("V 15  R 15  N 65  P 45  M 25   =  165 min, with breaks between").performScrollTo().assertExists()
    }

    @Test fun VR7_the_settings_card_changes_a_step_a_preset_and_the_longest_cycle() {
        val state = androidx.compose.runtime.mutableStateOf(VrConfig())
        rule.setContent { EfaTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { VrnpmSettingsCard(state.value) { state.value = it } } } }
        rule.onNodeWithText("STUDY CYCLE (VRNPM)").assertExists()
        rule.onNodeWithContentDescription("More: N notes (min)").performScrollTo().performClick(); rule.waitForIdle(); assertEquals(50, state.value.n)
        rule.onNodeWithContentDescription("Use the ideal cycle: 165 minutes").performScrollTo().performClick(); rule.waitForIdle(); assertEquals("Ideal", state.value.preset())
        rule.onNodeWithContentDescription("More: Longest cycle (min in the steps)").performScrollTo().performClick(); rule.waitForIdle(); assertEquals(60, state.value.capMin)
        rule.onNodeWithContentDescription("The Red Engine is built: stop booking the remedial").performScrollTo().performClick(); rule.waitForIdle(); assertTrue(state.value.redEngine)
    }

    @Test fun VR10_the_session_shows_V_and_R_as_done_by_override_and_a_cycle_of_two_and_a_half_hours() {
        val fake = Fake(); fake.cfg = VrConfig.SESSION
        rule.setContent { EfaTheme { BlockScreen(studySlot, fake) {} } }
        rule.onNodeWithText("V*  R*  N 65  P 45  M 25   =  135 min, with breaks between. * done by override, not timed.").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Video, overridden").assertExists(); rule.onNodeWithContentDescription("Read, overridden").assertExists()
    }

    @Test fun VR10_the_settings_card_has_the_session_preset_and_the_override_switch() {
        val state = androidx.compose.runtime.mutableStateOf(VrConfig())
        rule.setContent { EfaTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { VrnpmSettingsCard(state.value) { state.value = it } } } }
        rule.onNodeWithContentDescription("Use the 2.5 hour session", substring = true).performScrollTo().performClick(); rule.waitForIdle()
        assertEquals("Session", state.value.preset()); assertTrue(state.value.skipVR)
        rule.onNodeWithText("A cycle is 135 min in the steps", substring = true).performScrollTo().assertExists()
        rule.onNodeWithContentDescription("V and R are done by override: cycles start at notes", substring = true).performScrollTo().performClick(); rule.waitForIdle()
        assertFalse(state.value.skipVR)
    }

    @Test fun VR9_a_running_cycle_can_be_paused_and_resumed_and_says_so() {
        val fake = Fake(); fake.vr = VrRun(45, listOf(System.currentTimeMillis() - 4 * 60_000L))
        rule.setContent { EfaTheme { BlockScreen(studySlot, fake) {} } }
        rule.onNodeWithText("Pause").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.vr?.paused == true }
        rule.onNodeWithText("PAUSED", substring = true).performScrollTo().assertExists()
        rule.onNodeWithText("Resume").performScrollTo().performClick()
        rule.waitUntil(5_000) { fake.vr?.paused == false }
    }

    @Test fun VR1_a_routine_block_does_not_get_the_VRNPM_timer() {
        rule.setContent { EfaTheme { BlockScreen(wakeSlot, Fake()) {} } }
        rule.onNodeWithText("VRNPM").assertDoesNotExist()
    }

    @Test fun ST3_the_day_strip_says_in_words_what_it_shows() {
        val blocks = listOf(
            StripBlock("Wake, shower", 735, 755, 740, 758, StripState.ON_TIME, true),
            StripBlock("Bible study", 755, 785, 770, null, StripState.RUNNING, true),
            StripBlock("Lunch", 785, 815, null, null, StripState.NOT_STARTED, true),
        )
        rule.setContent { EfaTheme { DayStrip(blocks, 780) } }
        rule.onNodeWithContentDescription("Today so far. 1 done, 1 running, 1 not done.").assertExists()
    }

    @Test fun W1_a_hold_shorter_than_one_second_does_not_finish() {
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(past, fake) {} } }
        rule.onNodeWithText("Timer finished.").assertExists()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Hold to finish", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Hold to finish", substring = true).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        assertNull(fake.hold)
        rule.onNodeWithText("Take proof photo").assertDoesNotExist()
    }

    @Test fun W1_G5_hold_one_second_then_one_capture_with_the_in_app_camera() {
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(past, fake) {} } }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Hold to finish", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_200)
        rule.onRoot().performTouchInput { up() }        // the button is gone once the hold completes, so release through the root
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { fake.hold != null }
        rule.onNodeWithText("Take proof photo").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Take photo").fetchSemanticsNodes().isNotEmpty() }   // the camera has bound
        rule.onNodeWithText("Take photo").performClick()
        try { rule.waitUntil(20_000) { fake.photos == 1 } } catch (e: Throwable) { throw AssertionError("no photo arrived. Screen: " + rule.onRoot().printToString(), e) }
        assertTrue(fake.lastSize > 1000, "a real JPEG came from the camera")
        rule.onNodeWithText("Verified. 125 min confirmed.", substring = true).assertExists()
    }

    @Test fun G1_W1_a_code_block_ends_with_timer_hold_then_a_commit_check() {
        val code = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "code" }
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(code, fake) {} } }
        rule.onNodeWithText("Take proof photo").assertDoesNotExist()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Hold to finish", substring = true).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_200)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { fake.hold != null }
        rule.onNodeWithText("Check commit").performClick()
        rule.waitUntil(5_000) { fake.commitChecks == 1 }
        rule.onNodeWithText("Verified. 70 min confirmed.", substring = true).assertExists()
    }

    private fun lectureNow(): DatedSlot {
        val now = ZonedDateTime.now(efa.LONDON)
        val slot = PatternSlot(java.time.DayOfWeek.MONDAY, 0, 60, "Test lecture - Murray Learning Centre LC-UG05", "lecture", "Murray Learning Centre LC-UG05", false, "")
        return DatedSlot(slot, now.toLocalDate(), now.minusMinutes(5), now.plusMinutes(55))
    }

    @Test fun L1_L2_attendance_takes_one_photo_then_one_location_read_inside_the_window() {
        val fake = Fake()
        rule.setContent { EfaTheme { BlockScreen(lectureNow(), fake) {} } }
        rule.onNodeWithText("Take proof photo").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Take photo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Take photo").performClick()
        rule.waitUntil(20_000) { fake.photos == 1 }
        assertEquals(1, fake.fixAsked, "the location is read once, after the photo (A8)")
        assertNotNull(fake.lastFix)
    }

    @Test fun A9_attendance_asks_for_location_with_its_reason_before_the_camera() {
        val fake = Fake().also { it.permission = false }
        rule.setContent { EfaTheme { BlockScreen(lectureNow(), fake) {} } }
        rule.onNodeWithText("Take proof photo").performClick()
        rule.onNodeWithText("Location is read once", substring = true).assertExists()
        rule.onNodeWithText("Allow location").assertExists()
        assertEquals(0, fake.fixAsked)
    }

    @Test fun L1_a_lecture_outside_its_window_offers_no_photo() {
        val lec = slotsFor(WeekB.slots, LocalDate.of(2026, 10, 19)).first { it.slot.type == "lecture" }
        rule.setContent { EfaTheme { BlockScreen(lec, Fake()) {} } }
        rule.onNodeWithText("Take proof photo").assertDoesNotExist()
        rule.onNodeWithText(Reason.OUTSIDE_WINDOW.text()).assertExists()
    }

    @Test fun VR13_the_settings_card_turns_the_reminder_on_and_off_and_sets_how_often_it_repeats() {
        val state = androidx.compose.runtime.mutableStateOf(VrConfig())
        rule.setContent { EfaTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { VrnpmSettingsCard(state.value) { state.value = it } } } }
        rule.onNodeWithContentDescription("More: Remind again while over (min)").performScrollTo().performClick(); rule.waitForIdle(); assertEquals(10, state.value.overEvery)
        rule.onNodeWithContentDescription("A reminder with a sound when a step ends", substring = true).performScrollTo().performClick(); rule.waitForIdle()
        assertFalse(state.value.remind); rule.onNodeWithContentDescription("More: Remind again while over (min)").assertDoesNotExist()
    }
}
