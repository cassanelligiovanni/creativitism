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
    fun matches(candidate: String, blockedItems: Set<String>): Boolean {
        return blockedItems.any { blocked ->
            if (blocked.contains(".")) {
                candidate.contains(blocked, ignoreCase = true)
            } else {
                candidate == blocked
            }
        }
    }
}
