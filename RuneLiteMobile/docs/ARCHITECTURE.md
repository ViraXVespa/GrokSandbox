# RuneLiteMobile Architecture

## Overview

RuneLiteMobile allows playing OSRS on a phone while running the full RuneLite client + plugins on a PC, connected over local network.

## Core Goals

1. Hide desktop UI elements when on mobile
2. Natural touch input (swipes, pinch, taps)
3. Mobile-friendly settings access with live sync
4. UI rescaling for different devices

## Component Breakdown

### Plugin (PC)
- **RuneLiteMobilePlugin**: Wires everything and manages lifecycle
- **RemoteSessionManager**: WebSocket server, client management, frame streaming
- **InputInjector**: Injects phone input into RuneLite
- **MobileUIAdapter**: Hides/shows UI elements + scaling
- **FrameCapture**: Captures game frames (real + test fallback)

### Android App
- **MainActivity**: Connection UI + hosts renderer
- **ConnectionManager**: WebSocket client + state
- **TouchInputHandler**: Gesture → protocol messages
- **GameRendererView**: Receives and displays frames

## Current Implementation Status

**Working / Functional**
- End-to-end frame streaming (test frames + real capture attempt)
- Touch input with visual feedback on frames
- UI hiding when client connects
- Basic camera control direction logic
- Settings sync message types + stubs

**In Progress / Partial**
- Real canvas capture (attempts real + falls back)
- Camera control quality
- Settings sync flow

**Not Started / Future**
- Efficient real streaming (compression, deltas)
- Full mobile settings UI
- Auto-discovery

## Protocol

Current simple string protocol:
`TYPE:x,y,dx,dy,scale`

Types: TAP, LONG_PRESS, SWIPE_CAMERA, PINCH_SCALE, SETTINGS_REQUEST, CONFIG_UPDATE, FRAME

## Next Priorities

1. Make real canvas capture stable and efficient
2. Improve camera control feel
3. Build out settings sync + mobile UI
4. Add reconnection resilience

This architecture directly supports the four original project goals.