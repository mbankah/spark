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
import app.efa.data.META_MEAL_CHECKS
import app.efa.data.META_MEAL_LOG
import app.efa.data.MetaRow
import app.efa.data.PlanRepo
import efa.MealAnswer
import efa.MealLog
import efa.nextMealCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * MC1: "Did you eat?" ten minutes after a meal block, with Ate and Not yet on the notification. One alarm is set at a time, for the next question in the calendar; when it
 * fires or an answer is given, the next is set. It costs nothing and changes no plan. Failure behaviour (X7): if the alarm cannot be set exactly it is set inexactly, and if it
 * cannot be set at all nothing else is affected.
 */
object MealCheckAlarm {
    const val ACTION = "app.efa.MEAL_CHECK"
    const val ACTION_ANSWER = "app.efa.MEAL_ANSWER"
    const val EXTRA_KEY = "key"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ATE = "ate"
    const val NOTIFICATION_ID = 4208
    private const val CHANNEL = "mealcheck"
    private const val META_AT = "meal_alarm_at_ms"

    suspend fun enabled(db: EfaDb): Boolean = db.meta().get(META_MEAL_CHECKS) != "0"

    suspend fun log(db: EfaDb): MealLog = MealLog.decode(db.meta().get(META_MEAL_LOG))

    /** Writes one answer. Safe to call twice. */
    suspend fun answer(ctx: Context, key: String, ate: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val db = EfaDb.get(ctx.applicationContext)
        val next = log(db).answered(key, if (ate) MealAnswer.ATE else MealAnswer.NOT_YET, nowMs)
        db.meta().put(MetaRow(META_MEAL_LOG, next.encode(nowMs)))
        ctx.applicationContext.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        db.meta().put(MetaRow(META_AT, "0")); refresh(ctx)
    }

    /** Sets the alarm for the next question, or clears it if the setting is off or none is wanted. Does nothing if it is already set for that time. */
    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val app = ctx.applicationContext
        val db = EfaDb.get(app)
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = if (enabled(db)) nextMealCheck(PlanRepo(db).slots(), log(db), now) else null
        if (next == null) { am.cancel(pending(app, 0, "", "")); db.meta().put(MetaRow(META_AT, "0")); return }
        val at = next.at.toInstant().toEpochMilli()
        if (db.meta().get(META_AT)?.toLongOrNull() == at) return
        if (schedule(app, at, next.key, next.title)) db.meta().put(MetaRow(META_AT, at.toString()))
    }

    fun schedule(app: Context, atMs: Long, key: String, title: String, slot: Int = 11): Boolean {
        if (atMs <= System.currentTimeMillis() + 1_000) return false
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(app, atMs, key, title, slot)
        return try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true }
        catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi); true } catch (e2: Exception) { false } }
    }

    private fun pending(app: Context, at: Long, key: String, title: String, slot: Int = 11): PendingIntent = PendingIntent.getBroadcast(
        app, slot, Intent(app, MealCheckReceiver::class.java).setAction(ACTION).putExtra("at", at).putExtra(EXTRA_KEY, key).putExtra(EXTRA_TITLE, title),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun answerIntent(ctx: Context, key: String, ate: Boolean): PendingIntent = PendingIntent.getBroadcast(
        ctx, if (ate) 12 else 13, Intent(ctx, MealAnswerReceiver::class.java).setAction(ACTION_ANSWER).putExtra(EXTRA_KEY, key).putExtra(EXTRA_ATE, ate),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun post(ctx: Context, key: String, title: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Meals", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Asks whether you have eaten after a meal" })
        val open = PendingIntent.getActivity(ctx, 14, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle("Did you eat?").setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setContentIntent(open)
            .addAction(0, "Ate", answerIntent(ctx, key, true)).addAction(0, "Not yet", answerIntent(ctx, key, false)).build())
    }
}

class MealCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MealCheckAlarm.ACTION) return
        val app = context.applicationContext
        MealCheckAlarm.post(app, intent.getStringExtra(MealCheckAlarm.EXTRA_KEY) ?: "", intent.getStringExtra(MealCheckAlarm.EXTRA_TITLE) ?: "Your meal")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { EfaDb.get(app).meta().put(MetaRow("meal_alarm_at_ms", "0")) } catch (_: Throwable) { /* the minute loop sets the next one (X7) */ } finally { pending.finish() }
        }
    }
}

class MealAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MealCheckAlarm.ACTION_ANSWER) return
        val key = intent.getStringExtra(MealCheckAlarm.EXTRA_KEY) ?: return
        val ate = intent.getBooleanExtra(MealCheckAlarm.EXTRA_ATE, true)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { MealCheckAlarm.answer(app, key, ate) } catch (_: Throwable) { /* the minute loop sets the next one (X7) */ } finally { pending.finish() } }
    }
}
