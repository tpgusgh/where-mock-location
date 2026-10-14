package com.tpgusgh.wheremocklocation

import org.json.JSONArray
import org.json.JSONObject

data class LocationPreset(val name: String, val lat: Double, val lng: Double)

object PresetSerializer {
    fun encode(presets: List<LocationPreset>): String {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("name", preset.name)
                    .put("lat", preset.lat)
                    .put("lng", preset.lng)
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<LocationPreset> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LocationPreset(
                name = obj.getString("name"),
                lat = obj.getDouble("lat"),
                lng = obj.getDouble("lng")
            )
        }
    }
}
