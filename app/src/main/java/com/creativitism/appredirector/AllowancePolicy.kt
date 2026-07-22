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

    fun newExpiry(now: Long): Long = now + ALLOWANCE_MILLIS

    fun isAllowed(expiryMillis: Long, now: Long): Boolean =
        expiryMillis != NO_ALLOWANCE && now < expiryMillis
}
