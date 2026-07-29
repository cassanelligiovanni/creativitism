package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedItemPolicyTest {
    @Test
    fun `defaults are strict with confession on`() {
        val d = BlockedItemPolicy.DEFAULT
        assertEquals(EnforcementMode.STRICT, d.enforcementMode)
        assertTrue(d.confessionEnabled)
    }
}
