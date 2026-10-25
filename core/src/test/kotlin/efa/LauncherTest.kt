package efa

import kotlin.system.measureNanoTime
import kotlin.test.*

class LauncherTest {
    // ---- U1, U2 ----
    @Test fun `U1 setup asks for the steps in order until none is left`() {
        var done = emptySet<SetupStep>()
        val asked = mutableListOf<SetupStep>()
        while (true) { val n = nextSetupStep(done) ?: break; asked += n; done = done + n }
        assertEquals(SETUP_ORDER, asked)
        assertTrue(setupComplete(done))
    }

    @Test fun `U1 setup does not continue past a step that is not done`() {
        assertEquals(SetupStep.ACCESSIBILITY, nextSetupStep(emptySet()))
        assertEquals(SetupStep.ACCESSIBILITY, nextSetupStep(setOf(SetupStep.BLOCK_LIST, SetupStep.CAMERA)))
        assertFalse(setupComplete(SETUP_ORDER.toSet() - SetupStep.CAMERA))
    }

    @Test fun `LA1 LA3 the default home app is asked last so an unfinished setup never becomes the home screen`() {
        assertEquals(SetupStep.DEFAULT_HOME, SETUP_ORDER.last())
        assertTrue(SETUP_ORDER.dropLast(1).none { it == SetupStep.DEFAULT_HOME })
    }

    @Test fun `U2 every permission in the ledger list is either asked for or listed as later with a reason`() {
        val ledger = setOf("ACCESSIBILITY", "NOTIFICATION_ACCESS", "OVERLAY", "USAGE_ACCESS", "EXACT_ALARMS", "CAMERA", "LOCATION", "MICROPHONE", "ACTIVITY_RECOGNITION", "BATTERY", "DEFAULT_HOME")
        val known = SetupStep.entries.map { it.name }.toSet()
        assertTrue(ledger.all { it in known }, "missing: ${ledger - known}")
        assertTrue(laterSteps().all { it.laterBecause.isNotBlank() })
        assertEquals(setOf("OVERLAY", "USAGE_ACCESS", "EXACT_ALARMS", "MICROPHONE", "ACTIVITY_RECOGNITION"), laterSteps().map { it.name }.toSet())
    }

    @Test fun `A9 every step that is asked for has a plain reason`() {
        for (s in SETUP_ORDER) assertTrue(s.reason.length > 20, s.name)
    }

    @Test fun `U1 the microphone is never asked for before voice exists`() {
        assertFalse(SetupStep.MICROPHONE in SETUP_ORDER)
    }

    // ---- LA3 ----
    @Test fun `LA3 three crashes in ten minutes hand over to the default home`() {
        val now = 1_000_000_000L
        assertFalse(shouldHandOver(listOf(now - 1000, now - 500), now))
        assertTrue(shouldHandOver(listOf(now - 9 * 60_000, now - 5 * 60_000, now), now))
    }

    @Test fun `LA3 crashes older than ten minutes do not count`() {
        val now = 1_000_000_000L
        assertFalse(shouldHandOver(listOf(now - 11 * 60_000, now - 10 * 60_000 - 1, now), now))
        assertEquals(1, recentCrashes(listOf(now - 11 * 60_000, now), now).size)
    }

    @Test fun `LA3 exactly ten minutes ago still counts and the future does not`() {
        val now = 1_000_000_000L
        assertEquals(1, recentCrashes(listOf(now - CRASH_WINDOW_MS), now).size)
        assertEquals(0, recentCrashes(listOf(now + 1), now).size)
    }

    // ---- LA5 ----
    private val apps = listOf("Calendar", "Camera", "Chrome", "Clock", "Contacts", "Google Maps", "Instagram", "Messages", "Photos", "Play Store", "YouTube", "YouTube Music")

    @Test fun `LA5 a few letters find the app`() {
        assertEquals("Camera", apps[matchApps("cam", apps).first()])
        assertEquals("Instagram", apps[matchApps("ins", apps).first()])
        assertEquals("Chrome", apps[matchApps("chr", apps).single()])
    }

    @Test fun `LA5 a word inside a name matches and ranks after names that start with the letters`() {
        val r = matchApps("music", apps).map { apps[it] }
        assertEquals(listOf("YouTube Music"), r)
        assertEquals("Google Maps", apps[matchApps("maps", apps).first()])
    }

    @Test fun `LA5 letters in order find an app when nothing starts with them`() {
        assertEquals("Instagram", apps[matchApps("igm", apps).first()])
    }

    @Test fun `LA5 case does not matter and an empty query finds nothing, so there is no list to browse`() {
        assertEquals(matchApps("CAM", apps), matchApps("cam", apps))
        assertTrue(matchApps("", apps).isEmpty()); assertTrue(matchApps("   ", apps).isEmpty())
    }

    @Test fun `LA5 an unknown name finds nothing`() { assertTrue(matchApps("zzz", apps).isEmpty()) }

    @Test fun `LA5 results are ordered starts-with first then by name`() {
        val r = matchApps("c", apps).map { apps[it] }
        assertEquals(listOf("Calendar", "Camera", "Chrome", "Clock", "Contacts"), r.take(5))
    }

    @Test fun `LA4 search over 600 apps answers well inside 100 ms`() {
        val many = (1..600).map { "App number $it of the long list" } + apps
        matchApps("ins", many)                                                  // warm up
        val ms = measureNanoTime { repeat(20) { matchApps("ins", many) } } / 20 / 1_000_000.0
        assertTrue(ms < 20.0, "one search took $ms ms")
    }
}
