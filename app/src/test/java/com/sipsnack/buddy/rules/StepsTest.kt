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
}
