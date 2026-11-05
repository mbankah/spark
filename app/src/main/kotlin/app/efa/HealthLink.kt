package app.efa

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import efa.HealthSession
import java.time.Duration
import java.time.Instant

/**
 * Health Connect as an optional sleep input (S6). Reading only, sleep only, and only after the person allows it. Everything is caught: without Health Connect, without
 * permission or without a watch the answer is simply "no sessions", and nothing else in Spark changes (X7).
 */
object HealthLink {
    val READ_SLEEP: Set<String> = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    enum class Status { UNAVAILABLE, NEEDS_INSTALL, NEEDS_PERMISSION, READY }

    fun status(ctx: Context): Status = try {
        when (HealthConnectClient.getSdkStatus(ctx)) {
            HealthConnectClient.SDK_UNAVAILABLE, HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UNAVAILABLE
            else -> Status.READY
        }
    } catch (e: Exception) { Status.UNAVAILABLE }

    suspend fun granted(ctx: Context): Boolean = try { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(READ_SLEEP) } catch (e: Exception) { false }

    /** The sleep sessions that ended between [from] and [to], with the minutes actually asleep (awake and out-of-bed stages are taken off). */
    suspend fun sessions(ctx: Context, from: Instant, to: Instant): List<HealthSession> = try {
        if (!granted(ctx)) emptyList() else HealthConnectClient.getOrCreate(ctx).readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, to))).records.map { r ->
            val awake = r.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED || it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            HealthSession(r.startTime, r.endTime, (Duration.between(r.startTime, r.endTime).toMinutes() - awake).toInt().coerceAtLeast(0))
        }
    } catch (e: Exception) { emptyList() }
}

/** The page Health Connect shows when the person asks why Spark wants to read sleep. */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EfaTheme {
                val c = LocalColors.current
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Why Spark reads your sleep", style = Ts(c.text, 24.sp, androidx.compose.ui.text.font.FontWeight.Bold))
                    BasicText("If you wear a watch or band that writes sleep to Health Connect, Spark shows its night next to the one the phone worked out, so you can see when they disagree. Spark only reads sleep sessions. It writes nothing, shares nothing, and keeps nothing except the minutes, and it works the same without it.", style = Ts(c.text, 16.sp))
                    Btn("Close", { finish() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
