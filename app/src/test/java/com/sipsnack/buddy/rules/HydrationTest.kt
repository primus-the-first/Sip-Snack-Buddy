package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HydrationTest {
    private val hour = 60 * 60 * 1000L
    private val minute = 60 * 1000L

    @Test
    fun counts_only_water_toward_the_goal() {
        val entries = listOf(
            LogEntry(0, LogKind.WATER, 2.0),
            LogEntry(0, LogKind.OTHER_DRINK, 1.0),
            LogEntry(0, LogKind.MEAL, 0.0),
            LogEntry(0, LogKind.WATER, 1.0),
        )
        assertEquals(3.0, Hydration.cups(entries), 0.001)
    }

    @Test
    fun crossing_halfway_plays_the_halfway_moment() {
        assertEquals(Moment.HALFWAY, Hydration.momentForLog(before = 3.0, after = 4.0, goal = 8))
    }

    @Test
    fun crossing_the_goal_beats_halfway() {
        assertEquals(Moment.GOAL_HIT, Hydration.momentForLog(before = 3.0, after = 8.0, goal = 8))
    }

    @Test
    fun a_cup_after_the_goal_is_just_a_cup() {
        assertEquals(Moment.CUP_LOGGED, Hydration.momentForLog(before = 8.0, after = 9.0, goal = 8))
        assertEquals(Moment.CUP_LOGGED, Hydration.momentForLog(before = 1.0, after = 2.0, goal = 8))
    }

    @Test
    fun expected_cups_follow_the_awake_window() {
        val window = DayWindow(wakeMinute = 8 * 60, sleepMinute = 22 * 60)
        assertEquals(0.0, Hydration.expectedCups(8, 7 * 60, window), 0.001)
        assertEquals(4.0, Hydration.expectedCups(8, 15 * 60, window), 0.001)
        assertEquals(8.0, Hydration.expectedCups(8, 23 * 60, window), 0.001)
    }

    private fun nudge(
        minuteOfDay: Int,
        cups: Double = 3.0,
        lastDrinkMinute: Int? = null,
        state: NudgeState = NudgeState(),
    ): Moment? {
        val dayStart = 0L
        return Nudges.decide(
            nowMillis = dayStart + minuteOfDay * minute,
            minuteOfDay = minuteOfDay,
            dayStartMillis = dayStart,
            cups = cups,
            goal = 8,
            lastDrinkAtMillis = lastDrinkMinute?.let { dayStart + it * minute },
            state = state,
            window = DayWindow(),
        )
    }

    @Test
    fun no_drink_for_two_hours_is_a_delay() {
        assertEquals(Moment.DELAY, nudge(minuteOfDay = 12 * 60, cups = 3.0, lastDrinkMinute = 10 * 60))
    }

    @Test
    fun a_recent_drink_on_pace_is_no_delay() {
        assertNull(nudge(minuteOfDay = 12 * 60, cups = 3.0, lastDrinkMinute = 11 * 60 + 30))
    }

    @Test
    fun two_cups_behind_pace_is_a_delay_even_after_a_recent_sip() {
        // At 3 pm she should be at 4 cups.
        assertEquals(Moment.DELAY, nudge(minuteOfDay = 15 * 60, cups = 2.0, lastDrinkMinute = 14 * 60 + 30))
    }

    @Test
    fun quiet_hours_and_goal_hit_never_nudge() {
        assertNull(nudge(minuteOfDay = 23 * 60, cups = 1.0, lastDrinkMinute = 9 * 60))
        assertNull(nudge(minuteOfDay = 7 * 60, cups = 0.0))
        assertNull(nudge(minuteOfDay = 16 * 60, cups = 8.0, lastDrinkMinute = 10 * 60))
    }

    @Test
    fun ignoring_a_nudge_for_thirty_minutes_escalates_once() {
        val nudged = NudgeState(lastNudgeAtMillis = 12 * hour, nudgesToday = 1, lastWasEscalation = false)
        assertNull(nudge(minuteOfDay = 12 * 60 + 20, lastDrinkMinute = 9 * 60, state = nudged))
        assertEquals(Moment.STILL_DELAYING, nudge(minuteOfDay = 12 * 60 + 30, lastDrinkMinute = 9 * 60, state = nudged))

        val escalated = nudged.copy(nudgesToday = 2, lastWasEscalation = true, lastNudgeAtMillis = 12 * hour + 30 * minute)
        assertNull(nudge(minuteOfDay = 13 * 60, lastDrinkMinute = 9 * 60, state = escalated))
    }

    @Test
    fun drinking_after_a_nudge_resets_the_escalation() {
        val nudged = NudgeState(lastNudgeAtMillis = 12 * hour, nudgesToday = 1, lastWasEscalation = false)
        assertNull(nudge(minuteOfDay = 12 * 60 + 40, cups = 4.0, lastDrinkMinute = 12 * 60 + 10, state = nudged))
    }

    @Test
    fun at_most_three_delay_nudges_a_day() {
        val tired = NudgeState(lastNudgeAtMillis = 10 * hour, nudgesToday = 3, lastWasEscalation = true)
        assertNull(nudge(minuteOfDay = 18 * 60, cups = 1.0, lastDrinkMinute = 9 * 60, state = tired))
    }

    @Test
    fun missing_lunch_by_two_and_dinner_by_eight() {
        assertNull(Meals.missing(minuteOfDay = 13 * 60, mealMinutes = emptyList(), alreadyNudged = emptySet()))
        assertEquals(Meal.LUNCH, Meals.missing(14 * 60, mealMinutes = listOf(8 * 60), alreadyNudged = emptySet()))
        assertNull(Meals.missing(14 * 60, mealMinutes = listOf(12 * 60), alreadyNudged = emptySet()))
        assertNull(Meals.missing(15 * 60, mealMinutes = emptyList(), alreadyNudged = setOf(Meal.LUNCH)))
        assertEquals(Meal.DINNER, Meals.missing(20 * 60, mealMinutes = listOf(12 * 60), alreadyNudged = setOf(Meal.LUNCH)))
        assertNull(Meals.missing(21 * 60, mealMinutes = listOf(12 * 60, 18 * 60), alreadyNudged = emptySet()))
    }
}
