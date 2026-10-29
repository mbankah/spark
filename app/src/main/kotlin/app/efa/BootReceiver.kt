package app.efa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * U3: blocking starts by itself at every boot. Android starts the accessibility service and the notification listener on its own once
 * they are switched on, and starts the home app, so nothing here has to be started by hand. This receiver only fills the Buffer
 * notification straight away instead of waiting for the first minute tick. It starts no service, so Android 15's limits on what a boot
 * receiver may start do not apply. If anything fails, nothing else is affected (X7).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try { BlockingState.refresh(app); BufferNotifier.update(app); AlarmScheduler.rebuild(app) } catch (_: Throwable) { } finally { pending.finish() }     // X2: the alarm is set again at boot
        }
    }
}
