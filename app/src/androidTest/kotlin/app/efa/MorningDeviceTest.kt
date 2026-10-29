package app.efa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.exifinterface.media.ExifInterface
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import kotlin.random.Random
import kotlin.test.*

/** A made-up room as a JPEG: soft blocks of light and dark, so it has structure like a desk and a window. */
internal fun roomJpeg(seed: Int, w: Int = 640, h: Int = 480, light: Float = 1f, noise: Int = 0, dx: Int = 0, dy: Int = 0, black: Boolean = false): ByteArray {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp); val p = Paint()
    c.drawColor(Color.BLACK)
    if (!black) {
        val r = Random(seed)
        val tones = Array(6) { Array(8) { 40 + r.nextInt(180) } }
        for (by in 0 until 6) for (bx in 0 until 8) {
            val v = (tones[by][bx] * light).toInt().coerceIn(0, 255)
            p.color = Color.rgb(v, (v * 0.9).toInt(), (v * 0.8).toInt())
            c.drawRect((bx * w / 8 + dx).toFloat(), (by * h / 6 + dy).toFloat(), ((bx + 1) * w / 8 + dx).toFloat(), ((by + 1) * h / 6 + dy).toFloat(), p)
        }
        if (noise > 0) { val n = Random(seed + 99); repeat(w * h / 40) { p.color = Color.argb(60, n.nextInt(256), n.nextInt(256), n.nextInt(256)); c.drawPoint(n.nextInt(w).toFloat(), n.nextInt(h).toFloat(), p) } }
    }
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}

/** Reference photos and matching on a real database and real image decoding. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class ReferenceStorageTest {
    private lateinit var ctx: Context
    private lateinit var db: EfaDb
    private lateinit var repo: ReferenceRepo
    private val now = 1_800_000_000_000L

    @Before fun open() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build()
        repo = ReferenceRepo(ctx, db)
        File(ctx.filesDir, "refs").deleteRecursively()
    }
    @After fun close() { db.close(); File(ctx.filesDir, "refs").deleteRecursively() }

    @Test fun M1_ST3_a_reference_is_kept_at_working_size_and_a_new_one_replaces_it() = runBlocking {
        val r = repo.set(RefKind.NIGHT, roomJpeg(1), now) as RefResult.Saved
        assertTrue(r.bytes in 1..StoragePolicy.REFERENCE_BYTES, "${r.bytes} bytes")
        val first = db.references().get(RefKind.NIGHT)!!; assertTrue(File(first.path).exists()); assertEquals(256, first.signature.size)
        repo.set(RefKind.NIGHT, roomJpeg(2), now + 1000)
        assertEquals(1, db.references().all().size); assertFalse(first.signature.contentEquals(db.references().get(RefKind.NIGHT)!!.signature))
    }

    @Test fun ST3_even_a_large_noisy_photo_is_squeezed_under_300_KB() = runBlocking {
        val r = repo.set(RefKind.NIGHT, roomJpeg(3, 3000, 2000, noise = 1), now) as RefResult.Saved
        assertTrue(r.bytes <= StoragePolicy.REFERENCE_BYTES, "${r.bytes}")
    }

    @Test fun ST7_the_kept_picture_carries_no_location() = runBlocking {
        val f = File(ctx.cacheDir, "gps.jpg").also { it.writeBytes(roomJpeg(4)) }
        ExifInterface(f.absolutePath).apply { setAttribute(ExifInterface.TAG_GPS_LATITUDE, "52/1,27/1,0/1"); setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N"); setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "1/1,55/1,0/1"); setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W"); saveAttributes() }
        assertNotNull(ExifInterface(f.absolutePath).getAttribute(ExifInterface.TAG_GPS_LATITUDE), "the test photo must carry a location to begin with")
        repo.set(RefKind.NIGHT, f.readBytes(), now)
        val kept = ExifInterface(db.references().get(RefKind.NIGHT)!!.path)
        assertNull(kept.getAttribute(ExifInterface.TAG_GPS_LATITUDE)); assertNull(kept.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
    }

    @Test fun M3_a_photo_that_shows_nothing_cannot_be_a_reference() = runBlocking {
        assertEquals(RefResult.TooDark, repo.set(RefKind.NIGHT, roomJpeg(5, black = true), now)); assertEquals(0, db.references().all().size)
    }

    @Test fun X7_bytes_that_are_not_a_picture_change_nothing() = runBlocking {
        repo.set(RefKind.NIGHT, roomJpeg(6), now)
        assertEquals(RefResult.Unreadable, repo.set(RefKind.NIGHT, byteArrayOf(1, 2, 3, 4), now + 1))
        assertEquals(now, db.references().get(RefKind.NIGHT)!!.takenAtMs)
    }

    @Test fun M2_the_same_room_in_other_light_and_slightly_moved_matches() = runBlocking {
        repo.set(RefKind.NIGHT, roomJpeg(7), now)
        for (live in listOf(roomJpeg(7, light = 0.6f), roomJpeg(7, light = 1.0f, noise = 1), roomJpeg(7, dx = 12, dy = 8), roomJpeg(7, light = 0.75f, dx = -10, dy = 6, noise = 1))) {
            val m = repo.match(listOf(RefKind.NIGHT), live, now + 3_600_000)
            assertTrue(m.hadReference && m.result.passed, "score ${m.result.score}")
        }
    }

    @Test fun M2_M3_another_room_a_black_photo_and_a_garbage_file_do_not_match() = runBlocking {
        repo.set(RefKind.NIGHT, roomJpeg(8), now)
        for (seed in 9..30) assertFalse(repo.match(listOf(RefKind.NIGHT), roomJpeg(seed), now + 1000).result.passed, "room $seed")
        assertFalse(repo.match(listOf(RefKind.NIGHT), roomJpeg(8, black = true), now + 1000).result.passed)
        assertFalse(repo.match(listOf(RefKind.NIGHT), byteArrayOf(9, 9, 9), now + 1000).result.passed)
    }

    @Test fun M4_a_night_photo_older_than_36_hours_is_no_longer_the_most_recent_valid_reference() = runBlocking {
        repo.set(RefKind.NIGHT, roomJpeg(10), now)
        val live = roomJpeg(10)
        assertTrue(repo.match(listOf(RefKind.NIGHT), live, now + Duration.ofHours(36).toMillis()).result.passed)
        val stale = repo.match(listOf(RefKind.NIGHT), live, now + Duration.ofHours(36).toMillis() + 1)
        assertFalse(stale.hadReference); assertFalse(stale.result.passed)
    }

    @Test fun M4_the_standing_reference_never_goes_stale_and_either_reference_can_stop_the_alarm() = runBlocking {
        repo.set(RefKind.NIGHT, roomJpeg(11), now); repo.set(RefKind.STANDING, roomJpeg(12), now)
        val later = now + Duration.ofDays(5).toMillis()
        assertTrue(repo.match(listOf(RefKind.NIGHT, RefKind.STANDING), roomJpeg(12), later).result.passed)
        assertFalse(repo.match(listOf(RefKind.NIGHT, RefKind.STANDING), roomJpeg(11), later).result.passed, "the night photo is stale, so it cannot count")
        assertTrue(repo.match(listOf(RefKind.NIGHT, RefKind.STANDING), roomJpeg(11), now + 1000).result.passed)
    }

    @Test fun M4_with_no_reference_at_all_nothing_matches_and_the_caller_is_told() = runBlocking {
        val m = repo.match(listOf(RefKind.NIGHT, RefKind.STANDING), roomJpeg(13), now)
        assertFalse(m.hadReference); assertFalse(m.result.passed)
    }

    @Test fun M8_the_ready_spot_is_its_own_reference() = runBlocking {
        repo.set(RefKind.READY, roomJpeg(14), now); repo.set(RefKind.NIGHT, roomJpeg(15), now)
        assertTrue(repo.match(listOf(RefKind.READY), roomJpeg(14), now).result.passed)
        assertFalse(repo.match(listOf(RefKind.READY), roomJpeg(15), now).result.passed, "the setup photo must not end the social block")
    }

    @Test fun M4_clearing_the_standing_reference_removes_its_file() = runBlocking {
        repo.set(RefKind.STANDING, roomJpeg(16), now); val path = db.references().get(RefKind.STANDING)!!.path
        repo.clear(RefKind.STANDING); assertNull(db.references().get(RefKind.STANDING)); assertFalse(File(path).exists())
    }
}

@RunWith(AndroidJUnit4::class)
class MorningMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_8_to_9_keeps_data_and_adds_the_morning_tables() {
        helper.createDatabase("mig89.db", 8).apply {
            execSQL("INSERT INTO task (title,dueMs,dueZone,heaviness,hoursEstimate,createdMs) VALUES ('Essay',1,'Europe/London',5,6.25,1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig89.db", 9, true, MIGRATION_8_9)
        db.query("SELECT COUNT(*) FROM task").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO reference_photo (kind,signature,path,bytes,takenAtMs) VALUES ('night',x'00','/p',1,1)")
        db.execSQL("INSERT INTO morning (date,alarmAtMs,alarmReason,failedPhotos,bestScore,steps,readyFailed,missed) VALUES ('2026-11-04',1,'ONSET',0,0,0,0,0)")
    }
}

@RunWith(AndroidJUnit4::class)
class MorningSetupScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun M1_nothing_set_says_so_and_each_photo_has_its_button() {
        val taken = mutableListOf<String>()
        rule.setContent { EfaTheme { MorningSetupScreen(MorningSetupStatus(), null, { taken += it }, {}, {}) } }
        rule.onAllNodesWithText("Not set.").assertCountEquals(3)
        rule.onNodeWithText("Take tonight's setup photo").performScrollTo().performClick()
        rule.onNodeWithText("Take the ready spot photo").performScrollTo().performClick()
        rule.onNodeWithText("Take a standing reference").performScrollTo().performClick()
        assertEquals(listOf(RefKind.NIGHT, RefKind.READY, RefKind.STANDING), taken)
        rule.onNodeWithText("Remove the standing reference").assertDoesNotExist()
    }

    @Test fun M4_a_stale_night_photo_says_it_does_not_count() {
        rule.setContent { EfaTheme { MorningSetupScreen(MorningSetupStatus(night = RefInfo("Mon 21:40", "/none", stale = true)), null, {}, {}, {}) } }
        rule.onNodeWithText("Set Mon 21:40. Too old to count: take tonight's.").assertExists()
    }

    @Test fun M4_a_standing_reference_can_be_removed() {
        var removed = false
        rule.setContent { EfaTheme { MorningSetupScreen(MorningSetupStatus(standing = RefInfo("Tue 08:00", "/none")), null, {}, { removed = true }, {}) } }
        rule.onNodeWithText("Remove the standing reference").performScrollTo().performClick(); assertTrue(removed)
    }

    @Test fun M3_the_screen_says_the_spot_should_not_be_visible_from_bed() {
        rule.setContent { EfaTheme { MorningSetupScreen(MorningSetupStatus(), null, {}, {}, {}) } }
        rule.onNodeWithText("Pick a spot you cannot see from your bed.", substring = true).performScrollTo().assertExists()
    }
}

/** The real in-app camera on the emulator, from Setup to a saved reference, and two photos of the same scene matching each other. */
@RunWith(AndroidJUnit4::class)
class MorningSetupFlowTest {
    companion object {
        @JvmStatic @org.junit.BeforeClass fun gateOff() { SetupGate.enforced = false }
        @JvmStatic @org.junit.AfterClass fun gateOn() { SetupGate.enforced = true }
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun clean() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        EfaDb.get(ctx).openHelper.writableDatabase.execSQL("DELETE FROM reference_photo")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant app.efa android.permission.CAMERA").close()
    }

    private fun takeFromSetup(button: String) {
        try { rule.onNodeWithText("Set up mornings").performScrollTo().performClick() } catch (e: Throwable) { throw AssertionError("not on Setup. Screen: " + rule.onRoot().printToString(4000), e) }
        rule.onNodeWithText(button).performScrollTo().performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Take photo").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Take photo").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithText("Saved. It replaces the old one.").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun M1_M4_two_photos_of_the_same_scene_are_saved_from_the_real_camera_and_match_each_other() {
        rule.onNodeWithText("Setup").performScrollTo().performClick()
        takeFromSetup("Take tonight's setup photo")
        rule.onNodeWithText("Back").performScrollTo().performClick()
        takeFromSetup("Take a standing reference")
        val db = EfaDb.get(ApplicationProvider.getApplicationContext<Context>())
        val night = runBlocking { db.references().get(RefKind.NIGHT) }!!; val standing = runBlocking { db.references().get(RefKind.STANDING) }!!
        val r = matchScore(ImageSignature(night.signature), ImageSignature(standing.signature))
        assertTrue(night.bytes <= StoragePolicy.REFERENCE_BYTES && standing.bytes > 0)
        assertTrue(r.passed, "two photos of the same scene scored ${r.score} (pass mark $MATCH_PASS_SCORE)")
    }
}
