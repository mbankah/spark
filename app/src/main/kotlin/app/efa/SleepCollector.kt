package app.efa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.BatteryManager
import android.os.PowerManager
import app.efa.data.EfaDb
import app.efa.data.SleepRepo
import efa.SensorKind
import efa.lightBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Passive sleep logging (S5, S7), record only: screen on and off, charging, Android's significant-motion trigger and the light sensor.
 * There is no microphone and no location. It lives as long as the blocking service does, which is the one component Android keeps alive
 * overnight for us. The motion trigger and the light reading are chosen to cost almost no battery (LA13): the trigger fires only when
 * the hardware sees movement, and light is read for one sample about every 10 minutes while the screen is off.
 * A sensor the phone does not have is recorded as unavailable and simply produces no events.
 */
object SleepCollector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiver: BroadcastReceiver? = null
    private var motion: TriggerEventListener? = null
    private var ticks = 0
    @Volatile var screenOff = false; private set

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (receiver != null) return
        val repo = SleepRepo(EfaDb.get(app))
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        screenOff = !pm.isInteractive
        scope.launch {
            // The state at start, so the first event is never a guess (S7): a gap before this is time nothing was recorded.
            repo.record(SensorKind.SERVICE_START)
            repo.record(if (pm.isInteractive) SensorKind.SCREEN_ON else SensorKind.SCREEN_OFF)
            repo.record(if (bm.isCharging) SensorKind.CHARGING_ON else SensorKind.CHARGING_OFF)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val kind = when (i.action) {
                    Intent.ACTION_SCREEN_ON -> SensorKind.SCREEN_ON.also { screenOff = false }
                    Intent.ACTION_SCREEN_OFF -> SensorKind.SCREEN_OFF.also { screenOff = true }
                    Intent.ACTION_POWER_CONNECTED -> SensorKind.CHARGING_ON
                    Intent.ACTION_POWER_DISCONNECTED -> SensorKind.CHARGING_OFF
                    else -> return
                }
                val at = System.currentTimeMillis()
                scope.launch { repo.record(kind, 0, at) }
            }
        }
        app.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        armMotion(app, repo)
    }

    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        receiver?.let { try { app.unregisterReceiver(it) } catch (e: Exception) { /* already gone */ } }
        receiver = null
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motion?.let { l -> sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { sm.cancelTriggerSensor(l, it) } }
        motion = null
    }

    /** The significant-motion trigger fires once and must be armed again each time. */
    private fun armMotion(app: Context, repo: SleepRepo) {
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        val l = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                val at = System.currentTimeMillis()
                scope.launch { repo.record(SensorKind.MOTION, 0, at) }
                if (receiver != null) sm.requestTriggerSensor(this, sensor)
            }
        }
        motion = l
        sm.requestTriggerSensor(l, sensor)
    }

    /** Called from the service's one-minute loop: a heartbeat, a light sample about every 10 minutes while the screen is off, and the nightly roll-up. */
    fun tick(ctx: Context) {
        val app = ctx.applicationContext
        val repo = SleepRepo(EfaDb.get(app))
        ticks++
        scope.launch {
            repo.heartbeat()
            try { AlarmScheduler.refresh(app) } catch (e: Exception) { /* the last alarm stays set (X7) */ }
            try { FreeSlotAlarm.refresh(app) } catch (e: Exception) { /* a free slot may end in silence (X7) */ }
            if (ticks % 10 == 0) repo.rollup(Instant.now())
        }
        if (screenOff && ticks % 10 == 5) sampleLight(app, repo)
    }

    /** For tests: takes one light reading now, the way the minute loop does while the screen is off. */
    fun sampleLightNow(ctx: Context) = sampleLight(ctx.applicationContext, SleepRepo(EfaDb.get(ctx.applicationContext)))

    private fun sampleLight(app: Context, repo: SleepRepo) {
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return
        val once = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                sm.unregisterListener(this)
                val at = System.currentTimeMillis(); val bucket = lightBucket(e.values[0])
                scope.launch { repo.record(SensorKind.LIGHT, bucket, at) }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(once, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        scope.launch { kotlinx.coroutines.delay(5_000); sm.unregisterListener(once) }     // never leave the sensor on
    }

}
