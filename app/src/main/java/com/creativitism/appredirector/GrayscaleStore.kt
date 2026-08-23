package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for grayscale mode: the master toggle, the set of apps allowed to keep
 * their color, and a snapshot of the daltonizer values the user had before grayscale
 * mode took control of them (so pre-existing color-blindness correction is restored,
 * not clobbered).
 */
class GrayscaleStore(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun colorApps(): Set<String> =
        prefs.getStringSet(KEY_COLOR_APPS, emptySet())?.toSet() ?: emptySet()

    fun isColorApp(packageName: String): Boolean = packageName in colorApps()

    fun addColorApp(packageName: String) {
        val apps = colorApps().toMutableSet()
        apps.add(packageName)
        prefs.edit().putStringSet(KEY_COLOR_APPS, apps).apply()
    }

    fun removeColorApp(packageName: String) {
        val apps = colorApps().toMutableSet()
        apps.remove(packageName)
        prefs.edit().putStringSet(KEY_COLOR_APPS, apps).apply()
    }

    // --- Daltonizer snapshot ---

    val hasSnapshot: Boolean
        get() = prefs.contains(KEY_SNAPSHOT_ENABLED)

    fun saveSnapshot(daltonizerEnabled: Int, daltonizerMode: Int) {
        prefs.edit()
            .putInt(KEY_SNAPSHOT_ENABLED, daltonizerEnabled)
            .putInt(KEY_SNAPSHOT_MODE, daltonizerMode)
            .apply()
    }

    fun snapshotEnabled(): Int = prefs.getInt(KEY_SNAPSHOT_ENABLED, 0)

    fun snapshotMode(): Int = prefs.getInt(KEY_SNAPSHOT_MODE, GrayscalePolicy.DALTONIZER_DISABLED)

    fun clearSnapshot() {
        prefs.edit()
            .remove(KEY_SNAPSHOT_ENABLED)
            .remove(KEY_SNAPSHOT_MODE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "grayscale_settings"
        private const val KEY_ENABLED = "grayscale_enabled"
        private const val KEY_COLOR_APPS = "color_apps"
        private const val KEY_SNAPSHOT_ENABLED = "snapshot_daltonizer_enabled"
        private const val KEY_SNAPSHOT_MODE = "snapshot_daltonizer_mode"
    }
}
