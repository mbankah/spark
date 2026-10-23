package app.efa.data

import androidx.room.withTransaction
import efa.Commit
import efa.GitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** The GitHub username and the repos whose commits prove code blocks (G1). Stored as plain meta values. No token is stored. */
class GitConfigRepo(private val db: EfaDb) {
    suspend fun get(): GitConfig.Ok? {
        val user = db.meta().get(META_GITHUB_USER)?.takeIf { it.isNotBlank() } ?: return null
        val repos = db.meta().get(META_GITHUB_REPOS)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        return if (repos.isEmpty()) null else GitConfig.Ok(user, repos)
    }

    suspend fun save(c: GitConfig.Ok) = db.withTransaction {
        db.meta().put(MetaRow(META_GITHUB_USER, c.user))
        db.meta().put(MetaRow(META_GITHUB_REPOS, c.repos.joinToString("\n")))
    }
}

sealed interface CommitFetch {
    data class Ok(val commits: List<Commit>) : CommitFetch
    data class NotFound(val repo: String) : CommitFetch
    data object Unreachable : CommitFetch
}

/** Where commits come from. A fake stands in for it in tests. */
interface CommitSource {
    suspend fun fetch(user: String, repos: List<String>, since: Instant): CommitFetch
}

/**
 * Reads the user's own commits from the public GitHub API (G1). One request per repo, filtered to the configured author.
 * Public repos only: a private repo answers 404, which is reported as "not found or private". No token is stored, because the
 * ledger does not allow secrets to be entered or kept without a decision (rate limit without a token is 60 requests an hour).
 * Failure behaviour (X7): no network, a timeout, a rate limit or a server error all return Unreachable, and the block stays
 * unverified and checkable until the next wake.
 * Limit: git commit dates are written by the person's own computer, so a commit can be back-dated. This makes faking a commit
 * take deliberate effort. It cannot make it impossible.
 */
class GitHubSource(private val baseUrl: String = "https://api.github.com") : CommitSource {
    override suspend fun fetch(user: String, repos: List<String>, since: Instant): CommitFetch = withContext(Dispatchers.IO) {
        val all = mutableListOf<Commit>()
        for (repo in repos) {
            val url = "$baseUrl/repos/$repo/commits?author=${URLEncoder.encode(user, "UTF-8")}&since=$since&per_page=100"
            val conn = try { URL(url).openConnection() as HttpURLConnection } catch (e: Exception) { return@withContext CommitFetch.Unreachable }
            try {
                conn.connectTimeout = 10_000; conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                conn.setRequestProperty("User-Agent", "efa-app")
                when (conn.responseCode) {
                    200 -> all += parseCommits(conn.inputStream.bufferedReader().readText())
                    404, 409 -> if (conn.responseCode == 404) return@withContext CommitFetch.NotFound(repo)   // 409 = empty repo: no commits
                    else -> return@withContext CommitFetch.Unreachable
                }
            } catch (e: Exception) { return@withContext CommitFetch.Unreachable } finally { conn.disconnect() }
        }
        CommitFetch.Ok(all)
    }
}

/** Reads the GitHub "list commits" JSON. Uses the author date, which is when the work was done; falls back to the committer date. */
fun parseCommits(json: String): List<Commit> {
    val arr = JSONArray(json)
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val sha = o.optString("sha").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val c = o.optJSONObject("commit") ?: return@mapNotNull null
        val date = c.optJSONObject("author")?.optString("date")?.takeIf { it.isNotBlank() }
            ?: c.optJSONObject("committer")?.optString("date")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        runCatching { Commit(sha, Instant.parse(date)) }.getOrNull()
    }
}
