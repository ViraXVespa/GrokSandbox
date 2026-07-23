# StreamLabs Chat Bridge (ChatBet)

Local FastAPI service that receives chat from the Streamlabs Chat Box widget and provides:

1. **Betting commands** for the RuneLite plugin (`!bet`, etc.)
2. **Presence** — who is active in chat (join on first message / rejoin, leave after idle)
3. **Engagement rewards** — small anti-gameable token credits for real chat

## Run

```powershell
cd ChatBet\StreamLabs
pip install fastapi uvicorn pydantic
python stream_bet_bridge.py --debug
```

Default: `http://127.0.0.1:8765`

## Streamlabs setup

1. Dashboard → **All Widgets** → **Chat Box**
2. Enable **Custom HTML/CSS**
3. Paste `chatbox_forward.js` into the **JS** tab (adjust `BRIDGE` if needed)
4. Keep the Chat Box browser source loaded in OBS while streaming

## Presence

| Event | How |
|-------|-----|
| **Join** | First message (or re-message after idle leave) |
| **Leave** | No messages for `PRESENCE_IDLE_SECONDS` (default **600** = 10 min) |

```
GET /chatbet/presence
```

Returns current present chatters + recent join/leave events.

This tracks **chat activity**, not silent lurkers.

## Engagement rewards (anti-game)

Quality chat can earn **~15–40 tokens** (plus a one-time first-chat bonus).

### Rejected (no reward)

- Emoji / symbol-only / sticker spam  
- Too short (`< 12` chars after cleaning) or `< 3` words  
- Low character diversity (keyboard smash)  
- ALL CAPS spam  
- Low-value phrases (`hi`, `lol`, `pog`, `gg`, `first`, …)  
- Pure commands (`!bet` …) — betting is separate  
- Duplicates / near-duplicates of your recent messages  
- Burst spam (many lines in a few seconds) → temporary mute  
- Over hourly / daily caps  

### Rate limits (defaults)

| Limit | Default |
|-------|---------|
| Cooldown between rewards | 45s |
| Max rewards / hour | 12 |
| Max rewards / day | 80 |
| First-chat bonus | +25 once |
| Idle leave | 10 minutes |

Env vars: `ENGAGE_*`, `PRESENCE_IDLE_SECONDS`, `FORWARD_CREDITS_TO_HUB`, `DISCORD_HUB_URL`, `HUB_API_KEY`.

Credits are queued for **RuneLite** (`pending_credits` on `/chatbet/state`) and optionally POSTed to the Discord hub `/api/v1/balance/credit`.

## API cheat sheet

| Method | Path | Purpose |
|--------|------|---------|
| POST/GET | `/ingest` | Chat box → bridge |
| GET | `/chatbet/state` | RuneLite poll (bets + credits + presence events) |
| POST | `/chatbet/ack` | Ack consumed bet command |
| POST | `/chatbet/credits/ack` | Ack applied engagement credits |
| GET | `/chatbet/presence` | Present chatters roster |
