package com.creativitism.appredirector

object ConfessionSentence {

    const val FALLBACK_CREATIVE = "my creative work"

    fun build(blockedDisplay: String, creativeDisplay: String): String =
        "I choose $blockedDisplay over $creativeDisplay."

    fun pickCreativeDisplay(
        lastCreativeId: String?,
        creativeIds: List<String>,
        resolveName: (String) -> String,
    ): String {
        if (creativeIds.isEmpty()) return FALLBACK_CREATIVE
        if (lastCreativeId != null && lastCreativeId in creativeIds) {
            return resolveName(lastCreativeId)
        }
        return creativeIds
            .minByOrNull { resolveName(it).lowercase() }
            ?.let(resolveName)
            ?: FALLBACK_CREATIVE
    }
}
