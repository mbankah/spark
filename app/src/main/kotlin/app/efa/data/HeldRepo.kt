package app.efa.data

import efa.HeldItem

/** Held notifications (U8, LA2). Nothing unseen is ever deleted; a seen one goes after 30 days. */
class HeldRepo(private val db: EfaDb) {
    suspend fun add(pkg: String, label: String, title: String, text: String, atMs: Long): Long =
        db.held().insert(HeldRow(pkg = pkg, label = label, title = title, text = text, atMs = atMs))

    suspend fun unreviewed(): List<HeldItem> = db.held().unreviewed().map { HeldItem(it.id, it.pkg, it.label, it.title, it.text, it.atMs) }

    suspend fun anyUnreviewed(): Boolean = db.held().anyUnreviewed()

    suspend fun markReviewed(ids: List<Long>, nowMs: Long) {
        if (ids.isNotEmpty()) db.held().markReviewed(ids, nowMs)
        db.held().deleteReviewedBefore(nowMs - 30L * 24 * 3_600_000)
    }
}
