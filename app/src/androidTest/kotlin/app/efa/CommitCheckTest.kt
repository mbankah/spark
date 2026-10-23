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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

private class FakeSource(var result: CommitFetch) : CommitSource {
    var calls = 0; var lastUser: String? = null; var lastRepos: List<String> = emptyList()
    override suspend fun fetch(user: String, repos: List<String>, since: Instant): CommitFetch { calls++; lastUser = user; lastRepos = repos; return result }
}

/** The code-block commit check against a real database with a stand-in for GitHub. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class CommitCheckRepoTest {
    private lateinit var db: EfaDb
    private lateinit var ctx: Context
    private val day = LocalDate.of(2026, 10, 19)
    private fun t(h: Int, m: Int) = at(day, LocalTime.of(h, m))
    private fun slotAt(sh: Int, sm: Int, eh: Int, em: Int, title: String = "CS Project"): DatedSlot {
        val p = PatternSlot(DayOfWeek.MONDAY, sh * 60 + sm, eh * 60 + em, title, "code", "", false, "")
        return DatedSlot(p, day, t(sh, sm), t(eh, em))
    }
    private val csProject get() = slotsFor(WeekB.slots, day).first { it.slot.title == "CS Project" }   // 14:30 to 15:40
    private fun c(sha: String, z: ZonedDateTime) = Commit(sha, z.toInstant())

    @Before fun open() { ctx = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(ctx, EfaDb::class.java).build() }
    @After fun close() = db.close()

    private suspend fun configure() = GitConfigRepo(db).save(GitConfig.Ok("michael-b", listOf("michael-b/project")))
    private fun repo(src: CommitSource) = BlockRepo(ctx, db, WeekB.slots, src)

    @Test fun G1_without_GitHub_set_up_the_block_cannot_be_verified_and_says_so() = runBlocking {
        val src = FakeSource(CommitFetch.Ok(emptyList())); val r = repo(src)
        r.recordHold(csProject, 1L)
        val v = r.checkCommit(csProject, t(15, 45))
        assertEquals(Reason.COMMIT_CHECK_UNAVAILABLE, v.reason); assertEquals(0, src.calls)
    }

    @Test fun G1_a_commit_made_in_the_block_verifies_it_and_credits_the_time() = runBlocking {
        configure(); val src = FakeSource(CommitFetch.Ok(listOf(c("abc", t(15, 10))))); val r = repo(src)
        r.recordHold(csProject, 1L)
        val v = r.checkCommit(csProject, t(15, 45))
        assertTrue(v.verified); assertEquals(70, v.creditedMinutes)
        assertTrue(r.load(csProject)!!.verified)
        assertEquals("michael-b", src.lastUser); assertEquals(listOf("michael-b/project"), src.lastRepos)
        assertEquals(listOf("abc"), db.commits().usedShas())
    }

    @Test fun G1_no_photo_is_stored_for_a_code_block() = runBlocking {
        configure(); val r = repo(FakeSource(CommitFetch.Ok(listOf(c("abc", t(15, 10)))))); r.recordHold(csProject, 1L)
        r.checkCommit(csProject, t(15, 45))
        assertEquals(0, db.photos().fingerprints().size)
    }

    @Test fun G1_a_commit_outside_the_block_does_not_count() = runBlocking {
        configure(); val r = repo(FakeSource(CommitFetch.Ok(listOf(c("early", t(14, 0)), c("late", t(16, 30)))))); r.recordHold(csProject, 1L)
        val v = r.checkCommit(csProject, t(15, 45))
        assertEquals(Reason.NO_COMMIT, v.reason); assertFalse(r.load(csProject)!!.verified)
    }

    @Test fun G1_one_commit_cannot_prove_two_blocks() = runBlocking {
        configure(); val a = slotAt(14, 0, 15, 0, "Battl.io"); val b = slotAt(15, 5, 16, 0, "Mini and self project")
        val r = repo(FakeSource(CommitFetch.Ok(listOf(c("shared", t(15, 10))))))
        r.recordHold(a, 1L); r.recordHold(b, 1L)
        assertTrue(r.checkCommit(a, t(15, 12)).verified)
        assertEquals(Reason.COMMIT_USED, r.checkCommit(b, t(16, 5)).reason)
    }

    @Test fun W1_timer_and_hold_are_checked_before_any_network_call() = runBlocking {
        configure(); val src = FakeSource(CommitFetch.Ok(listOf(c("abc", t(15, 10))))); val r = repo(src)
        assertEquals(Reason.TIMER_RUNNING, r.checkCommit(csProject, t(15, 30)).reason)
        assertEquals(Reason.NEEDS_HOLD, r.checkCommit(csProject, t(15, 45)).reason)
        assertEquals(0, src.calls, "a too-early tap must not use a GitHub request")
    }

    @Test fun X7_GitHub_unreachable_keeps_the_block_checkable() = runBlocking {
        configure(); val src = FakeSource(CommitFetch.Unreachable); val r = repo(src); r.recordHold(csProject, 1L)
        assertEquals(Reason.GITHUB_UNREACHABLE, r.checkCommit(csProject, t(15, 45)).reason)
        assertFalse(r.load(csProject)!!.verified)
        src.result = CommitFetch.Ok(listOf(c("abc", t(15, 10))))
        assertTrue(r.checkCommit(csProject, t(16, 5)).verified, "a later check, once online, still counts")
    }

    @Test fun X7_a_private_or_missing_repo_says_only_public_repos_work() = runBlocking {
        configure(); val r = repo(FakeSource(CommitFetch.NotFound("michael-b/project"))); r.recordHold(csProject, 1L)
        val v = r.checkCommit(csProject, t(15, 45))
        assertEquals(Reason.REPO_NOT_FOUND, v.reason); assertTrue(v.reason.text().contains("public"))
    }

    @Test fun B7_a_late_commit_check_is_recovered_and_capped() = runBlocking {
        configure(); val r = repo(FakeSource(CommitFetch.Ok(listOf(c("abc", t(15, 10)))))); r.recordHold(csProject, 1L)
        val v = r.checkCommit(csProject, t(18, 0))
        assertTrue(v.verified && v.recovered); assertEquals(60, v.creditedMinutes)
    }

    @Test fun G1_a_photo_cannot_verify_a_code_block() = runBlocking {
        val r = repo(FakeSource(CommitFetch.Ok(emptyList()))); r.recordHold(csProject, 1L)
        assertFalse(r.submitPhoto(csProject, jpeg(77), t(15, 45)).verified)
    }

    @Test fun G1_the_check_does_nothing_for_a_block_that_is_not_a_code_block() = runBlocking {
        val study = slotsFor(WeekB.slots, day).first { it.slot.type == "study-standard" }
        assertEquals(Reason.NOT_REQUIRED, repo(FakeSource(CommitFetch.Unreachable)).checkCommit(study, t(13, 0)).reason)
    }

    @Test fun G1_the_username_and_repos_are_stored_and_read_back() = runBlocking {
        val g = GitConfigRepo(db); assertNull(g.get())
        g.save(GitConfig.Ok("michael-b", listOf("a/b", "c/d")))
        assertEquals(GitConfig.Ok("michael-b", listOf("a/b", "c/d")), g.get())
    }

    @Test fun G1_no_token_or_password_is_stored_anywhere() = runBlocking {
        configure()
        val keys = db.openHelper.readableDatabase.query("SELECT key FROM meta").use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertTrue(keys.none { it.contains("token", true) || it.contains("password", true) || it.contains("secret", true) }, keys.toString())
    }

    @Test fun G1_parsing_the_GitHub_commit_list() {
        val json = """[{"sha":"7fd1a60b01f91b314f59955a4e4d4e80d8edf11d","commit":{"author":{"name":"A","date":"2026-10-19T15:10:00Z"},"committer":{"date":"2026-10-19T15:11:00Z"}},"author":{"login":"michael-b"}},
            {"sha":"b2","commit":{"committer":{"date":"2026-10-19T14:50:00+01:00"}}},{"sha":"","commit":{}},{"commit":{"author":{"date":"bad"}},"sha":"c3"}]"""
        val got = parseCommits(json)
        assertEquals(2, got.size)
        assertEquals(Instant.parse("2026-10-19T15:10:00Z"), got[0].at)             // the author date is used
        assertEquals(Instant.parse("2026-10-19T13:50:00Z"), got[1].at)             // falls back to the committer date, offsets honoured
    }

    @Test fun G1_a_real_GitHub_request_works_and_a_missing_repo_is_reported() = runBlocking {
        val src = GitHubSource()
        val ok = src.fetch("octocat", listOf("octocat/Hello-World"), Instant.parse("2011-01-01T00:00:00Z"))
        assumeTrue("GitHub is not reachable from this device", ok !is CommitFetch.Unreachable)
        assertTrue(ok is CommitFetch.Ok, ok.toString())
        assertTrue((ok as CommitFetch.Ok).commits.all { it.sha.length == 40 })
        val missing = src.fetch("octocat", listOf("octocat/this-repo-does-not-exist-efa-test"), Instant.parse("2011-01-01T00:00:00Z"))
        assertEquals(CommitFetch.NotFound("octocat/this-repo-does-not-exist-efa-test"), missing)
    }
}

/** The GitHub card in Setup. */
@RunWith(AndroidJUnit4::class)
class GitSetupScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun G1_setup_says_GitHub_is_not_set_and_that_no_token_is_stored() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}) } }
        rule.onNodeWithText("Not set. Code blocks cannot be verified until it is.").assertExists()
        rule.onNodeWithText("No token or password is stored", substring = true).assertExists()
    }

    @Test fun G1_bad_input_is_refused_with_a_message_and_good_input_is_saved() {
        var saved: GitConfig.Ok? = null
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList()), {}, {}, {}, onGit = { saved = it }) } }
        rule.onNodeWithText("Save GitHub").performScrollTo().performClick()
        rule.onNodeWithText("Enter your GitHub username", substring = true).assertExists()
        assertNull(saved)
        rule.onNodeWithContentDescription("GitHub username").performTextInput("michael-b")
        rule.onNodeWithContentDescription("GitHub repos").performTextInput("michael-b/project")
        rule.onNodeWithText("Save GitHub").performScrollTo().performClick()
        assertEquals(GitConfig.Ok("michael-b", listOf("michael-b/project")), saved)
        rule.onNodeWithText("Saved.").assertExists()
    }

    @Test fun G1_setup_shows_the_saved_user_and_repo_count() {
        rule.setContent { EfaTheme { SetupScreen(PlacesState(null, emptyList(), GitConfig.Ok("michael-b", listOf("a/b", "c/d"))), {}, {}, {}) } }
        rule.onNodeWithText("Set for michael-b, 2 repos.").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class CommitMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EfaDb::class.java)

    @Test fun X4_migration_4_to_5_keeps_venues_and_adds_commit_use() {
        helper.createDatabase("mig45.db", 4).apply {
            execSQL("INSERT INTO venue_point (venueKey,lat,lon,atMs,source) VALUES ('Watson',52.45,-1.93,1,'user')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig45.db", 5, true, MIGRATION_4_5)
        db.query("SELECT COUNT(*) FROM venue_point").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO commit_use (sha,blockKey,atMs) VALUES ('abc','k',1)")
        db.query("SELECT COUNT(*) FROM commit_use").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
