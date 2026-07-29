package com.creativitism.appredirector

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulePolicyTest {

    private val mondayOnly = 1 // bit0 = Monday

    @Test
    fun `disabled schedule is always armed`() {
        val schedule = ActiveHours(
            enabled = false,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 18 * 60,
            weekdaysBitmask = mondayOnly,
        )

        assertTrue(SchedulePolicy.isArmed(schedule, minuteOfDay = 10 * 60, weekdayMonday0 = 0))
        assertTrue(SchedulePolicy.isArmed(schedule, minuteOfDay = 20 * 60, weekdayMonday0 = 0))
        assertNull(SchedulePolicy.nextResumeLabel(schedule, minuteOfDay = 20 * 60, weekdayMonday0 = 0))
    }

    @Test
    fun `weekday window is armed inside hours`() {
        val schedule = ActiveHours(
            enabled = true,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 18 * 60,
            weekdaysBitmask = mondayOnly,
        )

        assertTrue(SchedulePolicy.isArmed(schedule, minuteOfDay = 10 * 60, weekdayMonday0 = 0))
    }

    @Test
    fun `weekday window is dormant outside hours`() {
        val schedule = ActiveHours(
            enabled = true,
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 18 * 60,
            weekdaysBitmask = mondayOnly,
        )

        assertFalse(SchedulePolicy.isArmed(schedule, minuteOfDay = 20 * 60, weekdayMonday0 = 0))
    }

    @Test
    fun `overnight window is armed late evening and after midnight`() {
        val schedule = ActiveHours(
            enabled = true,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60,
            weekdaysBitmask = 0,
        )

        assertTrue(SchedulePolicy.isArmed(schedule, minuteOfDay = 23 * 60, weekdayMonday0 = 0))
        assertTrue(SchedulePolicy.isArmed(schedule, minuteOfDay = 1 * 60, weekdayMonday0 = 0))
    }

    @Test
    fun `overnight window is dormant during daytime gap`() {
        val schedule = ActiveHours(
            enabled = true,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 6 * 60,
            weekdaysBitmask = 0,
        )

        assertFalse(SchedulePolicy.isArmed(schedule, minuteOfDay = 12 * 60, weekdayMonday0 = 0))
    }
}
