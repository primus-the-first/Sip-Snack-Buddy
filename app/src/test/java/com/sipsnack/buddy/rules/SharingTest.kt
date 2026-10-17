package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SharingTest {
    private val day = listOf(
        LogEntry(1_000, LogKind.WATER, 2.0, "My bottle"),
        LogEntry(2_000, LogKind.MEAL, 0.0, "Meal"),
        LogEntry(3_000, LogKind.OTHER_DRINK, 0.0, "Other drink"),
        LogEntry(4_000, LogKind.WATER, 1.0, "Water"),
        LogEntry(5_000, LogKind.SNACK, 0.0, "Snack"),
    )

    @Test
    fun progress_only_shares_cups_and_last_sip_but_no_meals() {
        val shared = Sharing.summarise(day, goal = 8, level = ShareLevel.PROGRESS)
        assertEquals(3.0, shared.cups, 0.001)
        assertEquals(8, shared.goal)
        assertEquals(4_000L, shared.lastSipMillis)
        assertEquals(emptyList<SharedEvent>(), shared.events)
    }

    @Test
    fun with_meals_shares_every_log_as_time_and_kind_only() {
        val shared = Sharing.summarise(day, goal = 8, level = ShareLevel.WITH_MEALS)
        assertEquals(5, shared.events.size)
        assertEquals(SharedEvent(2_000, LogKind.MEAL, 0.0), shared.events[1])
    }

    @Test
    fun an_empty_day_has_no_last_sip() {
        assertEquals(null, Sharing.summarise(emptyList(), goal = 8, level = ShareLevel.WITH_MEALS).lastSipMillis)
    }

    @Test
    fun codes_are_eight_easy_to_read_characters_with_a_dash() {
        val code = Sharing.newCode(Random(7))
        assertTrue(code, Regex("^[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}$").matches(code))
        assertNotEquals(code, Sharing.newCode(Random(8)))
    }

    @Test
    fun typed_codes_are_forgiving() {
        assertEquals("K7Q2-MX9D", Sharing.normalise(" k7q2mx9d "))
        assertEquals("K7Q2-MX9D", Sharing.normalise("k7q2-mx9d"))
        assertEquals(null, Sharing.normalise("k7q2"))
    }
}
