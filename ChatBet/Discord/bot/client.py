"""Discord client for ChatBet."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Optional

import discord
from discord.ext import commands

from bot.polls_channel import PollsChannelManager
from config import Settings

if TYPE_CHECKING:
    from hub.engine import BetEngine

log = logging.getLogger("chatbet.discord")


class ChatBetBot(commands.Bot):
    def __init__(self, engine: "BetEngine", settings: Settings):
        intents = discord.Intents.default()
        # Slash commands do not require message content intent.
        super().__init__(command_prefix="!", intents=intents)
        self.engine = engine
        self.settings = settings
        self.polls_manager: Optional[PollsChannelManager] = None

    async def setup_hook(self) -> None:
        await self.load_extension("bot.cogs.betting")

        # Register slash commands
        if self.settings.discord_guild_id:
            guild = discord.Object(id=self.settings.discord_guild_id)
            self.tree.copy_global_to(guild=guild)
            synced = await self.tree.sync(guild=guild)
            log.info("Synced %s guild commands to %s", len(synced), self.settings.discord_guild_id)
        else:
            synced = await self.tree.sync()
            log.info("Synced %s global commands (may take up to 1 hour to appear)", len(synced))

    async def on_ready(self) -> None:
        log.info("Logged in as %s (%s)", self.user, self.user.id if self.user else "?")
        if self.settings.bot_status:
            await self.change_presence(
                activity=discord.Activity(type=discord.ActivityType.watching, name=self.settings.bot_status)
            )

        if self.settings.discord_polls_channel_id and self.polls_manager is None:
            self.polls_manager = PollsChannelManager(
                bot=self,
                engine=self.engine,
                channel_id=self.settings.discord_polls_channel_id,
            )
            self.polls_manager.start()
            log.info("Polls channel manager started for channel %s", self.settings.discord_polls_channel_id)

    async def close(self) -> None:
        if self.polls_manager:
            self.polls_manager.stop()
        await super().close()
