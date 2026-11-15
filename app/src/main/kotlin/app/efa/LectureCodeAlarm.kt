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
import app.efa.data.META_CODE_REMINDERS
import app.efa.data.MetaRow
import app.efa.data.PlanRepo
import efa.nextCodeReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * LC1: a notification with the alarm sound at every lecture hour and at each lecture's end, saying to enter the attendance code. It does not
 * depend on where you are: the code is asked for at home as much as in the room. One alarm is set at a time, for the next reminder in the
 * calendar; when it fires, the next is set. The minute loop, a change to the calendar and a restart all set it again, so a changed lecture moves it.
 * Failure behaviour (X7): if the alarm cannot be set exactly it is set inexactly, and if it cannot be set at all nothing else is affected.
 */
object LectureCodeAlarm {
    const val ACTION = "app.efa.LECTURE_CODE"
    const val EXTRA_TEXT = "text"
    const val EXTRA_AT = "at"
    const val NOTIFICATION_ID = 4205
    private const val CHANNEL = "lecturecode"
    private const val META_AT = "code_alarm_at_ms"

    suspend fun enabled(db: EfaDb): Boolean = db.meta().get(META_CODE_REMINDERS) != "0"          // on unless switched off

    /** Sets the alarm for the next reminder, or clears it if the setting is off or there is no lecture ahead. Does nothing if it is already set for that time. */
    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val app = ctx.applicationContext
        val db = EfaDb.get(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = if (enabled(db)) nextCodeReminder(PlanRepo(db).slots(), now) else null
        if (next == null) { am.cancel(pending(app, 0, "")); db.meta().put(MetaRow(META_AT, "0")); return }
        val at = next.at.toInstant().toEpochMilli()
        if (db.meta().get(META_AT)?.toLongOrNull() == at) return
        if (schedule(app, at, next.text())) db.meta().put(MetaRow(META_AT, at.toString()))
    }

    /** Sets the alarm. Returns false if the time is already past (Android would fire it at once) or the alarm cannot be set at all. */
    fun schedule(app: Context, atMs: Long, text: String): Boolean {
        if (atMs <= System.currentTimeMillis() + 1_000) return false
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(app, atMs, text)
        return try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true }
        catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true } catch (e2: Exception) { false } }
    }

    private fun pending(app: Context, at: Long, text: String): PendingIntent = PendingIntent.getBroadcast(
        app, 5, Intent(app, LectureCodeReceiver::class.java).setAction(ACTION).putExtra(EXTRA_AT, at).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lecture attendance code", NotificationManager.IMPORTANCE_HIGH).apply { description = "Reminds you to enter the attendance code after each lecture hour" })
        val open = PendingIntent.getActivity(ctx, 6, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle("Attendance code").setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build())
    }
}

class LectureCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LectureCodeAlarm.ACTION) return
        val app = context.applicationContext
        LectureCodeAlarm.post(app, intent.getStringExtra(LectureCodeAlarm.EXTRA_TEXT) ?: "Enter the attendance code.")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { EfaDb.get(app).meta().put(MetaRow("code_alarm_at_ms", "0")); LectureCodeAlarm.refresh(app) } catch (_: Throwable) { /* the minute loop sets the next one (X7) */ } finally { pending.finish() }
        }
    }
}
