package com.creativitism.appredirector

object ConfessionNormalizer {

    fun normalize(s: String): String =
        s.lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
            .trimEnd('.', '!', '?')

    fun matches(spoken: String, expected: String): Boolean =
        normalize(spoken) == normalize(expected)
}
