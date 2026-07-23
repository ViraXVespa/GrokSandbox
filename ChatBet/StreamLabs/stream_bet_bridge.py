#!/usr/bin/env python3
"""
OSRS Stream Bet Bridge
----------------------
Receives chat from Streamlabs Chat Box (custom JS) and provides:
  - ChatBet command routing for RuneLite
  - Presence (joined chat / left chat via idle timeout)
  - Anti-gameable engagement rewards (small token credits)

Run:  python stream_bet_bridge.py --debug
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import threading
import time
import unicodedata
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Deque, Dict, List, Optional, Set, Tuple

import json
import urllib.error
import urllib.request

import uvicorn
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict

# ---------------------------------------------------------------------------
# Config (env overrides)
# ---------------------------------------------------------------------------
DEBUG = os.getenv("DEBUG", "false").lower() in ("1", "true", "yes", "on")

# Presence: no message for this long → mark left chat
PRESENCE_IDLE_SECONDS = int(os.getenv("PRESENCE_IDLE_SECONDS", "600"))  # 10 min

# Engagement rewards (ChatBet tokens)
ENGAGE_BASE_REWARD = int(os.getenv("ENGAGE_BASE_REWARD", "15"))
ENGAGE_MAX_REWARD = int(os.getenv("ENGAGE_MAX_REWARD", "40"))
ENGAGE_MIN_CHARS = int(os.getenv("ENGAGE_MIN_CHARS", "12"))
ENGAGE_MIN_WORDS = int(os.getenv("ENGAGE_MIN_WORDS", "3"))
ENGAGE_COOLDOWN_SECONDS = int(os.getenv("ENGAGE_COOLDOWN_SECONDS", "45"))
ENGAGE_MAX_PER_HOUR = int(os.getenv("ENGAGE_MAX_PER_HOUR", "12"))
ENGAGE_MAX_PER_DAY = int(os.getenv("ENGAGE_MAX_PER_DAY", "80"))
ENGAGE_FIRST_CHAT_BONUS = int(os.getenv("ENGAGE_FIRST_CHAT_BONUS", "25"))
ENGAGE_SPAM_STRIKE_COOLDOWN = int(os.getenv("ENGAGE_SPAM_STRIKE_COOLDOWN", "180"))
ENGAGE_SIMILARITY_BLOCK = float(os.getenv("ENGAGE_SIMILARITY_BLOCK", "0.82"))

# Optional: push credits to Discord hub as well
DISCORD_HUB_URL = os.getenv("DISCORD_HUB_URL", "http://127.0.0.1:8766").rstrip("/")
DISCORD_HUB_API_KEY = os.getenv("HUB_API_KEY", os.getenv("DISCORD_HUB_API_KEY", ""))
FORWARD_CREDITS_TO_HUB = os.getenv("FORWARD_CREDITS_TO_HUB", "true").lower() in (
    "1",
    "true",
    "yes",
    "on",
)

# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------
app = FastAPI(title="OSRS Stream Bet Bridge", version="2.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

recent_messages: Deque[dict] = deque(maxlen=200)
active_request: Optional[dict] = None
presence_events: Deque[dict] = deque(maxlen=100)
pending_credits: Deque[dict] = deque(maxlen=200)
_lock = threading.RLock()

# Worthless-alone phrases (exact after normalize) — never reward alone
LOW_VALUE_PHRASES: Set[str] = {
    "hi",
    "hey",
    "hello",
    "yo",
    "sup",
    "hi chat",
    "hey chat",
    "hello chat",
    "hi guys",
    "hey guys",
    "lol",
    "lmao",
    "lmfao",
    "rofl",
    "kek",
    "kekw",
    "lul",
    "lulw",
    "omg",
    "wtf",
    "gg",
    "wp",
    "ez",
    "nice",
    "cool",
    "wow",
    "oof",
    "f",
    "rip",
    "same",
    "true",
    "facts",
    "first",
    "second",
    "poggers",
    "poggies",
    "pog",
    "pogchamp",
    "copium",
    "hopeium",
    "ratio",
    "W",
    "L",
    "based",
    "cringe",
    "yep",
    "yup",
    "yeah",
    "nah",
    "ok",
    "okay",
    "k",
    "kk",
    "ty",
    "thx",
    "thanks",
    "np",
    "wb",
    "gn",
    "gm",
    "brb",
    "afk",
    "idk",
    "imo",
    "imho",
    "tbh",
    "fr",
    "fr fr",
    "ong",
    "bet",
    "cap",
    "no cap",
    "sheesh",
    "sus",
    "mid",
    "goat",
    "lets go",
    "let's go",
    "go go go",
    "???",
    "...",
    "!!!",
    "yes",
    "no",
    "maybe",
    "sure",
    "what",
    "why",
    "how",
    "who",
    "when",
    "where",
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def now_ms() -> int:
    return int(time.time() * 1000)


def log_debug(msg: str) -> None:
    if DEBUG:
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}")


def normalize_user(user: str) -> str:
    return (user or "").strip()


def strip_emotes_and_noise(text: str) -> str:
    """Remove emotes, URLs, excessive punctuation for quality scoring."""
    if not text:
        return ""
    # Twitch-style emote placeholders / zero-width
    t = text.replace("\u200b", " ").replace("\ufeff", " ")
    # URLs
    t = re.sub(r"https?://\S+", " ", t)
    t = re.sub(r"www\.\S+", " ", t)
    # Common emote codes
    t = re.sub(r"\b[A-Z][a-z]+[A-Z][A-Za-z0-9]*\b", " ", t)  # CamelCase emotes often
    # Keep letters/numbers/basic punct
    out = []
    for ch in t:
        cat = unicodedata.category(ch)
        if cat.startswith("L") or cat.startswith("N") or ch in " '\"!?.,:-":
            out.append(ch)
        elif ch.isspace():
            out.append(" ")
    return re.sub(r"\s+", " ", "".join(out)).strip()


def is_mostly_emoji_or_symbols(text: str) -> bool:
    if not text or not text.strip():
        return True
    letters = sum(1 for c in text if unicodedata.category(c).startswith("L"))
    digits = sum(1 for c in text if unicodedata.category(c).startswith("N"))
    meaningful = letters + digits
    return meaningful < max(3, int(len(text.strip()) * 0.25))


def char_diversity_ratio(text: str) -> float:
    cleaned = re.sub(r"\s+", "", text.lower())
    if not cleaned:
        return 0.0
    return len(set(cleaned)) / max(1, len(cleaned))


def word_tokens(text: str) -> List[str]:
    return [w for w in re.findall(r"[a-z0-9']+", text.lower()) if w]


def jaccard_similarity(a: str, b: str) -> float:
    ta, tb = set(word_tokens(a)), set(word_tokens(b))
    if not ta or not tb:
        # fallback char-trigram-ish
        def grams(s: str) -> Set[str]:
            s = re.sub(r"\s+", "", s.lower())
            return {s[i : i + 3] for i in range(max(0, len(s) - 2))} if len(s) >= 3 else {s}

        ga, gb = grams(a), grams(b)
        if not ga or not gb:
            return 1.0 if a.strip().lower() == b.strip().lower() else 0.0
        inter = len(ga & gb)
        union = len(ga | gb)
        return inter / union if union else 0.0
    inter = len(ta & tb)
    union = len(ta | tb)
    return inter / union if union else 0.0


def message_fingerprint(text: str) -> str:
    norm = re.sub(r"\s+", " ", strip_emotes_and_noise(text).lower()).strip()
    return hashlib.sha1(norm.encode("utf-8")).hexdigest()[:16]


# ---------------------------------------------------------------------------
# Presence + engagement state
# ---------------------------------------------------------------------------
@dataclass
class ChatterState:
    user: str
    platform: str = "unknown"
    first_seen_ms: int = 0
    last_seen_ms: int = 0
    present: bool = True
    message_count: int = 0
    rewarded_count_hour: int = 0
    rewarded_count_day: int = 0
    hour_bucket: str = ""
    day_bucket: str = ""
    last_reward_ms: int = 0
    last_rewarded_text: str = ""
    recent_texts: Deque[str] = field(default_factory=lambda: deque(maxlen=12))
    recent_fps: Deque[str] = field(default_factory=lambda: deque(maxlen=20))
    spam_strikes: int = 0
    muted_until_ms: int = 0
    lifetime_rewards: int = 0
    first_chat_bonus_given: bool = False


chatters: Dict[str, ChatterState] = {}


def _buckets(ts_ms: int) -> Tuple[str, str]:
    dt = datetime.fromtimestamp(ts_ms / 1000.0)
    return dt.strftime("%Y-%m-%d-%H"), dt.strftime("%Y-%m-%d")


def get_chatter(user: str, platform: str, ts: int) -> ChatterState:
    key = user.lower()
    st = chatters.get(key)
    if st is None:
        st = ChatterState(user=user, platform=platform, first_seen_ms=ts, last_seen_ms=ts, present=True)
        chatters[key] = st
        presence_events.append(
            {
                "event": "join",
                "user": user,
                "platform": platform,
                "timestamp": ts,
            }
        )
        log_debug(f"JOIN chat: {user}")
    return st


def mark_activity(st: ChatterState, platform: str, ts: int) -> Optional[str]:
    """Returns 'join' if they re-entered after idle leave."""
    st.platform = platform or st.platform
    rejoin = False
    if not st.present:
        st.present = True
        rejoin = True
        presence_events.append(
            {"event": "join", "user": st.user, "platform": platform, "timestamp": ts, "rejoin": True}
        )
        log_debug(f"REJOIN chat: {st.user}")
    st.last_seen_ms = ts
    st.message_count += 1
    return "join" if rejoin else None


def sweep_idle_presence(now: Optional[int] = None) -> List[dict]:
    now = now or now_ms()
    left: List[dict] = []
    idle_ms = PRESENCE_IDLE_SECONDS * 1000
    with _lock:
        for st in chatters.values():
            if st.present and (now - st.last_seen_ms) >= idle_ms:
                st.present = False
                ev = {
                    "event": "leave",
                    "user": st.user,
                    "platform": st.platform,
                    "timestamp": now,
                    "idle_seconds": PRESENCE_IDLE_SECONDS,
                }
                presence_events.append(ev)
                left.append(ev)
                log_debug(f"LEAVE chat (idle): {st.user}")
    return left


def evaluate_engagement(user: str, platform: str, message: str, ts: int) -> Dict[str, Any]:
    """
    Decide whether to award tokens. Never awards for spam / low-effort.
    Returns dict with awarded, amount, reason, quality.
    """
    result = {
        "awarded": False,
        "amount": 0,
        "reason": "none",
        "quality": 0.0,
        "user": user,
    }

    raw = (message or "").strip()
    if not raw:
        result["reason"] = "empty"
        return result

    # Don't reward pure bot commands (betting already has its own economy)
    if raw.startswith("!"):
        result["reason"] = "command"
        return result

    if is_mostly_emoji_or_symbols(raw):
        result["reason"] = "emoji_or_symbols"
        return result

    cleaned = strip_emotes_and_noise(raw)
    if len(cleaned) < ENGAGE_MIN_CHARS:
        result["reason"] = "too_short"
        return result

    words = word_tokens(cleaned)
    if len(words) < ENGAGE_MIN_WORDS:
        result["reason"] = "too_few_words"
        return result

    # Single-character spam / keyboard smash
    if char_diversity_ratio(cleaned) < 0.22:
        result["reason"] = "low_diversity"
        return result

    # All-caps spam (allow short acronyms)
    letters = [c for c in cleaned if c.isalpha()]
    if len(letters) >= 10 and sum(1 for c in letters if c.isupper()) / len(letters) > 0.85:
        result["reason"] = "all_caps"
        return result

    # Exact low-value phrase
    phrase = re.sub(r"\s+", " ", cleaned.lower()).strip()
    if phrase in LOW_VALUE_PHRASES or phrase.replace(" ", "") in {p.replace(" ", "") for p in LOW_VALUE_PHRASES}:
        result["reason"] = "low_value_phrase"
        return result

    # If after stripping only a low-value phrase remains with fluff
    if len(words) <= 4 and all(w in LOW_VALUE_PHRASES or len(w) <= 2 for w in words):
        result["reason"] = "low_value_words"
        return result

    with _lock:
        st = get_chatter(user, platform, ts)
        mark_activity(st, platform, ts)

        # Hour/day buckets
        hour_b, day_b = _buckets(ts)
        if st.hour_bucket != hour_b:
            st.hour_bucket = hour_b
            st.rewarded_count_hour = 0
        if st.day_bucket != day_b:
            st.day_bucket = day_b
            st.rewarded_count_day = 0

        if st.muted_until_ms and ts < st.muted_until_ms:
            result["reason"] = "spam_timeout"
            return result

        # Burst detection: too many messages in last 15s without reward spacing
        recent_window = [m for m in recent_messages if m.get("user", "").lower() == user.lower()
                         and ts - int(m.get("timestamp") or 0) < 15_000]
        if len(recent_window) >= 6:
            st.spam_strikes += 1
            st.muted_until_ms = ts + ENGAGE_SPAM_STRIKE_COOLDOWN * 1000
            result["reason"] = "burst_spam"
            log_debug(f"SPAM mute {user} for {ENGAGE_SPAM_STRIKE_COOLDOWN}s")
            return result

        # Duplicate fingerprint
        fp = message_fingerprint(cleaned)
        if fp in st.recent_fps:
            st.spam_strikes += 1
            if st.spam_strikes >= 3:
                st.muted_until_ms = ts + ENGAGE_SPAM_STRIKE_COOLDOWN * 1000
            result["reason"] = "duplicate"
            return result

        # Near-duplicate of recent
        for prev in st.recent_texts:
            if jaccard_similarity(cleaned, prev) >= ENGAGE_SIMILARITY_BLOCK:
                result["reason"] = "too_similar"
                st.spam_strikes += 1
                return result

        # Cooldown
        if st.last_reward_ms and (ts - st.last_reward_ms) < ENGAGE_COOLDOWN_SECONDS * 1000:
            result["reason"] = "cooldown"
            return result

        if st.rewarded_count_hour >= ENGAGE_MAX_PER_HOUR:
            result["reason"] = "hourly_cap"
            return result
        if st.rewarded_count_day >= ENGAGE_MAX_PER_DAY:
            result["reason"] = "daily_cap"
            return result

        # Quality score 0..1
        quality = 0.35
        quality += min(0.25, len(cleaned) / 120.0)
        quality += min(0.20, len(set(words)) / 12.0)
        quality += min(0.15, char_diversity_ratio(cleaned))
        if "?" in cleaned or "!" in cleaned:
            quality += 0.05
        # Question or substantive punctuation slightly better than empty hype
        if any(w in words for w in ("because", "think", "should", "maybe", "what", "how", "why", "poll", "bet")):
            quality += 0.08
        quality = max(0.0, min(1.0, quality))

        # Diminishing returns after many rewards today
        diminish = 1.0
        if st.rewarded_count_day >= 20:
            diminish = 0.7
        if st.rewarded_count_day >= 40:
            diminish = 0.45
        if st.rewarded_count_day >= 60:
            diminish = 0.25

        amount = int(ENGAGE_BASE_REWARD + (ENGAGE_MAX_REWARD - ENGAGE_BASE_REWARD) * quality)
        amount = max(1, int(amount * diminish))

        bonus = 0
        if not st.first_chat_bonus_given:
            bonus = ENGAGE_FIRST_CHAT_BONUS
            st.first_chat_bonus_given = True

        total = amount + bonus

        # Commit reward
        st.last_reward_ms = ts
        st.last_rewarded_text = cleaned
        st.recent_texts.append(cleaned)
        st.recent_fps.append(fp)
        st.rewarded_count_hour += 1
        st.rewarded_count_day += 1
        st.lifetime_rewards += total
        st.spam_strikes = max(0, st.spam_strikes - 1)

        credit = {
            "id": f"{ts}-{fp}",
            "user": user,
            "platform": platform,
            "amount": total,
            "base": amount,
            "bonus": bonus,
            "quality": round(quality, 3),
            "reason": "engagement",
            "message_preview": raw[:80],
            "timestamp": ts,
            "acked": False,
        }
        pending_credits.append(credit)

        result.update(
            {
                "awarded": True,
                "amount": total,
                "reason": "engagement" + ("+first_chat" if bonus else ""),
                "quality": round(quality, 3),
                "credit_id": credit["id"],
            }
        )
        log_debug(f"REWARD +{total} → {user} (q={quality:.2f}, {result['reason']})")

    # Best-effort hub forward (outside lock)
    if result["awarded"] and FORWARD_CREDITS_TO_HUB:
        _forward_credit_to_hub(user, int(result["amount"]))

    return result


def _forward_credit_to_hub(username: str, amount: int) -> None:
    def run() -> None:
        try:
            payload = json.dumps(
                {"username": username, "amount": amount, "reason": "stream_engagement"}
            ).encode("utf-8")
            req = urllib.request.Request(
                f"{DISCORD_HUB_URL}/api/v1/balance/credit",
                data=payload,
                method="POST",
                headers={"Content-Type": "application/json"},
            )
            if DISCORD_HUB_API_KEY:
                req.add_header("X-Api-Key", DISCORD_HUB_API_KEY)
            urllib.request.urlopen(req, timeout=2)
        except (urllib.error.URLError, TimeoutError, OSError):
            pass

    threading.Thread(target=run, daemon=True).start()


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------
def parse_chat_command(message: str) -> Optional[dict]:
    if not message or not message.strip().startswith("!"):
        return None

    cleaned = message.strip()
    lower = cleaned.lower()

    if lower.startswith("!bet ") or lower.startswith("!wager "):
        rest = cleaned.split(maxsplit=1)[1] if " " in cleaned else ""
        if " on " in rest.lower():
            # case-insensitive split
            idx = rest.lower().index(" on ")
            amount_str = rest[:idx].strip()
            option = rest[idx + 4 :].strip()
            try:
                amount = int(amount_str.split()[-1])
            except ValueError:
                amount = None
            return {"command": "bet", "amount": amount, "option": option, "raw": cleaned}
        return {"command": "bet", "amount": None, "option": None, "raw": cleaned}

    parts = cleaned[1:].split(maxsplit=3)
    if not parts:
        return None
    cmd = parts[0].lower()
    if cmd in ("prob", "odds", "chance", "calculate"):
        cmd = "odds"
    return {"command": cmd, "args": parts[1:] if len(parts) > 1 else [], "raw": cleaned}


def process_inbound(
    platform: str,
    user: str,
    message: str,
    timestamp: Optional[int],
    event: str = "message",
) -> dict:
    global active_request
    user = normalize_user(user)
    platform = platform or "unknown"
    ts = timestamp or now_ms()
    sweep_idle_presence(ts)

    # Explicit join/leave from Chatbox JS (if ever sent)
    if event in ("join", "leave"):
        with _lock:
            if event == "join":
                st = get_chatter(user, platform, ts)
                mark_activity(st, platform, ts)
            else:
                key = user.lower()
                st = chatters.get(key)
                if st and st.present:
                    st.present = False
                    presence_events.append(
                        {"event": "leave", "user": user, "platform": platform, "timestamp": ts, "explicit": True}
                    )
        return {"status": "received", "event": event, "user": user}

    log_message(platform, user, message, ts)
    entry = {
        "platform": platform,
        "user": user,
        "message": message,
        "timestamp": ts,
        "event": "message",
    }
    with _lock:
        recent_messages.append(entry)

    # Presence on any real message
    with _lock:
        st = get_chatter(user, platform, ts)
        mark_activity(st, platform, ts)

    engagement = evaluate_engagement(user, platform, message, ts)

    parsed = parse_chat_command(message)
    if parsed:
        with _lock:
            active_request = {
                "user": user,
                "command": parsed["command"],
                "args": parsed.get("args"),
                "amount": parsed.get("amount"),
                "option": parsed.get("option"),
                "timestamp": ts,
                "raw_message": parsed["raw"],
            }
        if DEBUG:
            print(f"  → Parsed command: !{parsed['command']}")

    return {
        "status": "received",
        "event": "message",
        "is_command": parsed is not None,
        "engagement": engagement,
    }


def log_message(platform: str, user: str, message: str, timestamp: Optional[int] = None) -> None:
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


# ---------------------------------------------------------------------------
# API models
# ---------------------------------------------------------------------------
class ChatMessage(BaseModel):
    platform: str = "unknown"
    user: str
    message: str = ""
    timestamp: Optional[int] = None
    raw: Optional[str] = None
    event: str = "message"  # message | join | leave

    model_config = ConfigDict(extra="ignore")


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------
@app.get("/health")
def health() -> dict:
    sweep_idle_presence()
    return {"ok": True, "service": "stream-bet-bridge", "present": sum(1 for c in chatters.values() if c.present)}


@app.get("/ingest")
async def ingest_get(
    platform: str = Query("unknown"),
    user: str = Query(...),
    message: str = Query(""),
    timestamp: Optional[int] = Query(None),
    event: str = Query("message"),
):
    return process_inbound(platform, user, message, timestamp, event)


@app.post("/ingest")
async def ingest_post(msg: ChatMessage):
    return process_inbound(msg.platform, msg.user, msg.message, msg.timestamp, msg.event)


@app.get("/chatbet/state")
async def get_chatbet_state():
    sweep_idle_presence()
    with _lock:
        credits = [c for c in pending_credits if not c.get("acked")]
        return {
            "active_request": active_request,
            "recent_messages": list(recent_messages)[-20:],
            "recent_messages_count": len(recent_messages),
            "pending_credits": credits[-50:],
            "pending_credits_count": len(credits),
            "presence_events": list(presence_events)[-20:],
            "present_count": sum(1 for c in chatters.values() if c.present),
            "last_updated": now_ms(),
        }


@app.post("/chatbet/ack")
async def ack_active_request(timestamp: Optional[int] = None):
    global active_request
    with _lock:
        if active_request is None:
            return {"status": "empty"}
        if timestamp is not None and active_request.get("timestamp") != timestamp:
            return {"status": "stale", "kept": True}
        active_request = None
        return {"status": "acked"}


class CreditAckBody(BaseModel):
    ids: Optional[List[str]] = None

    model_config = ConfigDict(extra="ignore")


@app.post("/chatbet/credits/ack")
async def ack_credits(body: Optional[CreditAckBody] = None):
    """RuneLite acks applied engagement credits so they are not re-applied."""
    ids = body.ids if body else None
    with _lock:
        if not ids:
            for c in pending_credits:
                c["acked"] = True
            return {"status": "acked_all"}
        idset = set(ids)
        n = 0
        for c in pending_credits:
            if c.get("id") in idset and not c.get("acked"):
                c["acked"] = True
                n += 1
        return {"status": "acked", "count": n}


@app.get("/chatbet/presence")
async def get_presence():
    sweep_idle_presence()
    with _lock:
        present = [
            {
                "user": st.user,
                "platform": st.platform,
                "first_seen_ms": st.first_seen_ms,
                "last_seen_ms": st.last_seen_ms,
                "message_count": st.message_count,
                "lifetime_rewards": st.lifetime_rewards,
                "idle_for_s": max(0, (now_ms() - st.last_seen_ms) // 1000),
            }
            for st in chatters.values()
            if st.present
        ]
        present.sort(key=lambda x: x["last_seen_ms"], reverse=True)
        return {
            "present": present,
            "present_count": len(present),
            "recent_events": list(presence_events)[-30:],
            "idle_timeout_seconds": PRESENCE_IDLE_SECONDS,
            "engagement": {
                "base_reward": ENGAGE_BASE_REWARD,
                "max_reward": ENGAGE_MAX_REWARD,
                "cooldown_seconds": ENGAGE_COOLDOWN_SECONDS,
                "max_per_hour": ENGAGE_MAX_PER_HOUR,
                "max_per_day": ENGAGE_MAX_PER_DAY,
                "min_chars": ENGAGE_MIN_CHARS,
                "min_words": ENGAGE_MIN_WORDS,
            },
        }


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="OSRS Stream Bet Bridge")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    if args.debug:
        os.environ["DEBUG"] = "true"
        DEBUG = True

    print(f"\n{'=' * 60}")
    print("Starting OSRS Stream Bet Bridge v2")
    print(f"URL: http://127.0.0.1:{args.port}")
    print(f"Debug: {'ON' if DEBUG else 'off'}")
    print(f"Presence idle: {PRESENCE_IDLE_SECONDS}s")
    print(
        f"Engagement: {ENGAGE_BASE_REWARD}-{ENGAGE_MAX_REWARD} tok, "
        f"cd {ENGAGE_COOLDOWN_SECONDS}s, cap {ENGAGE_MAX_PER_HOUR}/h {ENGAGE_MAX_PER_DAY}/d"
    )
    print(f"{'=' * 60}\n")

    uvicorn.run(
        "stream_bet_bridge:app",
        host="127.0.0.1",
        port=args.port,
        access_log=False,
        log_level="warning",
    )
