# RuneLiteMobile Architecture

## Implementation Progress (as of latest big update)

**Plugin Side**
- RemoteSessionManager: WebSocket server + frame broadcast + session state management working
- InputInjector: Handles TAP, LONG_PRESS, SWIPE_CAMERA, PINCH_SCALE
- MobileUIAdapter: Reflection-based hiding of sidebar and top bar functional
- FrameCapture: Placeholder ready for real implementation
- Full wiring in RuneLiteMobilePlugin

**Android Side**
- TouchInputHandler: Real GestureDetector + ScaleGestureDetector sending protocol messages
- ConnectionManager: OkHttp WebSocket client with send/receive
- GameRendererView: SurfaceView stub ready for frame decoding
- MainActivity: Wired with touch + renderer
- MobileSettingsActivity: Stub for future Compose UI

**Protocol (MVP)**
Simple string format: `TYPE:x,y,dx,dy,scale`

The core loop (touch → send → parse → inject + UI adaptation) is now connected end-to-end in skeleton form.