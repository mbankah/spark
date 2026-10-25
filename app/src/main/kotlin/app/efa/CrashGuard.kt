package app.efa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import efa.recentCrashes
import efa.shouldHandOver

/**
 * LA3: if the launcher crashes it restarts at once (Android relaunches a home app). After 3 crashes in 10 minutes Spark stops being a
 * home candidate, so Android's own home screen takes over, a notice is posted and it is logged. Blocking keeps running because the
 * accessibility service is a separate component (X7).
 *
 * The record lives in SharedPreferences, written with a synchronous commit, because the crashing process cannot rely on the database.
 */
object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val TIMES = "times"
    private const val HANDED_OVER = "handed_over_at"
    private const val LOGGED = "logged"
    private const val HOME_ALIAS = "app.efa.HomeAlias"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { onCrash(app, System.currentTimeMillis()) } catch (_: Throwable) { /* never hide the crash behind a failure here */ }
            previous?.uncaughtException(t, e)
        }
    }

    /** Records a crash. Returns true when this one handed the home screen over. */
    fun onCrash(ctx: Context, nowMs: Long): Boolean {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val times = recentCrashes(p.getString(TIMES, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() } + nowMs, nowMs)
        val over = shouldHandOver(times, nowMs)
        val e = p.edit().putString(TIMES, times.joinToString(","))
        if (over) e.putLong(HANDED_OVER, nowMs).putBoolean(LOGGED, false)
        e.commit()
        if (over) { setHomeAlias(ctx, false); notice(ctx) }
        return over
    }

    fun handedOverAt(ctx: Context): Long? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(HANDED_OVER, 0L).takeIf { it > 0 }

    fun crashesRecorded(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TIMES, "").orEmpty().split(',').count { it.isNotBlank() }

    /** Returns the hand-over time once, so it is written to the tamper log exactly once. */
    fun takeUnloggedHandOver(ctx: Context): Long? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong(HANDED_OVER, 0L)
        if (at <= 0 || p.getBoolean(LOGGED, false)) return null
        p.edit().putBoolean(LOGGED, true).commit()
        return at
    }

    /** Puts Spark back among the home candidates. The person then makes it the home app again in setup. */
    fun restore(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        setHomeAlias(ctx, true)
    }

    fun homeAliasEnabled(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx, HOME_ALIAS)) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setHomeAlias(ctx: Context, enabled: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, HOME_ALIAS),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun notice(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("launcher", "Launcher", NotificationManager.IMPORTANCE_HIGH))
            nm.notify(
                4202,
                NotificationCompat.Builder(ctx, "launcher").setSmallIcon(R.drawable.ic_stat_flame)
                    .setContentTitle("Spark paused as your home screen")
                    .setContentText("It crashed 3 times in 10 minutes. Blocking is still running. Open Spark to put it back.")
                    .setAutoCancel(true).build(),
            )
        } catch (_: Throwable) { /* a missing notice must not matter */ }
    }
}

class SparkApp : Application() {
    override fun onCreate() { super.onCreate(); CrashGuard.install(this) }
}
