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
import app.efa.data.MetaRow
import app.efa.data.PlanRepo
import efa.nextEventReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * RP1: a notification with the alarm sound when a reminder event from a timetable file starts, saying its title (Melatonin 3mg, say). One alarm is
 * set at a time, for the next reminder in the calendar; when it fires, the next is set. The minute loop, a change to the calendar and a restart all set it again, so a changed lecture moves it.
 * Failure behaviour (X7): if the alarm cannot be set exactly it is set inexactly, and if it cannot be set at all nothing else is affected.
 */
object EventReminderAlarm {
    const val ACTION = "app.efa.EVENT_REMINDER"
    const val EXTRA_TEXT = "text"
    const val EXTRA_AT = "at"
    const val NOTIFICATION_ID = 4207
    private const val CHANNEL = "eventreminder"
    private const val META_AT = "reminder_alarm_at_ms"

    /** Sets the alarm for the next reminder, or clears it if the setting is off or there is no lecture ahead. Does nothing if it is already set for that time. */
    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val app = ctx.applicationContext
        val db = EfaDb.get(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = nextEventReminder(PlanRepo(db).slots(), now)
        if (next == null) { am.cancel(pending(app, 0, "")); db.meta().put(MetaRow(META_AT, "0")); return }
        val at = next.at.toInstant().toEpochMilli()
        if (db.meta().get(META_AT)?.toLongOrNull() == at) return
        if (schedule(app, at, next.title)) db.meta().put(MetaRow(META_AT, at.toString()))
    }

    /** Sets the alarm. Returns false if the time is already past (Android would fire it at once) or the alarm cannot be set at all. */
    fun schedule(app: Context, atMs: Long, text: String, slot: Int = 9): Boolean {
        if (atMs <= System.currentTimeMillis() + 1_000) return false
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(app, atMs, text, slot)
        return try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true }
        catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true } catch (e2: Exception) { false } }
    }

    private fun pending(app: Context, at: Long, text: String, slot: Int = 9): PendingIntent = PendingIntent.getBroadcast(
        app, slot, Intent(app, EventReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_AT, at).putExtra(EXTRA_TEXT, text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "Sounds at the start of a reminder from your timetable file" })
        val open = PendingIntent.getActivity(ctx, 6, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle("Reminder").setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build())
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EventReminderAlarm.ACTION) return
        val app = context.applicationContext
        EventReminderAlarm.post(app, intent.getStringExtra(EventReminderAlarm.EXTRA_TEXT) ?: "Reminder.")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { EfaDb.get(app).meta().put(MetaRow("reminder_alarm_at_ms", "0")); EventReminderAlarm.refresh(app) } catch (_: Throwable) { /* the minute loop sets the next one (X7) */ } finally { pending.finish() }
        }
    }
}
