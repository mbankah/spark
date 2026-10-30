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
import efa.freeSlotNow
import java.time.ZonedDateTime

/**
 * F1: an alarm sounds when a free slot ends, saying what is next. It is a short notification with the alarm sound, not the morning alarm, and it needs
 * no photo to stop. It is set while a free slot is running, once per slot.
 * Failure behaviour (X7): if it cannot be set nothing else is affected, and the free slot simply ends in silence.
 */
object FreeSlotAlarm {
    const val ACTION = "app.efa.FREE_SLOT_END"
    const val EXTRA_END = "end"
    const val EXTRA_TEXT = "text"
    const val NOTIFICATION_ID = 4204
    private const val CHANNEL = "freeslot"
    private const val META_END = "free_alarm_end_ms"

    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val app = ctx.applicationContext
        val db = EfaDb.get(app)
        val info = freeSlotNow(PlanRepo(db).slots(), now) ?: return
        val end = info.slot.end.toInstant().toEpochMilli()
        if (db.meta().get(META_END)?.toLongOrNull() == end) return
        val next = info.next?.let { "Next: ${it.slot.title.substringBefore(" - ")} at ${"%02d:%02d".format(it.start.hour, it.start.minute)}." } ?: "Free time is over."
        if (schedule(app, end, next)) db.meta().put(MetaRow(META_END, end.toString()))
    }

    /** Sets the end-of-slot alarm. Returns false if the time is already past (Android would fire it at once) or the alarm cannot be set. */
    fun schedule(app: Context, endMs: Long, text: String): Boolean {
        if (endMs <= System.currentTimeMillis() + 1_000) return false
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(app, 3, Intent(app, FreeSlotReceiver::class.java).setAction(ACTION).putExtra(EXTRA_END, endMs).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, pi); true } catch (e: SecurityException) { false }
    }

    fun post(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Free time ending", NotificationManager.IMPORTANCE_HIGH).apply { description = "Sounds when a free slot ends" })
        val open = PendingIntent.getActivity(ctx, 4, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle("Free time is over").setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build())
    }
}

class FreeSlotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FreeSlotAlarm.ACTION) return
        FreeSlotAlarm.post(context.applicationContext, intent.getStringExtra(FreeSlotAlarm.EXTRA_TEXT) ?: "Free time is over.")
    }
}
