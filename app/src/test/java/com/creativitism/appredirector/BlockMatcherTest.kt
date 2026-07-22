package com.creativitism.appredirector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockMatcherTest {

    @Test
    fun `package name matches its own blocked entry`() {
        assertTrue(BlockMatcher.matches("com.instagram.android", setOf("com.instagram.android")))
    }

    @Test
    fun `unrelated package does not match`() {
        assertFalse(BlockMatcher.matches("com.spotify.music", setOf("com.instagram.android")))
    }

    @Test
    fun `blocked domain matches full url`() {
        assertTrue(BlockMatcher.matches("https://www.instagram.com/reels", setOf("instagram.com")))
    }

    @Test
    fun `domain matching is case insensitive`() {
        assertTrue(BlockMatcher.matches("HTTPS://INSTAGRAM.COM", setOf("instagram.com")))
    }

    @Test
    fun `unrelated url does not match`() {
        assertFalse(BlockMatcher.matches("https://wikipedia.org", setOf("instagram.com")))
    }

    @Test
    fun `empty block list matches nothing`() {
        assertFalse(BlockMatcher.matches("com.instagram.android", emptySet()))
    }
}
