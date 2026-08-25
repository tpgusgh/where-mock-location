package com.tpgusgh.wheremocklocation

import org.junit.Assert.*
import org.junit.Test

class PairingTokenTest {
    @Test
    fun `generate produces different tokens each call`() {
        val a = PairingToken.generate()
        val b = PairingToken.generate()
        assertNotEquals(a, b)
    }

    @Test
    fun `generate produces a non-empty url-safe token`() {
        val token = PairingToken.generate()
        assertTrue(token.isNotEmpty())
        assertFalse(token.contains("+"))
        assertFalse(token.contains("/"))
    }

    @Test
    fun `isValid returns true for matching tokens`() {
        assertTrue(PairingToken.isValid("abc123", "abc123"))
    }

    @Test
    fun `isValid returns false for non-matching tokens`() {
        assertFalse(PairingToken.isValid("abc123", "xyz789"))
    }
}
