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
3. Tap the map or search an address — each change sends the new
   coordinates to the phone immediately. The status line under the
   map shows whether the phone applied it.

## Notes

- The phone and Mac must be on the same local network (same Wi-Fi).
- Pairing fields are not saved between launches (v1) — re-enter them
  each time you reopen the app.
- Manual entry only in v1; no camera QR scanning yet.
- Dragging the pin to reposition isn't implemented in v1 — tap the
  map to place it anywhere instead (functionally equivalent).
