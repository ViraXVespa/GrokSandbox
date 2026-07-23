"""
ChatBet betting engine (Python port of BetManager).

Source of truth for Discord balances, wagers, and poll display state.
RuneLite can create/resolve polls and place bets through the hub API.
"""

from __future__ import annotations

import json
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from hub.models import BetType, PollView, WagerView


@dataclass
class Wager:
    username: str
    poll_id: int
    option_index: int
    amount: int
    source: str = "unknown"
    discord_user_id: Optional[str] = None


@dataclass
class Poll:
    id: int
    question: str
    bet_type: BetType
    options: List[str]
    is_open: bool = True
    resolution_trigger: Optional[str] = None
    source: str = "hub"
    created_at: float = field(default_factory=time.time)
    closed_at: Optional[float] = None
    winning_option_index: Optional[int] = None
    discord_message_id: Optional[str] = None


class BetEngine:
    def __init__(self, starting_balance: int = 10_000, data_path: Optional[Path] = None, recent_closed_limit: int = 8):
        self.starting_balance = starting_balance
        self.recent_closed_limit = recent_closed_limit
        self.data_path = data_path

        self._lock = threading.RLock()
        self._next_poll_id = 1
        self._active: Dict[int, Poll] = {}
        self._closed: List[Poll] = []
        self._wagers: Dict[int, List[Wager]] = {}
        self._balances: Dict[str, int] = {}
        # Map discord snowflake → display name used for balances
        self._discord_links: Dict[str, str] = {}

        if data_path:
            self.load()

    # ------------------------------------------------------------------ storage
    def load(self) -> None:
        if not self.data_path or not self.data_path.exists():
            return
        try:
            raw = json.loads(self.data_path.read_text(encoding="utf-8"))
        except Exception:
            return

        with self._lock:
            self._balances = {k: int(v) for k, v in raw.get("balances", {}).items()}
            self._discord_links = dict(raw.get("discord_links", {}))
            self._next_poll_id = int(raw.get("next_poll_id", 1))
            self._active.clear()
            self._closed.clear()
            self._wagers.clear()

            for p in raw.get("active_polls", []):
                poll = self._poll_from_dict(p)
                self._active[poll.id] = poll
                self._wagers[poll.id] = [self._wager_from_dict(w) for w in p.get("wagers", [])]

            for p in raw.get("closed_polls", []):
                poll = self._poll_from_dict(p)
                self._closed.append(poll)
                self._wagers[poll.id] = [self._wager_from_dict(w) for w in p.get("wagers", [])]

            self._closed = self._closed[-self.recent_closed_limit :]

    def save(self) -> None:
        if not self.data_path:
            return
        with self._lock:
            payload = {
                "next_poll_id": self._next_poll_id,
                "balances": self._balances,
                "discord_links": self._discord_links,
                "active_polls": [self._poll_to_dict(p, include_wagers=True) for p in self._active.values()],
                "closed_polls": [self._poll_to_dict(p, include_wagers=True) for p in self._closed],
            }
        self.data_path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.data_path.with_suffix(".tmp")
        tmp.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        tmp.replace(self.data_path)

    # ------------------------------------------------------------------ balances
    def link_discord_user(self, discord_user_id: str, username: str) -> None:
        with self._lock:
            self._discord_links[str(discord_user_id)] = username
            self._get_or_create_balance(username)
            self.save()

    def username_for_discord(self, discord_user_id: str, fallback_name: str) -> str:
        with self._lock:
            linked = self._discord_links.get(str(discord_user_id))
            if linked:
                return linked
            # Prefer clean display name; store mapping for stability
            name = fallback_name.strip() or f"user_{discord_user_id}"
            self._discord_links[str(discord_user_id)] = name
            self._get_or_create_balance(name)
            self.save()
            return name

    def get_balance(self, username: str) -> int:
        with self._lock:
            return self._get_or_create_balance(username)

    def set_balance(self, username: str, amount: int) -> int:
        with self._lock:
            self._balances[username] = max(0, int(amount))
            self.save()
            return self._balances[username]

    def credit_balance(self, username: str, amount: int) -> int:
        """Add tokens (engagement rewards, etc.)."""
        with self._lock:
            if amount <= 0:
                return self._get_or_create_balance(username)
            bal = self._get_or_create_balance(username) + int(amount)
            self._balances[username] = bal
            self.save()
            return bal

    def leaderboard(self, n: int = 10) -> List[Tuple[str, int]]:
        with self._lock:
            items = sorted(self._balances.items(), key=lambda kv: kv[1], reverse=True)
            return items[:n]

    def _get_or_create_balance(self, username: str) -> int:
        if username not in self._balances:
            self._balances[username] = self.starting_balance
        return self._balances[username]

    # ------------------------------------------------------------------ polls
    def create_poll(
        self,
        question: str,
        options: List[str],
        bet_type: BetType = BetType.MULTIPLE_CHOICE,
        resolution_trigger: Optional[str] = None,
        poll_id: Optional[int] = None,
        source: str = "hub",
    ) -> Poll:
        cleaned = [o.strip() for o in options if o and o.strip()]
        if len(cleaned) < 2:
            raise ValueError("A poll needs at least 2 options")
        if not question or not question.strip():
            raise ValueError("Question is required")

        with self._lock:
            if poll_id is not None:
                if poll_id in self._active or any(p.id == poll_id for p in self._closed):
                    # Idempotent re-sync from RuneLite: refresh open poll metadata
                    existing = self._active.get(poll_id)
                    if existing and existing.is_open:
                        existing.question = question.strip()
                        existing.options = cleaned
                        existing.bet_type = bet_type
                        existing.resolution_trigger = resolution_trigger
                        existing.source = source
                        self.save()
                        return existing
                    raise ValueError(f"Poll id {poll_id} already exists")
                new_id = poll_id
                self._next_poll_id = max(self._next_poll_id, poll_id + 1)
            else:
                new_id = self._next_poll_id
                self._next_poll_id += 1

            poll = Poll(
                id=new_id,
                question=question.strip(),
                bet_type=bet_type,
                options=cleaned,
                resolution_trigger=resolution_trigger,
                source=source,
            )
            self._active[new_id] = poll
            self._wagers[new_id] = []
            self.save()
            return poll

    def get_poll(self, poll_id: int) -> Optional[Poll]:
        with self._lock:
            if poll_id in self._active:
                return self._active[poll_id]
            for p in self._closed:
                if p.id == poll_id:
                    return p
            return None

    def list_active(self) -> List[Poll]:
        with self._lock:
            return sorted(self._active.values(), key=lambda p: p.id)

    def list_closed(self) -> List[Poll]:
        with self._lock:
            return list(self._closed)

    def set_discord_message_id(self, poll_id: int, message_id: str) -> None:
        with self._lock:
            poll = self._active.get(poll_id)
            if poll is None:
                for p in self._closed:
                    if p.id == poll_id:
                        p.discord_message_id = message_id
                        self.save()
                        return
                return
            poll.discord_message_id = message_id
            self.save()

    def resolve_option_index(self, poll: Poll, option: str) -> int:
        option = (option or "").strip()
        if option.isdigit():
            idx = int(option)
            # Accept 1-based from humans
            if 1 <= idx <= len(poll.options):
                return idx - 1
            if 0 <= idx < len(poll.options):
                return idx
        for i, name in enumerate(poll.options):
            if name.lower() == option.lower() or option.lower() in name.lower():
                return i
        raise ValueError(f"Option not found. Available: {', '.join(poll.options)}")

    def place_wager(
        self,
        username: str,
        poll_id: int,
        option: str,
        amount: int,
        source: str = "discord",
        discord_user_id: Optional[str] = None,
    ) -> Wager:
        if amount <= 0:
            raise ValueError("Amount must be positive")

        with self._lock:
            poll = self._active.get(poll_id)
            if poll is None or not poll.is_open:
                raise ValueError("Poll not found or already closed")

            option_index = self.resolve_option_index(poll, option)
            balance = self._get_or_create_balance(username)
            if balance < amount:
                raise ValueError(f"Insufficient balance ({balance} available)")

            self._balances[username] = balance - amount
            wager = Wager(
                username=username,
                poll_id=poll_id,
                option_index=option_index,
                amount=amount,
                source=source,
                discord_user_id=discord_user_id,
            )
            self._wagers.setdefault(poll_id, []).append(wager)
            self.save()
            return wager

    def resolve_poll(self, poll_id: int, winning_option_index: int) -> Poll:
        with self._lock:
            poll = self._active.get(poll_id)
            if poll is None or not poll.is_open:
                raise ValueError("Poll not found or already closed")
            if winning_option_index < 0 or winning_option_index >= len(poll.options):
                raise ValueError("Invalid winning option index")

            wagers = self._wagers.get(poll_id, [])
            if wagers:
                total_pool = sum(w.amount for w in wagers)
                winners = [w for w in wagers if w.option_index == winning_option_index]
                if winners:
                    total_winning = sum(w.amount for w in winners)
                    for w in winners:
                        share = w.amount / total_winning
                        payout = int(total_pool * share)
                        self._balances[w.username] = self._get_or_create_balance(w.username) + payout

            poll.is_open = False
            poll.closed_at = time.time()
            poll.winning_option_index = winning_option_index
            del self._active[poll_id]
            self._closed.append(poll)
            if len(self._closed) > self.recent_closed_limit:
                dropped = self._closed[:-self.recent_closed_limit]
                self._closed = self._closed[-self.recent_closed_limit :]
                for d in dropped:
                    self._wagers.pop(d.id, None)
            self.save()
            return poll

    def resolve_by_trigger(self, trigger: str, winning_option_index: int = 0) -> List[Poll]:
        resolved: List[Poll] = []
        with self._lock:
            targets = [
                p.id
                for p in self._active.values()
                if p.resolution_trigger and p.resolution_trigger.lower() == trigger.lower()
            ]
        for pid in targets:
            try:
                resolved.append(self.resolve_poll(pid, winning_option_index))
            except ValueError:
                continue
        return resolved

    # ------------------------------------------------------------------ views
    def poll_view(self, poll: Poll) -> PollView:
        with self._lock:
            wagers = self._wagers.get(poll.id, [])
            option_pools = [0] * len(poll.options)
            wager_views: List[WagerView] = []
            for w in wagers:
                if 0 <= w.option_index < len(option_pools):
                    option_pools[w.option_index] += w.amount
                wager_views.append(
                    WagerView(
                        username=w.username,
                        poll_id=w.poll_id,
                        option_index=w.option_index,
                        option_text=poll.options[w.option_index]
                        if 0 <= w.option_index < len(poll.options)
                        else "?",
                        amount=w.amount,
                        source=w.source,
                    )
                )
            return PollView(
                id=poll.id,
                question=poll.question,
                bet_type=poll.bet_type,
                options=poll.options,
                is_open=poll.is_open,
                resolution_trigger=poll.resolution_trigger,
                source=poll.source,
                created_at=poll.created_at,
                closed_at=poll.closed_at,
                winning_option_index=poll.winning_option_index,
                total_pool=sum(option_pools),
                wager_count=len(wagers),
                option_pools=option_pools,
                wagers=wager_views,
                discord_message_id=poll.discord_message_id,
            )

    def snapshot(self) -> dict:
        with self._lock:
            active = [self.poll_view(p) for p in sorted(self._active.values(), key=lambda x: x.id)]
            closed = [self.poll_view(p) for p in self._closed]
            board = [{"username": u, "balance": b} for u, b in self.leaderboard(15)]
            return {
                "active_polls": [p.model_dump() for p in active],
                "closed_polls": [p.model_dump() for p in closed],
                "balances": dict(self._balances),
                "leaderboard": board,
            }

    # ------------------------------------------------------------------ serdes
    def _poll_to_dict(self, poll: Poll, include_wagers: bool = False) -> dict:
        d = {
            "id": poll.id,
            "question": poll.question,
            "bet_type": poll.bet_type.value if isinstance(poll.bet_type, BetType) else poll.bet_type,
            "options": poll.options,
            "is_open": poll.is_open,
            "resolution_trigger": poll.resolution_trigger,
            "source": poll.source,
            "created_at": poll.created_at,
            "closed_at": poll.closed_at,
            "winning_option_index": poll.winning_option_index,
            "discord_message_id": poll.discord_message_id,
        }
        if include_wagers:
            d["wagers"] = [
                {
                    "username": w.username,
                    "poll_id": w.poll_id,
                    "option_index": w.option_index,
                    "amount": w.amount,
                    "source": w.source,
                    "discord_user_id": w.discord_user_id,
                }
                for w in self._wagers.get(poll.id, [])
            ]
        return d

    def _poll_from_dict(self, d: dict) -> Poll:
        return Poll(
            id=int(d["id"]),
            question=d["question"],
            bet_type=BetType(d.get("bet_type", BetType.MULTIPLE_CHOICE)),
            options=list(d.get("options", [])),
            is_open=bool(d.get("is_open", True)),
            resolution_trigger=d.get("resolution_trigger"),
            source=d.get("source", "hub"),
            created_at=float(d.get("created_at", time.time())),
            closed_at=d.get("closed_at"),
            winning_option_index=d.get("winning_option_index"),
            discord_message_id=d.get("discord_message_id"),
        )

    def _wager_from_dict(self, d: dict) -> Wager:
        return Wager(
            username=d["username"],
            poll_id=int(d["poll_id"]),
            option_index=int(d["option_index"]),
            amount=int(d["amount"]),
            source=d.get("source", "unknown"),
            discord_user_id=d.get("discord_user_id"),
        )
