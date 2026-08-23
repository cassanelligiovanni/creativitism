package com.creativitism.appredirector

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline smoke test: the app launches and the Guard tab renders.
 *
 * Deliberately avoids Espresso view interactions: the Guard tab's GlassCardViews wrap
 * live BlurViews that invalidate via Choreographer every frame, so Espresso's
 * MAIN_LOOPER_HAS_IDLED idle-sync never resolves on this screen (deterministic
 * AppNotIdleException even with all animation scales at 0). Direct view assertions
 * via ActivityScenario.onActivity sidestep the idle-sync entirely.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun mainActivityLaunches_guardTabVisible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.tabGuard).isShown)
                assertTrue(activity.findViewById<View>(R.id.serviceSwitch).isShown)
                assertTrue(activity.findViewById<View>(R.id.grayscaleCard).isShown)
            }
        }
    }
}
