package app.efa

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import app.efa.data.EfaDb
import app.efa.data.META_LAST_BACKUP
import app.efa.data.META_CHANGES
import app.efa.data.META_DOOR_USES
import app.efa.data.MetaRow
import efa.HomeMode
import efa.LONDON
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * CT1, CT2: a backup is a copy of the database, written to Downloads/Spark, so it survives an uninstall, an update that goes wrong and a new phone. It holds the plan, the tasks, the
 * settings, the import log and the change and door counts. It holds no photos. Restoring puts a file back after keeping a safety copy of what is there now, and never overwrites silently.
 */
object Backup {
    private const val DB = "efa.db"
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
    private const val ONE_DAY_MS = 20L * 3_600_000L

    /** Writes a backup and returns its file name, or null if it could not be written (X7: nothing else is affected). */
    suspend fun write(ctx: Context): String? = try {
        val app = ctx.applicationContext; val db = EfaDb.get(app)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val name = "spark-backup-${LocalDateTime.now().format(STAMP)}.db"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream"); put(MediaStore.Downloads.RELATIVE_PATH, "Download/Spark")
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        app.contentResolver.openOutputStream(uri)?.use { out -> app.getDatabasePath(DB).inputStream().use { it.copyTo(out) } } ?: return null
        db.meta().put(MetaRow(META_LAST_BACKUP, System.currentTimeMillis().toString()))
        name
    } catch (e: Exception) { null }

    /** CT1: nightly, in effect once the last one is about a day old. Cheap when it is not due. */
    suspend fun nightly(ctx: Context) {
        val last = EfaDb.get(ctx.applicationContext).meta().get(META_LAST_BACKUP)?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - last >= ONE_DAY_MS) write(ctx)
    }

    suspend fun lastBackupText(ctx: Context): String {
        val ms = EfaDb.get(ctx.applicationContext).meta().get(META_LAST_BACKUP)?.toLongOrNull() ?: return "No backup yet."
        return "Last backup " + java.time.Instant.ofEpochMilli(ms).atZone(LONDON).format(DateTimeFormatter.ofPattern("EEE d MMM HH:mm")) + "."
    }

    /** True if [f] is a Spark database: the SQLite header and the tables Spark needs. */
    fun looksLikeBackup(f: File): Boolean = try {
        f.inputStream().use { s -> val h = ByteArray(16); s.read(h) == 16 && String(h, 0, 15, Charsets.US_ASCII) == "SQLite format 3" } &&
            android.database.sqlite.SQLiteDatabase.openDatabase(f.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { d ->
                listOf("task", "meta", "modifier").all { t -> d.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(t)).use { it.moveToFirst() } }
            }
    } catch (e: Exception) { false }

    /**
     * CT2: puts a backup file back. It checks the file first, keeps a safety copy of the present database, then replaces it. The caller restarts the app afterwards.
     * Returns a plain sentence saying what happened.
     */
    suspend fun restore(ctx: Context, uri: Uri): String {
        val app = ctx.applicationContext
        val tmp = File(app.cacheDir, "restore.db")
        try {
            app.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: return "That file could not be read. Nothing was changed."
            if (!looksLikeBackup(tmp)) return "That is not a Spark backup. Nothing was changed."
            val live = app.getDatabasePath(DB)
            EfaDb.close()
            if (live.exists()) live.copyTo(File(app.filesDir, "before-restore.db"), overwrite = true)
            File(live.path + "-wal").delete(); File(live.path + "-shm").delete()
            tmp.copyTo(live, overwrite = true)
            return "Restored. Spark restarts now."
        } catch (e: Exception) { return "The restore failed (${e.javaClass.simpleName}). Your data from before is kept in before-restore.db." }
        finally { tmp.delete() }
    }

    /** PK2: "this doesn't work". A short local report so a hole can be fixed later, and you carry on. It holds no photos, no location and none of your text. */
    suspend fun report(ctx: Context, note: String): String? = try {
        val app = ctx.applicationContext; val db = EfaDb.get(app)
        val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (e: Exception) { "?" }
        val mods = db.modifiers().all().joinToString("; ") { "${it.preset} ${it.startDate} to ${it.endDate} ${it.state}" }
        val body = buildString {
            appendLine("Spark report, ${LocalDateTime.now()}"); appendLine("Version $version, Android ${Build.VERSION.SDK_INT}, ${Build.MODEL}")
            appendLine("Note: ${note.trim().ifEmpty { "(none)" }}"); appendLine("Modifiers: ${mods.ifEmpty { "none" }}")
            appendLine("Open tasks: ${db.tasks().open().size}"); appendLine("Changes: ${db.meta().get(META_CHANGES).orEmpty().lines().size} counted")
            appendLine("Door uses: ${db.meta().get(META_DOOR_USES).orEmpty().lines().size} recorded")
        }
        val name = "spark-report-${LocalDateTime.now().format(STAMP)}.txt"
        val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "text/plain"); put(MediaStore.Downloads.RELATIVE_PATH, "Download/Spark/reports") }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        app.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) } ?: return null
        name
    } catch (e: Exception) { null }
}

/**
 * SL9: greyscale at wind-down. It needs a permission granted once from the PC (adb shell pm grant app.efa android.permission.WRITE_SECURE_SETTINGS). Without it nothing changes and
 * the Right now page says how to grant it. Spark only turns greyscale off if Spark turned it on, so your own setting is never overridden.
 */
object WindDown {
    const val COMMAND = "adb shell pm grant app.efa android.permission.WRITE_SECURE_SETTINGS"
    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    @Volatile private var onBySpark = false

    fun available(ctx: Context): Boolean = ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun sync(ctx: Context, mode: HomeMode) {
        if (!available(ctx)) return
        val want = mode == HomeMode.WIND_DOWN || mode == HomeMode.NIGHT
        val cr = ctx.contentResolver
        try {
            if (want && !onBySpark && Settings.Secure.getInt(cr, ENABLED, 0) == 0) { Settings.Secure.putInt(cr, MODE, 0); Settings.Secure.putInt(cr, ENABLED, 1); onBySpark = true }
            else if (!want && onBySpark) { Settings.Secure.putInt(cr, ENABLED, 0); onBySpark = false }
        } catch (e: Exception) { /* X7: a refused setting changes nothing else */ }
    }
}
