package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object InterventionLogCodec {

    fun encode(events: List<InterventionEvent>): String {
        val array = JSONArray()
        for (event in events) {
            array.put(
                JSONObject()
                    .put("timestamp", event.timestamp)
                    .put("itemId", event.itemId)
                    .put("displayLabel", event.displayLabel)
                    .put("kind", event.kind.name)
                    .put("boughtMillis", event.boughtMillis),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<InterventionEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    InterventionEvent(
                        timestamp = obj.getLong("timestamp"),
                        itemId = obj.getString("itemId"),
                        displayLabel = obj.getString("displayLabel"),
                        kind = InterventionKind.valueOf(obj.getString("kind")),
                        boughtMillis = obj.optLong("boughtMillis", 0L),
                    ),
                )
            }
        }
    }
}

fun prune(
    events: List<InterventionEvent>,
    cutoff: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<InterventionEvent> =
    events.filter { event ->
        Instant.ofEpochMilli(event.timestamp).atZone(zoneId).toLocalDate() >= cutoff
    }

class InterventionLog(
    private val prefs: SharedPreferences,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun append(event: InterventionEvent) {
        val cutoff = LocalDate.now(zoneId).minusDays(RETENTION_DAYS)
        val pruned = prune(all() + event, cutoff, zoneId)
        prefs.edit().putString(KEY_EVENTS, InterventionLogCodec.encode(pruned)).apply()
    }

    fun all(): List<InterventionEvent> = InterventionLogCodec.decode(prefs.getString(KEY_EVENTS, null))

    companion object {
        const val PREFS_NAME = "intervention_log"
        private const val KEY_EVENTS = "events"
        const val RETENTION_DAYS = 90L
    }
}
