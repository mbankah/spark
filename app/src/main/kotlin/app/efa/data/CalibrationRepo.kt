package app.efa.data

import androidx.room.withTransaction
import efa.*

/** The real times of finished tasks, and what is still to ask about (B5, R10, E9). */
class CalibrationRepo(private val db: EfaDb) {
    suspend fun calibration(): Calibration = Calibration(db.calibration().all().map { CalSample(it.heaviness, it.estimatedHours, it.actualHours) })

    /** The newest finished task that Spark made an estimate for and has not yet asked about. Imported deadlines and quick-add count; automatic catch-up does not. */
    suspend fun nextToReview(sinceMs: Long): ReviewCandidate? {
        val t = db.tasks().finishedUnreviewed(sinceMs) ?: return null
        return ReviewCandidate(t, calibration().note(t.heaviness))
    }

    /**
     * Records what the person said. "More" puts the task back in the Buffer with that time added, so the work is not lost; the others close the question.
     * A report that is not one of the offered choices records nothing. Each task is asked about once.
     */
    suspend fun report(task: TaskRow, report: TookReport, nowMs: Long): Boolean = db.withTransaction {
        if (db.calibration().get(task.id) != null) return@withTransaction false
        val s = sampleFrom(task.heaviness, task.hoursEstimate, report) ?: return@withTransaction false
        db.calibration().insert(CalibrationRow(task.id, task.heaviness, s.estimatedHours, s.actualHours, nowMs, report::class.simpleName.orEmpty()))
        if (report is TookReport.MoreMinutes) db.tasks().reopen(task.id, report.minutes / 60.0)
        true
    }
}

data class ReviewCandidate(val task: TaskRow, val note: String)

/** The shopping list lives in the database so ticks and receipt prices survive (SH1, SH3). It is filled from the Master Plan the first time it is opened. */
class ShoppingRepo(private val db: EfaDb) {
    suspend fun items(): List<ShoppingRow> {
        if (db.shopping().all().isEmpty()) Shopping.all.forEach { db.shopping().insert(ShoppingRow(name = it.name, pricePence = it.pence, estimate = it.estimate, pantry = it.pantry)) }
        return db.shopping().all()
    }
    suspend fun tick(row: ShoppingRow) = db.shopping().setTicked(row.id, !row.ticked)
    suspend fun price(row: ShoppingRow, pence: Int) { if (pence in 1..99_999) db.shopping().setPrice(row.id, pence) }
    suspend fun newShop() = db.shopping().untickWeekly()
}
