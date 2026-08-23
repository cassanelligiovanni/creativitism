package com.creativitism.appredirector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level E2E suite for persistent grayscale mode. Every test drives the real
 * `RedirectorService` foreground poll and observes the actual device truth
 * (Settings.Secure daltonizer keys, readable without any permission) rather than
 * mocking any layer. Screenshots do NOT show the color transform on the emulator, so
 * these secure settings are the only oracle used here.
 *
 * Tests run IN the app process under instrumentation (standard AndroidJUnitRunner
 * model), so RedirectorService — started from this same process — is a real,
 * observable service for the duration of each test method.
 */
@RunWith(AndroidJUnit4::class)
class GrayscaleE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    private lateinit var store: GrayscaleStore

    private val settingsPackage = "com.android.settings"

    private val launcherPackage: String by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?: "com.google.android.apps.nexuslauncher"
    }

    private val settingsLabel: String by lazy {
        val appInfo = context.packageManager.getApplicationInfo(settingsPackage, 0)
        context.packageManager.getApplicationLabel(appInfo).toString()
    }

    // --- Shell helper -------------------------------------------------------

    /**
     * Executes a shell command via UiAutomation and returns its stdout, fully
     * drained. The stream MUST be read to EOF or the command may not actually run.
     */
    private fun shell(cmd: String): String {
        val pfd: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
            stream.bufferedReader().readText()
        }
    }

    // --- Device-truth oracle --------------------------------------------------

    private fun daltonizerEnabled(): Int =
        Settings.Secure.getInt(context.contentResolver, GrayscaleController.KEY_DALTONIZER_ENABLED, 0)

    private fun daltonizerMode(): Int =
        Settings.Secure.getInt(
            context.contentResolver,
            GrayscaleController.KEY_DALTONIZER,
            GrayscalePolicy.DALTONIZER_DISABLED,
        )

    private fun isDeviceGray(): Boolean =
        daltonizerEnabled() == 1 && daltonizerMode() == GrayscalePolicy.DALTONIZER_MONOCHROMACY

    private fun awaitGrayscale(expectGray: Boolean, timeoutMs: Long = 10_000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isDeviceGray() == expectGray) return
            Thread.sleep(250)
        }
        fail(
            "Expected device grayscale=$expectGray within ${timeoutMs}ms but got " +
                "daltonizer_enabled=${daltonizerEnabled()} daltonizer=${daltonizerMode()}",
        )
    }

    private fun awaitServiceStopped(timeoutMs: Long = 5_000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!ServiceKeepAliveManager.isServiceRunning(context)) return
            Thread.sleep(100)
        }
        fail("RedirectorService did not stop within ${timeoutMs}ms")
    }

    private fun awaitTrue(timeoutMs: Long, description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            Thread.sleep(100)
        }
        fail("Timed out after ${timeoutMs}ms waiting for: $description")
    }

    /**
     * MainActivity's Guard tab hosts ~10 live [GlassCardView]s, each backed by a
     * continuously-redrawing BlurView tied to the shared BlurTarget. That constant
     * frame invalidation means Espresso's idle-sync (MAIN_LOOPER_HAS_IDLED) never
     * resolves on this screen, so `onView(...).perform(click())` reliably times out
     * here (AppNotIdleException) even with window/transition animations globally
     * disabled. Real UI tap injected via the input system sidesteps Espresso's
     * idle-wait while still exercising the genuine on-screen switch.
     */
    private fun tapViewOnScreen(scenario: ActivityScenario<MainActivity>, viewId: Int) {
        // The Guard tab scrolls; the target may start below the fold, so bring it
        // on-screen first (Espresso scrollTo() would hang on this screen's idle-sync).
        scenario.onActivity { activity ->
            val view = activity.findViewById<View>(viewId)
            view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height), true)
        }
        Thread.sleep(600) // let the scroll settle
        val loc = IntArray(2)
        var width = 0
        var height = 0
        scenario.onActivity { activity ->
            val view = activity.findViewById<View>(viewId)
            view.getLocationOnScreen(loc)
            width = view.width
            height = view.height
        }
        val x = loc[0] + width / 2
        val y = loc[1] + height / 2
        shell("input tap $x $y")
    }

    // --- App/service control ---------------------------------------------------

    private fun startService() {
        context.startForegroundService(Intent(context, RedirectorService::class.java))
    }

    private fun stopService() {
        context.stopService(Intent(context, RedirectorService::class.java))
    }

    private fun launchSettings() {
        shell("am start -W -n $settingsPackage/.Settings")
    }

    private fun pressHome() {
        shell("input keyevent KEYCODE_HOME")
    }

    // --- Harness ----------------------------------------------------------------

    @Before
    fun setUp() {
        shell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        shell("appops set ${context.packageName} android:get_usage_stats allow")
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        shell("svc power stayon true")
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")

        // Pre-exempt from battery optimization so MainActivity never shows its
        // "ignore battery optimizations?" AlertDialog. That dialog outlives an
        // ActivityScenario.close() (WindowLeaked), and the leaked window was
        // observed to stall the *next* test's Espresso idle-sync by ~2 minutes.
        shell("dumpsys deviceidle whitelist +${context.packageName}")

        resetGrayscaleState()
    }

    @After
    fun tearDown() {
        resetGrayscaleState()
        shell("settings delete secure accessibility_display_daltonizer_enabled")
        shell("dumpsys deviceidle whitelist -${context.packageName}")
    }

    private fun resetGrayscaleState() {
        store = GrayscaleStore(context)
        store.enabled = false
        store.colorApps().forEach { store.removeColorApp(it) }
        store.clearSnapshot()
        stopService()

        shell("settings put secure accessibility_display_daltonizer_enabled 0")
        shell("settings delete secure accessibility_display_daltonizer")
    }

    // --- Tests --------------------------------------------------------------

    @Test
    fun enableGrayscale_viaUiSwitch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tapViewOnScreen(scenario, R.id.grayscaleSwitch)

            awaitTrue(timeoutMs = 5_000, description = "GrayscaleStore.enabled becomes true") {
                GrayscaleStore(context).enabled
            }
            awaitGrayscale(true)
        }
    }

    @Test
    fun normalApp_getsGrayscale() {
        store.enabled = true
        startService()

        launchSettings()
        awaitGrayscale(true)
    }

    @Test
    fun excludedApp_keepsColor() {
        store.enabled = true
        store.addColorApp(settingsPackage)
        startService()

        launchSettings()
        awaitGrayscale(false)
    }

    @Test
    fun transition_excludedToNormal() {
        store.enabled = true
        store.addColorApp(settingsPackage)
        startService()
        launchSettings()
        awaitGrayscale(false)

        pressHome()
        awaitGrayscale(true)
    }

    @Test
    fun transition_normalToExcluded() {
        store.enabled = true
        store.addColorApp(settingsPackage)
        startService()

        pressHome()
        awaitGrayscale(true)

        launchSettings()
        awaitGrayscale(false)
    }

    @Test
    fun multipleExclusions() {
        store.enabled = true
        store.addColorApp(settingsPackage)
        store.addColorApp(launcherPackage)
        startService()

        pressHome()
        awaitGrayscale(false)

        launchSettings()
        awaitGrayscale(false)

        store.removeColorApp(launcherPackage)
        pressHome()
        awaitGrayscale(true)
    }

    @Test
    fun removeExclusionWhileActive() {
        store.enabled = true
        store.addColorApp(settingsPackage)
        startService()
        launchSettings()
        awaitGrayscale(false)

        store.removeColorApp(settingsPackage)
        awaitGrayscale(true)
    }

    @Test
    fun addExclusionWhileActive() {
        store.enabled = true
        startService()
        launchSettings()
        awaitGrayscale(true)

        store.addColorApp(settingsPackage)
        awaitGrayscale(false)
    }

    @Test
    fun serviceRestart_reappliesState() {
        store.enabled = true
        startService()
        pressHome()
        awaitGrayscale(true)

        stopService()
        awaitServiceStopped()
        assertTrue(
            "Setting must remain gray after service death; it is never cleared on onDestroy",
            isDeviceGray(),
        )

        store.addColorApp(settingsPackage)
        startService()
        launchSettings()
        awaitGrayscale(false)
    }

    @Test
    fun permissionRevoked_failsSafe_thenRecovers() {
        store.enabled = true
        startService()
        pressHome()
        awaitGrayscale(true)

        shell("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")

        store.addColorApp(settingsPackage)
        launchSettings()
        Thread.sleep(2_000)

        assertTrue(
            "Device should stay gray: permission is revoked so the controller cannot write",
            isDeviceGray(),
        )
        assertTrue(
            "Instrumentation process/service must survive the revoke",
            ServiceKeepAliveManager.isServiceRunning(context),
        )
        assertTrue("Store must remain readable after the revoke", store.enabled)

        shell("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        awaitGrayscale(false, timeoutMs = 15_000)
    }

    @Test
    fun snapshotRestored_whenUserHadColorCorrection() {
        // Simulate a user who already had color-blindness correction configured
        // (deuteranomaly, mode 11) before grayscale mode ever took over.
        shell("settings put secure accessibility_display_daltonizer_enabled 1")
        shell("settings put secure accessibility_display_daltonizer 11")

        store.enabled = true
        startService()
        pressHome()
        awaitGrayscale(true)

        store.addColorApp(launcherPackage)
        awaitGrayscale(false)
        assertEquals("User's daltonizer_enabled should be restored", 1, daltonizerEnabled())
        assertEquals("User's daltonizer mode should be restored, not just disabled", 11, daltonizerMode())
    }

    @Test
    fun colorAppsPicker_togglePersists() {
        val wasColorApp = store.isColorApp(settingsPackage)

        ActivityScenario.launch(ColorAppsActivity::class.java).use {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var clicked = false
            var lastError: Throwable? = null
            while (!clicked && SystemClock.elapsedRealtime() < deadline) {
                try {
                    onView(withId(R.id.searchInput)).perform(replaceText(settingsLabel), closeSoftKeyboard())
                    onView(
                        allOf(
                            withText(settingsLabel),
                            isDescendantOfA(withId(R.id.colorAppsRecyclerView)),
                        ),
                    ).perform(click())
                    clicked = true
                } catch (e: Throwable) {
                    lastError = e
                    Thread.sleep(300)
                }
            }
            assertTrue(
                "Could not find/click row '$settingsLabel' in ColorAppsActivity's list: $lastError",
                clicked,
            )
        }

        assertEquals(!wasColorApp, GrayscaleStore(context).isColorApp(settingsPackage))
    }
}
