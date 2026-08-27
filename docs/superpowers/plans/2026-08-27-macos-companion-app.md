# macOS Companion App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the macOS half of the mock-location toolkit: a menu-bar app with a map picker that pushes coordinates to the already-shipped Android app over its token-authenticated pairing WebSocket.

**Architecture:** A SwiftUI `MenuBarExtra` app. Coordinate-encoding/response-decoding logic is a pure, dependency-free Swift type (unit tested); the actual `URLSessionWebSocketTask` networking and MapKit UI are thin framework-bound layers, manually verified (same split Android used: pure logic tested, framework glue verified by running the app). No Xcode project file — built via Swift Package Manager (`swift build`/`swift test`) and packaged into a `.app`/`.dmg` by a small shell script, avoiding the complexity and fragility of hand-authoring a `.pbxproj`.

**Tech Stack:** Swift 5.9+ (Swift Package Manager), SwiftUI (`MenuBarExtra`), MapKit (`Map`, `MapReader`, `MKLocalSearch`), `URLSessionWebSocketTask` — all system frameworks, no third-party dependencies. XCTest for unit tests. `hdiutil` for DMG creation (macOS built-in).

**Spec:** [docs/superpowers/specs/2026-08-25-mock-location-toolkit-design.md](../specs/2026-08-25-mock-location-toolkit-design.md) — see "2. macOS app" section.

## Global Constraints

- Manual pairing entry only for v1 (IP, port, token — three fields). No camera-based QR scanning.
- No persistence of pairing info across launches for v1 — re-enter each launch.
- On coordinate change (map tap, drag, or address search result), send `{token, lat, lng}` immediately over WebSocket.
- No Play-Store-equivalent distribution — unsigned `.dmg` (no paid Apple Developer account), Gatekeeper right-click-to-open expected on first launch. No notarization.
- No new package dependencies — `URLSessionWebSocketTask`, MapKit, SwiftUI are all system frameworks.
- **Ruling (deviates from the macOS-13-floor discussed in chat):** deployment target is **macOS 14.0**, not 13.0. `MenuBarExtra` only needs 13+, but the interactive `Map(position:)` + `MapReader` tap-to-place API this plan uses needs 14+. Bumping the floor to 14 (released 2023, a reasonable minimum for a personal dev tool in 2026) avoids the legacy `Map(coordinateRegion:annotationItems:)` API, which doesn't cleanly expose tap coordinates. Cost if wrong: nothing — this is a solo tool, the only user is whoever builds it, and Xcode 26.6 on this machine only ships SDKs new enough that 14+ is not a real constraint in practice.
- Package name `WhereMockLocationMac`, bundle id `com.tpgusgh.wheremocklocationmac`, project lives under `mac/` in the repo root.
- Protocol contract (already shipped and fixed on the Android side — do not renegotiate): send `{"token": "...", "lat": <double>, "lng": <double>}`, expect back `{"status": "ok"|"error", "message": "..."}`.

---

### Task 1: Package scaffold

**Files:**
- Create: `mac/Package.swift`
- Create: `mac/Sources/WhereMockLocationMac/WhereMockLocationMacApp.swift` (minimal placeholder, filled in by Task 6)

**Interfaces:**
- Produces: a buildable Swift package (`swift build` succeeds) that later tasks add sources/tests to. Later Swift files live under `mac/Sources/WhereMockLocationMac/`, tests under `mac/Tests/WhereMockLocationMacTests/`.

- [ ] **Step 1: Create `mac/Package.swift`**

```swift
// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "WhereMockLocationMac",
    platforms: [.macOS(.v14)],
    targets: [
        .executableTarget(
            name: "WhereMockLocationMac",
            path: "Sources/WhereMockLocationMac"
        ),
        .testTarget(
            name: "WhereMockLocationMacTests",
            dependencies: ["WhereMockLocationMac"],
            path: "Tests/WhereMockLocationMacTests"
        )
    ]
)
```

- [ ] **Step 2: Create a placeholder app entry point at `mac/Sources/WhereMockLocationMac/WhereMockLocationMacApp.swift`**

```swift
import SwiftUI

@main
struct WhereMockLocationMacApp: App {
    var body: some Scene {
        MenuBarExtra("Where Mock Location", systemImage: "location.fill") {
            Text("Where Mock Location")
                .padding()
        }
    }
}
```

- [ ] **Step 3: Verify the package builds**

Run: `cd mac && swift build`
Expected: build succeeds with no errors (`Compiling...` / `Build complete!`)

- [ ] **Step 4: Commit**

```bash
git add mac/
git commit -m "chore: scaffold macOS Swift package"
```

---

### Task 2: Location update message encoding/decoding

**Files:**
- Create: `mac/Sources/WhereMockLocationMac/LocationUpdateEncoder.swift`
- Test: `mac/Tests/WhereMockLocationMacTests/LocationUpdateEncoderTests.swift`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `enum PairingResponseStatus: Equatable { case ok(message: String); case error(message: String) }`, `enum LocationUpdateEncoder { static func encode(token: String, lat: Double, lng: Double) -> String; static func decodeResponse(_ json: String) -> PairingResponseStatus? }` — used by Task 4 (`PairingConnection`).

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import WhereMockLocationMac

final class LocationUpdateEncoderTests: XCTestCase {
    func testEncodeProducesValidJSONWithCorrectFields() throws {
        let json = LocationUpdateEncoder.encode(token: "abc123", lat: 37.5665, lng: 126.9780)
        let data = try XCTUnwrap(json.data(using: .utf8))
        let obj = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(obj["token"] as? String, "abc123")
        XCTAssertEqual(obj["lat"] as? Double, 37.5665)
        XCTAssertEqual(obj["lng"] as? Double, 126.9780)
    }

    func testDecodeResponseParsesOkStatus() {
        let result = LocationUpdateEncoder.decodeResponse(#"{"status":"ok","message":"location updated"}"#)
        XCTAssertEqual(result, .ok(message: "location updated"))
    }

    func testDecodeResponseParsesErrorStatus() {
        let result = LocationUpdateEncoder.decodeResponse(#"{"status":"error","message":"invalid token"}"#)
        XCTAssertEqual(result, .error(message: "invalid token"))
    }

    func testDecodeResponseReturnsNilForMalformedJSON() {
        XCTAssertNil(LocationUpdateEncoder.decodeResponse("not json"))
    }

    func testDecodeResponseReturnsNilWhenFieldsMissing() {
        XCTAssertNil(LocationUpdateEncoder.decodeResponse(#"{"status":"ok"}"#))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd mac && swift test --filter LocationUpdateEncoderTests`
Expected: FAIL — `LocationUpdateEncoder`/`PairingResponseStatus` unresolved

- [ ] **Step 3: Implement `LocationUpdateEncoder`**

```swift
import Foundation

enum PairingResponseStatus: Equatable {
    case ok(message: String)
    case error(message: String)
}

enum LocationUpdateEncoder {
    static func encode(token: String, lat: Double, lng: Double) -> String {
        let payload: [String: Any] = ["token": token, "lat": lat, "lng": lng]
        let data = (try? JSONSerialization.data(withJSONObject: payload)) ?? Data()
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func decodeResponse(_ json: String) -> PairingResponseStatus? {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let status = obj["status"] as? String,
              let message = obj["message"] as? String
        else {
            return nil
        }
        return status == "ok" ? .ok(message: message) : .error(message: message)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mac && swift test --filter LocationUpdateEncoderTests`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add mac/Sources/WhereMockLocationMac/LocationUpdateEncoder.swift mac/Tests/WhereMockLocationMacTests/LocationUpdateEncoderTests.swift
git commit -m "feat: add location update message encoding and response decoding"
```

---

### Task 3: Pairing target validation

**Files:**
- Create: `mac/Sources/WhereMockLocationMac/PairingTarget.swift`
- Test: `mac/Tests/WhereMockLocationMacTests/PairingTargetTests.swift`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `struct PairingTarget: Equatable { var ip: String; var port: String; var token: String; var isComplete: Bool { get }; var url: URL? { get } }` — used by Task 4 (`PairingConnection`) and Task 6 (`ContentView`).

- [ ] **Step 1: Write the failing tests**

```swift
import XCTest
@testable import WhereMockLocationMac

final class PairingTargetTests: XCTestCase {
    func testIsCompleteTrueWhenAllFieldsPresentAndPortNumeric() {
        let target = PairingTarget(ip: "192.168.1.5", port: "8765", token: "abc")
        XCTAssertTrue(target.isComplete)
    }

    func testIsCompleteFalseWhenIPBlank() {
        let target = PairingTarget(ip: "  ", port: "8765", token: "abc")
        XCTAssertFalse(target.isComplete)
    }

    func testIsCompleteFalseWhenPortNotNumeric() {
        let target = PairingTarget(ip: "192.168.1.5", port: "abc", token: "abc")
        XCTAssertFalse(target.isComplete)
    }

    func testIsCompleteFalseWhenTokenBlank() {
        let target = PairingTarget(ip: "192.168.1.5", port: "8765", token: "")
        XCTAssertFalse(target.isComplete)
    }

    func testURLConstructsWebSocketURLWhenComplete() {
        let target = PairingTarget(ip: "192.168.1.5", port: "8765", token: "abc")
        XCTAssertEqual(target.url?.absoluteString, "ws://192.168.1.5:8765")
    }

    func testURLNilWhenIncomplete() {
        let target = PairingTarget(ip: "", port: "8765", token: "abc")
        XCTAssertNil(target.url)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd mac && swift test --filter PairingTargetTests`
Expected: FAIL — `PairingTarget` unresolved

- [ ] **Step 3: Implement `PairingTarget`**

```swift
import Foundation

struct PairingTarget: Equatable {
    var ip: String
    var port: String
    var token: String

    var isComplete: Bool {
        !ip.trimmingCharacters(in: .whitespaces).isEmpty
            && Int(port) != nil
            && !token.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var url: URL? {
        guard isComplete else { return nil }
        return URL(string: "ws://\(ip):\(port)")
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd mac && swift test --filter PairingTargetTests`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add mac/Sources/WhereMockLocationMac/PairingTarget.swift mac/Tests/WhereMockLocationMacTests/PairingTargetTests.swift
git commit -m "feat: add pairing target validation and WebSocket URL construction"
```

---

### Task 4: WebSocket pairing connection

**Files:**
- Create: `mac/Sources/WhereMockLocationMac/PairingConnection.swift`

**Interfaces:**
- Consumes: `LocationUpdateEncoder.encode`/`decodeResponse` (Task 2), `PairingTarget` (Task 3).
- Produces: `enum PairingConnectionError: Error { case invalidTarget; case malformedResponse }`, `final class PairingConnection { func send(target: PairingTarget, token: String, lat: Double, lng: Double, completion: @escaping (Result<PairingResponseStatus, Error>) -> Void) }` — used by Task 6 (`ContentView`).

No automated test for this task — `URLSessionWebSocketTask` needs a real network round-trip against a real server to test meaningfully (this mirrors the Android plan's treatment of framework-bound networking code: pure logic tested, transport glue manually verified). Verified in Task 8 against the already-shipped Android app.

- [ ] **Step 1: Implement `PairingConnection`**

```swift
import Foundation

enum PairingConnectionError: Error {
    case invalidTarget
    case malformedResponse
}

final class PairingConnection {
    private let session = URLSession(configuration: .default)

    func send(
        target: PairingTarget,
        token: String,
        lat: Double,
        lng: Double,
        completion: @escaping (Result<PairingResponseStatus, Error>) -> Void
    ) {
        guard let url = target.url else {
            completion(.failure(PairingConnectionError.invalidTarget))
            return
        }

        let task = session.webSocketTask(with: url)
        task.resume()

        let finish: (Result<PairingResponseStatus, Error>) -> Void = { result in
            task.cancel(with: .normalClosure, reason: nil)
            completion(result)
        }

        let message = URLSessionWebSocketTask.Message.string(
            LocationUpdateEncoder.encode(token: token, lat: lat, lng: lng)
        )
        task.send(message) { error in
            if let error = error {
                finish(.failure(error))
                return
            }
            task.receive { result in
                switch result {
                case .success(let message):
                    switch message {
                    case .string(let text):
                        if let status = LocationUpdateEncoder.decodeResponse(text) {
                            finish(.success(status))
                        } else {
                            finish(.failure(PairingConnectionError.malformedResponse))
                        }
                    case .data:
                        finish(.failure(PairingConnectionError.malformedResponse))
                    @unknown default:
                        finish(.failure(PairingConnectionError.malformedResponse))
                    }
                case .failure(let error):
                    finish(.failure(error))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the package still builds**

Run: `cd mac && swift build`
Expected: build succeeds

- [ ] **Step 3: Commit**

```bash
git add mac/Sources/WhereMockLocationMac/PairingConnection.swift
git commit -m "feat: add WebSocket pairing connection to send location updates"
```

---

### Task 5: Address search

**Files:**
- Create: `mac/Sources/WhereMockLocationMac/LocationSearch.swift`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `enum LocationSearch { static func search(query: String, completion: @escaping (CLLocationCoordinate2D?) -> Void) }` — used by Task 6 (`ContentView`).

No automated test — `MKLocalSearch` calls Apple's live map-search service over the network; not meaningfully unit-testable, verified manually in Task 8 by searching a real address.

- [ ] **Step 1: Implement `LocationSearch`**

```swift
import MapKit

enum LocationSearch {
    static func search(query: String, completion: @escaping (CLLocationCoordinate2D?) -> Void) {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            completion(nil)
            return
        }
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query
        MKLocalSearch(request: request).start { response, _ in
            completion(response?.mapItems.first?.placemark.coordinate)
        }
    }
}
```

- [ ] **Step 2: Verify the package still builds**

Run: `cd mac && swift build`
Expected: build succeeds

- [ ] **Step 3: Commit**

```bash
git add mac/Sources/WhereMockLocationMac/LocationSearch.swift
git commit -m "feat: add address search via MKLocalSearch"
```

---

### Task 6: Main UI — map picker, search, pairing fields

**Files:**
- Modify: `mac/Sources/WhereMockLocationMac/WhereMockLocationMacApp.swift` (point at the real `ContentView` instead of the Task 1 placeholder)
- Create: `mac/Sources/WhereMockLocationMac/ContentView.swift`

**Interfaces:**
- Consumes: `PairingTarget` (Task 3), `PairingConnection`/`PairingConnectionError` (Task 4), `LocationSearch` (Task 5), `PairingResponseStatus` (Task 2).
- Produces: the running app — nothing further consumes this.

No automated test — this is the UI wiring layer, verified manually in Task 8.

- [ ] **Step 1: Replace `WhereMockLocationMacApp.swift`**

```swift
import SwiftUI

@main
struct WhereMockLocationMacApp: App {
    var body: some Scene {
        MenuBarExtra("Where Mock Location", systemImage: "location.fill") {
            ContentView()
        }
        .menuBarExtraStyle(.window)
    }
}
```

- [ ] **Step 2: Create `ContentView.swift`**

```swift
import SwiftUI
import MapKit

struct ContentView: View {
    @State private var cameraPosition = MapCameraPosition.region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780),
            span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
        )
    )
    @State private var pin = CLLocationCoordinate2D(latitude: 37.5665, longitude: 126.9780)
    @State private var searchText = ""
    @State private var ip = ""
    @State private var port = "8765"
    @State private var token = ""
    @State private var statusMessage = ""

    private let connection = PairingConnection()

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                TextField("Search address", text: $searchText)
                Button("Search") { search() }
            }

            MapReader { proxy in
                Map(position: $cameraPosition) {
                    Marker("Target", coordinate: pin)
                }
                .onTapGesture { screenPoint in
                    if let coordinate = proxy.convert(screenPoint, from: .local) {
                        pin = coordinate
                        sendUpdate()
                    }
                }
            }
            .frame(width: 360, height: 260)

            GroupBox("Pairing") {
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Phone IP", text: $ip)
                    TextField("Port", text: $port)
                    TextField("Token", text: $token)
                }
            }

            Text("Lat: \(pin.latitude, specifier: "%.5f")  Lng: \(pin.longitude, specifier: "%.5f")")
                .font(.caption)
                .foregroundStyle(.secondary)

            if !statusMessage.isEmpty {
                Text(statusMessage)
                    .font(.caption)
            }
        }
        .padding()
        .frame(width: 400)
    }

    private func search() {
        LocationSearch.search(query: searchText) { coordinate in
            DispatchQueue.main.async {
                guard let coordinate else {
                    statusMessage = "No results for \"\(searchText)\""
                    return
                }
                pin = coordinate
                cameraPosition = .region(
                    MKCoordinateRegion(
                        center: coordinate,
                        span: MKCoordinateSpan(latitudeDelta: 0.05, longitudeDelta: 0.05)
                    )
                )
                sendUpdate()
            }
        }
    }

    private func sendUpdate() {
        let target = PairingTarget(ip: ip, port: port, token: token)
        guard target.isComplete else {
            statusMessage = "Enter IP, port, and token first"
            return
        }
        connection.send(target: target, token: token, lat: pin.latitude, lng: pin.longitude) { result in
            DispatchQueue.main.async {
                switch result {
                case .success(.ok(let message)):
                    statusMessage = "Sent: \(message)"
                case .success(.error(let message)):
                    statusMessage = "Rejected: \(message)"
                case .failure(let error):
                    statusMessage = "Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify the package builds**

Run: `cd mac && swift build`
Expected: build succeeds. If a MapKit API signature in this step doesn't match your toolchain exactly (e.g. `MapReader`/`MapCameraPosition`/`proxy.convert`), consult the compiler error and Quick Help for the correct signature on this machine's SDK, adjust minimally to compile while preserving the same behavior (tap on map → convert screen point to coordinate → update `pin` → call `sendUpdate()`), and note the deviation in your report.

- [ ] **Step 4: Commit**

```bash
git add mac/Sources/WhereMockLocationMac/WhereMockLocationMacApp.swift mac/Sources/WhereMockLocationMac/ContentView.swift
git commit -m "feat: wire up main UI with map picker, address search, and pairing fields"
```

---

### Task 7: App bundle packaging and DMG build script

**Files:**
- Create: `mac/Resources/Info.plist`
- Create: `mac/scripts/build-dmg.sh`

**Interfaces:**
- Consumes: the built binary from `swift build -c release` (Tasks 1-6).
- Produces: `mac/build/Where Mock Location.dmg` when the script runs — nothing further in this plan consumes this; it's the distributable.

- [ ] **Step 1: Create `mac/Resources/Info.plist`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>
    <string>Where Mock Location</string>
    <key>CFBundleDisplayName</key>
    <string>Where Mock Location</string>
    <key>CFBundleIdentifier</key>
    <string>com.tpgusgh.wheremocklocationmac</string>
    <key>CFBundleVersion</key>
    <string>1.0</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleExecutable</key>
    <string>WhereMockLocationMac</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>LSUIElement</key>
    <true/>
    <key>LSMinimumSystemVersion</key>
    <string>14.0</string>
</dict>
</plist>
```

- [ ] **Step 2: Create `mac/scripts/build-dmg.sh`**

```bash
#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

APP_NAME="Where Mock Location"
BUILD_DIR=".build/release"
APP_BUNDLE="build/${APP_NAME}.app"

swift build -c release

rm -rf build
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"

cp "${BUILD_DIR}/WhereMockLocationMac" "${APP_BUNDLE}/Contents/MacOS/WhereMockLocationMac"
cp "Resources/Info.plist" "${APP_BUNDLE}/Contents/Info.plist"

hdiutil create -volname "${APP_NAME}" -srcfolder "${APP_BUNDLE}" -ov -format UDZO "build/${APP_NAME}.dmg"

echo "Built build/${APP_NAME}.dmg"
```

- [ ] **Step 3: Make the script executable and run it**

Run: `chmod +x mac/scripts/build-dmg.sh && cd mac && ./scripts/build-dmg.sh`
Expected: script completes and prints `Built build/Where Mock Location.dmg`; the file `mac/build/Where Mock Location.dmg` exists.

- [ ] **Step 4: Commit**

```bash
git add mac/Resources/Info.plist mac/scripts/build-dmg.sh mac/.gitignore
git commit -m "feat: add app bundle packaging and DMG build script"
```

(If `mac/build/` and `mac/.build/` aren't already git-ignored, add a `mac/.gitignore` with `.build/` and `build/` before committing — the DMG and compiled binaries should never be tracked, same lesson as the Android plan's Task 1.)

---

### Task 8: End-to-end verification and README

**Files:**
- Create: `mac/README.md`

**Interfaces:**
- Consumes: the full app from Tasks 1–7.
- Produces: nothing further — this is the final task in this plan.

- [ ] **Step 1: Run the full test suite**

Run: `cd mac && swift test`
Expected: all tests pass (5 `LocationUpdateEncoderTests` + 6 `PairingTargetTests` = 11 tests)

- [ ] **Step 2: Smoke-test that the built app launches without crashing**

Run: `cd mac && swift build -c release && (.build/release/WhereMockLocationMac &) && sleep 3 && pgrep -f WhereMockLocationMac && pkill -f WhereMockLocationMac`
Expected: `pgrep` prints a PID (process is still running 3 seconds after launch, i.e. it didn't crash on startup), then `pkill` cleans it up.

- [ ] **Step 3: Build the DMG**

Run: `cd mac && ./scripts/build-dmg.sh`
Expected: `Built build/Where Mock Location.dmg`, and the file exists.

- [ ] **Step 4: Write `mac/README.md`**

```markdown
# where-mock-location-mac

Menu-bar companion to the Android `where-mock-location` app. Pick a
location on the map (or search an address) and it's pushed live to a
paired phone over the local network — no ADB, no USB.

## Build & run (development)

```
cd mac
swift run
```

## Build the distributable app

```
cd mac
./scripts/build-dmg.sh
```

Produces `mac/build/Where Mock Location.dmg`. It's unsigned (no paid
Apple Developer account) — on first launch, Gatekeeper will refuse to
open it normally; right-click the app → Open, then confirm.

## Pairing with the phone

1. On the Android app, tap "Start mocking" (or just open the app) —
   it shows an IP, port, and token as text (and as a QR code, not yet
   used by this Mac app).
2. In this Mac app's menu-bar popover, type that IP, port, and token
   into the Pairing fields.
3. Tap the map, drag the pin, or search an address — each change
   sends the new coordinates to the phone immediately. The status
   line under the map shows whether the phone applied it.

## Notes

- The phone and Mac must be on the same local network (same Wi-Fi).
- Pairing fields are not saved between launches (v1) — re-enter them
  each time you reopen the app.
- Manual entry only in v1; no camera QR scanning yet.
```

- [ ] **Step 5: Commit**

```bash
git add mac/README.md
git commit -m "docs: add macOS app build, packaging, and pairing instructions"
```

**On-device verification not runnable in a sandboxed build environment** (needs a real Android phone on the same Wi-Fi as the Mac): after Task 8 lands, pair against a running instance of the Android app per this README and confirm the phone's location actually moves when the map pin moves.
