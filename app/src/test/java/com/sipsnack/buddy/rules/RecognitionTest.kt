package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionTest {
    @Test
    fun a_bottle_reads_as_water() {
        val guess = Recognition.guess(listOf("Bottle" to 0.91f, "Plastic" to 0.6f))
        assertEquals(LogKind.WATER, guess?.kind)
        assertEquals(0.91f, guess!!.confidence, 0.001f)
    }

    @Test
    fun food_labels_read_as_a_meal_and_snacks_as_a_snack() {
        assertEquals(LogKind.MEAL, Recognition.guess(listOf("Food" to 0.88f, "Rice" to 0.8f))?.kind)
        assertEquals(LogKind.SNACK, Recognition.guess(listOf("Cookie" to 0.84f))?.kind)
    }

    @Test
    fun coffee_is_another_drink() {
        assertEquals(LogKind.OTHER_DRINK, Recognition.guess(listOf("Coffee" to 0.8f, "Cup" to 0.75f))?.kind)
    }

    @Test
    fun low_confidence_or_unknown_means_she_picks() {
        assertNull(Recognition.guess(listOf("Bottle" to 0.5f)))
        assertNull(Recognition.guess(listOf("Sky" to 0.95f)))
        assertNull(Recognition.guess(emptyList()))
    }
}
