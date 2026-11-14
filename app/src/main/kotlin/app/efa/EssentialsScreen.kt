package app.efa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.EfaDb
import app.efa.data.META_ESSENTIALS_EXTRA
import app.efa.data.META_ESSENTIALS_WINDOW
import app.efa.data.MetaRow
import efa.*

/**
 * The essentials on the phone (MG1 to MG3a). Calls, messages, maps, banking, authenticators, the camera, the clock, Settings, the launcher and Spark are never blocked.
 * The phone's own default dialer, messages and home apps are worked out each time (MG2). The person can add apps, which loosens blocking, so it goes through the wall
 * and then a 5 minute window that takes at most 3 apps (MG3a). Taking an app out of the ones the person added needs no wall, because that only tightens.
 */
class EssentialsRepo(private val ctx: Context, private val db: EfaDb) {
    suspend fun extra(): Set<String> = db.meta().get(META_ESSENTIALS_EXTRA).orEmpty().split(",").filter { it.isNotBlank() }.toSet()

    /** MG2: worked out when it is enforced, not saved. */
    suspend fun resolve(): Set<String> {
        val defaults = ArrayList<String?>()
        try { defaults += (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage } catch (e: Exception) { }
        try { defaults += Telephony.Sms.getDefaultSmsPackage(ctx) } catch (e: Exception) { }
        try { defaults += ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName } catch (e: Exception) { }
        return resolveEssentials(defaults, extra())
    }

    suspend fun window(): EssentialsWindow = EssentialsWindow.decode(db.meta().get(META_ESSENTIALS_WINDOW))

    /** The wall has been passed: a fresh window opens. */
    suspend fun openWindow(nowMs: Long) = db.meta().put(MetaRow(META_ESSENTIALS_WINDOW, EssentialsWindow.passWall(nowMs).encode()))

    /** Adds one app if the window is open and has room. Returns false if it is closed or full. */
    suspend fun add(pkg: String, nowMs: Long): Boolean {
        val w = window()
        if (!w.canAdd(nowMs) || pkg in ESSENTIAL_CORE) return false
        db.meta().put(MetaRow(META_ESSENTIALS_EXTRA, (extra() + pkg).sorted().joinToString(",")))
        db.meta().put(MetaRow(META_ESSENTIALS_WINDOW, w.afterAdd(nowMs).encode()))
        return true
    }

    suspend fun remove(pkg: String) { db.meta().put(MetaRow(META_ESSENTIALS_EXTRA, (extra() - pkg).sorted().joinToString(","))) }
}

data class EssentialsUi(val added: List<AppInfo>, val windowMs: Long, val addedInWindow: Int, val installed: List<AppInfo>, val message: String? = null)

@Composable
fun EssentialsScreen(ui: EssentialsUi, onOpenWindow: () -> Unit, onAdd: (AppInfo) -> Unit, onRemove: (AppInfo) -> Unit, onBack: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("ESSENTIALS")
        BasicText("Apps that are never blocked", style = Ts(c.text, 28.sp, FontWeight.Bold))
        BasicText("Calls, messages, maps, banking, authenticators, the camera, the clock, Android Settings, the launcher and Spark are never blocked and never counted, whatever is on the block list or in force. You cannot remove those.", style = Ts(c.muted, 14.sp))
        ui.message?.let { Column(Modifier.fillMaxWidth().card().padding(16.dp)) { BasicText(it, style = Ts(c.text, 15.sp, FontWeight.Bold)) } }

        Label("YOUR ADDITIONS")
        if (ui.added.isEmpty()) BasicText("None.", style = Ts(c.muted, 14.sp))
        ui.added.forEach { a -> Column(Modifier.fillMaxWidth().card().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(a.label, style = Ts(c.text, 16.sp, FontWeight.Bold)); Btn("Remove", { onRemove(a) }, description = "Remove ${a.label} from the essentials. It can be blocked again.")
        } }

        if (ui.windowMs > 0) {
            Label("ADD AN APP")
            BasicText("The window is open for ${ui.windowMs / 60_000}:${"%02d".format(ui.windowMs / 1000 % 60)} more. ${ESSENTIALS_WINDOW_MAX_APPS - ui.addedInWindow} more can be added.", style = Ts(c.text, 15.sp, FontWeight.Bold))
            ui.installed.forEach { a -> Btn("Add ${a.label}", { onAdd(a) }, description = "Add ${a.label} to the essentials", modifier = Modifier.fillMaxWidth()) }
        } else {
            Label("ADD AN APP")
            BasicText("Adding an app loosens blocking, so it needs the typed wall. Passing it opens a 5 minute window that takes at most 3 apps.", style = Ts(c.muted, 14.sp))
            Btn("Open the add window: type ${ESSENTIALS_ADD_WALL} characters", onOpenWindow, primary = true, modifier = Modifier.fillMaxWidth(), description = "Open the add window. Needs the typed wall.")
        }
        Btn("Back", onBack, modifier = Modifier.fillMaxWidth())
    }
}
