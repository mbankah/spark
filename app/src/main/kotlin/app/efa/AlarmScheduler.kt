package app.efa

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.efa.data.EfaDb
import app.efa.data.MetaRow
import app.efa.data.MorningRepo
import app.efa.data.PlanRepo
import app.efa.data.SleepRepo
import efa.*
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Keeps one alarm set at all times (G2, Z1, Z2, Y7). It uses Android's alarm-clock alarm, which Doze lets through and which needs no special
 * permission. The alarm waits at the 12:00 backstop until sleep has started, then moves to onset plus 8 hours; a hard alarm rings earlier.
 * Failure behaviour (X7): if planning fails the last alarm stays set, and the backstop is always there.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "app.efa.ALARM_FIRE"
    const val EXTRA_AT = "at"
    const val EXTRA_REASON = "reason"
    private const val META_AT = "alarm_at_ms"
    private const val META_REASON = "alarm_reason"

    /** Where sleep started, if the live night says it has and the person is still in it. */
    suspend fun liveOnset(repo: SleepRepo, now: ZonedDateTime): ZonedDateTime? {
        val (from, _) = nightWindow(now.toLocalDate().let { if (now.toLocalTime().isBefore(NIGHT_UNTIL)) it.minusDays(1) else it }, now.zone)
        val night = deriveNight(repo.events(from, now.toInstant().toEpochMilli() + 1), from, now.toInstant().toEpochMilli() + 1) ?: return null
        val stillAsleep = night.endMs >= now.toInstant().toEpochMilli() - 60_000
        return if (stillAsleep) Instant.ofEpochMilli(night.startMs).atZone(now.zone) else null
    }

    suspend fun planNow(ctx: Context, now: ZonedDateTime): AlarmPlan? {
        val db = EfaDb.get(ctx)
        return planAlarm(now, liveOnset(SleepRepo(db), now), PlanRepo(db).slots())
    }

    /** Sets the alarm for [plan] if it differs from what is set by a minute or more. Returns whether it changed anything. */
    suspend fun apply(ctx: Context, plan: AlarmPlan?): Boolean {
        val app = ctx.applicationContext
        val db = EfaDb.get(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (plan == null) { am.cancel(pending(app, 0, "")); db.meta().put(MetaRow(META_AT, "0")); return true }
        val at = plan.at.toInstant().toEpochMilli()
        if (at <= System.currentTimeMillis() + 1_000) return false          // never set an alarm in the past: Android would ring it at once (a test clock can plan one)
        val current = db.meta().get(META_AT)?.toLongOrNull() ?: 0L
        if (kotlin.math.abs(current - at) < 60_000 && current > System.currentTimeMillis()) return false
        val show = PendingIntent.getActivity(app, 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(app, at, plan.reason.name))
        db.meta().put(MetaRow(META_AT, at.toString())); db.meta().put(MetaRow(META_REASON, plan.reason.name))
        return true
    }

    /** The minute loop and the app both call this: plan from the live night and set the alarm. */
    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) { apply(ctx, planNow(ctx, now)) }

    /**
     * X2: after a restart the alarm is set again. If the alarm time already passed while the phone was off, that morning is flagged as missed,
     * with no penalty and no social block, and the next alarm is set.
     */
    suspend fun rebuild(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val db = EfaDb.get(ctx.applicationContext)
        val at = db.meta().get(META_AT)?.toLongOrNull() ?: 0L
        if (at in 1 until now.toInstant().toEpochMilli() && MorningRepo(db).get(MorningRepo(db).dateOf(at)) == null)
            MorningRepo(db).markMissed(at, db.meta().get(META_REASON) ?: AlarmReason.BACKSTOP.name)
        db.meta().put(MetaRow(META_AT, "0"))                                   // force the alarm to be set again
        refresh(ctx, now)
    }

    suspend fun scheduledAt(ctx: Context): Long = EfaDb.get(ctx.applicationContext).meta().get(META_AT)?.toLongOrNull() ?: 0L

    fun pending(app: Context, at: Long, reason: String): PendingIntent = PendingIntent.getBroadcast(
        app, 0, Intent(app, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_AT, at).putExtra(EXTRA_REASON, reason),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

}

/** The alarm goes off: record that it is ringing and start the ring service, which shows the alarm screen (M5). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val at = intent.getLongExtra(AlarmScheduler.EXTRA_AT, System.currentTimeMillis())
        val reason = intent.getStringExtra(AlarmScheduler.EXTRA_REASON) ?: AlarmReason.BACKSTOP.name
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MorningRepo(EfaDb.get(context.applicationContext)).fire(at, reason, System.currentTimeMillis())
                BlockingState.refresh(context.applicationContext)
                AlarmService.start(context.applicationContext)
                AlarmSession.ringing = true
                BlockerService.instance?.showAlarm()
            } finally { pending.finish() }
        }
    }
}
