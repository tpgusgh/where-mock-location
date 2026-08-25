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
