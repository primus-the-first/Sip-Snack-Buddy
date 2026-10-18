package com.sipsnack.buddy.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun a_higher_release_is_newer() {
        assertTrue(Versions.isNewer("v0.2.2", "0.2.1"))
        assertTrue(Versions.isNewer("0.3.0", "0.2.9"))
        assertTrue(Versions.isNewer("v1.0", "0.9.9"))
        assertTrue(Versions.isNewer("0.2.10", "0.2.9"))
    }

    @Test
    fun the_same_or_older_release_is_not_newer() {
        assertFalse(Versions.isNewer("v0.2.1", "0.2.1"))
        assertFalse(Versions.isNewer("0.2.0", "0.2.1"))
        assertFalse(Versions.isNewer("v0.2", "0.2.0"))
    }

    @Test
    fun junk_is_never_newer() {
        assertFalse(Versions.isNewer("latest", "0.2.1"))
        assertFalse(Versions.isNewer("", "0.2.1"))
    }
}
