package app.efa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.WallLength
import efa.defaultBlockList
import efa.newWallString

data class AppInfo(val pkg: String, val label: String)

/** Launchable apps on this phone, except Spark. */
fun installedApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
}

private val SERVICE_NAMES = listOf("app.efa/app.efa.BlockerService", "app.efa/.BlockerService")

/** Is the blocking service switched on in Android's accessibility settings? Android only lets the person do that (U1, U4). */
fun isBlockerEnabled(ctx: Context): Boolean {
    val on = Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
    val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty().split(':')
    return on && list.any { it in SERVICE_NAMES }
}

fun openAccessibilitySettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** What the Setup screen shows about blocking. */
data class BlockingStatus(val serviceOn: Boolean, val listSize: Int?)

@Composable
fun BlockingCard(s: BlockingStatus, onOpenSettings: () -> Unit, onChoose: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("BLOCKING")
        BasicText(if (s.serviceOn) "On. Spark can see which app is in front." else "Off. Blocking does nothing until the Spark service is on.", style = Ts(c.text, 16.sp, FontWeight.SemiBold))
        BasicText(
            "Android lets only you switch this on. Spark reads which app is in front, only to close the apps you chose. It does not read what is on screen. Android also lets you switch it off, and Spark will notice and say so.",
            style = Ts(c.muted, 14.sp),
        )
        if (!s.serviceOn) Btn("Open accessibility settings", onOpenSettings, primary = true, modifier = Modifier.fillMaxWidth())
        BasicText(if (s.listSize == null) "Block list: not chosen yet." else "Block list: ${s.listSize} app${if (s.listSize == 1) "" else "s"}. Fixed, so changing it takes ${WallLength.BLOCKLIST_EDIT} typed characters.", style = Ts(c.text, 16.sp))
        Btn(if (s.listSize == null) "Choose the apps to block" else "Change the block list", onChoose, primary = s.listSize == null && s.serviceOn, modifier = Modifier.fillMaxWidth())
    }
}

/** K2, M6: chosen once at first setup. The social and video apps that are installed start selected. */
@Composable
fun BlockListScreen(apps: List<AppInfo>, initial: Set<String>?, onSave: (Set<String>) -> Unit, onCancel: () -> Unit) {
    val c = LocalColors.current
    var chosen by remember { mutableStateOf(initial ?: defaultBlockList(apps.map { it.pkg })) }
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Block list", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        BasicText("Chosen once. These apps are closed in work blocks, while getting ready, in transitions and after the night cutoff. Calls, maps, banking and your authenticator are never closed.", style = Ts(c.muted, 14.sp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().card(), contentPadding = PaddingValues(8.dp)) {
            items(apps, key = { it.pkg }) { a ->
                val on = a.pkg in chosen
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { chosen = if (on) chosen - a.pkg else chosen + a.pkg }
                        .semantics { selected = on; contentDescription = "${a.label}, ${if (on) "blocked" else "not blocked"}" }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(22.dp).background(if (on) c.accent else c.track, CardShape))
                    BasicText(a.label, style = Ts(c.text, 17.sp))
                    Spacer(Modifier.weight(1f))
                    BasicText(if (on) "blocked" else "", style = Ts(c.muted, 13.sp))
                }
            }
        }
        BasicText("${chosen.size} selected", style = Ts(c.muted, 14.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Btn("Save list", { onSave(chosen) }, primary = true, modifier = Modifier.weight(1f))
            Btn("Cancel", onCancel, modifier = Modifier.weight(1f))
        }
    }
}

/** K2, M7: changing a saved block list takes the wall. */
@Composable
fun ListWallGate(onPassed: () -> Unit, onCancel: () -> Unit) {
    val c = LocalColors.current
    val target = remember { newWallString(WallLength.BLOCKLIST_EDIT) }
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Change the block list", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
        BasicText("The list is fixed so it cannot be loosened in a weak moment. Type the characters below by hand to edit it.", style = Ts(c.text, 16.sp))
        Column(Modifier.fillMaxWidth().card().padding(16.dp)) { WallField(target, onPassed) }
        Btn("Cancel", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

/** What the Setup screen shows about notifications. */
data class NotificationsStatus(val accessOn: Boolean, val bufferNoticeOn: Boolean)

/**
 * Two separate switches, each made by the person in Android (U1, A9): notification access lets Spark hold notifications from blocked apps
 * (U8); the notification permission lets it show the Buffer on the lock screen (LA11).
 */
@Composable
fun NotificationsCard(s: NotificationsStatus, onOpenAccess: () -> Unit, onAllowBuffer: () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("NOTIFICATIONS")
        BasicText(if (s.accessOn) "Holding is on. Notifications from blocked apps wait in a digest." else "Holding is off. Blocked apps can still notify you.", style = Ts(c.text, 16.sp, FontWeight.Bold))
        BasicText("Held ones are kept on this phone only, shown after the block, and never deleted unseen. Calls, alarms, authenticator codes, banking alerts and MyUoB notices are never held.", style = Ts(c.muted, 14.sp))
        if (!s.accessOn) Btn("Open notification access", onOpenAccess, primary = true, modifier = Modifier.fillMaxWidth())
        BasicText(if (s.bufferNoticeOn) "The Buffer notification is on." else "The Buffer notification is off.", style = Ts(c.text, 16.sp, FontWeight.Bold))
        BasicText("It shows the Buffer, the block now and the next one on the lock screen, with a Quick add button. It is silent and has no badge.", style = Ts(c.muted, 14.sp))
        if (!s.bufferNoticeOn) Btn("Allow the Buffer notification", onAllowBuffer, modifier = Modifier.fillMaxWidth())
    }
}
