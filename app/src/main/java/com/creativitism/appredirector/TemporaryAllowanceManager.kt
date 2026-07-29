package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists per-package temporary allowances ("use it for 5 minutes") so both the
 * interstitial and the monitoring service see the same state.
 */
class TemporaryAllowanceManager(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("temporary_allowances", Context.MODE_PRIVATE),
    )

    fun grant(packageName: String, now: Long = System.currentTimeMillis()) =
        grantStrict(packageName, now)

    fun grantStrict(packageName: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(packageName, AllowancePolicy.newExpiry(now)).apply()
    }

    fun grantVisit(packageName: String) {
        prefs.edit().putLong(packageName, AllowancePolicy.VISIT_SCOPED).apply()
    }

    fun isAllowed(
        packageName: String,
        now: Long = System.currentTimeMillis(),
        visitStillOpen: Boolean,
    ): Boolean {
        val expiry = prefs.getLong(packageName, AllowancePolicy.NO_ALLOWANCE)
        if (AllowancePolicy.isVisitScoped(expiry)) {
            if (!visitStillOpen) {
                prefs.edit().remove(packageName).apply()
                return false
            }
            return true
        }
        if (expiry != AllowancePolicy.NO_ALLOWANCE && !AllowancePolicy.isAllowed(expiry, now)) {
            prefs.edit().remove(packageName).apply()
        }
        return AllowancePolicy.isAllowed(expiry, now)
    }

    fun isAllowed(packageName: String, now: Long = System.currentTimeMillis()): Boolean {
        val expiry = prefs.getLong(packageName, AllowancePolicy.NO_ALLOWANCE)
        if (AllowancePolicy.isVisitScoped(expiry)) {
            return false
        }
        if (expiry != AllowancePolicy.NO_ALLOWANCE && !AllowancePolicy.isAllowed(expiry, now)) {
            prefs.edit().remove(packageName).apply()
        }
        return AllowancePolicy.isAllowed(expiry, now)
    }

    fun clear(packageName: String) {
        prefs.edit().remove(packageName).apply()
    }

    /** Clears only a visit-scoped allowance; leaves strict timed grants alone. */
    fun clearIfVisitScoped(packageName: String) {
        val expiry = prefs.getLong(packageName, AllowancePolicy.NO_ALLOWANCE)
        if (AllowancePolicy.isVisitScoped(expiry)) {
            prefs.edit().remove(packageName).apply()
        }
    }
}
