package com.creativitism.appredirector

/**
 * Pure decision logic for temporary allowances. Kept free of Android types so it
 * can be unit tested on the JVM. Timestamps are epoch millis supplied by callers.
 */
object AllowancePolicy {

    /** How long the user must wait on the interstitial before "allow" unlocks. */
    const val WAIT_MILLIS: Long = 10_000L

    /** How long a granted allowance lasts. */
    const val ALLOWANCE_MILLIS: Long = 5 * 60_000L

    /** Sentinel for "no allowance stored". */
    const val NO_ALLOWANCE: Long = 0L

    /** Sentinel stored as expiry for visit-scoped grants. */
    const val VISIT_SCOPED: Long = -1L

    fun newExpiry(now: Long): Long = now + ALLOWANCE_MILLIS

    fun isVisitScoped(expiryMillis: Long): Boolean = expiryMillis == VISIT_SCOPED

    fun isAllowed(expiryMillis: Long, now: Long): Boolean =
        expiryMillis != NO_ALLOWANCE && now < expiryMillis

    fun isAllowed(expiryMillis: Long, now: Long, visitStillOpen: Boolean): Boolean =
        if (isVisitScoped(expiryMillis)) visitStillOpen
        else isAllowed(expiryMillis, now)
}
