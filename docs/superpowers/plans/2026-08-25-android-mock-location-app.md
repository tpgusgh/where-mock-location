# Android Mock Location App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android half of the mock-location toolkit: a standalone app that fakes GPS location via the official Android mock-location-provider API, controllable locally (manual lat/lng entry, saved presets) and remotely over a token-authenticated local-network WebSocket (for the future macOS companion).

**Architecture:** A foreground `Service` owns the mock location provider lifecycle. A small embedded WebSocket server accepts token-authenticated coordinate updates from the local network and forwards them into the service. Parsing/validation/serialization logic is kept in plain Kotlin objects with no Android framework dependency so it can run under fast JVM unit tests; anything that must touch `LocationManager`/`Context` is a thin wrapper verified manually on a device.

**Tech Stack:** Kotlin, Android Gradle Plugin, `org.java-websocket:Java-WebSocket` (embedded WebSocket server), `com.google.zxing:core` (QR generation), `org.json` (built-in JSON, no serialization library), JUnit4.

**Spec:** [docs/superpowers/specs/2026-08-25-mock-location-toolkit-design.md](../specs/2026-08-25-mock-location-toolkit-design.md)

## Global Constraints

- Must use the officially supported `LocationManager.addTestProvider` / `setTestProviderLocation` mechanism — no root.
- Foreground notification is mandatory while injecting fake location.
- Every WebSocket location-update message must carry a pairing token; unauthenticated updates must be rejected.
- No ADB/USB-debugging dependency in the pairing protocol — local-network WebSocket only.
- No Play Store distribution — signed with a local/debug keystore, sideloaded.
- Map picker UI is out of scope for this plan (spec marks it stretch/v2).
- `minSdk = 26`, `compileSdk = 34`, package `com.tpgusgh.wheremocklocation`, project lives under `android/` in the repo root.

---

### Task 1: Project scaffold

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/themes.xml`
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/MainActivity.kt` (empty placeholder Activity, replaced in Task 7)
- Create: `android/gradle.properties`

**Interfaces:**
- Produces: a buildable Gradle project (`:app`) that later tasks add sources to. All later Kotlin files live under `android/app/src/main/java/com/tpgusgh/wheremocklocation/`, tests under `android/app/src/test/java/com/tpgusgh/wheremocklocation/`.

- [ ] **Step 1: Create `android/settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "where-mock-location"
include(":app")
```

- [ ] **Step 2: Create `android/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

- [ ] **Step 3: Create `android/gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 4: Create `android/app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tpgusgh.wheremocklocation"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tpgusgh.wheremocklocation"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
```

- [ ] **Step 5: Create `android/app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:theme="@style/Theme.WhereMockLocation">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".MockLocationService"
            android:foregroundServiceType="location"
            android:exported="false" />

    </application>
</manifest>
```

- [ ] **Step 6: Create `android/app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">Where Mock Location</string>
</resources>
```

- [ ] **Step 7: Create `android/app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.WhereMockLocation" parent="Theme.MaterialComponents.DayNight.NoActionBar" />
</resources>
```

- [ ] **Step 8: Create placeholder `android/app/src/main/java/com/tpgusgh/wheremocklocation/MainActivity.kt`**

```kotlin
package com.tpgusgh.wheremocklocation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
```

- [ ] **Step 9: Verify the project builds**

Run: `cd android && ./gradlew :app:assembleDebug` (use `gradle wrapper` first if `gradlew` is missing: `gradle wrapper --gradle-version 8.7`)
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add android/
git commit -m "chore: scaffold Android Gradle project"
```

---

### Task 2: Pairing token generation and validation

**Files:**
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/PairingToken.kt`
- Test: `android/app/src/test/java/com/tpgusgh/wheremocklocation/PairingTokenTest.kt`

**Interfaces:**
- Produces: `PairingToken.generate(): String`, `PairingToken.isValid(candidate: String, expected: String): Boolean` — used by Task 4 (`LocationUpdateMessage`) and Task 7 (`MainActivity`).

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.PairingTokenTest"`
Expected: FAIL — `PairingToken` unresolved reference

- [ ] **Step 3: Implement `PairingToken`**

```kotlin
package com.tpgusgh.wheremocklocation

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PairingToken {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isValid(candidate: String, expected: String): Boolean {
        val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(candidateBytes, expectedBytes)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.PairingTokenTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/tpgusgh/wheremocklocation/PairingToken.kt android/app/src/test/java/com/tpgusgh/wheremocklocation/PairingTokenTest.kt
git commit -m "feat: add pairing token generation and validation"
```

---

### Task 3: Location presets — model, serialization, storage

**Files:**
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/PresetSerializer.kt` (also defines `LocationPreset`)
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/PresetRepository.kt`
- Test: `android/app/src/test/java/com/tpgusgh/wheremocklocation/PresetSerializerTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `data class LocationPreset(name: String, lat: Double, lng: Double)`, `PresetSerializer.encode(List<LocationPreset>): String`, `PresetSerializer.decode(String): List<LocationPreset>`, `PresetRepository(context: Context)` with `getAll(): List<LocationPreset>`, `add(preset: LocationPreset)`, `remove(name: String)` — used by Task 7 (`MainActivity`).

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.PresetSerializerTest"`
Expected: FAIL — `PresetSerializer`/`LocationPreset` unresolved reference

- [ ] **Step 3: Implement `PresetSerializer` and `LocationPreset`**

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.PresetSerializerTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Implement `PresetRepository`**

Thin `SharedPreferences` wrapper around the already-tested serializer — no new automated test (see spec's testing section: framework-bound classes are manually verified). Verified in Task 7 by saving/reloading a preset through the running app.

```kotlin
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
```

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/tpgusgh/wheremocklocation/PresetSerializer.kt android/app/src/main/java/com/tpgusgh/wheremocklocation/PresetRepository.kt android/app/src/test/java/com/tpgusgh/wheremocklocation/PresetSerializerTest.kt
git commit -m "feat: add location preset model, serialization, and storage"
```

---

### Task 4: Location update message parsing

**Files:**
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/LocationUpdateMessage.kt`
- Test: `android/app/src/test/java/com/tpgusgh/wheremocklocation/LocationUpdateMessageTest.kt`

**Interfaces:**
- Consumes: `PairingToken.isValid` (Task 2).
- Produces: `sealed class LocationUpdateResult` (`Success(lat, lng)`, `InvalidToken`, `Malformed(reason)`), `LocationUpdateMessage.parse(json: String, expectedToken: String): LocationUpdateResult`, `LocationUpdateMessage.encodeStatus(status: String, message: String): String` — used by Task 5 (`PairingServer`).

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.LocationUpdateMessageTest"`
Expected: FAIL — `LocationUpdateMessage`/`LocationUpdateResult` unresolved reference

- [ ] **Step 3: Implement `LocationUpdateMessage`**

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.LocationUpdateMessageTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/tpgusgh/wheremocklocation/LocationUpdateMessage.kt android/app/src/test/java/com/tpgusgh/wheremocklocation/LocationUpdateMessageTest.kt
git commit -m "feat: add location update message parsing and validation"
```

---

### Task 5: Pairing WebSocket server

**Files:**
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/PairingServer.kt`
- Test: `android/app/src/test/java/com/tpgusgh/wheremocklocation/PairingServerTest.kt`

**Interfaces:**
- Consumes: `LocationUpdateMessage.parse`, `LocationUpdateMessage.encodeStatus` (Task 4).
- Produces: `class PairingServer(port: Int, expectedToken: String, onValidUpdate: (Double, Double) -> Unit, onStarted: () -> Unit = {})` with `start()`/`stop()` (inherited from `WebSocketServer`) — used by Task 7 (`MainActivity`).

- [ ] **Step 1: Write the failing test**

```kotlin
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
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.PairingServerTest"`
Expected: FAIL — `PairingServer` unresolved reference

- [ ] **Step 3: Implement `PairingServer`**

```kotlin
package com.tpgusgh.wheremocklocation

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class PairingServer(
    port: Int,
    private val expectedToken: String,
    private val onValidUpdate: (lat: Double, lng: Double) -> Unit,
    private val onStarted: () -> Unit = {}
) : WebSocketServer(InetSocketAddress(port)) {

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}

    override fun onMessage(conn: WebSocket, message: String) {
        when (val result = LocationUpdateMessage.parse(message, expectedToken)) {
            is LocationUpdateResult.Success -> {
                onValidUpdate(result.lat, result.lng)
                conn.send(LocationUpdateMessage.encodeStatus("ok", "location updated"))
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
    override fun onError(conn: WebSocket?, ex: Exception) {}
    override fun onStart() {
        onStarted()
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.tpgusgh.wheremocklocation.PairingServerTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/tpgusgh/wheremocklocation/PairingServer.kt android/app/src/test/java/com/tpgusgh/wheremocklocation/PairingServerTest.kt
git commit -m "feat: add token-authenticated pairing WebSocket server"
```

---

### Task 6: Mock location provider gateway and foreground service

**Files:**
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/LocationProviderGateway.kt`
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/SystemLocationProviderGateway.kt`
- Create: `android/app/src/main/java/com/tpgusgh/wheremocklocation/MockLocationService.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks directly (wired together in Task 7).
- Produces: `interface LocationProviderGateway { start(); update(lat, lng); stop() }`, `class MockLocationService : Service()` with `companion object` constants `ACTION_START`, `ACTION_UPDATE`, `ACTION_STOP`, `EXTRA_LAT`, `EXTRA_LNG` — used by Task 7 (`MainActivity`).

No automated unit test for this task — `LocationManager` and `Service` are Android-framework classes that require a running device/emulator to exercise meaningfully (this matches the spec's testing section, which calls for manual verification here via `adb shell dumpsys location`). Verification is a manual step below.

- [ ] **Step 1: Implement `LocationProviderGateway`**

```kotlin
package com.tpgusgh.wheremocklocation

interface LocationProviderGateway {
    fun start()
    fun update(lat: Double, lng: Double)
    fun stop()
}
```

- [ ] **Step 2: Implement `SystemLocationProviderGateway`**

```kotlin
package com.tpgusgh.wheremocklocation

import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

class SystemLocationProviderGateway(
    private val locationManager: LocationManager,
    private val providerName: String = LocationManager.GPS_PROVIDER
) : LocationProviderGateway {

    override fun start() {
        locationManager.addTestProvider(
            providerName,
            false, false, false, false, true, true, true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(providerName, true)
    }

    override fun update(lat: Double, lng: Double) {
        val location = Location(providerName).apply {
            latitude = lat
            longitude = lng
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(providerName, location)
    }

    override fun stop() {
        locationManager.removeTestProvider(providerName)
    }
}
```

- [ ] **Step 3: Implement `MockLocationService`**

```kotlin
package com.tpgusgh.wheremocklocation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MockLocationService : Service() {

    private lateinit var gateway: LocationProviderGateway

    override fun onCreate() {
        super.onCreate()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gateway = SystemLocationProviderGateway(locationManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                gateway.start()
            }
            ACTION_UPDATE -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    gateway.update(lat, lng)
                }
            }
            ACTION_STOP -> {
                gateway.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mock location", NotificationManager.IMPORTANCE_LOW)
            )
        }
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock location active")
            .setContentText("Faking GPS location for testing")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.tpgusgh.wheremocklocation.action.START"
        const val ACTION_UPDATE = "com.tpgusgh.wheremocklocation.action.UPDATE"
        const val ACTION_STOP = "com.tpgusgh.wheremocklocation.action.STOP"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        private const val CHANNEL_ID = "mock_location_channel"
        private const val NOTIFICATION_ID = 1
    }
}
```

- [ ] **Step 4: Verify the project still builds**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/tpgusgh/wheremocklocation/LocationProviderGateway.kt android/app/src/main/java/com/tpgusgh/wheremocklocation/SystemLocationProviderGateway.kt android/app/src/main/java/com/tpgusgh/wheremocklocation/MockLocationService.kt
git commit -m "feat: add mock location provider gateway and foreground service"
```

---

### Task 7: Main UI — manual entry, presets, pairing display

**Files:**
- Create: `android/app/src/main/res/layout/activity_main.xml`
- Modify: `android/app/src/main/java/com/tpgusgh/wheremocklocation/MainActivity.kt` (replaces Task 1's placeholder)

**Interfaces:**
- Consumes: `PairingToken.generate` (Task 2), `LocationPreset`/`PresetRepository` (Task 3), `PairingServer` (Task 5), `MockLocationService` actions/extras (Task 6).
- Produces: the running app — nothing further consumes this.

No automated test — this is the UI wiring layer, verified manually in Task 8's end-to-end check.

- [ ] **Step 1: Create `activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <EditText
        android:id="@+id/et_latitude"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Latitude"
        android:inputType="numberDecimal|numberSigned" />

    <EditText
        android:id="@+id/et_longitude"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Longitude"
        android:inputType="numberDecimal|numberSigned" />

    <Button
        android:id="@+id/btn_toggle_mock"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Start mocking" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <EditText
            android:id="@+id/et_preset_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Preset name" />

        <Button
            android:id="@+id/btn_save_preset"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Save preset" />
    </LinearLayout>

    <ListView
        android:id="@+id/list_presets"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <TextView
        android:id="@+id/tv_pairing_info"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textIsSelectable="true" />

    <ImageView
        android:id="@+id/iv_qr_code"
        android:layout_width="200dp"
        android:layout_height="200dp"
        android:layout_gravity="center_horizontal" />

</LinearLayout>
```

- [ ] **Step 2: Replace `MainActivity.kt`**

```kotlin
package com.tpgusgh.wheremocklocation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tpgusgh.wheremocklocation.databinding.ActivityMainBinding
import org.json.JSONObject
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var presetRepository: PresetRepository
    private var pairingServer: PairingServer? = null
    private var isMocking = false
    private val pairingToken = PairingToken.generate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        presetRepository = PresetRepository(this)
        refreshPresetList()
        showPairingInfo()
        startPairingServer()

        binding.btnToggleMock.setOnClickListener { onToggleMockClicked() }
        binding.btnSavePreset.setOnClickListener { onSavePresetClicked() }
        binding.listPresets.setOnItemClickListener { _, _, position, _ ->
            val preset = presetRepository.getAll()[position]
            binding.etLatitude.setText(preset.lat.toString())
            binding.etLongitude.setText(preset.lng.toString())
        }
    }

    override fun onDestroy() {
        pairingServer?.stop()
        super.onDestroy()
    }

    private fun onToggleMockClicked() {
        val lat = binding.etLatitude.text.toString().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().toDoubleOrNull()
        if (lat == null || lng == null) return

        if (!isMocking) {
            startService(Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_START))
            sendLocationUpdate(lat, lng)
            binding.btnToggleMock.text = "Stop mocking"
        } else {
            startService(Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP))
            binding.btnToggleMock.text = "Start mocking"
        }
        isMocking = !isMocking
    }

    private fun sendLocationUpdate(lat: Double, lng: Double) {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_UPDATE
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LNG, lng)
        }
        startService(intent)
    }

    private fun onSavePresetClicked() {
        val name = binding.etPresetName.text.toString()
        val lat = binding.etLatitude.text.toString().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().toDoubleOrNull()
        if (name.isBlank() || lat == null || lng == null) return
        presetRepository.add(LocationPreset(name, lat, lng))
        binding.etPresetName.text.clear()
        refreshPresetList()
    }

    private fun refreshPresetList() {
        val names = presetRepository.getAll().map { "${it.name} (${it.lat}, ${it.lng})" }
        binding.listPresets.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }

    private fun startPairingServer() {
        val server = PairingServer(
            port = PAIRING_PORT,
            expectedToken = pairingToken,
            onValidUpdate = { lat, lng ->
                runOnUiThread {
                    binding.etLatitude.setText(lat.toString())
                    binding.etLongitude.setText(lng.toString())
                    if (isMocking) sendLocationUpdate(lat, lng)
                }
            }
        )
        server.start()
        pairingServer = server
    }

    private fun showPairingInfo() {
        val ip = localIpAddress() ?: "unknown"
        binding.tvPairingInfo.text = "IP: $ip  Port: $PAIRING_PORT"
        val payload = JSONObject()
            .put("ip", ip)
            .put("port", PAIRING_PORT)
            .put("token", pairingToken)
            .toString()
        binding.ivQrCode.setImageBitmap(renderQrCode(payload))
    }

    private fun localIpAddress(): String? {
        NetworkInterface.getNetworkInterfaces().iterator().forEach { iface ->
            iface.inetAddresses.iterator().forEach { addr ->
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                    return addr.hostAddress
                }
            }
        }
        return null
    }

    private fun renderQrCode(content: String): Bitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    companion object {
        private const val PAIRING_PORT = 8765
    }
}
```

- [ ] **Step 3: Verify the project builds**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/layout/activity_main.xml android/app/src/main/java/com/tpgusgh/wheremocklocation/MainActivity.kt
git commit -m "feat: wire up main UI for manual entry, presets, and pairing display"
```

---

### Task 8: End-to-end manual verification and README

**Files:**
- Create: `android/README.md`

**Interfaces:**
- Consumes: the full app from Tasks 1–7.
- Produces: nothing further — this is the final task in this plan.

- [ ] **Step 1: Install and enable mock location**

Run: `cd android && ./gradlew :app:installDebug`
On the device: Settings → About phone → tap "Build number" 7 times to enable Developer Options → Developer Options → "Select mock location app" → choose "Where Mock Location".

- [ ] **Step 2: Verify mock location injection**

In the app, enter a latitude/longitude (e.g. `37.5665`, `126.9780`) and tap "Start mocking".
Run: `adb shell dumpsys location | grep -A 5 "mock"`
Expected: output shows the `gps` provider enabled with a mock location matching the entered coordinates.

- [ ] **Step 3: Verify the pairing server responds**

With the app running and the device on Wi-Fi, note the IP/port shown in the app. From a machine on the same network:

Run: `pip install websocket-client && python3 -c "
import websocket, json
ws = websocket.create_connection('ws://<device-ip>:8765')
ws.send(json.dumps({'token': '<token-from-app-QR>', 'lat': 35.6762, 'lng': 139.6503}))
print(ws.recv())
"`
Expected: prints `{"status":"ok","message":"location updated"}` and the app's lat/lng fields update to the Tokyo coordinates.

- [ ] **Step 4: Write `android/README.md`**

```markdown
# where-mock-location (Android)

Fakes GPS location for testing location-aware app behavior during
development. Uses the official Android mock-location-provider API — no
root required.

## Setup

1. Enable Developer Options on the test device (Settings → About phone →
   tap "Build number" 7 times).
2. Developer Options → "Select mock location app" → choose
   "Where Mock Location".
3. Install: `./gradlew :app:installDebug`

## Use

- Enter latitude/longitude directly, or tap a saved preset, then tap
  "Start mocking".
- Save the current coordinates as a named preset with "Save preset".
- To drive location from another machine on the same Wi-Fi (e.g. the
  macOS companion app), scan the QR code shown in the app — it encodes
  the device's IP, pairing port, and a one-time token. Send
  `{"token": "...", "lat": ..., "lng": ...}` as a WebSocket message to
  that address; the app responds `{"status": "ok"}` or
  `{"status": "error", "message": "..."}`.

## Verifying it's working

`adb shell dumpsys location` shows the mock `gps` provider and its
current injected location while mocking is active.
```

- [ ] **Step 5: Commit**

```bash
git add android/README.md
git commit -m "docs: add Android app setup and verification instructions"
```
