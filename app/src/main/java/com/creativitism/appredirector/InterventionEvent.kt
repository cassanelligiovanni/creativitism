package com.creativitism.appredirector

enum class InterventionKind {
    ALARM_FIRED,
    ESCAPED,
    GAVE_IN,
    ALLOWANCE_GRANTED,
    ALLOWANCE_EXPIRED,
    VISIT_BOUGHT,
}

/**
 * A single append-only intervention log row.
 *
 * @param boughtMillis Meaning depends on [kind]:
 * - **GAVE_IN:** `0` (default) = strict give-in; aggregator counts +5 min immediately.
 *   [StatsAggregator.VISIT_DEFERRED_BOUGHT] = once-per-visit; aggregator counts 0 until a
 *   matching [InterventionKind.VISIT_BOUGHT] arrives. Prefer [strictGiveIn] /
 *   [visitDeferredGiveIn] over raw construction.
 * - **VISIT_BOUGHT:** elapsed millis bought during the visit (≥ 0).
 * - **All other kinds:** ignored by the aggregator; defaults to `0`.
 */
data class InterventionEvent(
    val timestamp: Long,
    val itemId: String,
    val displayLabel: String,
    val kind: InterventionKind,
    val boughtMillis: Long = 0L,
) {

    companion object {

        /** Strict give-in: bought time is fixed at 5 minutes. */
        fun strictGiveIn(
            timestamp: Long,
            itemId: String,
            displayLabel: String,
        ): InterventionEvent = InterventionEvent(
            timestamp = timestamp,
            itemId = itemId,
            displayLabel = displayLabel,
            kind = InterventionKind.GAVE_IN,
            boughtMillis = 0L,
        )

        /** Once-per-visit give-in: bought time recorded later via [visitBought]. */
        fun visitDeferredGiveIn(
            timestamp: Long,
            itemId: String,
            displayLabel: String,
        ): InterventionEvent = InterventionEvent(
            timestamp = timestamp,
            itemId = itemId,
            displayLabel = displayLabel,
            kind = InterventionKind.GAVE_IN,
            boughtMillis = StatsAggregator.VISIT_DEFERRED_BOUGHT,
        )

        fun visitBought(
            timestamp: Long,
            itemId: String,
            displayLabel: String,
            boughtMillis: Long,
        ): InterventionEvent = InterventionEvent(
            timestamp = timestamp,
            itemId = itemId,
            displayLabel = displayLabel,
            kind = InterventionKind.VISIT_BOUGHT,
            boughtMillis = boughtMillis.coerceAtLeast(0L),
        )
    }
}

data class StatsSnapshot(
    val reclaimedMillis: Long,
    val boughtMillis: Long,
    val alarmsByDay: Map<String, Int>,
    val worstOffenders: List<Pair<String, Int>>,
    val giveInFreeStreakDays: Int,
)
