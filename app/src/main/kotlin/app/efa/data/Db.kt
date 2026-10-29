package app.efa.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.execSQL
import efa.PatternSlot
import efa.WeekB
import java.time.DayOfWeek

/** The weekly pattern, stored so it can be edited in the app (O9). `edited` marks rows changed after seeding. */
@Entity(tableName = "pattern_slot")
data class PatternSlotRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Int,                // DayOfWeek.value, 1 = Monday
    val startMin: Int, val endMin: Int,
    val title: String, val type: String, val venue: String,
    val hardAlarm: Boolean, val notes: String,
    val edited: Boolean = false,
) {
    fun toSlot() = PatternSlot(DayOfWeek.of(day), startMin, endMin, title, type, venue, hardAlarm, notes)
}

/** Small key/value store. Times are epoch milliseconds plus the zone id, so nothing depends on the device zone later (X4). */
@Entity(tableName = "meta")
data class MetaRow(@PrimaryKey val key: String, val value: String)

/**
 * A manual deadline from quick-add (D2). The due time is epoch milliseconds plus the zone it was entered in (X4).
 * hoursEstimate is fixed at entry from the heaviness slider; calibration (layer 7) will correct it.
 * `doneAtMs` stays null until a proven block completes it, so nothing counts as done without proof (P8).
 */
@Entity(tableName = "task", indices = [Index(value = ["sourceKey"], unique = true)])
data class TaskRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueMs: Long, val dueZone: String,
    val heaviness: Int, val hoursEstimate: Double,
    val createdMs: Long,
    val doneAtMs: Long? = null,
    /** "manual" (quick-add), "import" (a deadline row), "import-cycle" (a lecture a file added, D1) or "missed" (RS1). */
    @ColumnInfo(defaultValue = "'manual'") val source: String = "manual",
    /** Makes an automatic or imported task appear once (IM8, RS5). Null for quick-add. */
    val sourceKey: String? = null,
    /** Minutes of proven work already credited to this task (RS6). */
    @ColumnInfo(defaultValue = "0") val doneMin: Int = 0,
    val importId: Long? = null,
    /** Why an automatic task exists, shown to you (RS8, Z4). */
    @ColumnInfo(defaultValue = "''") val reason: String = "",
) {
    fun remainingHours() = (hoursEstimate - doneMin / 60.0).coerceAtLeast(0.0)
    fun remainingMin() = Math.ceil(hoursEstimate * 60 - doneMin).toInt().coerceAtLeast(0)
    fun dueAt(): java.time.ZonedDateTime = java.time.Instant.ofEpochMilli(dueMs).atZone(java.time.ZoneId.of(dueZone))
    fun toDue() = efa.DueWork(title, remainingHours(), heaviness, dueAt())
}

/**
 * A dated change to the weekly pattern (IM1): an imported event or cancel, or free time turned into work (F3). `rowHash` makes a row
 * appear once however often it is offered (IM8). `reason` is what the person is told (RS8).
 */
@Entity(tableName = "overlay_item", indices = [Index(value = ["rowHash"], unique = true)])
data class OverlayRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, val startMin: Int, val endMin: Int,
    val title: String, val type: String, val venue: String, val notes: String,
    val hardAlarm: Boolean, val cancelled: Boolean,
    val kind: String,            // "import" or "convert"
    val rowHash: String, val importId: Long?, val reason: String, val createdMs: Long,
) {
    fun toSlot(): PatternSlot { val d = java.time.LocalDate.parse(date); return PatternSlot(d.dayOfWeek, startMin, endMin, title, type, venue, hardAlarm, notes, d, cancelled) }
}

/** An assignment week or a catch-up week (PW1). It orders work and labels home; it moves nothing (PW2). */
@Entity(tableName = "period", indices = [Index(value = ["rowHash"], unique = true)])
data class PlannedPeriodRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: String, val endDate: String, val kind: String, val title: String, val rowHash: String, val importId: Long?,
)

/** One applied file (IM8, IM12). `fileHash` stops the same file being logged twice; removing the import deletes its row. */
@Entity(tableName = "import_log", indices = [Index(value = ["fileHash"], unique = true)])
data class ImportLogRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long, val name: String, val fileHash: String, val summary: String,
)

/** Passive sleep logging (S5, S7): one row per screen, charging, movement or light event, and heartbeats that show the logger was alive. Kept 30 days (ST4). */
@Entity(tableName = "sensor_event", indices = [Index(value = ["atMs"])])
data class SensorEventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long, val kind: Int, val value: Int,
)

/** One line per night, kept after the raw events are removed (ST4). Dates are the evening the night is counted from. */
@Entity(tableName = "night_summary")
data class NightSummaryRow(
    @PrimaryKey val date: String,
    val startMs: Long, val endMs: Long, val sleepMin: Int, val checkCount: Int, val checkMin: Int,
    val chargingPct: Int, val motionEvents: Int, val darkPct: Int, val gapCount: Int, val derivedAtMs: Long,
)

@Dao
interface SensorDao {
    @Insert suspend fun insert(row: SensorEventRow): Long
    @Query("SELECT * FROM sensor_event WHERE atMs >= :fromMs AND atMs < :untilMs ORDER BY atMs, id") suspend fun between(fromMs: Long, untilMs: Long): List<SensorEventRow>
    @Query("SELECT * FROM sensor_event WHERE atMs < :beforeMs ORDER BY atMs, id") suspend fun before(beforeMs: Long): List<SensorEventRow>
    @Query("DELETE FROM sensor_event WHERE atMs < :beforeMs") suspend fun deleteBefore(beforeMs: Long): Int
    @Query("SELECT MAX(atMs) FROM sensor_event") suspend fun lastAt(): Long?
    @Query("SELECT MIN(atMs) FROM sensor_event") suspend fun firstAt(): Long?
    @Query("SELECT COUNT(*) FROM sensor_event") suspend fun count(): Int
}

@Dao
interface NightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: NightSummaryRow)
    @Query("SELECT * FROM night_summary ORDER BY date DESC LIMIT 1") suspend fun latest(): NightSummaryRow?
    @Query("SELECT * FROM night_summary ORDER BY date") suspend fun all(): List<NightSummaryRow>
    @Query("SELECT * FROM night_summary WHERE date = :date") suspend fun get(date: String): NightSummaryRow?
}

@Dao
interface OverlayDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: OverlayRow): Long
    @Query("SELECT * FROM overlay_item ORDER BY date, startMin") suspend fun all(): List<OverlayRow>
    @Query("SELECT rowHash FROM overlay_item") suspend fun hashes(): List<String>
    @Query("DELETE FROM overlay_item WHERE importId = :importId") suspend fun deleteByImport(importId: Long): Int
    @Query("SELECT * FROM overlay_item WHERE kind = 'convert' AND createdMs >= :sinceMs ORDER BY createdMs") suspend fun convertedSince(sinceMs: Long): List<OverlayRow>
}

@Dao
interface PeriodDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: PlannedPeriodRow): Long
    @Query("SELECT * FROM period ORDER BY startDate") suspend fun all(): List<PlannedPeriodRow>
    @Query("SELECT rowHash FROM period") suspend fun hashes(): List<String>
    @Query("DELETE FROM period WHERE importId = :importId") suspend fun deleteByImport(importId: Long): Int
}

@Dao
interface ImportDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: ImportLogRow): Long
    @Query("SELECT * FROM import_log ORDER BY id DESC") suspend fun all(): List<ImportLogRow>
    @Query("SELECT * FROM import_log WHERE fileHash = :hash") suspend fun byHash(hash: String): ImportLogRow?
    @Query("DELETE FROM import_log WHERE id = :id") suspend fun delete(id: Long)
}

/**
 * One block on one planned day (key from efa.blockKey). `holdAtMs` is when the one-second hold finished (W1).
 * `verified` is true only after a proof photo passed; `creditedMin` is the confirmed time (W2), `recovered` marks backfill (B7).
 */
@Entity(tableName = "block", indices = [Index(value = ["key"], unique = true)])
data class BlockRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String, val title: String, val type: String,
    val startMs: Long, val endMs: Long, val zone: String,
    val holdAtMs: Long? = null,
    val verified: Boolean = false, val creditedMin: Int = 0, val recovered: Boolean = false, val weaker: Boolean = false,
    val provenAtMs: Long? = null,
    /** Set when an app was opened through the wall during this block (LA9): the block ends as broken and its unproven time is lost (W2). */
    val brokenAtMs: Long? = null,
)

/** ST2: the forever record. No coordinates, no image. `thumbPath` is cleared when the thumbnail expires (ST1). */
@Entity(tableName = "photo")
data class PhotoRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockKey: String, val atMs: Long, val type: String,
    val locationResult: String, val distanceBand: String, val matchScore: Int, val fingerprint: Long,
    val thumbPath: String? = null, val thumbBytes: Int = 0,
)

/**
 * A venue point the user placed on the map (L5). Rows are only added, never changed, so the table is the correction log.
 * The latest row for a key wins. Coordinates here are directory data, not the person's position (ST7).
 */
@Entity(tableName = "venue_point")
data class VenuePointRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val venueKey: String, val lat: Double, val lon: Double, val atMs: Long, val source: String,
)

/**
 * An app opened through the wall (LA9, 30 minutes) or a night override (N3, all listed apps). One row per use, so the table is also
 * the count of overrides tonight (N4) and the tamper-adjacent record of every loosening.
 */
@Entity(tableName = "allowance")
data class AllowanceRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,            // "app" or "night"
    val pkg: String?,            // the package for "app", null for "night"
    val untilMs: Long, val atMs: Long, val wallLength: Int,
)

@Dao
interface AllowanceDao {
    @Insert suspend fun insert(row: AllowanceRow): Long
    @Query("SELECT * FROM allowance WHERE untilMs > :nowMs") suspend fun active(nowMs: Long): List<AllowanceRow>
    @Query("SELECT COUNT(*) FROM allowance WHERE kind = 'night' AND atMs >= :sinceMs") suspend fun nightUsesSince(sinceMs: Long): Int
}

/**
 * A notification held while its app was blocked (U8, LA2). The text is kept on this phone only, never sent anywhere. An unseen row is
 * never deleted; a seen one is removed after 30 days.
 */
@Entity(tableName = "held_notification")
data class HeldRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pkg: String, val label: String, val title: String, val text: String, val atMs: Long,
    val reviewedAtMs: Long? = null,
)

@Dao
interface HeldDao {
    @Insert suspend fun insert(row: HeldRow): Long
    @Query("SELECT * FROM held_notification WHERE reviewedAtMs IS NULL ORDER BY atMs, id") suspend fun unreviewed(): List<HeldRow>
    @Query("SELECT EXISTS(SELECT 1 FROM held_notification WHERE reviewedAtMs IS NULL)") suspend fun anyUnreviewed(): Boolean
    @Query("UPDATE held_notification SET reviewedAtMs = :nowMs WHERE id IN (:ids)") suspend fun markReviewed(ids: List<Long>, nowMs: Long)
    @Query("DELETE FROM held_notification WHERE reviewedAtMs IS NOT NULL AND reviewedAtMs < :beforeMs") suspend fun deleteReviewedBefore(beforeMs: Long): Int
}

/** U4, X5: a loosening nobody asked for, such as the blocking service being switched off. Kept 1 year (ST4). */
@Entity(tableName = "tamper")
data class TamperRow(@PrimaryKey(autoGenerate = true) val id: Long = 0, val atMs: Long, val kind: String, val detail: String)

@Dao
interface TamperDao {
    @Insert suspend fun insert(row: TamperRow): Long
    @Query("SELECT * FROM tamper ORDER BY id") suspend fun all(): List<TamperRow>
    @Query("DELETE FROM tamper WHERE atMs < :beforeMs") suspend fun deleteBefore(beforeMs: Long): Int
}

/** A commit that has already proved a block, so one commit cannot prove two (G1). The sha is public repo data, not a secret. */
@Entity(tableName = "commit_use")
data class CommitUseRow(@PrimaryKey val sha: String, val blockKey: String, val atMs: Long)

@Dao
interface CommitDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(row: CommitUseRow)
    @Query("SELECT sha FROM commit_use") suspend fun usedShas(): List<String>
}

@Dao
interface VenueDao {
    @Insert suspend fun insert(row: VenuePointRow): Long
    @Query("SELECT * FROM venue_point ORDER BY id") suspend fun all(): List<VenuePointRow>
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM block WHERE `key` = :key") suspend fun get(key: String): BlockRow?
    @Query("SELECT `key` FROM block WHERE verified = 1") suspend fun verifiedKeys(): List<String>
    @Query("SELECT COALESCE(SUM(creditedMin), 0) FROM block WHERE recovered = 1 AND provenAtMs >= :sinceMs") suspend fun recoveredMinutesSince(sinceMs: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: BlockRow): Long
}

@Dao
interface PhotoDao {
    @Insert suspend fun insert(row: PhotoRow): Long
    @Query("SELECT fingerprint FROM photo") suspend fun fingerprints(): List<Long>
    @Query("SELECT * FROM photo WHERE thumbPath IS NOT NULL ORDER BY atMs") suspend fun withThumbs(): List<PhotoRow>
    @Query("UPDATE photo SET thumbPath = NULL, thumbBytes = 0 WHERE id = :id") suspend fun dropThumb(id: Long)
}

@Dao
interface TaskDao {
    @Insert suspend fun insert(row: TaskRow): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOnce(row: TaskRow): Long
    @Query("SELECT * FROM task WHERE doneAtMs IS NULL ORDER BY dueMs") suspend fun open(): List<TaskRow>
    @Query("SELECT * FROM task WHERE source = 'missed' AND createdMs >= :sinceMs ORDER BY createdMs") suspend fun missedSince(sinceMs: Long): List<TaskRow>
    @Query("SELECT sourceKey FROM task WHERE sourceKey IS NOT NULL") suspend fun sourceKeys(): List<String>
    @Query("UPDATE task SET doneMin = doneMin + :min WHERE id = :id") suspend fun addDone(id: Long, min: Int)
    @Query("UPDATE task SET doneAtMs = :nowMs WHERE id = :id") suspend fun close(id: Long, nowMs: Long)
    @Query("SELECT * FROM task WHERE id = :id") suspend fun get(id: Long): TaskRow?
    @Query("DELETE FROM task WHERE importId = :importId AND doneMin = 0") suspend fun deleteByImport(importId: Long): Int
}

@Dao
interface PatternDao {
    @Query("SELECT COUNT(*) FROM pattern_slot") suspend fun count(): Int
    @Query("SELECT * FROM pattern_slot ORDER BY day, startMin") suspend fun all(): List<PatternSlotRow>
    @Insert suspend fun insertAll(rows: List<PatternSlotRow>)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key") suspend fun get(key: String): String?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putIfAbsent(row: MetaRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(row: MetaRow)
}

@Database(entities = [PatternSlotRow::class, MetaRow::class, TaskRow::class, BlockRow::class, PhotoRow::class, VenuePointRow::class, CommitUseRow::class, AllowanceRow::class, TamperRow::class, HeldRow::class, OverlayRow::class, PlannedPeriodRow::class, ImportLogRow::class, SensorEventRow::class, NightSummaryRow::class], version = 8, exportSchema = true)
abstract class EfaDb : RoomDatabase() {
    abstract fun pattern(): PatternDao
    abstract fun meta(): MetaDao
    abstract fun tasks(): TaskDao
    abstract fun blocks(): BlockDao
    abstract fun photos(): PhotoDao
    abstract fun venues(): VenueDao
    abstract fun commits(): CommitDao
    abstract fun allowances(): AllowanceDao
    abstract fun tamper(): TamperDao
    abstract fun held(): HeldDao
    abstract fun overlay(): OverlayDao
    abstract fun periods(): PeriodDao
    abstract fun imports(): ImportDao
    abstract fun sensors(): SensorDao
    abstract fun nights(): NightDao

    companion object {
        @Volatile private var inst: EfaDb? = null
        fun get(ctx: Context): EfaDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, EfaDb::class.java, "efa.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { inst = it }
        }
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `task` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `dueMs` INTEGER NOT NULL, `dueZone` TEXT NOT NULL, `heaviness` INTEGER NOT NULL, `hoursEstimate` REAL NOT NULL, `createdMs` INTEGER NOT NULL, `doneAtMs` INTEGER)")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `block` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `zone` TEXT NOT NULL, `holdAtMs` INTEGER, `verified` INTEGER NOT NULL, `creditedMin` INTEGER NOT NULL, `recovered` INTEGER NOT NULL, `weaker` INTEGER NOT NULL, `provenAtMs` INTEGER)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_block_key` ON `block` (`key`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `blockKey` TEXT NOT NULL, `atMs` INTEGER NOT NULL, `type` TEXT NOT NULL, `locationResult` TEXT NOT NULL, `distanceBand` TEXT NOT NULL, `matchScore` INTEGER NOT NULL, `fingerprint` INTEGER NOT NULL, `thumbPath` TEXT, `thumbBytes` INTEGER NOT NULL)")
    }
}

val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `venue_point` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `venueKey` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `atMs` INTEGER NOT NULL, `source` TEXT NOT NULL)")
    }
}

val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `commit_use` (`sha` TEXT NOT NULL, `blockKey` TEXT NOT NULL, `atMs` INTEGER NOT NULL, PRIMARY KEY(`sha`))")
    }
}

val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("ALTER TABLE `block` ADD COLUMN `brokenAtMs` INTEGER")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `held_notification` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pkg` TEXT NOT NULL, `label` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, `atMs` INTEGER NOT NULL, `reviewedAtMs` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tamper` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMs` INTEGER NOT NULL, `kind` TEXT NOT NULL, `detail` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `allowance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `pkg` TEXT, `untilMs` INTEGER NOT NULL, `atMs` INTEGER NOT NULL, `wallLength` INTEGER NOT NULL)")
    }
}

val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("ALTER TABLE `task` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'manual'")
        connection.execSQL("ALTER TABLE `task` ADD COLUMN `sourceKey` TEXT")
        connection.execSQL("ALTER TABLE `task` ADD COLUMN `doneMin` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `task` ADD COLUMN `importId` INTEGER")
        connection.execSQL("ALTER TABLE `task` ADD COLUMN `reason` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_task_sourceKey` ON `task` (`sourceKey`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `overlay_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, `title` TEXT NOT NULL, `type` TEXT NOT NULL, `venue` TEXT NOT NULL, `notes` TEXT NOT NULL, `hardAlarm` INTEGER NOT NULL, `cancelled` INTEGER NOT NULL, `kind` TEXT NOT NULL, `rowHash` TEXT NOT NULL, `importId` INTEGER, `reason` TEXT NOT NULL, `createdMs` INTEGER NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_overlay_item_rowHash` ON `overlay_item` (`rowHash`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `period` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `rowHash` TEXT NOT NULL, `importId` INTEGER)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_period_rowHash` ON `period` (`rowHash`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `import_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMs` INTEGER NOT NULL, `name` TEXT NOT NULL, `fileHash` TEXT NOT NULL, `summary` TEXT NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_import_log_fileHash` ON `import_log` (`fileHash`)")
    }
}

val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `sensor_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMs` INTEGER NOT NULL, `kind` INTEGER NOT NULL, `value` INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_sensor_event_atMs` ON `sensor_event` (`atMs`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `night_summary` (`date` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `sleepMin` INTEGER NOT NULL, `checkCount` INTEGER NOT NULL, `checkMin` INTEGER NOT NULL, `chargingPct` INTEGER NOT NULL, `motionEvents` INTEGER NOT NULL, `darkPct` INTEGER NOT NULL, `gapCount` INTEGER NOT NULL, `derivedAtMs` INTEGER NOT NULL, PRIMARY KEY(`date`))")
    }
}

const val META_BLOCKLIST = "blocklist"
const val META_GITHUB_USER = "github_user"
const val META_GITHUB_REPOS = "github_repos"
const val META_HOME_LAT = "home_lat"
const val META_HOME_LON = "home_lon"
const val META_FIRST_USE = "first_use_epoch_ms"
const val META_ZONE = "zone_id"

/**
 * First run: seed week type B (section 3 changes already applied) and record the first-use time, which starts the
 * 14-day record-only period (G6). Safe to call every launch: it does nothing once seeded.
 * Failure behaviour (X7): if the insert throws, nothing is half-written because Room runs it in one transaction,
 * and the home screen shows "Nothing scheduled" and the error is surfaced by the caller.
 */
suspend fun EfaDb.seedIfEmpty(nowEpochMs: Long, zoneId: String) {
    withTransaction {
        if (pattern().count() == 0) {
            pattern().insertAll(WeekB.slots.map {
                PatternSlotRow(day = it.day.value, startMin = it.startMin, endMin = it.endMin, title = it.title,
                    type = it.type, venue = it.venue, hardAlarm = it.hardAlarm, notes = it.notes)
            })
        }
        meta().putIfAbsent(MetaRow(META_FIRST_USE, nowEpochMs.toString()))
        meta().putIfAbsent(MetaRow(META_ZONE, zoneId))
    }
}
