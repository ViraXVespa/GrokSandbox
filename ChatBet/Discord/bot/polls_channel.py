"""Keep the Discord polls channel in sync with hub state."""

from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING, Any, Dict, Optional, Set

import discord

from bot.embeds import poll_embed

if TYPE_CHECKING:
    from hub.engine import BetEngine

log = logging.getLogger("chatbet.discord.polls")


class PollsChannelManager:
    def __init__(
        self,
        bot: discord.Client,
        engine: "BetEngine",
        channel_id: int,
        refresh_seconds: float = 8.0,
    ):
        self.bot = bot
        self.engine = engine
        self.channel_id = channel_id
        self.refresh_seconds = refresh_seconds
        self._task: Optional[asyncio.Task] = None
        self._known_message_ids: Set[int] = set()

    def start(self) -> None:
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self._loop(), name="chatbet-polls-channel")

    def stop(self) -> None:
        if self._task and not self._task.done():
            self._task.cancel()

    async def refresh_now(self) -> None:
        await self._sync()

    async def _loop(self) -> None:
        await self.bot.wait_until_ready()
        while not self.bot.is_closed():
            try:
                await self._sync()
            except asyncio.CancelledError:
                raise
            except Exception:
                log.exception("Polls channel sync failed")
            await asyncio.sleep(self.refresh_seconds)

    async def _channel(self) -> Optional[discord.TextChannel]:
        ch = self.bot.get_channel(self.channel_id)
        if ch is None:
            try:
                ch = await self.bot.fetch_channel(self.channel_id)
            except Exception:
                log.error("Cannot access polls channel id=%s", self.channel_id)
                return None
        if not isinstance(ch, discord.TextChannel):
            log.error("Polls channel %s is not a text channel", self.channel_id)
            return None
        return ch

    async def _sync(self) -> None:
        channel = await self._channel()
        if channel is None:
            return

        # Active polls first, then recent closed
        polls = [self.engine.poll_view(p).model_dump() for p in self.engine.list_active()]
        polls += [self.engine.poll_view(p).model_dump() for p in self.engine.list_closed()]

        for poll in polls:
            await self._upsert_poll_message(channel, poll)

    async def _upsert_poll_message(self, channel: discord.TextChannel, poll: Dict[str, Any]) -> None:
        embed = poll_embed(poll)
        mid = poll.get("discord_message_id")
        message: Optional[discord.Message] = None

        if mid:
            try:
                message = await channel.fetch_message(int(mid))
            except (discord.NotFound, discord.HTTPException, ValueError):
                message = None

        if message is not None:
            try:
                await message.edit(embed=embed)
                self._known_message_ids.add(message.id)
                return
            except discord.HTTPException:
                log.warning("Failed to edit message for poll %s", poll.get("id"))

        # Create new message
        try:
            message = await channel.send(embed=embed)
            self.engine.set_discord_message_id(int(poll["id"]), str(message.id))
            self._known_message_ids.add(message.id)
            log.info("Posted poll #%s message %s", poll.get("id"), message.id)
        except discord.HTTPException:
            log.exception("Failed to post poll #%s", poll.get("id"))
