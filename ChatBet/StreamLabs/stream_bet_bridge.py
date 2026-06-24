#!/usr/bin/env python3
"""
OSRS Stream Bet Bridge
Receives chat messages forwarded from Streamlabs Chat Box (via custom JS).
Sets up interop buffer for ChatBet command routing + forwarding non-command/
non-emoji stream chat directly into RuneLite chat output.

Run with: python stream_bet_bridge.py --debug
"""

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict
from datetime import datetime
import uvicorn
import argparse
import os
from typing import Optional
from collections import deque

app = FastAPI(title="OSRS Stream Bet Bridge")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Module-level debug flag
DEBUG = os.getenv("DEBUG", "false").lower() in ("1", "true", "yes", "on")

# Buffer for recent messages (used for command interop + forwarding regular chat to RuneLite)
recent_messages: deque = deque(maxlen=100)


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


def parse_chat_command(message: str) -> Optional[dict]:
    """Detect ChatBet commands (!bet, !odds, !chatbet, etc). Returns parsed info or None for regular chat."""
    if not message or not message.strip().startswith("!"):
        return None
    cleaned = message.strip()
    parts = cleaned[1:].split(maxsplit=3)
    if not parts:
        return None
    cmd = parts[0].lower()
    if cmd in ("bet", "wager"):
        cmd = "bet"
    elif cmd in ("prob", "odds", "chance", "calculate"):
        cmd = "odds"
    return {
        "command": cmd,
        "args": parts[1:] if len(parts) > 1 else [],
        "raw": cleaned
    }


class ChatMessage(BaseModel):
    platform: str = "unknown"
    user: str
    message: str
    timestamp: Optional[int] = None
    raw: Optional[str] = None

    model_config = ConfigDict(extra="ignore")


@app.get("/ingest")
async def ingest_get(
    platform: str = Query("unknown"),
    user: str = Query(...),
    message: str = Query(...),
    timestamp: Optional[int] = Query(None)
):
    log_message(platform, user, message, timestamp)
    ts = timestamp or int(datetime.now().timestamp() * 1000)
    recent_messages.append({
        "platform": platform,
        "user": user,
        "message": message,
        "timestamp": ts
    })
    return {"status": "received"}


@app.post("/ingest")
async def ingest_post(msg: ChatMessage):
    log_message(msg.platform, msg.user, msg.message, msg.timestamp)
    ts = msg.timestamp or int(datetime.now().timestamp() * 1000)
    recent_messages.append({
        "platform": msg.platform,
        "user": msg.user,
        "message": msg.message,
        "timestamp": ts
    })
    return {"status": "received"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="OSRS Stream Bet Bridge")
    parser.add_argument("--debug", action="store_true", help="Enable pretty debug logging")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    if args.debug:
        os.environ["DEBUG"] = "true"
        # Re-read it so the current process also sees it
        DEBUG = True

    print(f"\n{'='*60}")
    print("Starting OSRS Stream Bet Bridge")
    print(f"URL: http://127.0.0.1:{args.port}")
    print(f"Debug mode: {'ENABLED' if DEBUG else 'disabled'}")
    if DEBUG:
        print(→ Messages will appear in clean format: [platform]HH:MM:SS - user - message")
    print(f"{'='*60}\n")

    uvicorn.run(
        "stream_bet_bridge:app",
        host="127.0.0.1",
        port=args.port,
        access_log=False,
        log_level="warning"
    )