package com.creativitism.appredirector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

class RedirectorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var redirectionManager: RedirectionManager
    private lateinit var appListManager: AppListManager
    private lateinit var allowanceManager: TemporaryAllowanceManager

    // Last time (elapsedRealtime) the interstitial was launched, per package.
    private val interstitialLaunchedAt = mutableMapOf<String, Long>()

    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "RedirectorServiceChannel"

        // Don't relaunch the interstitial for the same package within this window,
        // so the 500ms poll doesn't stack activities while it animates in.
        private const val INTERSTITIAL_DEBOUNCE_MILLIS = 3_000L
    }

    override fun onCreate() {
        super.onCreate()
        redirectionManager = RedirectionManager(this)
        appListManager = AppListManager(this)
        allowanceManager = TemporaryAllowanceManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        startMonitoring()

        return START_STICKY
    }

    private fun startMonitoring() {
        scope.launch {
            delay(1000) // Add a 1-second delay to mitigate race conditions on service start
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    // Log any errors to avoid crashing the service
                    Log.e("RedirectorService", "Error in monitoring loop", e)
                }
                delay(500) // Check every 500ms
            }
        }
    }

    // Runs every tick (not only on foreground changes) so an expired 5-minute
    // allowance kicks the user out even while they stay inside the blocked app.
    private fun checkForegroundApp() {
        // Usage stats lag behind for a few seconds after the interstitial covers a
        // blocked app; relaunching it would restart the countdown.
        if (BlockedInterstitialActivity.isShowing) return
        val foregroundApp = getForegroundApp() ?: return
        if (foregroundApp == packageName) return
        if (!redirectionManager.isSoulSucking(foregroundApp)) return
        if (allowanceManager.isAllowed(foregroundApp)) return

        val now = SystemClock.elapsedRealtime()
        val lastLaunch = interstitialLaunchedAt[foregroundApp] ?: 0L
        if (now - lastLaunch < INTERSTITIAL_DEBOUNCE_MILLIS) return
        interstitialLaunchedAt[foregroundApp] = now

        val interstitialIntent = Intent(this, BlockedInterstitialActivity::class.java).apply {
            putExtra(BlockedInterstitialActivity.EXTRA_BLOCKED_PACKAGE, foregroundApp)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(interstitialIntent)
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
        val name = getString(R.string.notification_channel_name)
        val descriptionText = getString(R.string.notification_channel_description)
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
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_tilde)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // Stop all coroutines
        Log.d("RedirectorService", "Service destroyed")

        // Schedule a restart if the service was stopped unexpectedly
        val sharedPrefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean("service_enabled", false)) {
            Log.d("RedirectorService", "Service should be running, scheduling restart")
            ServiceKeepAliveManager.schedulePeriodicCheck(this)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("RedirectorService", "Task removed, but service will continue running")

        // Restart the service when the app is removed from recent apps
        val sharedPrefs = getSharedPreferences("app_state", Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean("service_enabled", false)) {
            val restartServiceIntent = Intent(applicationContext, RedirectorService::class.java)
            applicationContext.startForegroundService(restartServiceIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 