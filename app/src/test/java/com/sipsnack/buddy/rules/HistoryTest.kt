package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistoryTest {
    private val today = LocalDate.parse("2026-10-20")
    private fun day(date: String, cups: Double, steps: Int? = null, med: Boolean? = null) =
        DayStats(date, cups, goal = 8, steps = steps, stepGoal = 5_000, medsTaken = med)

    @Test
    fun a_range_is_oldest_first_with_blanks_for_quiet_days() {
        val r = History.range(mapOf("2026-10-19" to day("2026-10-19", 4.0)), lastDay = today, days = 3)
        assertEquals(listOf("2026-10-18", "2026-10-19", "2026-10-20"), r.map { it.first })
        assertNull(r[0].second)
        assertEquals(4.0, r[1].second!!.cups, 0.0)
    }

    @Test
    fun averages_only_count_days_with_something_recorded() {
        val p = History.period(listOf(day("a", 8.0, steps = 6_000), null, day("b", 4.0, steps = 2_000)))
        assertEquals(2, p.recordedDays)
        assertEquals(6.0, p.avgCups!!, 0.0)
        assertEquals(1, p.waterGoalDays)
        assertEquals(4_000, p.avgSteps)
        assertEquals(1, p.stepGoalDays)
    }

    @Test
    fun growth_compares_completed_days_and_leaves_today_out() {
        val days = mapOf(
            "2026-10-20" to day("2026-10-20", 0.5), // today, not over yet
            "2026-10-19" to day("2026-10-19", 6.0),
            "2026-10-18" to day("2026-10-18", 6.0),
            "2026-10-17" to day("2026-10-17", 4.0),
            "2026-10-16" to day("2026-10-16", 4.0),
        )
        val g = History.growth(days, today, days = 2)
        assertEquals(6.0, g.current.avgCups!!, 0.0)
        assertEquals(4.0, g.previous.avgCups!!, 0.0)
        assertEquals(50, g.waterChange)
    }

    @Test
    fun no_change_is_claimed_without_earlier_days() {
        val g = History.growth(mapOf("2026-10-19" to day("2026-10-19", 6.0)), today, days = 7)
        assertNull(g.waterChange)
        assertNull(g.stepsChange)
    }

    @Test
    fun medicine_days_count_only_days_it_was_tracked() {
        val p = History.period(listOf(day("a", 1.0, med = true), day("b", 1.0, med = false), day("c", 1.0, med = null)))
        assertEquals(2, p.medDays)
        assertEquals(1, p.medTakenDays)
    }

    @Test
    fun logs_group_into_local_days_with_water_meals_steps_and_medicine() {
        val zone = ZoneId.of("Africa/Accra")
        fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val logs = listOf(
            LogEntry(at("2026-10-19", 9), LogKind.WATER, 1.0, "Water"),
            LogEntry(at("2026-10-19", 13), LogKind.MEAL, 0.0, "Meal"),
            LogEntry(at("2026-10-19", 23), LogKind.WATER, 0.5, "Water"),
            LogEntry(at("2026-10-18", 8), LogKind.MED, 0.0, "Becoatin"),
            LogEntry(at("2026-10-20", 8), LogKind.MED, 0.0, "Becoatin"),
        )
        val days = History.fromLogs(logs, zone, goal = 8, stepGoal = 5_000, stepsByDate = mapOf("2026-10-18" to 3_000), tracksMeds = true)
        assertEquals(setOf("2026-10-18", "2026-10-19", "2026-10-20"), days.keys)
        assertEquals(1.5, days.getValue("2026-10-19").cups, 0.0)
        assertEquals(1, days.getValue("2026-10-19").meals)
        assertEquals(false, days.getValue("2026-10-19").medsTaken)
        assertEquals(true, days.getValue("2026-10-20").medsTaken)
        assertEquals(3_000, days.getValue("2026-10-18").steps)
    }

    @Test
    fun days_before_the_first_medicine_tick_are_not_counted_as_missed() {
        val zone = ZoneId.of("Africa/Accra")
        fun at(date: String) = LocalDate.parse(date).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val logs = listOf(
            LogEntry(at("2026-10-17"), LogKind.WATER, 1.0),
            LogEntry(at("2026-10-19"), LogKind.MED, 0.0),
            LogEntry(at("2026-10-20"), LogKind.WATER, 1.0),
        )
        val days = History.fromLogs(logs, zone, goal = 8, stepGoal = 5_000, stepsByDate = emptyMap(), tracksMeds = true)
        assertNull(days.getValue("2026-10-17").medsTaken)
        assertEquals(false, days.getValue("2026-10-20").medsTaken)
    }
}
