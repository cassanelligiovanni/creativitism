package com.creativitism.appredirector

/**
 * Pure decision logic for grayscale mode: everything is grayscale unless the
 * foreground app is one the user explicitly allowed to stay in color.
 */
object GrayscalePolicy {

    /** Daltonizer mode value for monochromacy (system grayscale). */
    const val DALTONIZER_MONOCHROMACY = 0

    /** Value read back from Settings.Secure when no daltonizer mode is set. */
    const val DALTONIZER_DISABLED = -1

    /**
     * What the device display should be this tick.
     *
     * Returns null when grayscale mode is off — the device settings must be left
     * untouched (they belong to the user, not to us).
     *
     * The base state is grayscale: screen off/locked and unknown-foreground both
     * resolve to grayscale, and only a foreground app on the color list turns
     * color on. Creativitism itself follows the same rule as every other app.
     */
    fun wantsGrayscale(
        modeEnabled: Boolean,
        screenInteractive: Boolean,
        foregroundPackage: String?,
        colorApps: Set<String>,
    ): Boolean? {
        if (!modeEnabled) return null
        if (!screenInteractive) return true
        if (foregroundPackage == null) return true
        return foregroundPackage !in colorApps
    }

    /**
     * Normalize a daltonizer snapshot taken when grayscale mode assumed control.
     *
     * If the user's own setting was already monochromacy, restoring it verbatim
     * would make "apps in color" impossible, so it normalizes to disabled.
     * Genuine color-blindness correction modes are preserved as-is.
     */
    fun normalizedSnapshot(daltonizerEnabled: Int, daltonizerMode: Int): Pair<Int, Int> =
        if (daltonizerEnabled == 1 && daltonizerMode == DALTONIZER_MONOCHROMACY) {
            0 to DALTONIZER_DISABLED
        } else {
            daltonizerEnabled to daltonizerMode
        }
}
