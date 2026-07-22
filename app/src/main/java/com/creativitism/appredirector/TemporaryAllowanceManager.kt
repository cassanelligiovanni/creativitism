package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists per-package temporary allowances ("use it for 5 minutes") so both the
 * interstitial and the monitoring service see the same state.
 */
class TemporaryAllowanceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("temporary_allowances", Context.MODE_PRIVATE)

    fun grant(packageName: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(packageName, AllowancePolicy.newExpiry(now)).apply()
    }

    fun isAllowed(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val expiry = prefs.getLong(packageName, AllowancePolicy.NO_ALLOWANCE)
        if (expiry != AllowancePolicy.NO_ALLOWANCE && !AllowancePolicy.isAllowed(expiry, now)) {
            prefs.edit().remove(packageName).apply()
        }
        return AllowancePolicy.isAllowed(expiry, now)
    }

    fun revoke(packageName: String) {
        prefs.edit().remove(packageName).apply()
    }
}
