# RuneLiteMobile Architecture

## Overview

RuneLiteMobile is a hybrid system consisting of a RuneLite plugin (running on PC) and a native Android app. The goal is to allow playing Old School RuneScape on a phone with full RuneLite features using natural touch controls, while the heavy lifting (game client + plugins) runs on a more powerful PC.

It works as a local-network "cloud gaming" style setup specifically tailored for OSRS + RuneLite.

## Core Goals

1. Hide desktop RuneLite UI elements (sidebar, top bar) when playing on mobile.
2. Support natural touch input (swipes for camera, pinch for zoom, taps, etc.).
3. Provide a mobile-friendly way to access and change RuneLite settings.
4. Support UI rescaling for different phone/tablet screen sizes.

## High-Level Components

### PC Side (RuneLite Plugin)

- **RuneLiteMobilePlugin**: Main entry point. Wires everything together and manages lifecycle.
- **RemoteSessionManager**: WebSocket server that handles Android client connections, message routing, and frame streaming.
- **InputInjector**: Receives input events from the phone and injects them into the RuneLite client (clicks, camera control, etc.).
- **MobileUIAdapter**: Handles hiding/showing desktop UI elements and applying scale factors when a mobile session is active.
- **FrameCapture**: Captures the current game view for streaming to the phone (currently uses test frames for development).

### Phone Side (Android App)

- **MainActivity**: Main screen with connection controls and hosts the game renderer.
- **ConnectionManager**: Manages the WebSocket connection to the PC plugin.
- **TouchInputHandler**: Converts Android touch gestures into protocol messages.
- **GameRendererView**: Receives frames from the PC and renders them.
- **MobileSettingsActivity**: (Future) Mobile-friendly settings UI with live sync.

## Current Protocol (MVP)

Simple string-based protocol for now:

`TYPE:x,y,dx,dy,scale`

Supported types include:
- `TAP`, `LONG_PRESS`, `SWIPE_CAMERA`, `PINCH_SCALE`, etc.
- `FRAME` for binary frame data

## Current Test Capabilities (as of latest updates)

- Connect from Android app to plugin via IP
- See test frames (colored rectangles + timestamp) streamed from PC
- Tap/swipe/pinch on phone and see visual feedback appear on the streamed frames
- UI hiding (sidebar/top bar) triggers when a client connects
- Basic connection status and controls on Android

This allows end-to-end testing of the core loop without needing a full game client integration yet.

## Next Priorities

1. Replace test frames with real game canvas capture
2. Improve input injection quality (especially camera control)
3. Add proper settings/config sync between phone and plugin
4. Make frame streaming more efficient (compression, delta frames, etc.)
5. Add auto-discovery / easier connection flow

## Key Design Decisions

- Started with a simple string protocol for fast iteration. Will likely move to a more structured format (JSON or custom binary) later.
- Using reflection in MobileUIAdapter because RuneLite's UI components are not fully exposed via the public API.
- Keeping the Android side relatively lightweight. Most logic lives in the plugin.