package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAllowanceManagerTest {

    @Test
    fun `visit scoped sentinel not deleted when visit still open at arbitrary now`() {
        val prefs = FakeSharedPreferences()
        val manager = TemporaryAllowanceManager(prefs)
        val itemId = "com.example.app"

        manager.grantVisit(itemId)

        assertTrue(manager.isAllowed(itemId, Long.MAX_VALUE, visitStillOpen = true))
        assertEquals(AllowancePolicy.VISIT_SCOPED, prefs.getLong(itemId, AllowancePolicy.NO_ALLOWANCE))
    }
}
