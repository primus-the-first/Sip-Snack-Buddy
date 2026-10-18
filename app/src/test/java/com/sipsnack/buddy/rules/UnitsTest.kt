package com.sipsnack.buddy.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun cups_show_as_millilitres() {
        assertEquals("2,000 ml", Units.ml(8.0))
        assertEquals("500 ml", Units.ml(2.0))
        assertEquals("50 ml", Units.ml(0.2))
        assertEquals("0 ml", Units.ml(0.0))
    }

    @Test
    fun millilitres_convert_back_to_cups() {
        assertEquals(0.4, Units.cups(100), 0.0001)
        assertEquals(1.0, Units.cups(250), 0.0001)
    }
}
