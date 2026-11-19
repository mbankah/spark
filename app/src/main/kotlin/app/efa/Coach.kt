package app.efa

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import app.efa.data.EfaDb
import app.efa.data.FactRepo
import app.efa.data.LocationFix
import app.efa.data.META_COACH
import app.efa.data.META_EMERGENCY_LOG
import app.efa.data.META_REMINDERS
import app.efa.data.META_REMINDER_LOG
import app.efa.data.MetaRow
import efa.EmergencyAction
import efa.Haptic
import efa.decodeReminders
import efa.nextReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** CO2, CO3: the coach speaks in a haptic pattern you can feel, and in a spoken line if you want it. One voice, one prompt at a time. Nothing is spoken with the voice off. */
object Coach {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    fun haptic(ctx: Context, h: Haptic) {
        try {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(h.pattern, -1))
        } catch (e: Exception) { /* X7: a prompt that cannot buzz changes nothing else */ }
    }

    fun speak(ctx: Context, text: String) {
        try {
            val app = ctx.applicationContext
            val go = { t: TextToSpeech -> t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "spark-coach") }
            val existing = tts
            if (existing != null && ready) go(existing)
            else if (existing == null) tts = TextToSpeech(app) { status -> ready = status == TextToSpeech.SUCCESS; tts?.takeIf { ready }?.let(go) }
        } catch (e: Exception) { /* X7 */ }
    }

    suspend fun voiceOn(ctx: Context): Boolean = EfaDb.get(ctx.applicationContext).meta().get(META_COACH) != "0"

    /** One prompt: the feel always, the spoken line only if the voice is on. */
    suspend fun prompt(ctx: Context, text: String, h: Haptic) { haptic(ctx, h); if (voiceOn(ctx)) speak(ctx, efa.toneLine(text, efa.Tone.of(EfaDb.get(ctx.applicationContext).meta().get(app.efa.data.META_TONE)))) }
}

/** EM1: the Emergency door. It is never gated and never asks a question. Each use is logged quietly on the phone. */
object Emergency {
    private val BANKS = listOf("com.monzo.android", "com.starlingbank.android", "com.revolut.revolut", "uk.co.hsbc.hsbcukmobilebanking", "com.barclays.android.barclaysmobilebanking", "com.lloydsbank.mobile", "com.natwest.mobile", "com.santander.santanderuk")

    /** Returns a short sentence when nothing could be opened, otherwise null. */
    suspend fun run(ctx: Context, a: EmergencyAction): String? {
        val app = ctx.applicationContext
        try { val db = EfaDb.get(app); db.meta().put(MetaRow(META_EMERGENCY_LOG, (db.meta().get(META_EMERGENCY_LOG).orEmpty().lines().filter { it.isNotBlank() }.takeLast(50) + "${System.currentTimeMillis()}|${a.name}").joinToString("\n"))) } catch (e: Exception) { }
        val new = Intent.FLAG_ACTIVITY_NEW_TASK
        return try {
            when (a) {
                EmergencyAction.CALL_EMERGENCY -> app.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:999")).addFlags(new))
                EmergencyAction.HEALTH -> app.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:111")).addFlags(new))
                EmergencyAction.CALL_ANYONE -> app.startActivity(Intent(Intent.ACTION_DIAL).addFlags(new))
                EmergencyAction.MAPS -> app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).addFlags(new))
                EmergencyAction.BANK -> {
                    val pkg = BANKS.firstOrNull { app.packageManager.getLaunchIntentForPackage(it) != null } ?: return "No banking app found. Open it by typing its name."
                    app.startActivity(app.packageManager.getLaunchIntentForPackage(pkg)!!.addFlags(new))
                }
                EmergencyAction.SHARE_LOCATION -> {
                    val fix = LocationFix.current(app, 8_000)
                    val text = if (fix != null) "I am here: https://maps.google.com/?q=${fix.lat},${fix.lon}" else "I need help. My location could not be read."
                    app.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share where I am").addFlags(new))
                }
            }
            null
        } catch (e: Exception) { "That could not be opened on this phone." }
    }
}

/** SL6: your own reminders. One alarm at a time, for the next one. It logs taken or not taken for you to show a prescriber and gives no advice. */
object ReminderAlarm {
    const val ACTION = "app.efa.REMINDER"
    const val ACTION_ANSWER = "app.efa.REMINDER_ANSWER"
    const val EXTRA_NAME = "name"
    const val EXTRA_TAKEN = "taken"
    const val NOTIFICATION_ID = 4209
    private const val CHANNEL = "reminders"

    suspend fun refresh(ctx: Context, now: ZonedDateTime = BlockingState.now()) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = nextReminder(decodeReminders(EfaDb.get(app).meta().get(META_REMINDERS)), now)
        if (next == null) { am.cancel(pending(app, "")); return }
        val at = next.second.toInstant().toEpochMilli()
        val pi = pending(app, next.first.name)
        try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (e: SecurityException) { try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (e2: Exception) { } }
    }

    private fun pending(app: Context, name: String): PendingIntent =
        PendingIntent.getBroadcast(app, 15, Intent(app, ReminderReceiver::class.java).setAction(ACTION).putExtra(EXTRA_NAME, name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun answerIntent(ctx: Context, name: String, taken: Boolean): PendingIntent =
        PendingIntent.getBroadcast(ctx, if (taken) 16 else 17, Intent(ctx, ReminderAnswerReceiver::class.java).setAction(ACTION_ANSWER).putExtra(EXTRA_NAME, name).putExtra(EXTRA_TAKEN, taken), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun post(ctx: Context, name: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Your reminders", NotificationManager.IMPORTANCE_HIGH).apply { description = "The reminders you set" })
        val open = PendingIntent.getActivity(ctx, 18, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle(name).setContentText("Taken, or not yet?")
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open)
            .addAction(0, "Taken", answerIntent(ctx, name, true)).addAction(0, "Not taken", answerIntent(ctx, name, false)).build())
    }

    suspend fun answer(ctx: Context, name: String, taken: Boolean) {
        val app = ctx.applicationContext; val db = EfaDb.get(app)
        db.meta().put(MetaRow(META_REMINDER_LOG, (db.meta().get(META_REMINDER_LOG).orEmpty().lines().filter { it.isNotBlank() }.takeLast(200) + "${System.currentTimeMillis()}|$name|${if (taken) "TAKEN" else "NOT_TAKEN"}").joinToString("\n")))
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        refresh(app)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderAlarm.ACTION) return
        val app = context.applicationContext
        val name = intent.getStringExtra(ReminderAlarm.EXTRA_NAME).orEmpty()
        if (name.isNotBlank()) ReminderAlarm.post(app, name)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { ReminderAlarm.refresh(app) } catch (_: Throwable) { /* the minute loop sets the next one (X7) */ } finally { pending.finish() } }
    }
}

class ReminderAnswerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderAlarm.ACTION_ANSWER) return
        val name = intent.getStringExtra(ReminderAlarm.EXTRA_NAME) ?: return
        val taken = intent.getBooleanExtra(ReminderAlarm.EXTRA_TAKEN, true)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { ReminderAlarm.answer(app, name, taken) } catch (_: Throwable) { } finally { pending.finish() } }
    }
}

/**
 * CO1, CO2: routines are prompted at their time. When a routine's block starts and you have not started it, one prompt goes out at each level: the feel, a spoken line, a notification, and the
 * gate that already holds for that block. Each level is given once, a prompt within 20 seconds of the last is dropped, and after half an hour it stops.
 */
object RoutinePrompter {
    @Volatile private var lastPromptAt: java.time.Instant? = null
    private const val CHANNEL = "routines"
    private const val NOTIFICATION_ID = 4210

    suspend fun tick(ctx: Context, pattern: List<efa.PatternSlot>, now: ZonedDateTime) {
        val app = ctx.applicationContext; val db = EfaDb.get(app)
        val cur = efa.nowAndNext(pattern, now); val slot = cur.now ?: return
        val r = efa.routineForSlot(slot.slot) ?: return
        val started = efa.routineStarted(r, FactRepo(db).today(), slot.start)
        val level = efa.promptLevelAt(java.time.Duration.between(slot.start, now).toMinutes(), started) ?: return
        val key = "prompt:" + efa.blockKey(slot)
        if (level.ordinal <= (db.meta().get(key)?.toIntOrNull() ?: -1)) return
        if (!efa.mayPrompt(lastPromptAt, now.toInstant())) return
        db.meta().put(MetaRow(key, level.ordinal.toString())); lastPromptAt = now.toInstant()
        val tier = efa.capTier(efa.pickTier(r, efa.availableMinutes(now, cur.next?.start), null, null), AdaptState.level)
        val text = efa.promptLine(r, tier, level)
        when (level) {
            efa.PromptLevel.HAPTIC -> Coach.haptic(app, Haptic.START)
            efa.PromptLevel.SPOKEN -> Coach.prompt(app, text, Haptic.START)
            efa.PromptLevel.NOTIFICATION, efa.PromptLevel.GATE -> { Coach.haptic(app, Haptic.LEAVE); post(app, text) }
        }
    }

    private fun post(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Routines", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "One prompt when a routine's time has come" })
        val open = PendingIntent.getActivity(ctx, 19, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle("Spark").setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setContentIntent(open).build())
    }
}
