package com.creativitism.appredirector

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InterventionLogTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `codec round trip preserves fields`() {
        val events = listOf(
            InterventionEvent(
                timestamp = 1_700_000_000_000L,
                itemId = "com.example.app",
                displayLabel = "Example",
                kind = InterventionKind.VISIT_BOUGHT,
                boughtMillis = 42_000L,
            ),
        )

        assertEquals(events, InterventionLogCodec.decode(InterventionLogCodec.encode(events)))
    }

    @Test
    fun `append prunes entries older than ninety days`() {
        val prefs = FakeSharedPreferences()
        val log = InterventionLog(prefs, zone)
        val today = LocalDate.now(zone)
        val oldDay = today.minusDays(InterventionLog.RETENTION_DAYS + 1)

        log.append(
            InterventionEvent(
                timestamp = oldDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                itemId = "old",
                displayLabel = "Old",
                kind = InterventionKind.ALARM_FIRED,
            ),
        )
        log.append(
            InterventionEvent(
                timestamp = today.atStartOfDay(zone).toInstant().toEpochMilli(),
                itemId = "new",
                displayLabel = "New",
                kind = InterventionKind.ALARM_FIRED,
            ),
        )

        assertEquals(1, log.all().size)
        assertEquals("new", log.all().single().itemId)
    }
}
