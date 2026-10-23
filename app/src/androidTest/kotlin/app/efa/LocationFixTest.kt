package app.efa

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.efa.data.LocationFix
import efa.haversineM
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

/** The real LocationManager path, with a mock provider so the test needs no GPS. Names start with the ledger IDs they prove. */
@RunWith(AndroidJUnit4::class)
class LocationFixTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private lateinit var ctx: Context
    private lateinit var lm: LocationManager
    private val providers = mutableListOf<String>()

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        lm = ctx.getSystemService(LocationManager::class.java)
        // Read the command to its end so the permission is in place before a test provider is added.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("appops set ${ctx.packageName} android:mock_location allow")).use { it.readBytes() }
        val names = buildList { add(LocationManager.GPS_PROVIDER); add(LocationManager.NETWORK_PROVIDER); if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER) }
        for (n in names) try {
            runCatching { lm.removeTestProvider(n) }
            lm.addTestProvider(n, false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE)
            lm.setTestProviderEnabled(n, true); providers += n
        } catch (_: Exception) {}
    }
    @After fun tearDown() { providers.forEach { runCatching { lm.removeTestProvider(it) } } }

    private fun push(lat: Double, lon: Double, acc: Float) {
        for (n in providers) {
            val l = Location(n).apply { latitude = lat; longitude = lon; accuracy = acc; time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() }
            lm.setTestProviderLocation(n, l)
        }
    }

    @Test fun A8_one_fix_is_read_on_request_with_its_accuracy() = runBlocking {
        // Android hands location to foreground apps only, so an activity must be showing, as it is while the camera is open.
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        assertTrue(providers.isNotEmpty(), "a test provider could not be added")
        val job = launch(Dispatchers.Default) { repeat(40) { push(52.4507, -1.9356, 18f); delay(250) } }
        val fix = LocationFix.current(ctx, timeoutMs = 10_000)
        job.cancel(); scenario.close()
        assertNotNull(fix, "no fix arrived")
        assertTrue(haversineM(fix.lat, fix.lon, 52.4507, -1.9356) < 30, "fix is ${fix.lat},${fix.lon}")
        assertTrue(fix.accuracyM in 1.0..50.0, "accuracy ${fix.accuracyM}")
    }
    // The no-answer path (null, never a guess) cannot be made deterministic here: Android serves a recent cached fix to the next reader.
    // It is covered by the repo tests (null fix gives a retake) and by docs/DEVICE_TESTS.md on the real phone.
}
