package com.creativitism.appredirector

import android.content.Context
import android.content.SharedPreferences

class ScheduleStore(
    private val prefs: SharedPreferences,
) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun load(): ActiveHours = ActiveHours(
        enabled = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
        startMinuteOfDay = prefs.getInt(KEY_START, DEFAULT_START),
        endMinuteOfDay = prefs.getInt(KEY_END, DEFAULT_END),
        weekdaysBitmask = prefs.getInt(KEY_WEEKDAYS, DEFAULT_WEEKDAYS),
    )

    fun save(schedule: ActiveHours) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putInt(KEY_START, schedule.startMinuteOfDay)
            .putInt(KEY_END, schedule.endMinuteOfDay)
            .putInt(KEY_WEEKDAYS, schedule.weekdaysBitmask)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "active_hours"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_START = "start_minute"
        private const val KEY_END = "end_minute"
        private const val KEY_WEEKDAYS = "weekdays_bitmask"
        private const val DEFAULT_ENABLED = false
        private const val DEFAULT_START = 9 * 60
        private const val DEFAULT_END = 18 * 60
        private const val DEFAULT_WEEKDAYS = 0
    }
}
