package app.efa

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.efa.data.ConfirmRepo
import app.efa.data.EfaDb
import app.efa.data.VrRepo
import efa.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CF1 to CF5: where an answer to a question does what its kind says. A kind is a branch here; a new thing that needs confirming adds a branch and raises a [Confirm], and gets the Home card and
 * the notification for free. Only the database is needed, so a notification button can answer too. What only the screen can do (a preview) comes back as [ConfirmOutcome.then].
 */
object ConfirmActions {
    suspend fun answer(ctx: Context, id: String, optionId: String, nowMs: Long = System.currentTimeMillis()): ConfirmOutcome? {
        val app = ctx.applicationContext; val c = ConfirmRepo(EfaDb.get(app)).answer(id, optionId, nowMs) ?: return null
        ConfirmNotifier.cancel(app, id)
        return when (c.kind) {
            VR_BLOCKEND_KIND -> vrBlockEnd(app, c, optionId, nowMs)
            else -> ConfirmOutcome()
        }
    }

    private suspend fun vrBlockEnd(ctx: Context, c: Confirm, optionId: String, nowMs: Long): ConfirmOutcome {
        val key = c.id.removePrefix("$VR_BLOCKEND_KIND:")
        val ds = VrNotifier.slotFor(ctx, key) ?: return ConfirmOutcome("That block is no longer in the plan.")
        val repo = VrRepo(EfaDb.get(ctx))
        return when (optionId) {
            VR_BLOCKEND_STOP -> { repo.stop(ds, nowMs); VrNotifier.refresh(ctx, ds); VrSignal.tick++; ConfirmOutcome("The cycle is stopped.") }
            VR_BLOCKEND_MOVE -> {
                val minutes = repo.get(ds)?.let { vrRemainingMin(it, nowMs) } ?: 0
                ConfirmOutcome(then = if (minutes > 0) "slide:$minutes" else null)
            }
            else -> ConfirmOutcome("The cycle runs on.")
        }
    }

    /** CF4: closes the questions whose subject is over (a block-end question whose cycle is no longer running). Run by whatever shows the questions. */
    suspend fun sweep(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
        val app = ctx.applicationContext; val db = EfaDb.get(app); val repo = ConfirmRepo(db)
        for (c in repo.open()) {
            if (c.kind != VR_BLOCKEND_KIND) continue
            val ds = VrNotifier.slotFor(app, c.id.removePrefix("$VR_BLOCKEND_KIND:"))
            val run = ds?.let { VrRepo(db).get(it) }
            if (run == null || vrView(run, nowMs).status != VrStatus.RUNNING) { repo.withdraw(c.id, nowMs); ConfirmNotifier.cancel(app, c.id) }
        }
    }
}

/** VR15: raises the block-end question once when a cycle that began inside its block is still running after the block ended. */
object BlockEndAsk {
    suspend fun check(ctx: Context, ds: DatedSlot, run: VrRun?, nowMs: Long = System.currentTimeMillis()) {
        if (run == null || !vrOverBlockEnd(run, ds.end.toInstant().toEpochMilli(), nowMs)) return
        val q = vrBlockEndConfirm(blockKey(ds), ds.slot.title, "%02d:%02d".format(ds.end.hour, ds.end.minute), run, nowMs)
        ConfirmRepo(EfaDb.get(ctx)).raise(q)?.let { ConfirmNotifier.post(ctx.applicationContext, it) }
    }
}

/** CF1: a question on the lock screen, with a button for each option that does not need the app. The ones that do open the app, where the Home card has all the options. */
object ConfirmNotifier {
    const val CHANNEL = "confirm"
    const val ACTION = "app.efa.CONFIRM_ANSWER"
    const val EXTRA_ID = "confirm_id"
    const val EXTRA_OPTION = "confirm_option"

    private fun notifId(id: String) = 4300 + (id.hashCode() and 0xff)

    private fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Builds the notification for [c]. Separate from [post] so a test can look at it. */
    fun build(ctx: Context, c: Confirm): Notification {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Questions", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When Spark needs you to confirm what to do"; lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(ctx, notifId(c.id) * 10, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val b = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_cycle).setContentTitle(c.title).setContentText(c.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.body)).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(open)
        c.options.forEachIndexed { i, o ->
            val code = notifId(c.id) * 10 + 1 + i
            val pi = if (o.opensApp) PendingIntent.getActivity(ctx, code, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
            else PendingIntent.getBroadcast(ctx, code, Intent(ctx, ConfirmReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, c.id).putExtra(EXTRA_OPTION, o.id), flags)
            b.addAction(0, o.label, pi)
        }
        return b.build()
    }

    fun post(ctx: Context, c: Confirm) {
        if (!canPost(ctx)) return
        ctx.getSystemService(NotificationManager::class.java).notify(notifId(c.id), build(ctx, c))
    }

    fun cancel(ctx: Context, id: String) { ctx.getSystemService(NotificationManager::class.java).cancel(notifId(id)) }
}

/** The notification's buttons that answer without the app. */
class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ConfirmNotifier.EXTRA_ID) ?: return; val opt = intent.getStringExtra(ConfirmNotifier.EXTRA_OPTION) ?: return
        val app = context.applicationContext; val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { ConfirmActions.answer(app, id, opt); VrSignal.tick++ } catch (_: Throwable) { } finally { pending.finish() } }
    }
}
