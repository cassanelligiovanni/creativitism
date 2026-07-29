package com.creativitism.appredirector

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class CreativitismApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Creativitism uses a fixed Raycast dark theme regardless of system setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
