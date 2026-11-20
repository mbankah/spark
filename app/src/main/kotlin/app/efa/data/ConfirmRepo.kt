package app.efa.data

import efa.*

/**
 * CF1 to CF5: the confirmation store, one entry per question in the small key-value store (nothing in the schema changes). A question is raised once for its id and kept after it is answered,
 * so it is not asked twice. Who handles an answer is [app.efa.ConfirmActions], by the question's kind.
 */
class ConfirmRepo(private val db: EfaDb) {
    suspend fun get(id: String): Confirm? = Confirm.decode(db.meta().get(PREFIX + id))

    private suspend fun put(c: Confirm) = db.meta().put(MetaRow(PREFIX + c.id, c.encode()))

    /** Raises [fresh] unless that id was ever raised. Returns the question when it is new, so the caller can say so (a notification), and null when it was already there. */
    suspend fun raise(fresh: Confirm): Confirm? = confirmRaise(get(fresh.id), fresh)?.also { put(it) }

    /** The questions waiting for an answer, oldest first. */
    suspend fun open(): List<Confirm> = confirmOpen(db.meta().withPrefix(PREFIX).mapNotNull { Confirm.decode(it.value) })

    /** Records the answer. Null when the question is not there, already answered, or the option is not its own. */
    suspend fun answer(id: String, optionId: String, nowMs: Long): Confirm? = get(id)?.let { confirmAnswer(it, optionId, nowMs) }?.also { put(it) }

    /** Closes a question that went stale. An answered one is left as it was. */
    suspend fun withdraw(id: String, nowMs: Long) { get(id)?.takeIf { it.open }?.let { put(confirmWithdraw(it, nowMs)) } }

    companion object { const val PREFIX = "confirm:" }
}
