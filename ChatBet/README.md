# ChatBet

A modular betting and probability system for Old School RuneScape streaming, built as a RuneLite plugin.

## Features

- Create multiple concurrent polls/bets via `!chatbet`
- Viewers wager using `!bet <pollId> <option> <amount>`
- Auto-resolution for in-game events (ETC drops, goal progress)
- Parimutuel (pool) betting with automatic payouts
- Fixed Odds bets with module-provided probabilities
- Balance tracking and leaderboard overlay
- Modular architecture for different activities

## Current Modules

- **PickpocketingModule** (Thieving / Elves)
  - ETC tracking and auto-resolution
  - 30% XP goal progress tracking
  - Suggested drop rates for Fixed Odds bets
- **RooftopAgilityModule** — laps before next fail
- **MiningSlotModule** — amethyst/runite weighted slot machine
- **Auto skill catalog** (`module/auto/*` via `ModuleCatalog`) — activate any from the side panel:
  - Combat Killstreak, Slayer Task
  - Fishing Haul, Cooking Burns, Woodcutting Nests, Firemaking Streak
  - Mining Inventory, Smithing / Crafting / Fletching / Herblore sessions
  - Prayer Bones, Magic High Alchs, Hunter Catches
  - Farming Harvest, Construction Build, Runecraft Trip
  - Thieving Stalls, Agility Marks
  - Clue Scrolls, Rare Drops, Level-Up Race

All auto modules open polls automatically and resolve from game chat / XP / deaths where applicable.
Viewers use `!bet <amount> on <option>` (brackets, Yes/No, skill names, or slot line counts).

## Commands

| Command | Who | Description |
|---------|-----|-------------|
| `!chatbet` | Streamer | Opens bet creation dialog |
| `!bet <id> <option> <amount>` | Viewers | Places a wager on a poll |
| `!bets` | Anyone | Lists active polls with IDs |
| `!resolve <id> <option>` | Streamer | Manually resolves a poll |
| `!balance` | Anyone | Check your current token balance |

## Architecture

```
ChatBetPlugin (Host)
├── BetManager          (Core betting engine + resolution)
├── ActiveModule        (PickpocketingModule, etc.)
│   ├── Game state & counters
│   ├── Goal tracking
│   └── Suggested outcomes
├── Overlay + Dialogs
└── DiscordHubClient    → ChatBet/Discord hub (:8766)
```

Modules can contribute their own UI to the overlay via `contributeToOverlay()`.

## StreamLabs bridge

See **[StreamLabs/README.md](StreamLabs/README.md)** for chat ingest, **presence** (join/leave-on-idle), and **anti-gameable engagement rewards**.

## Discord

See **[Discord/README.md](Discord/README.md)** for the bot + hub that posts polls to a channel and accepts `/bet` / `/balance` commands.

Quick start:

```powershell
cd ChatBet\Discord
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env   # fill DISCORD_BOT_TOKEN, guild/channel IDs
python main.py
```

## Building

```bash
cd ChatBet/RuneLite
./gradlew clean shadowJar
```

## Future Plans

- More activity modules (Skilling, Combat, Clues, etc.)
- Better Fixed Odds resolution and display
- Module switching commands
- More game event triggers

## Status

This is an active development project. The core betting system and PickpocketingModule are functional.

## Strict Operational Rules for Grok (Commit Procedure)

**SHA Verification Note:** When performing atomic commits, real GitHub tool calls (`github___get_file_contents` + `github___create_or_update_file`) return **actual SHAs** from the tool response. Placeholders like "(verified via tool)" are used only when full tool output is truncated in responses to comply with anti-simulation rules. Always prioritize real SHAs from tool results.