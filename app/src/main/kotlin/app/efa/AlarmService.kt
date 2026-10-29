package app.efa

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.efa.data.EfaDb
import app.efa.data.MorningRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the alarm screen reads while the alarm rings: the step count, which the service gathers in parallel from the start (M2). */
object AlarmSession {
    @Volatile var steps = 0
    @Volatile var ringing = false

    fun stepsAvailable(ctx: Context): Boolean {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Rings the alarm (M5): the alarm sound on the alarm stream, vibration, and a full-screen notification that opens the alarm screen even over
 * the lock screen. It counts steps from the first moment, in parallel, so walking while a photo fails is not wasted (M2). It stops only when
 * the morning is recorded as stopped. Night audio (sermons, binaural beats) is never touched: this plays its own sound and nothing else is paused.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: MediaPlayer? = null
    private var stepListener: SensorEventListener? = null
    private var baseline = -1f

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRinging(); stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        AlarmSession.ringing = true
        if (player == null) startSound()
        if (stepListener == null) startSteps()
        return START_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); description = "The morning alarm" })
        val open = PendingIntent.getActivity(this, 2, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_flame).setContentTitle("Alarm").setContentText("Take the photo of your setup to stop it.")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true)
            .setContentIntent(open).setFullScreenIntent(open, true).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
    }

    private fun startSound() {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmService, uri); isLooping = true; prepare(); start()
            }
        } catch (e: Exception) { null }            // no sound is possible on this phone: the vibration and the screen still ring
        val vib = getSystemService(VibratorManager::class.java)?.defaultVibrator
        vib?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    private fun startSteps() {
        if (!AlarmSession.stepsAvailable(this)) return
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (baseline < 0) baseline = e.values[0]
                val steps = (e.values[0] - baseline).toInt().coerceAtLeast(0)
                AlarmSession.steps = steps
                scope.launch { MorningRepo(EfaDb.get(applicationContext)).let { r -> r.latest()?.let { r.setSteps(LocalDate.parse(it.date), steps) } } }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        stepListener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopRinging() {
        AlarmSession.ringing = false
        try { player?.stop() } catch (e: Exception) { /* not playing */ }
        player?.release(); player = null
        getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        stepListener?.let { (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(it) }; stepListener = null
    }

    override fun onDestroy() { stopRinging(); scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "app.efa.ALARM_STOP"
        private const val CHANNEL = "alarm"
        private const val NOTIFICATION_ID = 4203
        fun start(ctx: Context) { ContextCompat.startForegroundService(ctx, Intent(ctx, AlarmService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP)) }
    }

    @Suppress("unused") private val v: Vibrator? = null
}
