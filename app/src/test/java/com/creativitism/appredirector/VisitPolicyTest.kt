package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitPolicyTest {

    private val lastSeenAt = 1_000_000L
    private val fiveMinutes = 5 * 60_000L

    @Test
    fun `visit end threshold is five minutes`() {
        assertEquals(fiveMinutes, VisitPolicy.VISIT_END_AFTER_MILLIS)
    }

    @Test
    fun `still within visit one millisecond before five minutes`() {
        val now = lastSeenAt + fiveMinutes - 1
        assertFalse(VisitPolicy.shouldEndVisit(lastSeenAt, now))
        assertTrue(VisitPolicy.isWithinVisit(lastSeenAt, now))
    }

    @Test
    fun `visit ends exactly at five minutes`() {
        val now = lastSeenAt + fiveMinutes
        assertTrue(VisitPolicy.shouldEndVisit(lastSeenAt, now))
        assertFalse(VisitPolicy.isWithinVisit(lastSeenAt, now))
    }

    @Test
    fun `visit ends after five minutes`() {
        val now = lastSeenAt + fiveMinutes + 60_000L
        assertTrue(VisitPolicy.shouldEndVisit(lastSeenAt, now))
        assertFalse(VisitPolicy.isWithinVisit(lastSeenAt, now))
    }

    @Test
    fun `fresh visit is within visit window`() {
        assertFalse(VisitPolicy.shouldEndVisit(lastSeenAt, lastSeenAt))
        assertTrue(VisitPolicy.isWithinVisit(lastSeenAt, lastSeenAt))
    }
}
