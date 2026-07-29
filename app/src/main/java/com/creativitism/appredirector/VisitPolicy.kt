package com.creativitism.appredirector

/**
 * Pure decision logic for visit lifecycle. Kept free of Android types so it
 * can be unit tested on the JVM. Timestamps are epoch millis supplied by callers.
 */
object VisitPolicy {

    /** How long after last activity a visit is considered ended. */
    const val VISIT_END_AFTER_MILLIS: Long = 5 * 60_000L

    fun shouldEndVisit(lastSeenAt: Long, now: Long): Boolean =
        now - lastSeenAt >= VISIT_END_AFTER_MILLIS

    fun isWithinVisit(lastSeenAt: Long, now: Long): Boolean =
        !shouldEndVisit(lastSeenAt, now)
}
