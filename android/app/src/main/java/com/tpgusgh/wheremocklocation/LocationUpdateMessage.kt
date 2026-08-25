package com.tpgusgh.wheremocklocation

import org.json.JSONException
import org.json.JSONObject

sealed class LocationUpdateResult {
    data class Success(val lat: Double, val lng: Double) : LocationUpdateResult()
    object InvalidToken : LocationUpdateResult()
    data class Malformed(val reason: String) : LocationUpdateResult()
}

object LocationUpdateMessage {
    fun parse(json: String, expectedToken: String): LocationUpdateResult {
        return try {
            val obj = JSONObject(json)
            val token = obj.optString("token", "")
            if (!PairingToken.isValid(token, expectedToken)) {
                return LocationUpdateResult.InvalidToken
            }
            if (!obj.has("lat") || !obj.has("lng")) {
                return LocationUpdateResult.Malformed("missing lat/lng")
            }
            val lat = obj.getDouble("lat")
            val lng = obj.getDouble("lng")
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                return LocationUpdateResult.Malformed("lat/lng out of range")
            }
            LocationUpdateResult.Success(lat, lng)
        } catch (e: JSONException) {
            LocationUpdateResult.Malformed(e.message ?: "invalid json")
        }
    }

    fun encodeStatus(status: String, message: String): String =
        JSONObject().put("status", status).put("message", message).toString()
}
