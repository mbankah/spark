package efa

/**
 * CF1 to CF5: the confirmation system. Anything that cannot decide for you asks, once, in one place. A question has a kind (who raised it, and so who handles the answer), a title, a body and
 * up to three options. It is raised once for a given id and never again, stays open until it is answered or withdrawn because it went stale, and is kept afterwards so it is not asked twice.
 * An option that needs the app on screen (for example to preview a change) says so, and is not offered as a button on a notification.
 */
const val CONFIRM_WITHDRAWN = "withdrawn"
const val CONFIRM_MAX_OPTIONS = 3

data class ConfirmOption(val id: String, val label: String, val opensApp: Boolean = false)

data class Confirm(
    val id: String, val kind: String, val title: String, val body: String, val options: List<ConfirmOption>, val raisedMs: Long,
    val answeredMs: Long? = null, val answer: String? = null,
) {
    val open: Boolean get() = answeredMs == null

    fun encode(): String = listOf(id, kind, title, body, options.take(CONFIRM_MAX_OPTIONS).joinToString("\u001e") { "${clean(it.id)}\u001f${clean(it.label)}\u001f${if (it.opensApp) 1 else 0}" }, raisedMs.toString(), answeredMs?.toString().orEmpty(), answer.orEmpty())
        .joinToString("\t") { escape(it) }

    companion object {
        fun decode(text: String?): Confirm? {
            if (text.isNullOrBlank()) return null
            val p = text.split("\t").map { unescape(it) }; if (p.size < 8) return null
            val opts = if (p[4].isBlank()) emptyList() else p[4].split("\u001e").mapNotNull { o -> o.split("\u001f").takeIf { it.size == 3 }?.let { ConfirmOption(it[0], it[1], it[2] == "1") } }
            return Confirm(p[0], p[1], p[2], p[3], opts, p[5].toLongOrNull() ?: return null, p[6].toLongOrNull(), p[7].ifBlank { null })
        }
        private fun clean(s: String) = s.replace("\u001e", " ").replace("\u001f", " ")
        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
        private fun unescape(s: String): String {
            val b = StringBuilder(); var i = 0
            while (i < s.length) { val ch = s[i]; if (ch == '\\' && i + 1 < s.length) { b.append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2 } else { b.append(ch); i++ } }
            return b.toString()
        }
    }
}

/** CF2: a question is raised once. When [existing] (open or answered) is there, nothing new is raised. */
fun confirmRaise(existing: Confirm?, fresh: Confirm): Confirm? = if (existing == null) fresh.copy(options = fresh.options.take(CONFIRM_MAX_OPTIONS)) else null

/** CF3: the answer to an open question, or null when it is already answered or the option is not one of its own. */
fun confirmAnswer(c: Confirm, optionId: String, nowMs: Long): Confirm? =
    if (!c.open || c.options.none { it.id == optionId }) null else c.copy(answeredMs = nowMs, answer = optionId)

/** CF4: a question that went stale (the thing it asked about is over) is closed without an answer. */
fun confirmWithdraw(c: Confirm, nowMs: Long): Confirm = if (c.open) c.copy(answeredMs = nowMs, answer = CONFIRM_WITHDRAWN) else c

/** The open questions, oldest first. */
fun confirmOpen(all: List<Confirm>): List<Confirm> = all.filter { it.open }.sortedBy { it.raisedMs }

/** What handling an answer did: a line to say, and something for the screen to do next that only the screen can do (for example "slide:15"). */
data class ConfirmOutcome(val message: String? = null, val then: String? = null)
