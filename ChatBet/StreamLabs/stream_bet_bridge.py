#!/usr/bin/env python3
"""
OSRS Stream Bet Bridge
Receives chat messages forwarded from Streamlabs Chat Box (via custom JS)
parses betting / odds / chatbet commands, maintains state, and exposes
endpoints for the RuneLite ChatBet plugin to poll.

Run with: python stream_bet_bridge.py --debug
"""

from fastapi import FastAPI, Query, Body
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict
from datetime import datetime
import uvicorn
import argparse
import os
from typing import Optional, List, Dict, Any
from collections import deque
import re

app = FastAPI(title="OSRS Stream Bet Bridge - ChatBet Edition")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Module-level debug flag
DEBUG = os.getenv("DEBUG", "false").lower() in ("1", "true", "yes", "on")

# In-memory state for communication with RuneLite plugin
recent_messages: deque = deque(maxlen=100)
active_request: Optional[Dict[str, Any]] = None
game_state: Dict[str, Any] = {}
last_request_timestamp: int = 0

def log_message(platform: str, user: str, message: str, timestamp: Optional[int] = None):
    """Pretty print only when --debug is used"""
    if not DEBUG:
        return

    if timestamp:
        try:
            time_str = datetime.fromtimestamp(timestamp / 1000).strftime("%H:%M:%S")
        except Exception:
            time_str = "??:??:??"
    else:
        time_str = datetime.now().strftime("%H:%M:%S")

    print(f"[{platform}]{time_str} - {user} - {message}")


def parse_chat_command(message: str) -> Optional[Dict[str, Any]]:
    """Simple command parser for !bet, !odds, !prob, !chatbet, etc.
    Extend this function with more sophisticated parsing / NLP as needed.
    """
    if not message or not message.strip().startswith("!"):
        return None

    # Clean and split
    cleaned = message.strip()
    parts = cleaned[1:].split(maxsplit=3)  # command + up to 3 args
    if not parts:
        return None

    cmd = parts[0].lower()
    args = parts[1:] if len(parts) > 1 else []

    # Normalize common aliases
    if cmd in ("prob", "odds", "chance", "calculate"):
        cmd = "odds"
    elif cmd in ("bet", "wager"):
        cmd = "bet"

    return {
        "command": cmd,
        "args": args,
        "raw": cleaned
    }


class ChatMessage(BaseModel):
    platform: str = "unknown"
    user: str
    message: str
    timestamp: Optional[int] = None
    raw: Optional[str] = None

    model_config = ConfigDict(extra="ignore")


class CommandRequest(BaseModel):
    user: str
    command: str
    args: List[str] = []
    timestamp: int
    raw_message: str


class BridgeState(BaseModel):
    active_request: Optional[CommandRequest] = None
    recent_messages_count: int
    last_updated: int
    game_stats: Dict[str, Any] = {}
    model_config = ConfigDict(extra="ignore")


@app.get("/ingest")
async def ingest_get(
    platform: str = Query("unknown"),
    user: str = Query(...),
    message: str = Query(...),
    timestamp: Optional[int] = Query(None)
):
    ts = timestamp or int(datetime.now().timestamp() * 1000)
    log_message(platform, user, message, ts)

    # Update state
    recent_messages.append({
        "platform": platform,
        "user": user,
        "message": message,
        "timestamp": ts
    })

    parsed = parse_chat_command(message)
    if parsed:
        global active_request, last_request_timestamp
        active_request = {
            "user": user,
            "command": parsed["command"],
            "args": parsed["args"],
            "timestamp": ts,
            "raw_message": parsed["raw"]
        }
        last_request_timestamp = ts
        if DEBUG:
            print(f"  → Parsed command: !{parsed['command']} {parsed['args']}")

    return {"status": "received", "parsed_command": parsed is not None}


@app.post("/ingest")
async def ingest_post(msg: ChatMessage):
    ts = msg.timestamp or int(datetime.now().timestamp() * 1000)
    log_message(msg.platform, msg.user, msg.message, ts)

    recent_messages.append({
        "platform": msg.platform,
        "user": msg.user,
        "message": msg.message,
        "timestamp": ts
    })

    parsed = parse_chat_command(msg.message)
    if parsed:
        global active_request, last_request_timestamp
        active_request = {
            "user": msg.user,
            "command": parsed["command"],
            "args": parsed["args"],
            "timestamp": ts,
            "raw_message": parsed["raw"]
        }
        last_request_timestamp = ts
        if DEBUG:
            print(f"  → Parsed command: !{parsed['command']} {parsed['args']}")

    return {"status": "received", "parsed_command": parsed is not None}


@app.get("/chatbet/state", response_model=BridgeState)
async def get_chatbet_state():
    """Endpoint for RuneLite plugin to poll current chat requests and game echo state."""
    global active_request
    return BridgeState(
        active_request=CommandRequest(**active_request) if active_request else None,
        recent_messages_count=len(recent_messages),
        last_updated=int(datetime.now().timestamp() * 1000),
        game_stats=game_state.copy()
    )


@app.post("/chatbet/ack")
async def ack_request(processed_timestamp: int = Body(..., embed=True)):
    """Plugin calls this after successfully processing a request to clear it (avoids re-processing)."""
    global active_request
    if active_request and active_request.get("timestamp") == processed_timestamp:
        active_request = None
        if DEBUG:
            print("  → Active request acknowledged and cleared by plugin.")
    return {"status": "ok"}


@app.post("/game/update")
async def update_game_state(stats: Dict[str, Any] = Body(...)):
    """Optional: Plugin pushes current tracking stats (attempts, successes, etcProb, etc.)
    so the bridge (or future web UI) has fresh data for dynamic odds or chat responses.
    """
    global game_state
    game_state = stats
    if DEBUG:
        print(f"  ← Game state updated from plugin: {list(stats.keys())}")
    return {"status": "updated", "received_keys": list(stats.keys())}


@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "active_request": bool(active_request),
        "game_state_keys": list(game_state.keys()),
        "messages_buffered": len(recent_messages)
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="OSRS Stream Bet Bridge")
    parser.add_argument("--debug", action="store_true", help="Enable pretty debug logging")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    if args.debug:
        os.environ["DEBUG"] = "true"
        DEBUG = True

    print(f"\n{'='*60}")
    print("Starting OSRS Stream Bet Bridge (ChatBet Edition)")
    print(f"URL: http://127.0.0.1:{args.port}")
    print(f"Debug mode: {'ENABLED' if DEBUG else 'disabled'}")
    if DEBUG:
        print("→ Messages + parsed commands will be logged")
        print("→ Plugin should poll GET /chatbet/state")
        print("→ Plugin can POST current stats to /game/update")
    print(f"{'='*60}\n")

    uvicorn.run(
        "stream_bet_bridge:app",
        host="127.0.0.1",
        port=args.port,
        access_log=False,
        log_level="warning"
    )
