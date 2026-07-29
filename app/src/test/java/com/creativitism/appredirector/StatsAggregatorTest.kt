package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsAggregatorTest {

    private val zone = ZoneId.of("UTC")

    private fun dayStart(day: LocalDate, plusMillis: Long = 0L): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli() + plusMillis

    private fun event(
        day: LocalDate,
        kind: InterventionKind,
        itemId: String = "com.example.app",
        displayLabel: String = "Example",
        boughtMillis: Long = 0L,
        plusMillis: Long = 0L,
    ) = InterventionEvent(
        timestamp = dayStart(day, plusMillis),
        itemId = itemId,
        displayLabel = displayLabel,
        kind = kind,
        boughtMillis = boughtMillis,
    )

    @Test
    fun `reclaimed counts five minutes per escape`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.ESCAPED),
            event(today, InterventionKind.ESCAPED, plusMillis = 1_000L),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(2 * AllowancePolicy.ALLOWANCE_MILLIS, snapshot.reclaimedMillis)
    }

    @Test
    fun `strict bought counts five minutes from gave in with zero boughtMillis`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.GAVE_IN, boughtMillis = 0L),
            event(today, InterventionKind.GAVE_IN, boughtMillis = 0L, plusMillis = 1_000L),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(2 * AllowancePolicy.ALLOWANCE_MILLIS, snapshot.boughtMillis)
    }

    @Test
    fun `default omitted boughtMillis on gave in counts as strict five minutes`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            InterventionEvent.strictGiveIn(dayStart(today), "com.example.app", "Example"),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(AllowancePolicy.ALLOWANCE_MILLIS, snapshot.boughtMillis)
    }

    @Test
    fun `visit deferred gave in still breaks streak`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            InterventionEvent.visitDeferredGiveIn(dayStart(today), "com.example.app", "Example"),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(0, snapshot.giveInFreeStreakDays)
    }

    @Test
    fun `once per visit gave in contributes zero until visit bought`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.GAVE_IN, boughtMillis = StatsAggregator.VISIT_DEFERRED_BOUGHT),
        )

        val beforeVisitEnd = StatsAggregator.aggregate(events, today, zone)
        assertEquals(0L, beforeVisitEnd.boughtMillis)

        val withVisitBought = events + event(
            today,
            InterventionKind.VISIT_BOUGHT,
            boughtMillis = 120_000L,
            plusMillis = 2_000L,
        )
        val afterVisitEnd = StatsAggregator.aggregate(withVisitBought, today, zone)
        assertEquals(120_000L, afterVisitEnd.boughtMillis)
    }

    @Test
    fun `visit bought does not break give in free streak`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today.minusDays(1), InterventionKind.VISIT_BOUGHT, boughtMillis = 60_000L),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(2, snapshot.giveInFreeStreakDays)
    }

    @Test
    fun `give in resets streak`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.GAVE_IN, boughtMillis = 0L),
            event(today.minusDays(2), InterventionKind.ESCAPED),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(0, snapshot.giveInFreeStreakDays)
    }

    @Test
    fun `empty days continue streak`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today.minusDays(4), InterventionKind.GAVE_IN, boughtMillis = 0L),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(4, snapshot.giveInFreeStreakDays)
    }

    @Test
    fun `alarms bucketed by local day`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.ALARM_FIRED),
            event(today, InterventionKind.ALARM_FIRED, plusMillis = 1_000L),
            event(today.minusDays(1), InterventionKind.ALARM_FIRED),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(
            mapOf(
                "2026-07-29" to 2,
                "2026-07-28" to 1,
            ),
            snapshot.alarmsByDay,
        )
    }

    @Test
    fun `worst offenders ranked by alarm count`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.ALARM_FIRED, itemId = "a", displayLabel = "Alpha"),
            event(today, InterventionKind.ALARM_FIRED, itemId = "a", displayLabel = "Alpha", plusMillis = 1_000L),
            event(today, InterventionKind.ALARM_FIRED, itemId = "b", displayLabel = "Beta", plusMillis = 2_000L),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(
            listOf("Alpha" to 2, "Beta" to 1),
            snapshot.worstOffenders,
        )
    }

    @Test
    fun `visit bought adds bought millis without strict gave in double count`() {
        val today = LocalDate.of(2026, 7, 29)
        val events = listOf(
            event(today, InterventionKind.GAVE_IN, boughtMillis = StatsAggregator.VISIT_DEFERRED_BOUGHT),
            event(today, InterventionKind.VISIT_BOUGHT, boughtMillis = 90_000L, plusMillis = 1_000L),
        )

        val snapshot = StatsAggregator.aggregate(events, today, zone)

        assertEquals(90_000L, snapshot.boughtMillis)
    }
}
