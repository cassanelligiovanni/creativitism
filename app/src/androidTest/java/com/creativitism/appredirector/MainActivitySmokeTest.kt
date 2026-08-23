package com.creativitism.appredirector

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Baseline smoke test: the app launches and the Guard tab renders. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun mainActivityLaunches_guardTabVisible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.tabGuard)).check(matches(isDisplayed()))
            onView(withId(R.id.serviceSwitch)).check(matches(isDisplayed()))
        }
    }
}
