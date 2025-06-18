package com.creativitism.appredirector

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RedirectProxyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetPackage = intent.getStringExtra("TARGET_PACKAGE")
        if (targetPackage != null) {
            val appListManager = AppListManager(this)
            appListManager.launchApp(targetPackage)
        }

        // Finish this activity immediately after launching the target
        finish()
    }
} 