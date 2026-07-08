# RuneLiteMobile

A hybrid RuneLite plugin + native Android app for playing Old School RuneScape on mobile with full RuneLite features.

## Vision

Run RuneLite on your PC. Connect from your phone over local network. Get natural touch controls, hidden desktop UI, mobile settings access, and proper UI scaling.

## Current State (Polished Initial Version)

The core foundation is complete and the main loops are functional for testing:

### Working Now
- Android app can connect to the plugin via IP
- Test frames (or real canvas capture attempts) are streamed from PC to phone
- Touch input on phone produces visual feedback on the streamed frames
- Sidebar and top bar can be hidden when a mobile session starts
- Basic camera control direction logic exists
- Settings sync communication foundation is in place
- Connection status and basic controls exist on Android

### Not Yet Production Ready
- Real high-quality game canvas capture + efficient streaming is still in progress
- Input injection (especially camera) needs more work for natural feel
- Full live settings sync UI is not yet built

## Project Structure

```
RuneLiteMobile/
├── README.md
├── CONTRIBUTING.md
├── docs/ARCHITECTURE.md
├── plugin/          # RuneLite plugin (Java/Gradle)
└── android/         # Android app (Kotlin)
```

## Next Major Milestones

1. Stable real game canvas capture + efficient frame streaming
2. High-quality camera control via touch
3. Functional mobile settings UI with live sync
4. Better auto-discovery / connection experience

See `docs/ARCHITECTURE.md` for detailed architecture and current implementation status.