package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfessionNormalizerTest {

    @Test
    fun `normalize lowercases`() {
        assertEquals("hello", ConfessionNormalizer.normalize("Hello"))
    }

    @Test
    fun `normalize collapses whitespace`() {
        assertEquals("hello world", ConfessionNormalizer.normalize("  hello   world  "))
    }

    @Test
    fun `normalize strips trailing period`() {
        assertEquals("hello", ConfessionNormalizer.normalize("hello."))
    }

    @Test
    fun `normalize strips trailing exclamation`() {
        assertEquals("hello", ConfessionNormalizer.normalize("hello!"))
    }

    @Test
    fun `normalize strips trailing question mark`() {
        assertEquals("hello", ConfessionNormalizer.normalize("hello?"))
    }

    @Test
    fun `normalize strips multiple trailing punctuation`() {
        assertEquals("hello", ConfessionNormalizer.normalize("hello!!!"))
    }

    @Test
    fun `normalize applies all rules together`() {
        assertEquals(
            "i choose reddit over writing",
            ConfessionNormalizer.normalize("  I Choose  Reddit  over  Writing.  "),
        )
    }

    @Test
    fun `matches is case insensitive`() {
        assertTrue(ConfessionNormalizer.matches("HELLO", "hello"))
    }

    @Test
    fun `matches ignores whitespace differences`() {
        assertTrue(ConfessionNormalizer.matches("  hello world  ", "hello world"))
    }

    @Test
    fun `matches ignores trailing punctuation`() {
        assertTrue(ConfessionNormalizer.matches("hello.", "hello"))
        assertTrue(ConfessionNormalizer.matches("hello", "hello!"))
    }

    @Test
    fun `matches rejects different text`() {
        assertFalse(ConfessionNormalizer.matches("hello", "goodbye"))
    }

    @Test
    fun build_formats_sentence() {
        assertEquals(
            "I choose Reddit over Writing.",
            ConfessionSentence.build("Reddit", "Writing"),
        )
    }

    @Test
    fun fallbackCreative_constant() {
        assertEquals("my creative work", ConfessionSentence.FALLBACK_CREATIVE)
    }

    @Test
    fun pickCreativeDisplay_uses_lastCreativeId_when_in_list() {
        val ids = listOf("a", "b", "c")
        val names = mapOf("a" to "Alpha", "b" to "Beta", "c" to "Charlie")

        assertEquals(
            "Beta",
            ConfessionSentence.pickCreativeDisplay("b", ids) { names.getValue(it) },
        )
    }

    @Test
    fun pickCreativeDisplay_falls_back_to_first_by_name_sort() {
        val ids = listOf("z", "a", "m")
        val names = mapOf("z" to "Zulu", "a" to "Alpha", "m" to "Mike")

        assertEquals(
            "Alpha",
            ConfessionSentence.pickCreativeDisplay(null, ids) { names.getValue(it) },
        )
    }

    @Test
    fun pickCreativeDisplay_falls_back_to_first_by_name_sort_when_last_not_in_list() {
        val ids = listOf("z", "a")
        val names = mapOf("z" to "Zulu", "a" to "Alpha")

        assertEquals(
            "Alpha",
            ConfessionSentence.pickCreativeDisplay("missing", ids) { names.getValue(it) },
        )
    }

    @Test
    fun pickCreativeDisplay_uses_fallback_when_list_empty() {
        assertEquals(
            ConfessionSentence.FALLBACK_CREATIVE,
            ConfessionSentence.pickCreativeDisplay(null, emptyList()) { "unused" },
        )
    }
}
