package app.efa

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.efa.data.BlockingRepo
import app.efa.data.EfaDb
import efa.SETUP_ORDER
import efa.SetupStep
import efa.laterSteps
import efa.nextSetupStep

/** Tests turn the gate off, because the emulator has none of the system switches on. It is always on in the app. */
object SetupGate { @Volatile var enforced: Boolean = true }

/** Which setup steps are done right now (U1, U4). Each reads the real system state, so a switch turned off later is noticed. */
object SetupStatus {
    suspend fun read(ctx: Context, db: EfaDb): Set<SetupStep> {
        val done = mutableSetOf<SetupStep>()
        if (isBlockerEnabled(ctx)) done += SetupStep.ACCESSIBILITY
        if (BlockingRepo(db).list() != null) done += SetupStep.BLOCK_LIST
        if (isNotificationAccessOn(ctx)) done += SetupStep.NOTIFICATION_ACCESS
        if (BufferNotifier.allowed(ctx)) done += SetupStep.POST_NOTIFICATIONS
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) done += SetupStep.CAMERA
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) done += SetupStep.LOCATION
        if (ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) done += SetupStep.BATTERY
        if (isHomeApp(ctx)) done += SetupStep.DEFAULT_HOME
        return done
    }

    fun isHomeApp(ctx: Context): Boolean = ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** The system page or dialog for a step, for the ones that are not a runtime permission. null means it is asked with a runtime permission. */
    fun settingsIntent(ctx: Context, step: SetupStep): Intent? = when (step) {
        SetupStep.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        SetupStep.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        SetupStep.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        SetupStep.DEFAULT_HOME -> ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
        else -> null
    }

    fun runtimePermission(step: SetupStep): String? = when (step) {
        SetupStep.CAMERA -> Manifest.permission.CAMERA
        SetupStep.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        SetupStep.POST_NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        else -> null
    }
}

/**
 * First run (X6) and "restore this" (U4) are one screen. It shows the steps in order, asks for the next one with its plain reason,
 * and says what comes later. Essentials stay one tap away so an unfinished setup never traps the person (LA1, LA3).
 * Android makes the person switch the special ones on in system settings; this screen guides and then checks, it cannot switch them on.
 */
@Composable
fun SetupFlowScreen(
    done: Set<SetupStep>, restore: Boolean, onAct: (SetupStep) -> Unit, onEssential: (String) -> Unit, onOpenSetup: () -> Unit,
    crashNotice: Boolean = false, onRestoreLauncher: () -> Unit = {},
) {
    val c = LocalColors.current
    val next = nextSetupStep(done)
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Label(if (restore) "Restore this" else "First run")
                SparkLogo(36.dp)
            }
            BasicText(if (restore) "Something is switched off" else "Set up Spark", style = Ts(c.text, 32.sp, FontWeight.Bold, letterSpacing = (-0.8).sp))
            BasicText(
                if (restore) "Spark noticed that a switch was turned off or changed. This was recorded. Until each item below is back on, Spark waits here."
                else "Each step below is asked once, in this order, with its reason. Android makes you switch some on yourself, and Spark checks each one afterwards.",
                style = Ts(c.muted, 14.sp),
            )
            if (crashNotice) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Launcher paused")
                BasicText("Spark crashed 3 times in 10 minutes, so Android's own home screen took over. Blocking kept running.", style = Ts(c.text, 15.sp))
                Btn("Put Spark back as a home choice", onRestoreLauncher, modifier = Modifier.fillMaxWidth())
            }
            SETUP_ORDER.forEach { step ->
                val isDone = step in done
                val isNext = step == next
                Column(
                    Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 14.dp)
                        .semantics { contentDescription = "${step.title}. ${if (isDone) "Done" else if (isNext) "Next" else "Waiting"}. ${if (isNext) step.reason else ""}" },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        BasicText(step.title, style = Ts(if (isDone) c.muted else c.text, 18.sp, FontWeight.Bold))
                        Label(if (isDone) "Done" else if (isNext) "Next" else "Waiting")
                    }
                    if (isNext) {
                        BasicText(step.reason, style = Ts(c.muted, 14.sp))
                        Btn(actionLabel(step), { onAct(step) }, primary = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (!restore) Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("Later")
                laterSteps().forEach { BasicText("${it.title}. ${it.laterBecause}", style = Ts(c.muted, 13.sp)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Phone" to "phone", "Msgs" to "messages", "Maps" to "maps").forEach { (label, key) ->
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).card().clip(CardShape).clickable(role = Role.Button) { onEssential(key) }.semantics { contentDescription = "$label. Opens your $label app." },
                    contentAlignment = Alignment.Center,
                ) { BasicText(label, style = Ts(c.text, 14.sp, FontWeight.Medium)) }
            }
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).card().clip(CardShape).clickable(role = Role.Button, onClick = onOpenSetup).semantics { contentDescription = "Setup" },
                contentAlignment = Alignment.Center,
            ) { BasicText("Setup", style = Ts(c.muted, 14.sp, FontWeight.Medium)) }
        }
    }
}

private fun actionLabel(step: SetupStep) = when (step) {
    SetupStep.ACCESSIBILITY -> "Open accessibility settings"
    SetupStep.BLOCK_LIST -> "Choose the apps to block"
    SetupStep.NOTIFICATION_ACCESS -> "Open notification access"
    SetupStep.POST_NOTIFICATIONS -> "Allow the Buffer notification"
    SetupStep.CAMERA -> "Allow the camera"
    SetupStep.LOCATION -> "Allow location"
    SetupStep.BATTERY -> "Allow background running"
    SetupStep.DEFAULT_HOME -> "Make Spark my home app"
    else -> "Continue"
}
