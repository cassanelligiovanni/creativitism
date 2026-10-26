package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for timed pauses. Each value is an epoch-millis end time; 0 means
 * not paused. The enabled flags stay untouched while paused, so the service keeps
 * running and picks the feature back up on its own when the time is up.
 */
class PauseStore(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    var protectionPausedUntil: Long
        get() = prefs.getLong(KEY_PROTECTION_UNTIL, 0L)
        set(value) {
            prefs.edit().putLong(KEY_PROTECTION_UNTIL, value).apply()
        }

    var grayscalePausedUntil: Long
        get() = prefs.getLong(KEY_GRAYSCALE_UNTIL, 0L)
        set(value) {
            prefs.edit().putLong(KEY_GRAYSCALE_UNTIL, value).apply()
        }

    fun isProtectionPaused(now: Long): Boolean =
        PausePolicy.isPaused(protectionPausedUntil, now)

    fun isGrayscalePaused(now: Long): Boolean =
        PausePolicy.isPaused(grayscalePausedUntil, now)

    fun clearProtection() {
        prefs.edit().remove(KEY_PROTECTION_UNTIL).apply()
    }

    fun clearGrayscale() {
        prefs.edit().remove(KEY_GRAYSCALE_UNTIL).apply()
    }

    fun clearAll() {
        prefs.edit()
            .remove(KEY_PROTECTION_UNTIL)
            .remove(KEY_GRAYSCALE_UNTIL)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "pause_state"
        private const val KEY_PROTECTION_UNTIL = "protection_paused_until"
        private const val KEY_GRAYSCALE_UNTIL = "grayscale_paused_until"
    }
}
