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

active_request: Optional[dict] = None


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
    """Detect ChatBet commands. Returns structured data for betting."""
    if not message or not message.strip().startswith("!"):
        return None

    cleaned = message.strip()
    lower = cleaned.lower()

    # Handle bet / wager with "!bet <amount> on <option>" pattern
    if lower.startswith("!bet ") or lower.startswith("!wager "):
        # Remove the command prefix
        rest = cleaned.split(maxsplit=1)[1] if " " in cleaned else ""
        # Look for " on " pattern (case-insensitive)
        if " on " in rest.lower():
            parts = rest.split(" on ", 1)
            amount_str = parts[0].strip()
            option = parts[1].strip() if len(parts) > 1 else ""

            try:
                amount = int(amount_str)
            except ValueError:
                amount = None

            return {
                "command": "bet",
                "amount": amount,
                "option": option,
                "raw": cleaned
            }
        else:
            # Fallback for malformed bet command
            return {
                "command": "bet",
                "amount": None,
                "option": None,
                "raw": cleaned
            }

    # Other commands (odds, chatbet, etc.)
    parts = cleaned[1:].split(maxsplit=3)
    if not parts:
        return None

    cmd = parts[0].lower()
    if cmd in ("prob", "odds", "chance", "calculate"):
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
        "platform": platform, "user": user, "message": message, "timestamp": ts
    })
    parsed = parse_chat_command(message)
    if parsed:
        global active_request
        active_request = {"user": user, "command": parsed["command"], "args": parsed.get("args"), "amount": parsed.get("amount"), "option": parsed.get("option"), "timestamp": ts, "raw_message": parsed["raw"]}
        if DEBUG:
            print(f"  → Parsed command: !{parsed['command']} {parsed.get('args') or parsed.get('option') or ''}")
    return {"status": "received", "is_command": parsed is not None}


@app.post("/ingest")
async def ingest_post(msg: ChatMessage):
    log_message(msg.platform, msg.user, msg.message, msg.timestamp)
    ts = msg.timestamp or int(datetime.now().timestamp() * 1000)
    recent_messages.append({
        "platform": msg.platform, "user": msg.user, "message": msg.message, "timestamp": ts
    })
    parsed = parse_chat_command(msg.message)
    if parsed:
        global active_request
        active_request = {"user": msg.user, "command": parsed["command"], "args": parsed.get("args"), "amount": parsed.get("amount"), "option": parsed.get("option"), "timestamp": ts, "raw_message": parsed["raw"]}
        if DEBUG:
            print(f"  → Parsed command: !{parsed['command']} {parsed.get('args') or parsed.get('option') or ''}")
    return {"status": "received", "is_command": parsed is not None}


@app.get("/chatbet/state")
async def get_chatbet_state():
    """Endpoint for RuneLite plugin to poll current command requests and buffer status."""
    global active_request
    return {
        "active_request": active_request,
        "recent_messages_count": len(recent_messages),
        "last_updated": int(datetime.now().timestamp() * 1000)
    }


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
        print("→ Messages will appear in clean format: [platform]HH:MM:SS - user - message")
    print(f"{'='*60}\n")

    uvicorn.run(
        "stream_bet_bridge:app",
        host="127.0.0.1",
        port=args.port,
        access_log=False,
        log_level="warning"
    )