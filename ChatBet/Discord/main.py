#!/usr/bin/env python3
"""
ChatBet Discord service entrypoint.

Starts:
  1) Local Hub HTTP API (default http://127.0.0.1:8766) for RuneLite sync
  2) Discord bot (slash commands + polls channel embeds)

Usage:
  cd ChatBet/Discord
  python -m venv .venv
  .venv\\Scripts\\activate          # Windows
  pip install -r requirements.txt
  copy .env.example .env           # then fill in values
  python main.py
"""

from __future__ import annotations

import asyncio
import logging
import sys
from pathlib import Path

import uvicorn

# Ensure package root is on path when launched as script
ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from bot.client import ChatBetBot
from config import get_settings
from hub.api import create_app
from hub.engine import BetEngine

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger("chatbet")


async def run() -> None:
    settings = get_settings()
    missing = settings.validate_for_bot()
    if missing:
        log.error("Missing required env vars: %s", ", ".join(missing))
        log.error("Copy .env.example to .env and fill them in. See README.md.")
        sys.exit(1)

    data_path = ROOT / settings.data_dir / "chatbet_state.json"
    engine = BetEngine(
        starting_balance=settings.starting_balance,
        data_path=data_path,
        recent_closed_limit=settings.recent_closed_limit,
    )
    log.info("Loaded engine (starting balance=%s, data=%s)", settings.starting_balance, data_path)

    app = create_app(engine, settings)
    bot = ChatBetBot(engine, settings)

    config = uvicorn.Config(
        app,
        host=settings.hub_host,
        port=settings.hub_port,
        log_level="info",
        access_log=False,
    )
    server = uvicorn.Server(config)

    log.info("Hub API → http://%s:%s", settings.hub_host, settings.hub_port)
    log.info("Starting Discord bot…")

    try:
        await asyncio.gather(
            server.serve(),
            bot.start(settings.discord_bot_token),
        )
    finally:
        if not bot.is_closed():
            await bot.close()
        engine.save()


def main() -> None:
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        log.info("Shutting down")


if __name__ == "__main__":
    main()
