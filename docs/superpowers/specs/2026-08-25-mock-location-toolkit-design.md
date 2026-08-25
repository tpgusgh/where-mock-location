# Mock Location Toolkit — Design

Date: 2026-08-25
Status: Approved

## Purpose

Developer needs to test location-aware app behavior against different
coordinates during development. Two pieces, one system:

- **Android APK** — does the actual GPS spoofing on-device, using the
  officially supported Android mock-location-provider mechanism.
- **macOS DMG** — a remote-control companion. Macs have no GPS hardware
  and no supported system-wide location override, so it does not spoof
  its own location. Instead it's a map UI to pick coordinates and push
  them to a paired phone in real time.

Both are standalone-usable; the Android app also has its own manual
coordinate entry so it doesn't strictly require the Mac companion.

Out of scope (explicitly rejected during brainstorming): spoofing
location in third-party apps not under the developer's control, VPN/IP
based geolocation changing, macOS system-wide CoreLocation override via
SIP-disable (unsupported, unstable across OS updates).

## Components

### 1. Android app — `where-mock-location`

- `MockLocationService`: foreground `Service` that registers as a test
  provider via `LocationManager.addTestProvider` /
  `setTestProviderLocation` on the `gps` and `network` providers.
  Requires the user to enable Developer Options → "Select mock
  location app" and pick this app (standard, documented Android dev
  flow — no root).
- Foreground notification is mandatory while injecting fake location
  (transparency, matches Android's foreground-service-location policy).
- UI: manual lat/lng entry, saved presets (name + coordinates), a
  start/stop toggle. Map picker if time allows (stretch, not required
  for v1).
- `PairingServer`: a small WebSocket server (Ktor or NanoHTTPD, on the
  local network) that accepts `{lat, lng}` update messages from the Mac
  companion and forwards them into `MockLocationService`.
- Pairing/auth: app displays a QR code encoding `{ip, port, token}`.
  Mac app scans/enters it once. Every WebSocket message from the Mac
  must include the token. This is the security boundary — without it,
  anything on the same Wi-Fi could push arbitrary locations into the
  service.

### 2. macOS app — `where-mock-location-mac`

- SwiftUI menu-bar app.
- MapKit view: click/drag pin or search an address to pick coordinates.
- Pairing: scan the phone's QR (via camera or manual token entry),
  stores `{ip, port, token}` for the session.
- On coordinate change, sends `{lat, lng}` over WebSocket to the phone
  immediately.
- No persistence of paired devices beyond the current session for v1
  (re-pair each launch) — YAGNI, add device list later if it's
  actually annoying in practice.

## Protocol

Local-network WebSocket, JSON messages:

```
Mac -> Phone: { "token": "<pairing token>", "lat": 37.5665, "lng": 126.9780 }
Phone -> Mac: { "status": "ok" | "error", "message": "..." }
```

No ADB dependency — avoids requiring USB debugging on top of the mock-
location developer option that's already required.

## Build order

1. Android app first — it owns the protocol and is independently
   useful without the Mac app.
2. macOS companion second, once the WebSocket contract is stable.

## Distribution

- APK: signed with a debug/local keystore, sideloaded directly
  (no Play Store — this is a personal dev tool).
- DMG: unsigned build (no paid Apple Developer account). Users will see
  the "unidentified developer" Gatekeeper warning and need
  right-click → Open on first launch. No notarization.

## Repo / workflow

- New GitHub repo under the `tpgusgh` personal account, created via
  `gh repo create`. Default private (dev tool, not something to expose
  publicly by default — can be flipped to public later on request).
- Work proceeds in feature branches with PRs opened periodically per
  logical chunk (e.g. "android mock provider service", "android
  pairing server + QR", "mac map picker", "mac pairing client"), rather
  than one giant commit.

## Testing

- Android: unit test the coordinate parsing/pairing-token validation;
  manual verification of mock provider injection via `adb shell dumpsys
  location` or a throwaway test app reading `FusedLocationProviderClient`.
- macOS: unit test WebSocket message encoding; manual verification by
  pairing against the Android app.
