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
}
