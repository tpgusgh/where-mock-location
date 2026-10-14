package com.tpgusgh.wheremocklocation

import org.junit.Assert.*
import org.junit.Test

class LocationUpdateMessageTest {
    private val token = "secret-token"

    @Test
    fun `parses a valid message`() {
        val json = """{"token":"secret-token","lat":37.5,"lng":127.0}"""
        val result = LocationUpdateMessage.parse(json, token)
        assertEquals(LocationUpdateResult.Success(37.5, 127.0), result)
    }

    @Test
    fun `rejects a wrong token`() {
        val json = """{"token":"wrong","lat":37.5,"lng":127.0}"""
        assertEquals(LocationUpdateResult.InvalidToken, LocationUpdateMessage.parse(json, token))
    }

    @Test
    fun `rejects missing coordinates`() {
        val json = """{"token":"secret-token"}"""
        val result = LocationUpdateMessage.parse(json, token)
        assertTrue(result is LocationUpdateResult.Malformed)
    }

    @Test
    fun `rejects out-of-range coordinates`() {
        val json = """{"token":"secret-token","lat":999.0,"lng":127.0}"""
        val result = LocationUpdateMessage.parse(json, token)
        assertTrue(result is LocationUpdateResult.Malformed)
    }

    @Test
    fun `rejects invalid json`() {
        val result = LocationUpdateMessage.parse("not json", token)
        assertTrue(result is LocationUpdateResult.Malformed)
    }

    @Test
    fun `encodeStatus produces a status and message field`() {
        val encoded = LocationUpdateMessage.encodeStatus("ok", "location updated")
        assertTrue(encoded.contains("\"status\":\"ok\""))
        assertTrue(encoded.contains("\"message\":\"location updated\""))
    }
}
