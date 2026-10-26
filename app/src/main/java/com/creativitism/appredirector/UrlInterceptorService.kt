package com.creativitism.appredirector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalDateTime

class UrlInterceptorService : AccessibilityService() {

    private lateinit var redirectionManager: RedirectionManager
    private lateinit var allowanceManager: TemporaryAllowanceManager
    private lateinit var visitTracker: VisitTracker
    private lateinit var scheduleStore: ScheduleStore
    private lateinit var pauseStore: PauseStore

    private var lastUrl: String? = null
    private var lastMatchedDomain: String? = null

    // Last alarm launch (elapsedRealtime) per domain — mirrors RedirectorService debounce.
    private val alarmLaunchedAt = mutableMapOf<String, Long>()

    companion object {
        private const val ALARM_DEBOUNCE_MILLIS = 3_000L
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        redirectionManager = RedirectionManager(this)
        allowanceManager = TemporaryAllowanceManager(this)
        visitTracker = VisitTracker(this)
        scheduleStore = ScheduleStore(this)
        pauseStore = PauseStore(this)
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf("com.android.chrome")
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        this.serviceInfo = info
        Log.d("UrlInterceptorService", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source ?: return
        val currentUrl = findUrlInNode(source) ?: return

        val wallNow = System.currentTimeMillis()
        val blocked = redirectionManager.getSoulSuckingItems()
        val matchedDomain = BlockMatcher.resolve(currentUrl, blocked)

        if (matchedDomain != null) {
            lastMatchedDomain = matchedDomain
            // Keep visit alive while the URL bar still matches this domain.
            visitTracker.touch(matchedDomain, wallNow)
            maybeLaunchAlarm(matchedDomain, wallNow)
        } else if (currentUrl != lastUrl && lastMatchedDomain != null) {
            // Navigate-away: stop touching so the absence clock runs.
            // Do not endVisit here — VisitTracker + RedirectorService close after 5 min.
            lastMatchedDomain = null
        }

        lastUrl = currentUrl
    }

    private fun maybeLaunchAlarm(domain: String, wallNow: Long) {
        if (AlarmActivity.isShowing) return
        // A timed Protection pause covers websites too.
        if (pauseStore.isProtectionPaused(wallNow)) return

        val localNow = LocalDateTime.now()
        val minuteOfDay = localNow.hour * 60 + localNow.minute
        val weekdayMonday0 = localNow.dayOfWeek.value - 1
        val armed = SchedulePolicy.isArmed(scheduleStore.load(), minuteOfDay, weekdayMonday0)
        if (!armed) return

        if (allowanceManager.isAllowed(
                domain,
                wallNow,
                visitStillOpen = visitTracker.isVisitOpen(domain, wallNow),
            )
        ) return

        val elapsed = SystemClock.elapsedRealtime()
        val lastLaunch = alarmLaunchedAt[domain] ?: 0L
        if (elapsed - lastLaunch < ALARM_DEBOUNCE_MILLIS) return
        alarmLaunchedAt[domain] = elapsed

        Log.d("UrlInterceptorService", "Launching alarm for blocked domain: $domain")
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra(AlarmActivity.EXTRA_BLOCKED_ITEM, domain)
            putExtra(AlarmActivity.EXTRA_DISPLAY_NAME, domain)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(alarmIntent)
    }

    private fun findUrlInNode(nodeInfo: AccessibilityNodeInfo): String? {
        // Chrome's URL bar has a resource ID of "com.android.chrome:id/url_bar"
        val urlBarNodes = nodeInfo.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        if (urlBarNodes.isNotEmpty()) {
            val urlBar = urlBarNodes[0]
            val url = urlBar.text?.toString()
            urlBar.recycle()
            return url
        }
        return null
    }

    override fun onInterrupt() {
        Log.d("UrlInterceptorService", "Service interrupted")
    }
}
