package app.efa

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import efa.Decision
import kotlinx.coroutines.*

/**
 * Foreground-app detection (K1 to K3, LA7). On every window change it looks at every visible application window, not only the
 * focused one, so split screen, floating windows and picture-in-picture are covered. If a listed app is blocked right now it puts
 * the blocked card in front. It reads only package names: no text, no clicks, no gestures.
 *
 * Limits, stated plainly: Android lets the user switch an accessibility service off in system settings. This service cannot
 * prevent that, only notice (U4, X5). It can be missed by an app that draws no window event, and by browsers' own tabs (K10 is
 * built separately).
 * Failure behaviour (X7): with no snapshot, or a decision that cannot be made, nothing is blocked. The phone is never trapped.
 */
class BlockerService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastShown = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { while (isActive) { BlockingState.refresh(applicationContext); BufferNotifier.update(applicationContext); delay(60_000) } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val packages = LinkedHashSet<String>()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) event.packageName?.toString()?.let { packages += it }
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                w.root?.let { root -> root.packageName?.toString()?.let { packages += it }; root.recycle() }
            }
        } catch (e: Exception) { /* windows can vanish while being read */ }
        for (pkg in packages) {
            if (pkg == packageName || pkg == "com.android.systemui") continue
            if (BlockingState.decide(pkg) is Decision.Block) { show(pkg); return }
        }
    }

    private fun show(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - (lastShown[pkg] ?: 0L) < 1_500) return        // one card per blocked window event burst
        lastShown[pkg] = now
        startActivity(Intent(this, BlockedActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra(BlockedActivity.EXTRA_PKG, pkg))
    }

    override fun onInterrupt() {}

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
