package com.creativitism.appredirector

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure rules for timed pauses: turning Protection or grayscale "off" for a while
 * instead of for good. A pause is just an end timestamp; once it has passed the
 * feature is back on without anyone having to remember to flip it.
 */
object PausePolicy {

    const val MORNING_MINUTE_OF_DAY = 8 * 60

    fun isPaused(pausedUntil: Long, now: Long): Boolean = pausedUntil > now

    fun endAfterMinutes(now: Long, minutes: Int): Long = now + minutes * 60_000L

    /** The next 08:00 strictly after [now]: later today if it is still night. */
    fun nextMorning(now: LocalDateTime): LocalDateTime {
        val morning = LocalTime.of(MORNING_MINUTE_OF_DAY / 60, MORNING_MINUTE_OF_DAY % 60)
        val today = now.toLocalDate().atTime(morning)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun formatEnd(end: LocalDateTime): String =
        SchedulePolicy.formatMinuteOfDay(end.hour * 60 + end.minute)
}
