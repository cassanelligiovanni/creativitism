package com.creativitism.appredirector

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class CreativitismApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Creativitism is always presented in its light "glass over paper" theme,
        // regardless of the system dark-mode setting.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
