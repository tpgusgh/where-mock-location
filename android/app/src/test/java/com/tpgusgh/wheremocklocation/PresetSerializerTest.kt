package com.tpgusgh.wheremocklocation

import org.junit.Assert.*
import org.junit.Test

class PresetSerializerTest {
    @Test
    fun `round trips a list of presets`() {
        val presets = listOf(
            LocationPreset("Seoul", 37.5665, 126.9780),
            LocationPreset("Tokyo", 35.6762, 139.6503)
        )
        val encoded = PresetSerializer.encode(presets)
        val decoded = PresetSerializer.decode(encoded)
        assertEquals(presets, decoded)
    }

    @Test
    fun `decoding blank string returns empty list`() {
        assertEquals(emptyList<LocationPreset>(), PresetSerializer.decode(""))
    }

    @Test
    fun `round trips names containing quotes`() {
        val presets = listOf(LocationPreset("My \"office\"", 1.0, 2.0))
        assertEquals(presets, PresetSerializer.decode(PresetSerializer.encode(presets)))
    }
}
