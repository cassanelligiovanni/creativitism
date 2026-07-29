package com.creativitism.appredirector

data class BlockedItemPolicy(
    val enforcementMode: EnforcementMode,
    val confessionEnabled: Boolean,
) {
    companion object {
        val DEFAULT = BlockedItemPolicy(
            enforcementMode = EnforcementMode.STRICT,
            confessionEnabled = true,
        )
    }
}
