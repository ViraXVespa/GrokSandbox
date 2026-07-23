"""Pydantic models for the ChatBet hub API."""

from __future__ import annotations

from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class BetType(str, Enum):
    MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
    CLOSEST_WINS = "CLOSEST_WINS"
    FIXED_ODDS = "FIXED_ODDS"
    SLOT_MACHINE = "SLOT_MACHINE"


class PollCreate(BaseModel):
    question: str
    bet_type: BetType = BetType.MULTIPLE_CHOICE
    options: List[str]
    resolution_trigger: Optional[str] = None
    # Optional fixed id (when syncing from RuneLite)
    id: Optional[int] = None
    source: str = "hub"


class BetPlace(BaseModel):
    username: str
    poll_id: int
    option: str = Field(description="Option text or 1-based index as string")
    amount: int = Field(gt=0)
    source: str = "discord"
    discord_user_id: Optional[str] = None


class PollResolve(BaseModel):
    winning_option_index: int = Field(ge=0)
    winning_option: Optional[str] = None


class BalanceSet(BaseModel):
    username: str
    amount: int = Field(ge=0)


class BalanceCredit(BaseModel):
    username: str
    amount: int = Field(gt=0)
    reason: str = "engagement"


class WagerView(BaseModel):
    username: str
    poll_id: int
    option_index: int
    option_text: str
    amount: int
    source: str = "unknown"


class PollView(BaseModel):
    id: int
    question: str
    bet_type: BetType
    options: List[str]
    is_open: bool
    resolution_trigger: Optional[str] = None
    source: str = "hub"
    created_at: float
    closed_at: Optional[float] = None
    winning_option_index: Optional[int] = None
    total_pool: int = 0
    wager_count: int = 0
    option_pools: List[int] = Field(default_factory=list)
    wagers: List[WagerView] = Field(default_factory=list)
    discord_message_id: Optional[str] = None


class StateSnapshot(BaseModel):
    active_polls: List[PollView]
    closed_polls: List[PollView]
    balances: Dict[str, int]
    leaderboard: List[Dict[str, Any]]


class ApiResponse(BaseModel):
    ok: bool
    message: str = ""
    data: Optional[Any] = None
