package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class StepsTest {
    @Test
    fun the_first_reading_starts_the_day_at_zero() {
        val s = Steps.update(null, raw = 12_000, today = "2026-10-19", yesterday = "2026-10-18")
        assertEquals(0, s.steps)
    }

    @Test
    fun steps_are_the_rise_since_the_day_started() {
        var s = Steps.update(null, 12_000, "2026-10-19", "2026-10-18")
        s = Steps.update(s, 13_250, "2026-10-19", "2026-10-18")
        assertEquals(1_250, s.steps)
    }

    @Test
    fun a_new_day_starts_from_last_night_reading_so_morning_steps_count() {
        var s = Steps.update(null, 10_000, "2026-10-18", "2026-10-17")
        s = Steps.update(s, 14_000, "2026-10-18", "2026-10-17") // 22:00 yesterday
        s = Steps.update(s, 14_800, "2026-10-19", "2026-10-18") // first reading next morning
        assertEquals(800, s.steps)
    }

    @Test
    fun after_days_away_the_day_starts_fresh() {
        var s = Steps.update(null, 10_000, "2026-10-15", "2026-10-14")
        s = Steps.update(s, 30_000, "2026-10-19", "2026-10-18")
        assertEquals(0, s.steps)
    }

    @Test
    fun a_phone_restart_keeps_the_steps_already_walked() {
        var s = Steps.update(null, 50_000, "2026-10-19", "2026-10-18")
        s = Steps.update(s, 52_000, "2026-10-19", "2026-10-18") // 2,000 walked
        s = Steps.update(s, 300, "2026-10-19", "2026-10-18")    // restarted, sensor back near zero
        assertEquals(2_300, s.steps)
        s = Steps.update(s, 1_000, "2026-10-19", "2026-10-18")
        assertEquals(3_000, s.steps)
    }

    // Times in these tests are hours on a made-up clock; midnight (the start of 21 Oct) is hour 24.
    private val h = 3_600_000L
    private fun read(s: StepState?, raw: Long, day: String, at: Double, lastStep: Double?, midnight: Double = 24.0) =
        Steps.update(
            s, raw, day, if (day == "2026-10-21") "2026-10-20" else "2026-10-19",
            at = (at * h).toLong(), lastStepAt = lastStep?.let { (it * h).toLong() }, dayStart = (midnight * h).toLong(),
        )

    @Test
    fun a_gap_across_midnight_does_not_count_last_evening_as_today() {
        var s = read(null, 50_000, "2026-10-20", at = 9.0, lastStep = 9.0)
        s = read(s, 56_000, "2026-10-20", at = 18.0, lastStep = 18.0)
        // Nothing was recorded until the next evening, but the last step was at 19:00 yesterday.
        s = read(s, 58_000, "2026-10-21", at = 44.0, lastStep = 19.0)
        assertEquals(0, s.steps)
    }

    @Test
    fun a_gap_that_spans_midnight_splits_the_steps_by_time() {
        var s = read(null, 50_000, "2026-10-20", at = 20.0, lastStep = 20.0)
        // 2,000 steps between 20:00 and 28:00 (04:00 today), half of that window after midnight.
        s = read(s, 52_000, "2026-10-21", at = 28.0, lastStep = 28.0)
        assertEquals(1_000, s.steps)
    }

    @Test
    fun readings_through_the_night_still_count_morning_steps_as_today() {
        var s = read(null, 58_000, "2026-10-20", at = 23.999, lastStep = 23.999)
        s = read(s, 58_040, "2026-10-21", at = 31.0, lastStep = 30.99)
        assertEquals(40, s.steps)
    }

    @Test
    fun unknown_step_times_fall_back_to_continuing_from_last_night() {
        var s = read(null, 10_000, "2026-10-20", at = 22.0, lastStep = null)
        s = read(s, 10_800, "2026-10-21", at = 31.0, lastStep = null)
        assertEquals(800, s.steps)
    }

    @Test
    fun the_time_of_a_step_comes_from_the_sensor_clock() {
        // The sensor stamped the step 10 s of uptime ago.
        assertEquals(990_000L, Steps.stepTime(nowMillis = 1_000_000, uptimeNanos = 500_000_000_000, eventNanos = 490_000_000_000))
    }

    @Test
    fun an_impossible_step_time_is_ignored() {
        assertEquals(null, Steps.stepTime(nowMillis = 1_000_000_000, uptimeNanos = 500_000_000_000, eventNanos = 600_000_000_000)) // in the future
        assertEquals(null, Steps.stepTime(nowMillis = 1_000_000_000_000, uptimeNanos = 9_000_000_000_000_000, eventNanos = 1)) // days old
    }
}
