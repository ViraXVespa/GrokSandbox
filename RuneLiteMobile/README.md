# RuneLiteMobile

A hybrid RuneLite plugin + native Android app that lets you play Old School RuneScape on your phone with full RuneLite power.

Run RuneLite on your PC. Connect from your phone over your local network. Get natural touch controls, hidden desktop UI, mobile-friendly settings access, and UI rescaling.

## Current Status

The initial foundation is complete and the core loop is functional for testing:

- Android app can connect to the plugin
- Test frames are streamed from PC to phone
- Touch input on phone produces visual feedback on the streamed frames
- Desktop UI elements can be hidden when a mobile session is active
- Basic connection status and controls exist on Android

This is still early development. Real game canvas capture and high-quality input injection are the next major milestones.

## Project Structure

```
RuneLiteMobile/
├── README.md
├── CONTRIBUTING.md
├── docs/
│   └── ARCHITECTURE.md
├── .gitignore
├── plugin/               # RuneLite plugin (Java)
│   ├── build.gradle
│   ├── runelite-plugin.properties
│   └── src/main/java/com/vxv/runelitemobile/
└── android/              # Android app (Kotlin)
    └── app/
```

## Getting Started (Development)

### Plugin
1. Open the `plugin/` folder in your IDE
2. Build with Gradle
3. Place the resulting jar in your RuneLite plugins folder

### Android App
1. Open the `android/` folder in Android Studio
2. Build and install the debug APK on your phone
3. Enter your PC's local IP and connect

## Next Steps

See `docs/ARCHITECTURE.md` for current capabilities and prioritized next work.