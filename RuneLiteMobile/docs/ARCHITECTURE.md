# RuneLiteMobile Architecture

This document expands on the high-level vision in the root README.

## Component Overview

### PC / RuneLite Plugin Side
- **RuneLiteMobilePlugin**: Entry point, dependency injection, lifecycle.
- **RemoteSessionManager**: Embedded server, client connections, frame streaming, input routing.
- **InputEvent** (model) + **InputInjector** (future): Translate phone events into RuneLite mouse/keyboard/camera actions.
- **MobileUIAdapter**: Hide desktop UI (sidebar, top bar), apply rescaling when mobile session active.
- **MobileConfigBridge** (future): Expose RuneLite + plugin configs to Android for live editing.
- **RuneLiteMobileConfig**: User-configurable options (enable remote, scale, port).

### Phone / Android App Side
- **MainActivity**: Launcher, hosts renderer and gesture layer.
- **GameRendererView**: Receives frames, renders game view (SurfaceView based).
- **TouchInputHandler**: GestureDetector + ScaleGestureDetector → creates InputEvent → sends via ConnectionManager.
- **ConnectionManager**: WebSocket client to plugin server, message handling.
- **Settings UI** (future, Compose recommended): Mobile-friendly panels that call into ConnectionManager to read/write config.

## Data Flow (MVP)

1. Android connects → RemoteSessionManager accepts
2. Plugin notifies MobileUIAdapter → hides desktop chrome + applies scale
3. Game frames captured on PC → streamed to Android → GameRendererView draws them
4. User touches screen → TouchInputHandler → ConnectionManager → RemoteSessionManager → InputInjector → RuneLite client
5. Settings changes on phone → sent to plugin → applied live

## Key Challenges & Notes
- Low latency screen streaming on LAN (start with simple MJPEG, improve later)
- Accurate input mapping so camera control feels natural
- Safe UI hiding/restoration without breaking normal RuneLite use
- Security: LAN-only by default

## Next Implementation Priorities
- RemoteSessionManager + basic WebSocket
- TouchInputHandler gesture recognition + event sending
- MobileUIAdapter reflection-based hiding
- Frame capture + streaming (even basic)

This architecture directly supports all 4 core goals from the project instructions.