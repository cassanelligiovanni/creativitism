package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockedItemPolicyCodecTest {
    @Test
    fun `parse missing yields default`() {
        assertEquals(BlockedItemPolicy.DEFAULT, BlockedItemPolicyCodec.decode(null))
    }

    @Test
    fun `round trip once-per-visit confession off`() {
        val p = BlockedItemPolicy(EnforcementMode.ONCE_PER_VISIT, false)
        assertEquals(p, BlockedItemPolicyCodec.decode(BlockedItemPolicyCodec.encode(p)))
    }
}
