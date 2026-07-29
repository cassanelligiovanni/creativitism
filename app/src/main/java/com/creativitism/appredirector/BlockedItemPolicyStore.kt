package com.creativitism.appredirector

import android.content.Context

object BlockedItemPolicyCodec {
    fun encode(p: BlockedItemPolicy): String =
        "${p.enforcementMode.name}|${if (p.confessionEnabled) 1 else 0}"

    fun decode(raw: String?): BlockedItemPolicy {
        if (raw.isNullOrBlank()) return BlockedItemPolicy.DEFAULT
        val parts = raw.split("|")
        val mode = runCatching { EnforcementMode.valueOf(parts[0]) }
            .getOrDefault(EnforcementMode.STRICT)
        val confession = parts.getOrNull(1) != "0"
        return BlockedItemPolicy(mode, confession)
    }
}

class BlockedItemPolicyStore(context: Context) {
    private val prefs = context.getSharedPreferences("blocked_item_policy", Context.MODE_PRIVATE)
    fun get(itemId: String) = BlockedItemPolicyCodec.decode(prefs.getString(itemId, null))
    fun set(itemId: String, policy: BlockedItemPolicy) {
        prefs.edit().putString(itemId, BlockedItemPolicyCodec.encode(policy)).apply()
    }
    fun remove(itemId: String) { prefs.edit().remove(itemId).apply() }
}
