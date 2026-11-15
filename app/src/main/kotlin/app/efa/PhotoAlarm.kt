package app.efa

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.efa.data.EfaDb
import app.efa.data.META_PHOTO_REMINDERS
import app.efa.data.MetaRow
import app.efa.data.PlanRepo
import app.efa.data.SleepRepo
import efa.PhotoState
import efa.nextPhotoReminder
import efa.photoPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * PH1: a notification with the alarm sound when a photo opens, and five minutes before it stops counting in full, saying what to photograph and by
 * when. Tapping it opens the home screen, where the photo card is first. One alarm is set at a time, for the next reminder; the minute loop, the
 * app and a restart set it again. When it fires, a photo that was taken meanwhile gives no sound (checked at that moment).
 * Failure behaviour (X7): if an exact alarm is refused it is set inexactly; if it cannot be set at all nothing else is affected.
 */
object PhotoAlarm {
    const val ACTION = "app.efa.PHOTO_DUE"
    const val EXTRA_KEY = "key"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val ACTION_OPEN = "photo"          // the extra the notification carries, read by MainActivity
    const val NOTIFICATION_ID = 4206
    private const val CHANNEL = "photodue"
    private const val META_AT = "photo_alarm_at_ms"

    suspend fun enabled(db: EfaDb): Boolean = db.meta().get(META_PHOTO_REMINDERS) != "0"

    private suspend fun planAt(db: EfaDb, now: ZonedDateTime) =
        photoPlan(PlanRepo(db).slots(), now, db.blocks().verifiedKeys().toSet(), SleepRepo(db).shutdownPhotoMs())

    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val app = ctx.applicationContext
        val db = EfaDb.get(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = if (enabled(db)) nextPhotoReminder(planAt(db, now), now) else null
        if (next == null) { am.cancel(pending(app, 0, "", "", "")); db.meta().put(MetaRow(META_AT, "0")); return }
        val at = next.at.toInstant().toEpochMilli()
        if (db.meta().get(META_AT)?.toLongOrNull() == at) return
        if (schedule(app, at, next.key, next.title, next.text)) db.meta().put(MetaRow(META_AT, at.toString()))
    }

    /** [slot] tells alarms apart: the one for the next reminder always uses 7, so the minute loop replaces it; a test uses another and is left alone. */
    fun schedule(app: Context, atMs: Long, key: String, title: String, text: String, slot: Int = 7): Boolean {
        if (atMs <= System.currentTimeMillis() + 1_000) return false
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(app, atMs, key, title, text, slot)
        return try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true }
        catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true } catch (e2: Exception) { false } }
    }

    private fun pending(app: Context, at: Long, key: String, title: String, text: String, slot: Int = 7): PendingIntent = PendingIntent.getBroadcast(
        app, slot, Intent(app, PhotoReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KEY, key).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Photo due", NotificationManager.IMPORTANCE_HIGH).apply { description = "Tells you when to take a proof photo and by when" })
        val open = PendingIntent.getActivity(ctx, 8, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(BufferNotifier.EXTRA_ACTION, ACTION_OPEN), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build())
    }
}

class PhotoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PhotoAlarm.ACTION) return
        val app = context.applicationContext
        val key = intent.getStringExtra(PhotoAlarm.EXTRA_KEY).orEmpty()
        val title = intent.getStringExtra(PhotoAlarm.EXTRA_TITLE) ?: "Photo due"
        val text = intent.getStringExtra(PhotoAlarm.EXTRA_TEXT) ?: "Take the photo."
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = EfaDb.get(app)
                // A photo taken since the alarm was set needs no sound.
                val now = BlockingState.now()
                val stillOpen = key.isEmpty() || photoStillOpen(db, key, now)
                if (stillOpen) PhotoAlarm.post(app, title, text)
                db.meta().put(MetaRow("photo_alarm_at_ms", "0")); PhotoAlarm.refresh(app)
            } catch (_: Throwable) { PhotoAlarm.post(app, title, text) } finally { pending.finish() }
        }
    }

    private suspend fun photoStillOpen(db: EfaDb, key: String, now: ZonedDateTime): Boolean {
        val p = efa.photoPlan(PlanRepo(db).slots(), now, db.blocks().verifiedKeys().toSet(), SleepRepo(db).shutdownPhotoMs()).firstOrNull { it.key == key } ?: return true
        return p.state != PhotoState.DONE
    }
}
