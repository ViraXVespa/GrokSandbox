"""Discord embed builders for ChatBet polls."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

import discord


COLOR_OPEN = discord.Color.from_rgb(88, 101, 242)       # blurple
COLOR_READY = discord.Color.from_rgb(87, 242, 135)      # green-ish
COLOR_CLOSED_WIN = discord.Color.from_rgb(87, 242, 135)
COLOR_CLOSED = discord.Color.from_rgb(237, 66, 69)


def _fmt_amount(n: int) -> str:
    return f"{n:,}"


def poll_embed(poll: Dict[str, Any]) -> discord.Embed:
    is_open = bool(poll.get("is_open"))
    question = poll.get("question", "Untitled poll")
    poll_id = poll.get("id")
    options: List[str] = list(poll.get("options") or [])
    option_pools: List[int] = list(poll.get("option_pools") or [0] * len(options))
    total_pool = int(poll.get("total_pool") or 0)
    wager_count = int(poll.get("wager_count") or 0)
    winning_idx = poll.get("winning_option_index")

    if is_open:
        color = COLOR_OPEN
        title = f"🟢 Poll #{poll_id} — OPEN"
    else:
        color = COLOR_CLOSED_WIN if winning_idx is not None else COLOR_CLOSED
        title = f"🔴 Poll #{poll_id} — CLOSED"

    embed = discord.Embed(
        title=title,
        description=f"**{question}**",
        color=color,
        timestamp=datetime.now(timezone.utc),
    )

    lines = []
    for i, opt in enumerate(options):
        pool = option_pools[i] if i < len(option_pools) else 0
        pct = (pool / total_pool * 100.0) if total_pool > 0 else 0.0
        marker = ""
        if not is_open and winning_idx is not None and i == winning_idx:
            marker = " ✅ **WINNER**"
        lines.append(f"`{i + 1}.` **{opt}** — {_fmt_amount(pool)} ({pct:.0f}%){marker}")

    embed.add_field(name="Options / pool", value="\n".join(lines) or "_no options_", inline=False)
    embed.add_field(name="Total pool", value=_fmt_amount(total_pool), inline=True)
    embed.add_field(name="Wagers", value=str(wager_count), inline=True)
    embed.add_field(name="Type", value=str(poll.get("bet_type", "MULTIPLE_CHOICE")), inline=True)

    if is_open:
        embed.add_field(
            name="How to bet",
            value=(
                f"Use `/bet poll_id:{poll_id} option:<name or #> amount:<tokens>`\n"
                f"Example: `/bet poll_id:{poll_id} option:1 amount:500`\n"
                f"Check balance with `/balance`"
            ),
            inline=False,
        )
    else:
        if winning_idx is not None and 0 <= winning_idx < len(options):
            embed.add_field(name="Result", value=f"**{options[winning_idx]}**", inline=False)

    source = poll.get("source") or "hub"
    embed.set_footer(text=f"ChatBet • source: {source} • id {poll_id}")
    return embed


def balance_embed(username: str, balance: int) -> discord.Embed:
    embed = discord.Embed(
        title="Your ChatBet balance",
        description=f"**{username}** has **{_fmt_amount(balance)}** tokens",
        color=COLOR_READY,
    )
    return embed


def leaderboard_embed(rows: List[Dict[str, Any]]) -> discord.Embed:
    embed = discord.Embed(title="ChatBet leaderboard", color=COLOR_OPEN)
    if not rows:
        embed.description = "_No balances yet._"
        return embed
    lines = []
    medals = ["🥇", "🥈", "🥉"]
    for i, row in enumerate(rows):
        prefix = medals[i] if i < 3 else f"`{i + 1}.`"
        lines.append(f"{prefix} **{row['username']}** — {_fmt_amount(int(row['balance']))}")
    embed.description = "\n".join(lines)
    return embed


def error_embed(message: str) -> discord.Embed:
    return discord.Embed(title="ChatBet", description=message, color=COLOR_CLOSED)


def success_embed(message: str) -> discord.Embed:
    return discord.Embed(title="ChatBet", description=message, color=COLOR_READY)
