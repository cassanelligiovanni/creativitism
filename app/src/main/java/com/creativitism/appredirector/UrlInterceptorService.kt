package com.creativitism.appredirector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UrlInterceptorService : AccessibilityService() {

    private lateinit var redirectionManager: RedirectionManager
    private var lastUrl: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        redirectionManager = RedirectionManager(this)
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
        val currentUrl = findUrlInNode(source)

        if (currentUrl != null && currentUrl != lastUrl) {
            lastUrl = currentUrl
            Log.d("UrlInterceptorService", "Detected URL: $currentUrl")

            if (redirectionManager.isSoulSucking(currentUrl)) {
                val targetItem = redirectionManager.getRandomCreativityBoostingItem()
                if (targetItem != null) {
                    Log.d("UrlInterceptorService", "Redirecting from $currentUrl to $targetItem")
                    val intent = Intent(this, RedirectProxyActivity::class.java).apply {
                        putExtra("TARGET_ITEM", targetItem)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            }
        }
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