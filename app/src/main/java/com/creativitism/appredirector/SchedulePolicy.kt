package com.creativitism.appredirector

/**
 * Pure decision logic for active-hours scheduling. Kept free of Android types so it
 * can be unit tested on the JVM. Clock inputs are supplied by callers.
 */
data class ActiveHours(
    val enabled: Boolean,
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int,
    val weekdaysBitmask: Int, // bit0=Mon … bit6=Sun; 0 = all days
)

object SchedulePolicy {

    fun isArmed(schedule: ActiveHours, minuteOfDay: Int, weekdayMonday0: Int): Boolean {
        if (!schedule.enabled) return true
        if (!isDayActive(schedule.weekdaysBitmask, weekdayMonday0)) return false
        return isMinuteInWindow(minuteOfDay, schedule.startMinuteOfDay, schedule.endMinuteOfDay)
    }

    fun nextResumeLabel(schedule: ActiveHours, minuteOfDay: Int, weekdayMonday0: Int): String? {
        if (!schedule.enabled || isArmed(schedule, minuteOfDay, weekdayMonday0)) return null

        for (daysAhead in 0..7) {
            val day = (weekdayMonday0 + daysAhead) % 7
            if (!isDayActive(schedule.weekdaysBitmask, day)) continue

            val resumeMinute = when {
                daysAhead == 0 -> resumeMinuteToday(schedule, minuteOfDay)
                else -> schedule.startMinuteOfDay
            }
            if (resumeMinute != null) return formatMinute(resumeMinute)
        }
        return null
    }

    private fun isDayActive(bitmask: Int, weekdayMonday0: Int): Boolean =
        bitmask == 0 || (bitmask and (1 shl weekdayMonday0)) != 0

    private fun isMinuteInWindow(minute: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }

    private fun resumeMinuteToday(schedule: ActiveHours, minuteOfDay: Int): Int? {
        val start = schedule.startMinuteOfDay
        val end = schedule.endMinuteOfDay
        return if (start < end) {
            if (minuteOfDay < start) start else null
        } else {
            if (minuteOfDay in end until start) start else null
        }
    }

    private fun formatMinute(minute: Int): String {
        val hours = minute / 60
        val minutes = minute % 60
        return "%02d:%02d".format(hours, minutes)
    }
}
