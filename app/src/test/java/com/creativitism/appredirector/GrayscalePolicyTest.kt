package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrayscalePolicyTest {

    // --- wantsGrayscale ---

    @Test
    fun `mode off returns null regardless of other args`() {
        assertNull(
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = false,
                screenInteractive = true,
                foregroundPackage = "com.example.color",
                colorApps = setOf("com.example.color"),
            ),
        )
        assertNull(
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = false,
                screenInteractive = false,
                foregroundPackage = null,
                colorApps = emptySet(),
            ),
        )
    }

    @Test
    fun `screen off returns true even for a color app in foreground`() {
        assertEquals(
            true,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = false,
                foregroundPackage = "com.example.color",
                colorApps = setOf("com.example.color"),
            ),
        )
    }

    @Test
    fun `null foreground returns true`() {
        assertEquals(
            true,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = true,
                foregroundPackage = null,
                colorApps = setOf("com.example.color"),
            ),
        )
    }

    @Test
    fun `foreground in colorApps returns false`() {
        assertEquals(
            false,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = true,
                foregroundPackage = "com.example.color",
                colorApps = setOf("com.example.color"),
            ),
        )
    }

    @Test
    fun `foreground not in colorApps returns true`() {
        assertEquals(
            true,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = true,
                foregroundPackage = "com.example.other",
                colorApps = setOf("com.example.color"),
            ),
        )
    }

    @Test
    fun `empty colorApps returns true`() {
        assertEquals(
            true,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = true,
                foregroundPackage = "com.example.other",
                colorApps = emptySet(),
            ),
        )
    }

    @Test
    fun `own package gets no special treatment`() {
        val ownPackage = "com.creativitism.appredirector"

        assertEquals(
            true,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = true,
                foregroundPackage = ownPackage,
                colorApps = emptySet(),
            ),
        )
        assertEquals(
            false,
            GrayscalePolicy.wantsGrayscale(
                modeEnabled = true,
                screenInteractive = true,
                foregroundPackage = ownPackage,
                colorApps = setOf(ownPackage),
            ),
        )
    }

    // --- normalizedSnapshot ---

    @Test
    fun `monochromacy snapshot normalizes to disabled`() {
        assertEquals(
            0 to GrayscalePolicy.DALTONIZER_DISABLED,
            GrayscalePolicy.normalizedSnapshot(1, GrayscalePolicy.DALTONIZER_MONOCHROMACY),
        )
    }

    @Test
    fun `correction mode snapshot is preserved`() {
        assertEquals(1 to 11, GrayscalePolicy.normalizedSnapshot(1, 11))
    }

    @Test
    fun `disabled snapshot is preserved`() {
        assertEquals(
            0 to GrayscalePolicy.DALTONIZER_DISABLED,
            GrayscalePolicy.normalizedSnapshot(0, GrayscalePolicy.DALTONIZER_DISABLED),
        )
    }

    @Test
    fun `edge case enabled zero mode zero is preserved`() {
        assertEquals(0 to 0, GrayscalePolicy.normalizedSnapshot(0, 0))
    }
}
