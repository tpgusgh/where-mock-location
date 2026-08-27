# where-mock-location-mac

Menu-bar companion to the Android `where-mock-location` app. Pick a
location on the map (or search an address) and it's pushed live to a
paired phone over the local network — no ADB, no USB.

## Build & run (development)

```
cd mac
swift run
```

Quick iteration only. `swift run` launches a bare binary with no app
bundle, so it has no bundle identifier — macOS's local-network
permission prompt (needed for this app's whole purpose) may not grant
correctly under it. For anything involving actual pairing, build and
launch the packaged app instead (below).

## Build the distributable app

```
cd mac
./scripts/build-dmg.sh
```

Produces `mac/build/Where Mock Location.dmg`, ad-hoc signed (no paid
Apple Developer account, so not notarized) — on first launch,
Gatekeeper will refuse to open it normally; right-click the app →
Open, then confirm. macOS will also prompt for local-network
permission on first connection attempt — allow it, or pairing will
fail silently.

## Pairing with the phone

1. Open the Android app — it immediately shows an IP, port, and token
   as text (and as a QR code, not yet used by this Mac app).
2. On the phone, type any starting coordinates into its own lat/lng
   fields and tap "Start mocking." Until mocking is actually on,
   the phone rejects every update this Mac app sends with
   `"mocking not active"` — pairing looks broken if you skip this.
3. In this Mac app's menu-bar popover, type the phone's IP, port, and
   token into the Pairing fields.
4. Tap the map or search an address — each change sends the new
   coordinates to the phone immediately. The status line under the
   map shows whether the phone applied it.

## Notes

- The phone and Mac must be on the same local network (same Wi-Fi).
- Pairing fields are not saved between launches (v1) — re-enter them
  each time you reopen the app.
- Manual entry only in v1; no camera QR scanning yet.
- Dragging the pin to reposition isn't implemented in v1 — tap the
  map to place it anywhere instead (functionally equivalent).
