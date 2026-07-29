package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowancePolicyTest {

    private val now = 1_000_000L

    @Test
    fun `no stored allowance is not allowed`() {
        assertFalse(AllowancePolicy.isAllowed(AllowancePolicy.NO_ALLOWANCE, now))
    }

    @Test
    fun `fresh grant is allowed`() {
        val expiry = AllowancePolicy.newExpiry(now)
        assertTrue(AllowancePolicy.isAllowed(expiry, now))
    }

    @Test
    fun `grant lasts five minutes`() {
        assertEquals(now + 5 * 60_000L, AllowancePolicy.newExpiry(now))
    }

    @Test
    fun `allowed one millisecond before expiry`() {
        val expiry = AllowancePolicy.newExpiry(now)
        assertTrue(AllowancePolicy.isAllowed(expiry, expiry - 1))
    }

    @Test
    fun `not allowed exactly at expiry`() {
        val expiry = AllowancePolicy.newExpiry(now)
        assertFalse(AllowancePolicy.isAllowed(expiry, expiry))
    }

    @Test
    fun `not allowed after expiry`() {
        val expiry = AllowancePolicy.newExpiry(now)
        assertFalse(AllowancePolicy.isAllowed(expiry, expiry + 60_000L))
    }

    @Test
    fun `wait period is ten seconds`() {
        assertEquals(10_000L, AllowancePolicy.WAIT_MILLIS)
    }

    @Test
    fun `visit scoped sentinel is negative one`() {
        assertEquals(-1L, AllowancePolicy.VISIT_SCOPED)
    }

    @Test
    fun `isVisitScoped recognizes sentinel`() {
        assertTrue(AllowancePolicy.isVisitScoped(AllowancePolicy.VISIT_SCOPED))
        assertFalse(AllowancePolicy.isVisitScoped(AllowancePolicy.NO_ALLOWANCE))
        assertFalse(AllowancePolicy.isVisitScoped(AllowancePolicy.newExpiry(now)))
    }

    @Test
    fun `visit scoped is allowed while visit still open`() {
        assertTrue(
            AllowancePolicy.isAllowed(AllowancePolicy.VISIT_SCOPED, now, visitStillOpen = true),
        )
    }

    @Test
    fun `visit scoped is denied when visit closed`() {
        assertFalse(
            AllowancePolicy.isAllowed(AllowancePolicy.VISIT_SCOPED, now, visitStillOpen = false),
        )
    }

    @Test
    fun `visit scoped allowed with arbitrary now when visit open`() {
        assertTrue(
            AllowancePolicy.isAllowed(
                AllowancePolicy.VISIT_SCOPED,
                Long.MAX_VALUE,
                visitStillOpen = true,
            ),
        )
    }

    @Test
    fun `timed isAllowed ignores visitStillOpen parameter for non sentinel`() {
        val expiry = AllowancePolicy.newExpiry(now)
        assertTrue(AllowancePolicy.isAllowed(expiry, now, visitStillOpen = false))
        assertFalse(AllowancePolicy.isAllowed(expiry, expiry, visitStillOpen = true))
    }
}
