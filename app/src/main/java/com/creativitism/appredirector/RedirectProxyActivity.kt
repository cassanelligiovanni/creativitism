package com.creativitism.appredirector

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class RedirectProxyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetItem = intent.getStringExtra("TARGET_ITEM")
        if (targetItem != null) {
            // Use the system's package manager to determine if the item is a launchable app.
            val launchIntent = packageManager.getLaunchIntentForPackage(targetItem)

            if (launchIntent != null) {
                // It's an app. Launch it.
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                // It's not an app, so treat it as a URL.
                var url = targetItem
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://$url"
                }
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Check if there is an activity that can handle this intent
                if (browserIntent.resolveActivity(packageManager) != null) {
                    startActivity(browserIntent)
                }
            }
        }

        // Finish this activity immediately after launching the target
        finish()
    }
} 