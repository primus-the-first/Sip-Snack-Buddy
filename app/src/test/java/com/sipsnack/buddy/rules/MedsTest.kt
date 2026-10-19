package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedsTest {
    private val becoatin = Med(id = "becoatin", name = "Becoatin", minuteOfDay = 8 * 60)

    @Test
    fun no_reminder_before_it_is_due() {
        assertFalse(Meds.shouldRemind(becoatin, minuteOfDay = 7 * 60 + 59, takenToday = false, remindersToday = 0))
    }

    @Test
    fun one_reminder_when_due_then_one_follow_up_two_hours_later() {
        assertTrue(Meds.shouldRemind(becoatin, 8 * 60, takenToday = false, remindersToday = 0))
        assertFalse(Meds.shouldRemind(becoatin, 9 * 60, takenToday = false, remindersToday = 1))
        assertTrue(Meds.shouldRemind(becoatin, 10 * 60, takenToday = false, remindersToday = 1))
        assertFalse(Meds.shouldRemind(becoatin, 13 * 60, takenToday = false, remindersToday = 2))
    }

    @Test
    fun taken_means_no_reminders() {
        assertFalse(Meds.shouldRemind(becoatin, 8 * 60 + 30, takenToday = true, remindersToday = 0))
    }

    @Test
    fun streak_counts_days_in_a_row_up_to_today_or_yesterday() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals(3, Meds.streak(setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)), today))
        // Not yet taken today: yesterday's run still counts.
        assertEquals(2, Meds.streak(setOf(today.minusDays(1), today.minusDays(2)), today))
        assertEquals(0, Meds.streak(setOf(today.minusDays(2)), today))
    }

    @Test
    fun medicines_round_trip_through_storage() {
        val list = listOf(becoatin, Med("m2", "Vitamin C | daily", 21 * 60 + 30))
        assertEquals(list, Meds.decode(Meds.encode(list)))
    }
}
