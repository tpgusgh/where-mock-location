package com.tpgusgh.wheremocklocation

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PairingServerTest {
    @Test
    fun `valid token forwards the update and replies ok`() {
        val token = "test-token-123"
        val updates = mutableListOf<Pair<Double, Double>>()
        val startLatch = CountDownLatch(1)

        val server = PairingServer(
            port = 0,
            expectedToken = token,
            onValidUpdate = { lat, lng -> updates.add(lat to lng) },
            onStarted = { startLatch.countDown() }
        )
        server.start()
        assertTrue(startLatch.await(3, TimeUnit.SECONDS))

        val responseLatch = CountDownLatch(1)
        val responses = mutableListOf<String>()
        val client = object : WebSocketClient(URI("ws://localhost:${server.port}")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send("""{"token":"$token","lat":37.5,"lng":127.0}""")
            }
            override fun onMessage(message: String) {
                responses.add(message)
                responseLatch.countDown()
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }

        client.connectBlocking(3, TimeUnit.SECONDS)
        assertTrue(responseLatch.await(3, TimeUnit.SECONDS))
        assertEquals(listOf(37.5 to 127.0), updates)
        assertTrue(responses[0].contains("\"status\":\"ok\""))

        client.closeBlocking()
        server.stop()
    }

    @Test
    fun `invalid token is rejected and does not forward the update`() {
        val updates = mutableListOf<Pair<Double, Double>>()
        val startLatch = CountDownLatch(1)

        val server = PairingServer(
            port = 0,
            expectedToken = "correct-token",
            onValidUpdate = { lat, lng -> updates.add(lat to lng) },
            onStarted = { startLatch.countDown() }
        )
        server.start()
        assertTrue(startLatch.await(3, TimeUnit.SECONDS))

        val responseLatch = CountDownLatch(1)
        val responses = mutableListOf<String>()
        val client = object : WebSocketClient(URI("ws://localhost:${server.port}")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send("""{"token":"wrong-token","lat":37.5,"lng":127.0}""")
            }
            override fun onMessage(message: String) {
                responses.add(message)
                responseLatch.countDown()
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }

        client.connectBlocking(3, TimeUnit.SECONDS)
        assertTrue(responseLatch.await(3, TimeUnit.SECONDS))
        assertTrue(updates.isEmpty())
        assertTrue(responses[0].contains("\"status\":\"error\""))

        client.closeBlocking()
        server.stop()
    }

    @Test
    fun `valid token but update not applied replies error`() {
        val token = "test-token-456"
        val startLatch = CountDownLatch(1)

        val server = PairingServer(
            port = 0,
            expectedToken = token,
            onValidUpdate = { _, _ -> false },
            onStarted = { startLatch.countDown() }
        )
        server.start()
        assertTrue(startLatch.await(3, TimeUnit.SECONDS))

        val responseLatch = CountDownLatch(1)
        val responses = mutableListOf<String>()
        val client = object : WebSocketClient(URI("ws://localhost:${server.port}")) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                send("""{"token":"$token","lat":37.5,"lng":127.0}""")
            }
            override fun onMessage(message: String) {
                responses.add(message)
                responseLatch.countDown()
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }

        client.connectBlocking(3, TimeUnit.SECONDS)
        assertTrue(responseLatch.await(3, TimeUnit.SECONDS))
        assertTrue(responses[0].contains("\"status\":\"error\""))
        assertTrue(responses[0].contains("mocking not active"))

        client.closeBlocking()
        server.stop()
    }
}
