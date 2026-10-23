package app.efa

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import app.efa.data.EfaDb
import app.efa.data.HeldRepo
import efa.Incoming
import efa.shouldHold
import kotlinx.coroutines.*

/**
 * Holds notifications from blocked apps while they are blocked (U8). Each one is stored on this phone first and only then removed
 * from the shade, so nothing can be lost: if the store fails, the notification stays where it was (X7).
 * Calls, alarms, authenticator codes, banking alerts and MyUoB notices are never held (LA1), and apps that are not blocked come through
 * as normal. It reads the notification's title and text only to put them in the digest; they never leave the phone.
 */
class NotifHolder : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val incoming = Incoming(sbn.packageName, n.category, sbn.isOngoing, n.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        if (!shouldHold(incoming, BlockingState.decide(sbn.packageName))) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        val key = sbn.key; val at = sbn.postTime
        scope.launch {
            val stored = try { HeldRepo(EfaDb.get(applicationContext)).add(sbn.packageName, label, title, text, at); true } catch (e: Exception) { false }
            if (stored) cancelNotification(key)
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

/** Is notification access switched on for Spark? Only the person can do that in Android settings (U1). */
fun isNotificationAccessOn(ctx: Context): Boolean = ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

fun notificationListenerComponent(ctx: Context) = ComponentName(ctx, NotifHolder::class.java)
