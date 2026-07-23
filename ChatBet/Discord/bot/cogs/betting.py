"""Slash commands: /bet /balance /bets /leaderboard (+ admin poll tools)."""

from __future__ import annotations

import logging
from typing import TYPE_CHECKING, Optional

import discord
from discord import app_commands
from discord.ext import commands

from bot.embeds import (
    balance_embed,
    error_embed,
    leaderboard_embed,
    poll_embed,
    success_embed,
)
from hub.models import BetType

if TYPE_CHECKING:
    from bot.client import ChatBetBot

log = logging.getLogger("chatbet.discord.commands")


class BettingCog(commands.Cog):
    def __init__(self, bot: "ChatBetBot"):
        self.bot = bot

    def _username(self, user: discord.abc.User) -> str:
        display = getattr(user, "display_name", None) or user.name
        return self.bot.engine.username_for_discord(str(user.id), display)

    def _is_admin(self, interaction: discord.Interaction) -> bool:
        member = interaction.user
        if isinstance(member, discord.Member):
            if member.guild_permissions.manage_guild or member.guild_permissions.administrator:
                return True
            admin_roles = set(self.bot.settings.admin_role_ids)
            if admin_roles and any(r.id in admin_roles for r in member.roles):
                return True
        return False

    @app_commands.command(name="balance", description="Check your ChatBet token balance")
    async def balance(self, interaction: discord.Interaction) -> None:
        username = self._username(interaction.user)
        bal = self.bot.engine.get_balance(username)
        await interaction.response.send_message(embed=balance_embed(username, bal), ephemeral=True)

    @app_commands.command(name="bet", description="Place a wager on an active poll")
    @app_commands.describe(
        poll_id="Poll number (see the betting channel or /bets)",
        option="Option name or number (1-based)",
        amount="How many tokens to wager",
    )
    async def bet(
        self,
        interaction: discord.Interaction,
        poll_id: int,
        option: str,
        amount: app_commands.Range[int, 1, 1_000_000_000],
    ) -> None:
        username = self._username(interaction.user)
        try:
            wager = self.bot.engine.place_wager(
                username=username,
                poll_id=poll_id,
                option=option,
                amount=int(amount),
                source="discord",
                discord_user_id=str(interaction.user.id),
            )
            poll = self.bot.engine.get_poll(poll_id)
            opt_name = poll.options[wager.option_index] if poll else option
            bal = self.bot.engine.get_balance(username)
            embed = success_embed(
                f"**{username}** bet **{amount:,}** on **{opt_name}** (poll #{poll_id}).\n"
                f"Remaining balance: **{bal:,}**"
            )
            await interaction.response.send_message(embed=embed, ephemeral=True)
            # Kick a channel refresh so pools update quickly
            if self.bot.polls_manager:
                self.bot.loop.create_task(self.bot.polls_manager.refresh_now())
        except ValueError as e:
            await interaction.response.send_message(embed=error_embed(str(e)), ephemeral=True)

    @app_commands.command(name="bets", description="List active ChatBet polls")
    async def bets(self, interaction: discord.Interaction) -> None:
        active = self.bot.engine.list_active()
        if not active:
            await interaction.response.send_message(
                embed=error_embed("No active polls right now."),
                ephemeral=True,
            )
            return

        # Show first poll as embed + summary of others
        first = self.bot.engine.poll_view(active[0]).model_dump()
        embed = poll_embed(first)
        if len(active) > 1:
            extras = "\n".join(f"• **#{p.id}** — {p.question}" for p in active[1:])
            embed.add_field(name="Other active polls", value=extras, inline=False)
        await interaction.response.send_message(embed=embed, ephemeral=True)

    @app_commands.command(name="leaderboard", description="Top ChatBet balances")
    async def leaderboard(self, interaction: discord.Interaction) -> None:
        rows = [{"username": u, "balance": b} for u, b in self.bot.engine.leaderboard(10)]
        await interaction.response.send_message(embed=leaderboard_embed(rows), ephemeral=False)

    # ----- admin -----
    poll_group = app_commands.Group(name="poll", description="Streamer tools for ChatBet polls")

    @poll_group.command(name="create", description="Create a poll from Discord (admin)")
    @app_commands.describe(
        question="What people are betting on",
        options="Comma-separated options, e.g. Yes,No or Fire,Water,Earth",
    )
    async def poll_create(self, interaction: discord.Interaction, question: str, options: str) -> None:
        if not self._is_admin(interaction):
            await interaction.response.send_message(embed=error_embed("Admin only."), ephemeral=True)
            return
        opts = [o.strip() for o in options.split(",") if o.strip()]
        try:
            poll = self.bot.engine.create_poll(
                question=question,
                options=opts,
                bet_type=BetType.MULTIPLE_CHOICE,
                source="discord",
            )
            await interaction.response.send_message(
                embed=poll_embed(self.bot.engine.poll_view(poll).model_dump()),
                ephemeral=False,
            )
            if self.bot.polls_manager:
                self.bot.loop.create_task(self.bot.polls_manager.refresh_now())
        except ValueError as e:
            await interaction.response.send_message(embed=error_embed(str(e)), ephemeral=True)

    @poll_group.command(name="resolve", description="Resolve a poll (admin)")
    @app_commands.describe(
        poll_id="Poll id",
        option="Winning option name or number (1-based)",
    )
    async def poll_resolve(self, interaction: discord.Interaction, poll_id: int, option: str) -> None:
        if not self._is_admin(interaction):
            await interaction.response.send_message(embed=error_embed("Admin only."), ephemeral=True)
            return
        try:
            poll = self.bot.engine.get_poll(poll_id)
            if poll is None or not poll.is_open:
                raise ValueError("Poll not found or already closed")
            idx = self.bot.engine.resolve_option_index(poll, option)
            resolved = self.bot.engine.resolve_poll(poll_id, idx)
            await interaction.response.send_message(
                embed=poll_embed(self.bot.engine.poll_view(resolved).model_dump()),
                ephemeral=False,
            )
            if self.bot.polls_manager:
                self.bot.loop.create_task(self.bot.polls_manager.refresh_now())
        except ValueError as e:
            await interaction.response.send_message(embed=error_embed(str(e)), ephemeral=True)

    @poll_group.command(name="refresh", description="Force-refresh the polls channel embeds (admin)")
    async def poll_refresh(self, interaction: discord.Interaction) -> None:
        if not self._is_admin(interaction):
            await interaction.response.send_message(embed=error_embed("Admin only."), ephemeral=True)
            return
        if self.bot.polls_manager:
            await self.bot.polls_manager.refresh_now()
        await interaction.response.send_message(embed=success_embed("Polls channel refreshed."), ephemeral=True)


async def setup(bot: commands.Bot) -> None:
    await bot.add_cog(BettingCog(bot))  # type: ignore[arg-type]
