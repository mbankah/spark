package app.efa

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import app.efa.data.EfaDb
import app.efa.data.PlanRepo
import app.efa.data.VrRepo
import efa.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Raised when something outside the screen changes a running cycle (the notification's Pause and Resume), so an open home screen reloads it. */
object VrSignal { var tick by mutableIntStateOf(0) }

/**
 * VR12: a running VRNPM cycle on the lock screen. An ongoing, silent notification whose live clock counts down to the end of the step and, once it is over, counts up from when it should have
 * ended and says OVER. It shows the five steps (done, now, to come) and the block, is public on the lock screen, opens the app when tapped, and has Pause or Resume. The next step is not
 * on it: that is a hold in the app (ST2), so it cannot be done by accident from a pocket.
 */
object VrNotifier {
    const val CHANNEL = "vrnpm"
    const val ID = 4220
    const val ALERT_CHANNEL = "vrnpm_alert"
    const val ALERT_ID = 4221
    const val ACTION_PAUSE = "app.efa.VR_PAUSE"
    const val ACTION_RESUME = "app.efa.VR_RESUME"
    const val ACTION_STEP_END = "app.efa.VR_STEP_END"
    const val EXTRA_KEY = "vr_key"

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Study cycle", NotificationManager.IMPORTANCE_LOW).apply {
            description = "The running VRNPM cycle, also on the lock screen"; lockscreenVisibility = Notification.VISIBILITY_PUBLIC; setShowBadge(false)
        })
    }

    private fun broadcast(ctx: Context, action: String, key: String, code: Int) =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, VrReceiver::class.java).setAction(action).putExtra(EXTRA_KEY, key), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Builds the notification for [run] at [nowMs]. Separate from [post] so a test can look at it. */
    fun build(ctx: Context, ds: DatedSlot, run: VrRun, nowMs: Long = System.currentTimeMillis()): Notification {
        val n = vrNote(run, nowMs); val key = blockKey(ds)
        val open = PendingIntent.getActivity(ctx, 21, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val status = when (n.mode) { VrNoteMode.PAUSED -> n.text; VrNoteMode.OVER -> "OVER"; VrNoteMode.COUNTDOWN -> "left" }
        val b = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_cycle)
            .setContentTitle("${n.title}  ·  VRNPM").setContentText(n.chips).setSubText(ds.slot.title.substringBefore(" - "))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$status\n${n.todo}\n${n.chips}"))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
        when (n.mode) {
            VrNoteMode.COUNTDOWN -> b.setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true).setWhen(n.whenMs)
            VrNoteMode.OVER -> b.setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(false).setWhen(n.whenMs)
            VrNoteMode.PAUSED -> b.setShowWhen(false)
        }
        b.addAction(0, if (n.mode == VrNoteMode.PAUSED) "Resume" else "Pause", broadcast(ctx, if (n.mode == VrNoteMode.PAUSED) ACTION_RESUME else ACTION_PAUSE, key, 31))
        return b.build()
    }

    fun post(ctx: Context, ds: DatedSlot, run: VrRun, nowMs: Long = System.currentTimeMillis()) {
        if (!canPost(ctx)) return
        channel(ctx)
        ctx.getSystemService(NotificationManager::class.java).notify(ID, build(ctx, ds, run, nowMs))
    }

    fun cancel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(ID); nm.cancel(ALERT_ID)
        stepEndAlarm(ctx, null, 0L)
    }

    /**
     * VR13: the reminder. It makes a sound and shows over the lock screen, says which step is up or over, and what is next and what to do in it. It is replaced by the next one and goes
     * when the cycle moves on.
     */
    fun alert(ctx: Context, ds: DatedSlot, run: VrRun, nowMs: Long = System.currentTimeMillis()) {
        if (!canPost(ctx)) return
        val a = vrAlert(run, nowMs) ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Study cycle reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A reminder when a step of the study cycle ends, and what to do next"; lockscreenVisibility = Notification.VISIBILITY_PUBLIC; enableVibration(true)
        })
        val open = PendingIntent.getActivity(ctx, 22, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(ALERT_ID, NotificationCompat.Builder(ctx, ALERT_CHANNEL).setSmallIcon(R.drawable.ic_stat_cycle).setContentTitle(a.title).setContentText(a.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.text)).setSubText(ds.slot.title.substringBefore(" - "))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true).setContentIntent(open).build())
    }

    /** One alarm at the end of the step in hand, so the notification changes from counting down to OVER on time even with the screen off. */
    private fun stepEndAlarm(ctx: Context, key: String?, atMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, ACTION_STEP_END, key.orEmpty(), 32)
        if (key == null) { am.cancel(pi); return }
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } catch (e: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi) }
    }

    /** Puts the notification in step with the cycle of [ds] as it is now: shown while it runs, gone when it is finished or was never started. */
    suspend fun refresh(ctx: Context, ds: DatedSlot) {
        val repo = VrRepo(EfaDb.get(ctx)); val run = repo.get(ds); val now = System.currentTimeMillis()
        if (run == null || vrView(run, now).status != VrStatus.RUNNING) { cancel(ctx); return }
        ctx.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)          // the cycle moved on, so the last reminder is out of date
        post(ctx, ds, run, now)
        val v = vrView(run, now); val cfg = repo.config()
        // one alarm: at the end of the step (to turn the clock into OVER, and to remind), then every few minutes of overrun when reminders are on
        val next = when { v.paused -> null; v.remainingMs > 0 -> v.remainingMs + 1_500L; cfg.remind -> vrNextReminderMs(0L, v.overMs, cfg.overEvery); else -> null }
        if (next != null) stepEndAlarm(ctx, blockKey(ds), now + next) else stepEndAlarm(ctx, null, 0L)
    }

    /** The block a notification's key names: its planned day, start and title (the same key the cycle is stored under). */
    suspend fun slotFor(ctx: Context, key: String): DatedSlot? {
        val p = key.split("|", limit = 3); if (p.size < 3) return null
        val date = runCatching { LocalDate.parse(p[0]) }.getOrNull() ?: return null; val start = p[1].toIntOrNull() ?: return null
        val pattern = PlanRepo(EfaDb.get(ctx)).slots()
        return slotsFor(pattern, date).firstOrNull { it.slot.startMin == start && it.slot.title == p[2] }
    }
}

/** The notification's Pause and Resume, and the alarm at the end of a step that turns its clock into OVER. */
class VrReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(VrNotifier.EXTRA_KEY).orEmpty(); val action = intent.action ?: return
        val app = context.applicationContext; val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ds = VrNotifier.slotFor(app, key)
                if (ds != null) {
                    val repo = VrRepo(EfaDb.get(app)); val now = System.currentTimeMillis()
                    when (action) { VrNotifier.ACTION_PAUSE -> repo.pause(ds, now); VrNotifier.ACTION_RESUME -> repo.resume(ds, now) }
                    VrNotifier.refresh(app, ds)
                    if (action == VrNotifier.ACTION_STEP_END && repo.config().remind) repo.get(ds)?.let { VrNotifier.alert(app, ds, it) }       // VR13
                    VrSignal.tick++
                }
            } catch (_: Throwable) { } finally { pending.finish() }
        }
    }
}
