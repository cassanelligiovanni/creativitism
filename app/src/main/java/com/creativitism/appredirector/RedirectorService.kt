package com.creativitism.appredirector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class RedirectorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var redirectionManager: RedirectionManager
    private lateinit var appListManager: AppListManager
    private var lastForegroundApp: String? = null

    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "RedirectorServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        redirectionManager = RedirectionManager(this)
        appListManager = AppListManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        scope.launch {
            delay(1000) // Add a 1-second delay to mitigate race conditions on service start
            while (isActive) {
                try {
                    val foregroundApp = getForegroundApp()
                    if (foregroundApp != null && foregroundApp != lastForegroundApp) {
                        val targetPackage = redirectionManager.getRedirection(foregroundApp)
                        if (targetPackage != null) {
                            // To prevent re-launching the same redirected app
                            if (targetPackage != lastForegroundApp) {
                                // Launch the proxy activity instead of the app directly
                                val proxyIntent = Intent(this@RedirectorService, RedirectProxyActivity::class.java).apply {
                                    putExtra("TARGET_PACKAGE", targetPackage)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(proxyIntent)
                            }
                        }
                        lastForegroundApp = foregroundApp
                    }
                } catch (e: Exception) {
                    // Log any errors to avoid crashing the service
                    Log.e("RedirectorService", "Error in monitoring loop", e)
                }
                delay(500) // Check every 500ms
            }
        }
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time
        )
        return usageStats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun createNotificationChannel() {
        val name = "Redirection Service"
        val descriptionText = "Monitors app launches for redirection"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Redirector Active")
            .setContentText("Monitoring for app redirections.")
            .setSmallIcon(android.R.drawable.ic_popup_sync) // Use a safe system icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // Stop all coroutines
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 