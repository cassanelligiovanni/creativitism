package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PausePolicyTest {

    @Test
    fun `paused only before the end time`() {
        assertFalse(PausePolicy.isPaused(pausedUntil = 0L, now = 1_000L))
        assertTrue(PausePolicy.isPaused(pausedUntil = 2_000L, now = 1_000L))
        assertFalse(PausePolicy.isPaused(pausedUntil = 1_000L, now = 1_000L))
        assertFalse(PausePolicy.isPaused(pausedUntil = 500L, now = 1_000L))
    }

    @Test
    fun `end after minutes adds whole minutes`() {
        assertEquals(1_000L + 15 * 60_000L, PausePolicy.endAfterMinutes(1_000L, 15))
        assertEquals(60 * 60_000L, PausePolicy.endAfterMinutes(0L, 60))
    }

    @Test
    fun `next morning in the evening is tomorrow at eight`() {
        val now = LocalDateTime.of(2026, 10, 26, 21, 30)

        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 0), PausePolicy.nextMorning(now))
    }

    @Test
    fun `next morning at night is later today`() {
        val now = LocalDateTime.of(2026, 10, 27, 2, 15)

        assertEquals(LocalDateTime.of(2026, 10, 27, 8, 0), PausePolicy.nextMorning(now))
    }

    @Test
    fun `next morning exactly at eight is the following day`() {
        val now = LocalDateTime.of(2026, 10, 27, 8, 0)

        assertEquals(LocalDateTime.of(2026, 10, 28, 8, 0), PausePolicy.nextMorning(now))
    }

    @Test
    fun `next morning crosses month end`() {
        val now = LocalDateTime.of(2026, 10, 31, 23, 0)

        assertEquals(LocalDateTime.of(2026, 11, 1, 8, 0), PausePolicy.nextMorning(now))
    }

    @Test
    fun `format end is hours and minutes`() {
        assertEquals("08:00", PausePolicy.formatEnd(LocalDateTime.of(2026, 10, 27, 8, 0)))
        assertEquals("14:05", PausePolicy.formatEnd(LocalDateTime.of(2026, 10, 27, 14, 5)))
    }
}
