package com.creativitism.appredirector

enum class InterventionKind {
    ALARM_FIRED,
    ESCAPED,
    GAVE_IN,
    ALLOWANCE_GRANTED,
    ALLOWANCE_EXPIRED,
    VISIT_BOUGHT,
}

data class InterventionEvent(
    val timestamp: Long,
    val itemId: String,
    val displayLabel: String,
    val kind: InterventionKind,
    val boughtMillis: Long = 0L,
)

data class StatsSnapshot(
    val reclaimedMillis: Long,
    val boughtMillis: Long,
    val alarmsByDay: Map<String, Int>,
    val worstOffenders: List<Pair<String, Int>>,
    val giveInFreeStreakDays: Int,
)
