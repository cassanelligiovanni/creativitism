package com.creativitism.appredirector

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StatsAggregator {

    /** Sentinel on GAVE_IN for once-per-visit grants; bought time arrives via VISIT_BOUGHT. */
    const val VISIT_DEFERRED_BOUGHT: Long = -1L

    fun aggregate(
        events: List<InterventionEvent>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): StatsSnapshot {
        var reclaimedMillis = 0L
        var boughtMillis = 0L
        val alarmsByDay = mutableMapOf<String, Int>()
        val alarmCountsByLabel = mutableMapOf<String, Int>()
        val giveInDays = mutableSetOf<LocalDate>()

        for (event in events) {
            when (event.kind) {
                InterventionKind.ESCAPED ->
                    reclaimedMillis += AllowancePolicy.ALLOWANCE_MILLIS

                InterventionKind.GAVE_IN -> {
                    giveInDays.add(event.localDate(zoneId))
                    if (event.boughtMillis == 0L) {
                        boughtMillis += AllowancePolicy.ALLOWANCE_MILLIS
                    }
                }

                InterventionKind.VISIT_BOUGHT ->
                    boughtMillis += event.boughtMillis.coerceAtLeast(0L)

                InterventionKind.ALARM_FIRED -> {
                    val dayKey = event.localDate(zoneId).toString()
                    alarmsByDay[dayKey] = (alarmsByDay[dayKey] ?: 0) + 1
                    alarmCountsByLabel[event.displayLabel] =
                        (alarmCountsByLabel[event.displayLabel] ?: 0) + 1
                }

                InterventionKind.ALLOWANCE_GRANTED,
                InterventionKind.ALLOWANCE_EXPIRED,
                -> Unit
            }
        }

        val worstOffenders = alarmCountsByLabel.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

        val earliestDay = events.minOfOrNull { it.localDate(zoneId) } ?: today

        return StatsSnapshot(
            reclaimedMillis = reclaimedMillis,
            boughtMillis = boughtMillis,
            alarmsByDay = alarmsByDay,
            worstOffenders = worstOffenders,
            giveInFreeStreakDays = computeGiveInFreeStreak(today, giveInDays, earliestDay),
        )
    }

    internal fun computeGiveInFreeStreak(
        today: LocalDate,
        giveInDays: Set<LocalDate>,
        earliestDay: LocalDate,
    ): Int {
        var streak = 0
        var day = today
        while (day !in giveInDays && !day.isBefore(earliestDay)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    private fun InterventionEvent.localDate(zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
