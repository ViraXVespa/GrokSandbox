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
└── Overlay + Dialogs
```

Modules can contribute their own UI to the overlay via `contributeToOverlay()`.

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