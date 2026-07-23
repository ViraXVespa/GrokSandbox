"""Load ChatBet Discord + hub settings from environment / .env file."""

from __future__ import annotations

from functools import lru_cache
from typing import List, Optional

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    discord_bot_token: str = Field(default="", alias="DISCORD_BOT_TOKEN")
    discord_application_id: str = Field(default="", alias="DISCORD_APPLICATION_ID")
    discord_guild_id: Optional[int] = Field(default=None, alias="DISCORD_GUILD_ID")
    discord_polls_channel_id: Optional[int] = Field(default=None, alias="DISCORD_POLLS_CHANNEL_ID")
    discord_admin_role_ids: str = Field(default="", alias="DISCORD_ADMIN_ROLE_IDS")

    starting_balance: int = Field(default=10_000, alias="STARTING_BALANCE")
    recent_closed_limit: int = Field(default=8, alias="RECENT_CLOSED_LIMIT")

    hub_host: str = Field(default="127.0.0.1", alias="HUB_HOST")
    hub_port: int = Field(default=8766, alias="HUB_PORT")
    hub_api_key: str = Field(default="", alias="HUB_API_KEY")

    bot_status: str = Field(default="ChatBet | /bet /balance", alias="BOT_STATUS")

    data_dir: str = Field(default="data", alias="DATA_DIR")

    @property
    def admin_role_ids(self) -> List[int]:
        if not self.discord_admin_role_ids.strip():
            return []
        ids: List[int] = []
        for part in self.discord_admin_role_ids.split(","):
            part = part.strip()
            if part.isdigit():
                ids.append(int(part))
        return ids

    @property
    def hub_base_url(self) -> str:
        return f"http://{self.hub_host}:{self.hub_port}"

    def validate_for_bot(self) -> List[str]:
        missing = []
        if not self.discord_bot_token:
            missing.append("DISCORD_BOT_TOKEN")
        if not self.discord_polls_channel_id:
            missing.append("DISCORD_POLLS_CHANNEL_ID")
        return missing


@lru_cache
def get_settings() -> Settings:
    return Settings()
