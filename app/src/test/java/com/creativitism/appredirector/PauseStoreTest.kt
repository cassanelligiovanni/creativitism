package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseStoreTest {

    @Test
    fun `defaults to not paused`() {
        val store = PauseStore(FakeSharedPreferences())

        assertEquals(0L, store.protectionPausedUntil)
        assertEquals(0L, store.grayscalePausedUntil)
        assertFalse(store.isProtectionPaused(now = 1_000L))
        assertFalse(store.isGrayscalePaused(now = 1_000L))
    }

    @Test
    fun `pauses are independent and expire on their own`() {
        val store = PauseStore(FakeSharedPreferences())

        store.protectionPausedUntil = 5_000L
        assertTrue(store.isProtectionPaused(now = 1_000L))
        assertFalse(store.isGrayscalePaused(now = 1_000L))
        assertFalse(store.isProtectionPaused(now = 5_000L))

        store.grayscalePausedUntil = 9_000L
        assertTrue(store.isGrayscalePaused(now = 6_000L))
    }

    @Test
    fun `clear one keeps the other`() {
        val store = PauseStore(FakeSharedPreferences())
        store.protectionPausedUntil = 5_000L
        store.grayscalePausedUntil = 5_000L

        store.clearProtection()
        assertFalse(store.isProtectionPaused(now = 1_000L))
        assertTrue(store.isGrayscalePaused(now = 1_000L))

        store.clearGrayscale()
        assertFalse(store.isGrayscalePaused(now = 1_000L))
    }

    @Test
    fun `clear all ends every pause`() {
        val store = PauseStore(FakeSharedPreferences())
        store.protectionPausedUntil = 5_000L
        store.grayscalePausedUntil = 5_000L

        store.clearAll()

        assertEquals(0L, store.protectionPausedUntil)
        assertEquals(0L, store.grayscalePausedUntil)
    }
}
