package com.creativitism.appredirector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that automatically restarts the RedirectorService
 * when the device boots up, connects to power, or the screen is unlocked.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ServiceRestartReceiver", "Received broadcast: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_USER_PRESENT -> {
                // Check if the service should be running
                if (shouldServiceBeRunning(context)) {
                    Log.d("ServiceRestartReceiver", "Starting RedirectorService")
                    try {
                        val serviceIntent = Intent(context, RedirectorService::class.java)
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        // Android 12+ restricts FGS starts from the background; the
                        // keep-alive alarm or the next unlock will retry.
                        Log.w("ServiceRestartReceiver", "Could not start service", e)
                    }

                    // Also schedule periodic checks
                    ServiceKeepAliveManager.schedulePeriodicCheck(context)
                }
            }
        }
    }

    private fun shouldServiceBeRunning(context: Context): Boolean {
        // Check if user has granted all necessary permissions
        val sharedPrefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("service_enabled", false)
    }
}
