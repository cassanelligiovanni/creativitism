package com.creativitism.appredirector

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * Manager that ensures the RedirectorService stays alive by scheduling
 * periodic checks using AlarmManager.
 */
object ServiceKeepAliveManager {

    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
    private const val ACTION_CHECK_SERVICE = "com.creativitism.appredirector.CHECK_SERVICE"
    private const val REQUEST_CODE_ALARM = 1001

    /**
     * Schedule a periodic check to ensure the service is running.
     */
    fun schedulePeriodicCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceCheckReceiver::class.java).apply {
            action = ACTION_CHECK_SERVICE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setRepeating for periodic alarms
        // Note: On newer Android versions, this might not be exact due to battery optimization
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            pendingIntent
        )

        Log.d("ServiceKeepAlive", "Periodic check scheduled every ${CHECK_INTERVAL_MS / 1000 / 60} minutes")
    }

    /**
     * Cancel the periodic check.
     */
    fun cancelPeriodicCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ServiceCheckReceiver::class.java).apply {
            action = ACTION_CHECK_SERVICE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d("ServiceKeepAlive", "Periodic check cancelled")
    }

    /**
     * Check if the RedirectorService is currently running.
     */
    fun isServiceRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = activityManager.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service.className == RedirectorService::class.java.name }
    }

    /**
     * BroadcastReceiver that checks if the service is running and restarts it if needed.
     */
    class ServiceCheckReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CHECK_SERVICE) {
                Log.d("ServiceCheckReceiver", "Checking service status...")

                val sharedPrefs = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
                val shouldBeRunning = sharedPrefs.getBoolean("service_enabled", false)

                if (shouldBeRunning && !isServiceRunning(context)) {
                    Log.d("ServiceCheckReceiver", "Service is not running but should be. Restarting...")
                    try {
                        val serviceIntent = Intent(context, RedirectorService::class.java)
                        context.startForegroundService(serviceIntent)
                    } catch (e: Exception) {
                        Log.w("ServiceCheckReceiver", "Could not restart service", e)
                    }
                } else {
                    Log.d("ServiceCheckReceiver", "Service is running correctly")
                }
            }
        }
    }
}
