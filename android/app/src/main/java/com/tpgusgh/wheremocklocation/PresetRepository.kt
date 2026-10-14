package com.tpgusgh.wheremocklocation

import android.content.Context

class PresetRepository(context: Context) {
    private val prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)

    fun getAll(): List<LocationPreset> =
        PresetSerializer.decode(prefs.getString(KEY_PRESETS, "") ?: "")

    fun add(preset: LocationPreset) {
        val updated = getAll().filterNot { it.name == preset.name } + preset
        save(updated)
    }

    fun remove(name: String) {
        save(getAll().filterNot { it.name == name })
    }

    private fun save(presets: List<LocationPreset>) {
        prefs.edit().putString(KEY_PRESETS, PresetSerializer.encode(presets)).apply()
    }

    companion object {
        private const val KEY_PRESETS = "presets_json"
    }
}
