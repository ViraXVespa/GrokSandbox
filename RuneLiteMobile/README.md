# RuneLiteMobile

A hybrid RuneLite plugin + native Android app for seamless Old School RuneScape gameplay on mobile devices.

Run RuneLite on your PC. Connect from your phone with a pseudo remote-desktop-style app that feels like RuneLite was built for mobile from the ground up.

## Vision

Bring the full power of RuneLite (overlays, plugins, configs, XP trackers, etc.) to your phone without compromises. Local-network "cloud gaming" tailored specifically for OSRS + RuneLite.

## Core Goals

1. Play the game **without** the RuneLite sidebar and topbar visible on the mobile device.
2. Use **natural touch input** to gracefully control the game:
   - Swipes to rotate the camera
   - Pinch gestures to zoom in/out
   - Intuitive taps, drags, and long-presses
3. Access and control **all RuneLite settings** via a clean, mobile-friendly UI.
4. **Rescale** the UI for comfortable use on phones and tablets of varying sizes and DPIs.

## User Flow

1. User boots RuneLite on their PC (RuneLiteMobile plugin loads remote/server functionality).
2. User opens the associated Android app on their phone.
3. App connects to the PC instance over the local network.
4. User plays OSRS smoothly on mobile with full RuneLite features, touch-optimized controls, and easy access to settings when needed.

## High-Level Architecture

**PC Side (RuneLite Plugin)**
- Remote session / server component (WebSocket or custom low-latency protocol)
- Screen/frame streaming to phone
- Input event receiver & injector (touch → mouse/keyboard/camera actions)
- UI hider & rescaler (hide desktop panels when mobile session active)
- Mobile config bridge (expose RuneLite configs for phone UI)

**Phone Side (Android App)**
- Connection manager (LAN discovery or manual IP + auth)
- Touch gesture handler (swipe/pinch/tap mapping)
- Frame renderer (low-latency display of game view)
- Mobile settings UI (touch-friendly, live sync with RuneLite)
- Performance & overlay controls

## Planned Project Structure

```
RuneLiteMobile/
├── README.md
├── .gitignore
├── plugin/               # RuneLite plugin (Java/Gradle)
│   ├── runelite-plugin.properties
│   └── src/main/java/com/vxv/runelitemobile/
│       └── RuneLiteMobilePlugin.java  (and managers)
└── android/              # Android client app (Kotlin)
    └── app/src/main/
        ├── AndroidManifest.xml
        └── java/com/vxv/runelitemobile/
            └── MainActivity.kt (and gesture/render/settings packages)
```

Full Gradle wrappers, build configs, and detailed source will be added iteratively.

## Next Steps

This is the initial foundation commit. We'll expand with:
- Plugin stub + embedded server start
- Android skeleton with gesture detection
- Communication protocol definition
- UI hiding & input injection proof-of-concept

Tell me what to tackle first (e.g. "add the plugin Java stub", "scaffold Android build files", "start WebSocket server code", or any tweaks to this README).

## Status

**Initialized** — Vision and structure documented. Ready to build.

---

*Project rooted in GrokSandbox per your instructions. Atomic GitHub-first workflow.*