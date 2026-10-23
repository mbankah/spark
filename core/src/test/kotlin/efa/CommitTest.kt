package efa

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.*

class CommitTest {
    private fun t(h: Int, m: Int = 0, d: Int = 19) = at(LocalDate.of(2026, 10, d), LocalTime.of(h, m))
    private val start = t(14, 30); private val end = t(15, 40)        // a CS Project block
    private val deadline = t(7, 30, 20)
    private fun c(sha: String, z: ZonedDateTime) = Commit(sha, z.toInstant())

    private fun verify(now: ZonedDateTime, outcome: CommitOutcome, hold: Boolean = true, recovered: Int = 0) =
        verifyProof(Proof.GIT_COMMIT, start, end, now, hold, duplicate = false, location = null, backfillDeadline = deadline, recoveredSoFarMin = recovered, commit = outcome)

    @Test fun `G1 a commit made during the block proves it`() {
        val m = matchCommit(listOf(c("a1", t(15, 10))), start, end, emptySet())
        assertEquals(CommitOutcome.FOUND, m.outcome); assertEquals("a1", m.sha)
    }

    @Test fun `G1 a commit before the block started does not count`() {
        assertEquals(CommitOutcome.NONE_FOUND, matchCommit(listOf(c("old", t(14, 29))), start, end, emptySet()).outcome)
    }

    @Test fun `G1 a commit up to 15 minutes after the end still counts and later does not`() {
        assertEquals(CommitOutcome.FOUND, matchCommit(listOf(c("a", t(15, 55))), start, end, emptySet()).outcome)
        assertEquals(CommitOutcome.NONE_FOUND, matchCommit(listOf(c("b", t(15, 56))), start, end, emptySet()).outcome)
    }

    @Test fun `G1 one commit proves one block`() {
        val commits = listOf(c("a1", t(15, 0)))
        assertEquals(CommitOutcome.ALREADY_USED, matchCommit(commits, start, end, setOf("a1")).outcome)
        val two = listOf(c("a1", t(15, 0)), c("a2", t(15, 20)))
        assertEquals("a2", matchCommit(two, start, end, setOf("a1")).sha)
    }

    @Test fun `G1 no commits at all is none found`() {
        assertEquals(CommitOutcome.NONE_FOUND, matchCommit(emptyList(), start, end, emptySet()).outcome)
    }

    @Test fun `W1 a code block also needs the timer to finish and the hold before the check counts`() {
        assertEquals(Reason.TIMER_RUNNING, verify(t(15, 39), CommitOutcome.FOUND).reason)
        assertEquals(Reason.NEEDS_HOLD, verify(t(15, 41), CommitOutcome.FOUND, hold = false).reason)
    }

    @Test fun `G1 timer then hold then a found commit verifies the whole block`() {
        val v = verify(t(15, 45), CommitOutcome.FOUND)
        assertTrue(v.verified); assertEquals(70, v.creditedMinutes); assertFalse(v.recovered)
    }

    @Test fun `G1 each failed check says why in plain words`() {
        assertEquals(Reason.NO_COMMIT, verify(t(15, 45), CommitOutcome.NONE_FOUND).reason)
        assertEquals(Reason.COMMIT_USED, verify(t(15, 45), CommitOutcome.ALREADY_USED).reason)
        assertEquals(Reason.GITHUB_UNREACHABLE, verify(t(15, 45), CommitOutcome.UNREACHABLE).reason)
        assertEquals(Reason.COMMIT_CHECK_UNAVAILABLE, verify(t(15, 45), CommitOutcome.NOT_CONFIGURED).reason)
        assertTrue(Reason.NO_COMMIT.text().contains("Push your commit"))
        assertTrue(Reason.COMMIT_CHECK_UNAVAILABLE.text().contains("Setup"))
    }

    @Test fun `B7 a late commit check is recovered and capped like a late photo`() {
        val v = verify(t(17, 0), CommitOutcome.FOUND)
        assertTrue(v.verified && v.recovered); assertEquals(60, v.creditedMinutes)
        assertEquals(Reason.BACKFILL_CAP, verify(t(17, 0), CommitOutcome.FOUND, recovered = 60).reason)
        assertEquals(Reason.OUTSIDE_WINDOW, verify(t(7, 31, 20), CommitOutcome.FOUND).reason)
    }

    @Test fun `G1 a photo can never verify a code block`() {
        val v = verifyProof(Proof.GIT_COMMIT, start, end, t(15, 45), true, duplicate = false, location = null, backfillDeadline = deadline, recoveredSoFarMin = 0)
        assertFalse(v.verified)
    }

    @Test fun `G1 every code block in the week is proved by a commit`() {
        val code = WeekB.slots.filter { it.type == "code" }
        assertEquals(17, code.size)
        assertTrue(code.all { proofFor(it) == Proof.GIT_COMMIT })
    }

    // ---- Setup input ----
    @Test fun `G1 a username and repos are accepted in common forms`() {
        val r = parseGitConfig(" @michael-b ", "michael-b/companion, https://github.com/michael-b/battl.io.git\nmichael-b/mini-projects/") as GitConfig.Ok
        assertEquals("michael-b", r.user)
        assertEquals(listOf("michael-b/companion", "michael-b/battl.io", "michael-b/mini-projects"), r.repos)
    }

    @Test fun `G1 bad input is refused with a plain message`() {
        assertTrue(parseGitConfig("", "a/b") is GitConfig.Invalid)
        assertTrue(parseGitConfig("-bad", "a/b") is GitConfig.Invalid)
        assertTrue(parseGitConfig("ok", "") is GitConfig.Invalid)
        assertTrue(parseGitConfig("ok", "noslash") is GitConfig.Invalid)
        assertTrue(parseGitConfig("ok", "a/b/c") is GitConfig.Invalid)
        assertTrue(parseGitConfig("ok", (1..11).joinToString(",") { "o/r$it" }) is GitConfig.Invalid)
    }

    @Test fun `G1 duplicate repos are listed once`() {
        assertEquals(listOf("o/r"), (parseGitConfig("ok", "o/r, O/R") as GitConfig.Ok).repos)
    }
}
