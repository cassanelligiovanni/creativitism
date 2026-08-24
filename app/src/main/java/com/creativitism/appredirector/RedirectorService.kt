package com.creativitism.appredirector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import java.time.LocalDateTime

class RedirectorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var monitoringJob: Job? = null

    private lateinit var redirectionManager: RedirectionManager
    private lateinit var appListManager: AppListManager
    private lateinit var allowanceManager: TemporaryAllowanceManager
    private lateinit var visitTracker: VisitTracker
    private lateinit var scheduleStore: ScheduleStore
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var grayscaleStore: GrayscaleStore
    private lateinit var grayscaleController: GrayscaleController
    private lateinit var powerManager: PowerManager

    // Last time (elapsedRealtime) the alarm was launched, per package.
    private val alarmLaunchedAt = mutableMapOf<String, Long>()
    private var lastNotificationText: String? = null

    // Usage-event watermark for foreground detection: each tick reads only events
    // newer than this, and the last ACTIVITY_RESUMED package seen wins.
    private var lastEventTimestamp = 0L
    private var lastKnownForeground: String? = null

    companion object {
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "RedirectorServiceChannel"

        // Don't relaunch the alarm for the same package within this window,
        // so the 500ms poll doesn't stack activities while it animates in.
        private const val ALARM_DEBOUNCE_MILLIS = 3_000L

        /**
         * The monitoring service must run while either feature that depends on
         * foreground-app detection is on: app blocking or grayscale mode.
         */
        fun shouldBeRunning(context: Context): Boolean {
            val appState = context.getSharedPreferences("app_state", Context.MODE_PRIVATE)
            return appState.getBoolean("service_enabled", false) ||
                GrayscaleStore(context).enabled
        }
    }

    override fun onCreate() {
        super.onCreate()
        redirectionManager = RedirectionManager(this)
        appListManager = AppListManager(this)
        allowanceManager = TemporaryAllowanceManager(this)
        visitTracker = VisitTracker(this)
        scheduleStore = ScheduleStore(this)
        settingsStore = AppSettingsStore(this)
        grayscaleStore = GrayscaleStore(this)
        grayscaleController = GrayscaleController(this, grayscaleStore)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification(getString(R.string.notification_text))
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        startMonitoring()

        return START_STICKY
    }

    // Idempotent: onStartCommand fires again for every startForegroundService call
    // (boot receiver, keep-alive, unlock, self-heal) and must not stack extra loops.
    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        monitoringJob = scope.launch {
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
        val wallNow = System.currentTimeMillis()
        val localNow = LocalDateTime.now()
        val minuteOfDay = localNow.hour * 60 + localNow.minute
        val weekdayMonday0 = localNow.dayOfWeek.value - 1
        val schedule = scheduleStore.load()
        val armed = SchedulePolicy.isArmed(schedule, minuteOfDay, weekdayMonday0)
        val resumeLabel = SchedulePolicy.nextResumeLabel(schedule, minuteOfDay, weekdayMonday0)
        val blockingEnabled = getSharedPreferences("app_state", Context.MODE_PRIVATE)
            .getBoolean("service_enabled", false)
        updateNotification(blockingEnabled, armed, resumeLabel)

        if (AlarmActivity.isShowing) {
            // Our full-screen alarm is on top, so we are the effective foreground.
            enforceGrayscale(packageName)
            if (!armed) {
                mainHandler.post { AlarmActivity.dismissIfShowingForDormant() }
            }
            return
        }

        val foreground = getForegroundApp()
        enforceGrayscale(foreground)
        if (foreground == null) return

        // Everything below is app blocking; the service may also be running for
        // grayscale mode alone, with the Protection switch off.
        if (!blockingEnabled) return

        if (redirectionManager.getCreativityBoostingItems().contains(foreground)) {
            settingsStore.lastCreativeItem = foreground
        }

        for (id in visitTracker.openVisitIds()) {
            if (id == foreground) {
                visitTracker.touch(id, wallNow)
            } else if (!visitTracker.isVisitOpen(id, wallNow)) {
                visitTracker.endVisit(id)
            }
        }

        if (!armed) return
        if (foreground == packageName) return
        if (!redirectionManager.isSoulSucking(foreground)) return
        if (allowanceManager.isAllowed(
                foreground,
                wallNow,
                visitStillOpen = visitTracker.isVisitOpen(foreground, wallNow),
            )
        ) return

        val now = SystemClock.elapsedRealtime()
        val lastLaunch = alarmLaunchedAt[foreground] ?: 0L
        if (now - lastLaunch < ALARM_DEBOUNCE_MILLIS) return
        alarmLaunchedAt[foreground] = now

        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_BLOCKED_ITEM, foreground)
            putExtra(AlarmActivity.EXTRA_DISPLAY_NAME, resolveDisplayName(foreground))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(alarmIntent)
    }

    // Grayscale is persistent and schedule-independent: every tick drives the
    // system color-correction setting toward what the policy wants for the
    // current foreground app. The controller skips redundant writes.
    private fun enforceGrayscale(foreground: String?) {
        val want = GrayscalePolicy.wantsGrayscale(
            modeEnabled = grayscaleStore.enabled,
            screenInteractive = powerManager.isInteractive,
            foregroundPackage = foreground,
            colorApps = grayscaleStore.colorApps(),
        )
        if (want != null) {
            grayscaleController.apply(want)
        }
    }

    private fun resolveDisplayName(pkg: String): String = try {
        val appInfo = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    // Foreground detection via usage EVENTS, not the aggregate lastTimeUsed stats:
    // some launchers (e.g. Nothing OS) do not bump the launcher's aggregate on a
    // gesture-home exit, so the aggregate max keeps naming the app the user just
    // left. ACTIVITY_RESUMED events fire on every transition, gestures included.
    // (ACTIVITY_RESUMED shares its value with the pre-API-29 MOVE_TO_FOREGROUND,
    // so this works across the whole supported range.)
    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        if (lastEventTimestamp == 0L) {
            lastEventTimestamp = now - 60_000
        }
        // Query with a small overlap: events can be committed slightly late, and
        // reprocessing an already-seen RESUMED event is harmless.
        val events = usageStatsManager.queryEvents(lastEventTimestamp - 1_000, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > lastEventTimestamp) {
                lastEventTimestamp = event.timeStamp
            }
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastKnownForeground = event.packageName
            }
        }
        if (lastKnownForeground == null) {
            // No resume event in the lookback window (fresh service on an idle
            // device): fall back to the aggregate API for an initial answer.
            lastKnownForeground = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 1000 * 10, now
            ).maxByOrNull { it.lastTimeUsed }?.packageName
        }
        return lastKnownForeground
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

    private fun updateNotification(blockingEnabled: Boolean, armed: Boolean, resumeLabel: String?) {
        val text = when {
            !blockingEnabled -> getString(R.string.notification_text_grayscale_only)
            armed -> getString(R.string.notification_text)
            else -> getString(
                R.string.notification_text_dormant,
                resumeLabel ?: "--:--",
            )
        }
        if (text == lastNotificationText) return
        lastNotificationText = text
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_tilde)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // Stop all coroutines
        Log.d("RedirectorService", "Service destroyed")

        // Schedule a restart if the service was stopped unexpectedly
        if (shouldBeRunning(this)) {
            Log.d("RedirectorService", "Service should be running, scheduling restart")
            ServiceKeepAliveManager.schedulePeriodicCheck(this)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("RedirectorService", "Task removed, but service will continue running")

        // Restart the service when the app is removed from recent apps
        if (shouldBeRunning(this)) {
            val restartServiceIntent = Intent(applicationContext, RedirectorService::class.java)
            applicationContext.startForegroundService(restartServiceIntent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
