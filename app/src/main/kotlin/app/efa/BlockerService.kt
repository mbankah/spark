package app.efa

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import efa.Decision
import efa.browserFor
import efa.guardedPage
import efa.hostOf
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
        instance = this
        SleepCollector.start(applicationContext)          // layer 2: passive sleep logging lives as long as this service
        scope.launch { while (isActive) { BlockingState.refresh(applicationContext); BufferNotifier.update(applicationContext); SleepCollector.tick(applicationContext); PcLink.tick(applicationContext); TimeTrust.tick(applicationContext); checkWindows(); delay(60_000) } }
    }

    private val lastBrowserCheck = HashMap<String, Long>()
    private var lastSettingsCheck = 0L

    /** All the text a window shows, to a limit, so a page can be recognised. */
    private fun textsOf(root: android.view.accessibility.AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>(); var seen = 0
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo, depth: Int) {
            if (seen++ > 400 || depth > 25) return
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
            n.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0); return out
    }

    /** LA6: Spark's own settings pages go through the wall. Best effort, logged each time one is seen. */
    private fun checkSettings(pkg: String) {
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        if (now - lastSettingsCheck < 400) return
        lastSettingsCheck = now
        try {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != pkg) { root.recycle(); return }
            val label = try { packageManager.getApplicationLabel(applicationInfo).toString() } catch (e: Exception) { "Spark" }
            val page = guardedPage(pkg, textsOf(root), label)
            root.recycle()
            if (page != null && !SettingsGate.open()) {
                scope.launch { try { app.efa.data.EfaDb.get(applicationContext).tamper().insert(app.efa.data.TamperRow(atMs = now, kind = "settings_page", detail = page.name)) } catch (e: Exception) { } }
                startActivity(Intent(this, SettingsGateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION).putExtra(SettingsGateActivity.EXTRA_PAGE, page.name))
            }
        } catch (e: Exception) { /* X7: a page that vanishes while being read is not an error */ }
    }

    /**
     * K10: in a browser, read the address bar and block the sites of blocked apps, private tabs included (a private tab still has an address bar). Only the
     * address bar's text is read, and only in a browser Spark knows. An unknown browser, an in-app web view or a mirror site can slip through (K8, K10).
     */
    private fun checkBrowser(pkg: String) {
        val browser = browserFor(pkg) ?: return
        val now = System.currentTimeMillis()
        if (now - (lastBrowserCheck[pkg] ?: 0L) < 250) return
        lastBrowserCheck[pkg] = now
        try {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != pkg) { root.recycle(); return }
            val bar = root.findAccessibilityNodeInfosByViewId(browser.urlBarId).firstOrNull()
            val host = hostOf(bar?.text?.toString())
            bar?.recycle(); root.recycle()
            BlockingState.decideHost(host)?.let { (owner, _) -> show(owner) }
        } catch (e: Exception) { /* a tab that vanishes while being read is not an error; X7: nothing is blocked on a failure */ }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            event.packageName?.toString()?.let { checkBrowser(it); checkSettings(it) }
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        event.packageName?.toString()?.let { checkBrowser(it); checkSettings(it) }
        if (AlarmSession.ringing && event.packageName?.toString() != packageName && event.packageName?.toString() != "com.android.systemui") { showAlarm(); return }
        checkWindows(event.packageName?.toString().takeIf { event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED })
    }

    /**
     * Looks at every visible application window and puts the blocked card in front of a blocked one (K1 to K3, LA7: split screen, floating windows and picture-in-picture
     * are all windows here). Called on window events, and every minute, so an app that was already open when a block began is closed within a minute and not only the
     * next time its window changes.
     */
    fun checkWindows(eventPackage: String? = null) {
        val packages = LinkedHashSet<String>()
        eventPackage?.let { packages += it }
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

    /** M5: when the alarm rings the alarm screen is put in front, even while another app is open. An accessibility service may start an activity from the background. */
    fun showAlarm() {
        startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onInterrupt() {}

    companion object { @Volatile var instance: BlockerService? = null }

    override fun onDestroy() { if (instance === this) instance = null; scope.cancel(); SleepCollector.stop(applicationContext); super.onDestroy() }
}
