package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InterventionLogPruneTest {

    private val zone = ZoneId.of("UTC")

    private fun event(day: LocalDate, kind: InterventionKind = InterventionKind.ALARM_FIRED): InterventionEvent =
        InterventionEvent(
            timestamp = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            itemId = "com.example.app",
            displayLabel = "Example",
            kind = kind,
        )

    @Test
    fun `prune drops events before cutoff`() {
        val cutoff = LocalDate.of(2026, 4, 30)
        val events = listOf(
            event(LocalDate.of(2026, 4, 29)),
            event(LocalDate.of(2026, 4, 30)),
            event(LocalDate.of(2026, 5, 1)),
        )

        val kept = prune(events, cutoff, zone)

        assertEquals(listOf(events[1], events[2]), kept)
    }

    @Test
    fun `prune keeps empty list`() {
        assertEquals(emptyList<InterventionEvent>(), prune(emptyList(), LocalDate.now(zone), zone))
    }
}
