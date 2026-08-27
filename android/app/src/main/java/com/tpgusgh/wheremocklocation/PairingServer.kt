package com.tpgusgh.wheremocklocation

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class PairingServer(
    port: Int,
    private val expectedToken: String,
    private val onValidUpdate: (lat: Double, lng: Double) -> Boolean,
    private val onStarted: () -> Unit = {},
    private val onServerError: (Exception) -> Unit = {}
) : WebSocketServer(InetSocketAddress(port)) {

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}

    override fun onMessage(conn: WebSocket, message: String) {
        when (val result = LocationUpdateMessage.parse(message, expectedToken)) {
            is LocationUpdateResult.Success -> {
                val applied = onValidUpdate(result.lat, result.lng)
                if (applied) {
                    conn.send(LocationUpdateMessage.encodeStatus("ok", "location updated"))
                } else {
                    conn.send(LocationUpdateMessage.encodeStatus("error", "mocking not active"))
                }
            }
            is LocationUpdateResult.InvalidToken -> {
                conn.send(LocationUpdateMessage.encodeStatus("error", "invalid token"))
            }
            is LocationUpdateResult.Malformed -> {
                conn.send(LocationUpdateMessage.encodeStatus("error", result.reason))
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {}
    override fun onError(conn: WebSocket?, ex: Exception) {
        onServerError(ex)
    }
    override fun onStart() {
        onStarted()
    }
}
