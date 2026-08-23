package com.creativitism.appredirector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * Applies the desired grayscale state to Android's system-wide color-correction
 * (daltonizer) secure settings.
 *
 * Requires [Manifest.permission.WRITE_SECURE_SETTINGS], which a normal app can only
 * receive through a one-time `adb shell pm grant`. All writes are guarded: a revoked
 * permission makes [apply] report failure instead of crashing the caller.
 *
 * Redundant writes are avoided with an in-memory last-applied state; the actual
 * setting is still re-verified every [VERIFY_INTERVAL_MS] so an external change (the
 * user flipping color correction in system Settings) does not silently stick.
 */
class GrayscaleController(
    context: Context,
    private val store: GrayscaleStore,
) {

    private val appContext = context.applicationContext

    private var lastApplied: Boolean? = null
    private var lastVerifiedAt = 0L
    private var loggedWriteFailure = false

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isDeviceGrayscale(): Boolean =
        readSetting(KEY_DALTONIZER_ENABLED, 0) == 1 &&
            readSetting(KEY_DALTONIZER, GrayscalePolicy.DALTONIZER_DISABLED) ==
            GrayscalePolicy.DALTONIZER_MONOCHROMACY

    /**
     * Drive the device toward [wantGrayscale]. Returns true when the device is in the
     * wanted state, false when it could not be applied (permission missing/revoked).
     */
    @Synchronized
    fun apply(wantGrayscale: Boolean): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (wantGrayscale == lastApplied && now - lastVerifiedAt < VERIFY_INTERVAL_MS) {
            return true
        }
        if (!hasPermission()) {
            lastApplied = null
            if (!loggedWriteFailure) {
                loggedWriteFailure = true
                Log.w(TAG, "WRITE_SECURE_SETTINGS not granted; cannot apply grayscale")
            }
            return false
        }
        // Re-check the master toggle right before writing: the UI may have just
        // disabled the mode and restored the user's settings while a service tick
        // decided "grayscale" from a moment-earlier read. Same-process prefs make
        // this read see the newest value, collapsing the race to a no-op.
        if (wantGrayscale && !store.enabled) {
            lastApplied = null
            return false
        }
        return try {
            if (wantGrayscale) {
                if (!isDeviceGrayscale()) {
                    // First takeover: remember the user's own color-correction setup
                    // so excluded apps restore it instead of blindly disabling it.
                    if (!store.hasSnapshot) {
                        store.saveSnapshot(
                            readSetting(KEY_DALTONIZER_ENABLED, 0),
                            readSetting(KEY_DALTONIZER, GrayscalePolicy.DALTONIZER_DISABLED),
                        )
                    }
                    writeGrayscale()
                }
            } else {
                writeSnapshot()
            }
            lastApplied = wantGrayscale
            lastVerifiedAt = now
            loggedWriteFailure = false
            true
        } catch (e: SecurityException) {
            lastApplied = null
            if (!loggedWriteFailure) {
                loggedWriteFailure = true
                Log.w(TAG, "Secure settings write rejected; permission revoked?", e)
            }
            false
        }
    }

    /**
     * Grayscale mode was turned off: hand the daltonizer settings back to the user
     * (restore their snapshot) and forget everything we knew about them.
     */
    @Synchronized
    fun release() {
        try {
            if (hasPermission()) {
                writeSnapshot()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not restore color correction on release", e)
        }
        store.clearSnapshot()
        lastApplied = null
        lastVerifiedAt = 0L
    }

    private fun writeGrayscale() {
        val resolver = appContext.contentResolver
        Settings.Secure.putInt(resolver, KEY_DALTONIZER, GrayscalePolicy.DALTONIZER_MONOCHROMACY)
        Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 1)
    }

    private fun writeSnapshot() {
        val (snapEnabled, snapMode) = GrayscalePolicy.normalizedSnapshot(
            store.snapshotEnabled(),
            store.snapshotMode(),
        )
        val resolver = appContext.contentResolver
        if (snapEnabled == 1) {
            if (readSetting(KEY_DALTONIZER, GrayscalePolicy.DALTONIZER_DISABLED) != snapMode) {
                Settings.Secure.putInt(resolver, KEY_DALTONIZER, snapMode)
            }
            if (readSetting(KEY_DALTONIZER_ENABLED, 0) != 1) {
                Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 1)
            }
        } else if (readSetting(KEY_DALTONIZER_ENABLED, 0) != 0) {
            Settings.Secure.putInt(resolver, KEY_DALTONIZER_ENABLED, 0)
        }
    }

    private fun readSetting(key: String, default: Int): Int =
        Settings.Secure.getInt(appContext.contentResolver, key, default)

    companion object {
        private const val TAG = "GrayscaleController"

        // Settings.Secure constants for these exist but are @hide; the string keys
        // are stable across API 21+ and are what the system Settings app writes.
        const val KEY_DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_DALTONIZER = "accessibility_display_daltonizer"

        private const val VERIFY_INTERVAL_MS = 10_000L
    }
}
