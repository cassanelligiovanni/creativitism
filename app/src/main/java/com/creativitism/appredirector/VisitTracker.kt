package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

/**
 * Tracks per-item visit windows (last foreground activity) and visit-scoped
 * grant start times for bought-time close-out on [endVisit].
 */
class VisitTracker(
    private val prefs: SharedPreferences,
    private val allowanceManager: TemporaryAllowanceManager,
    private val interventionLog: InterventionLog,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        TemporaryAllowanceManager(context),
        InterventionLog(context),
    )

    /** All timestamps are System.currentTimeMillis(). */
    fun touch(itemId: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(seenKey(itemId), now).apply()
    }

    fun lastSeen(itemId: String): Long = prefs.getLong(seenKey(itemId), 0L)

    fun isVisitOpen(itemId: String, now: Long = System.currentTimeMillis()): Boolean {
        val seen = lastSeen(itemId)
        if (seen == 0L) return false
        return VisitPolicy.isWithinVisit(seen, now)
    }

    /** All item ids that still have a recorded last-seen (including stale ones pending end). */
    fun openVisitIds(): Set<String> =
        prefs.all.keys.mapNotNull { key ->
            if (key.startsWith(SEEN_PREFIX)) key.removePrefix(SEEN_PREFIX) else null
        }.toSet()

    /**
     * Ends the visit: clears visit-scoped allowance; if a visit grant was started,
     * appends [InterventionKind.VISIT_BOUGHT] with elapsed millis, then clears grant start
     * and last-seen.
     */
    fun endVisit(itemId: String) {
        val now = System.currentTimeMillis()
        allowanceManager.clearIfVisitScoped(itemId)
        val grantStarted = visitGrantStartedAt(itemId)
        if (grantStarted != 0L) {
            interventionLog.append(
                InterventionEvent.visitBought(
                    timestamp = now,
                    itemId = itemId,
                    displayLabel = itemId,
                    boughtMillis = now - grantStarted,
                ),
            )
            clearVisitGrantStarted(itemId)
        }
        prefs.edit().remove(seenKey(itemId)).apply()
    }

    fun markVisitGrantStarted(itemId: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(grantKey(itemId), now).apply()
    }

    fun visitGrantStartedAt(itemId: String): Long = prefs.getLong(grantKey(itemId), 0L)

    fun clearVisitGrantStarted(itemId: String) {
        prefs.edit().remove(grantKey(itemId)).apply()
    }

    companion object {
        const val PREFS_NAME = "visit_tracker"
        private const val SEEN_PREFIX = "seen:"
        private const val GRANT_PREFIX = "grant:"

        private fun seenKey(itemId: String) = SEEN_PREFIX + itemId
        private fun grantKey(itemId: String) = GRANT_PREFIX + itemId
    }
}
