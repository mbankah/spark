package app.efa

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import efa.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The persistent Buffer notification (B2, LA11): the Buffer, the block now and the next one, on the lock screen, with a Quick add
 * action. It is silent, shows no badge, never mentions held notifications, and says when it was last updated so a stale one is never
 * mistaken for a current one (WG9).
 *
 * It is refreshed every minute by the blocking service and whenever Spark opens. If both stop (the service is switched off and Spark is
 * not opened), the notification stays as it was and its "Updated HH:mm" line shows how old it is.
 * Failure behaviour (X7): without notification permission, or if anything fails, nothing is posted and nothing else is affected.
 */
object BufferNotifier {
    const val CHANNEL = "buffer"
    const val ID = 4201
    const val EXTRA_ACTION = "efa_action"
    const val ACTION_QUICK_ADD = "quick_add"
    const val ACTION_READY = "ready_photo"      // M8: the blocked card sends you to the ready photo

    /** Tests switch this to simulate a refused permission, because revoking the real one would kill the test process. */
    @Volatile var allowedOverride: ((Context) -> Boolean)? = null

    fun allowed(ctx: Context): Boolean = allowedOverride?.invoke(ctx) ?: (
        (Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled())

    /** Builds the notice from the blocking snapshot. Pure apart from reading the clock. */
    fun text(now: ZonedDateTime = BlockingState.now()): NoticeText? {
        val s = BlockingState.snapshot ?: return null
        val buffer = bufferNow(now, studyCycles(s.pattern, now) + s.tasks, s.pattern, shortSleep = false)
        val nn = nowAndNext(s.pattern, now)
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        return bufferNotice(
            buffer,
            nn.now?.let { "${it.slot.title}, until ${hm.format(it.end)}" },
            nn.next?.let { "${hm.format(it.start)} ${it.slot.title}" },
            hm.format(now),
        )
    }

    fun update(ctx: Context) {
        try {
            if (!allowed(ctx)) return
            val n = text() ?: return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Buffer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The Buffer, now and next, always visible."
                    setShowBadge(false); enableLights(false); enableVibration(false); setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                })
            }
            fun open(action: String?, code: Int) = PendingIntent.getActivity(
                ctx, code, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply { action?.let { putExtra(EXTRA_ACTION, it) } },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_flame)
                .setContentTitle(n.title).setContentText(n.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(n.big))
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(open(null, 0))
                .addAction(0, "Quick add", open(ACTION_QUICK_ADD, 1))
                .build()
            nm.notify(ID, notification)
        } catch (e: Exception) { /* a notification that cannot be shown must never break blocking or the app */ }
    }
}
