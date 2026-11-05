package app.efa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import efa.*
import java.time.ZonedDateTime

/** LA6: whether Settings is open right now, after the wall. Shared by the service and the screen. */
object SettingsGate {
    @Volatile var allowance = SettingsAllowance(null)
    fun open(now: ZonedDateTime = BlockingState.now()) = allowance.open(now)
}

/**
 * The wall in front of Spark's own settings pages (LA6, K6, X5): the accessibility page, app info, the uninstall question and the default home choice. Best effort:
 * it is shown when the accessibility service recognises one of those pages. Cancelling or going back returns to the launcher.
 */
class SettingsGateActivity : ComponentActivity() {
    companion object { const val EXTRA_PAGE = "page" }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goHome() })
        val page = runCatching { GuardedPage.valueOf(intent.getStringExtra(EXTRA_PAGE).orEmpty()) }.getOrNull()
        setContent {
            EfaTheme {
                val target = remember { newWallString(SETTINGS_WALL_LENGTH) }
                val c = LocalColors.current
                Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Spark's settings", style = Ts(c.text, 28.sp, FontWeight.SemiBold))
                    BasicText("You are opening ${page?.label ?: "a page that controls Spark"}. Switching Spark off or removing it is the one thing the plan cannot work without, so it takes the typed wall. Android's other settings are not affected.", style = Ts(c.text, 16.sp))
                    Column(Modifier.fillMaxWidth().card().padding(16.dp)) { WallField(target) { SettingsGate.allowance = SettingsGate.allowance.after(BlockingState.now()); finishAndRemoveTask() } }
                    Btn("Go home", ::goHome, modifier = Modifier.fillMaxWidth())
                    BasicText("Best effort: a phone maker's own settings app, or safe mode, can go around this. Every time one of these pages is seen it is logged.", style = Ts(c.muted, 13.sp))
                }
            }
        }
    }
}
