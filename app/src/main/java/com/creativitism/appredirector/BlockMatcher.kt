package com.creativitism.appredirector

/**
 * Pure matching logic shared by app blocking (package names) and website blocking
 * (URLs seen in the browser address bar).
 */
object BlockMatcher {

    /**
     * True when [candidate] (a foreground package name or a URL) matches any entry
     * in [blockedItems]. Entries containing a dot are treated as domains and match
     * by substring (so "instagram.com" blocks any instagram.com URL); dot-less
     * entries must match exactly.
     */
    fun matches(candidate: String, blockedItems: Set<String>): Boolean =
        resolve(candidate, blockedItems) != null

    /**
     * Returns the canonical blocked entry that matches [candidate], preferring the
     * longest match when several domain entries apply. Null if nothing matches.
     */
    fun resolve(candidate: String, blockedItems: Set<String>): String? {
        return blockedItems.filter { blocked ->
            if (blocked.contains(".")) {
                candidate.contains(blocked, ignoreCase = true)
            } else {
                candidate == blocked
            }
        }.maxByOrNull { it.length }
    }
}
