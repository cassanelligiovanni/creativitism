package com.creativitism.appredirector

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not part of the regular connected test suite: state-setup entry points invoked by
 * scripts/e2e_grayscale.sh (which needs grayscale enabled *before* rebooting the
 * device, something an instrumentation test cannot survive). Each method is guarded by
 * an instrumentation argument so `connectedDebugAndroidTest` skips them:
 *
 *   adb shell am instrument -w -e scriptHelper true \
 *     -e class com.creativitism.appredirector.GrayscaleScriptHelper#enableGrayscaleMode \
 *     com.creativitism.appredirector.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class GrayscaleScriptHelper {

    private fun requireScriptMode() {
        assumeTrue(
            "script-only helper; pass -e scriptHelper true",
            InstrumentationRegistry.getArguments().getString("scriptHelper") == "true",
        )
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The store persists with apply(), but `am instrument` force-stops this process
     * the moment the run ends — losing queued async writes. A commit() on the same
     * prefs instance is a write barrier: it returns only after every apply() queued
     * before it has reached disk.
     */
    private fun flushPrefs() {
        context.getSharedPreferences("grayscale_settings", android.content.Context.MODE_PRIVATE)
            .edit().commit()
    }

    /**
     * Persist grayscale mode on, the way MainActivity does. Optional
     * -e colorApps "pkg1,pkg2" seeds the exclusion list.
     *
     * The service is NOT started here: when `am instrument` exits it force-stops the
     * app process, taking any service started from it down too. The calling script
     * starts the service afterwards with
     * `adb shell am start-foreground-service -n <pkg>/.RedirectorService`.
     */
    @Test
    fun enableGrayscaleMode() {
        requireScriptMode()
        val store = GrayscaleStore(context)
        InstrumentationRegistry.getArguments().getString("colorApps")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.forEach { store.addColorApp(it.trim()) }
        store.enabled = true
        flushPrefs()
        ServiceKeepAliveManager.schedulePeriodicCheck(context)
    }

    /** Disable grayscale mode and restore the user's color settings. */
    @Test
    fun disableGrayscaleMode() {
        requireScriptMode()
        val store = GrayscaleStore(context)
        store.enabled = false
        GrayscaleController(context, store).release()
        store.colorApps().forEach { store.removeColorApp(it) }
        flushPrefs()
        ServiceKeepAliveManager.cancelPeriodicCheck(context)
        context.stopService(Intent(context, RedirectorService::class.java))
    }
}
