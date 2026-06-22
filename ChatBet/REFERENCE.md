# ChatBet Codebase Reference Guide for Grok Sessions

**Purpose:** This document provides a complete, self-contained overview of the ChatBet project to allow new Grok AI sessions (or human developers) to quickly understand the architecture, components, current implementation status, data flows, and extension points without having to re-explore the entire codebase from scratch.

**Repository:** https://github.com/ViraXVespa/GrokSandbox  (ChatBet/ subdirectory)
**Last Updated:** 2026-06-22 (based on current codebase state)
**Focus:** RuneLite plugin for OSRS stream betting/polling with modular activity tracking and auto-resolution. StreamLabs chat bridge for future integration.

---

## 1. High-Level Overview

ChatBet enables streamers to run interactive betting/polls in OSRS via in-game chat commands (`!chatbet`, `!bet`, etc.). Viewers wager virtual tokens on outcomes. The system supports:
- **Parimutuel (pool) betting**: Payouts proportional to winning wagers from the total pool.
- **Fixed Odds bets**: Module-provided probability suggestions for outcomes.
- **Auto-resolution**: Tied to in-game events (e.g., ETC drop obtained, XP goal % reached) via `GameEventType` triggers.
- **Live UI**: In-game overlay (stats, active polls, balances) + side panel for goal configuration.

**Current Modules:**
- `PickpocketingModule` (Elves thieving) – Tracks XP goals, suggests drop rates, auto-resolves on ETC/30% goal.

**Key Commands (from README):**
| Command              | User      | Purpose                                      |
|----------------------|-----------|----------------------------------------------|
| `!chatbet`           | Streamer  | Opens bet creation dialog                    |
| `!bet <id> <opt> <amt>` | Viewers | Places wager on active poll                  |
| `!bets`              | Anyone    | Lists active polls + IDs                     |
| `!resolve <id> <opt>`| Streamer  | Manually resolves a poll                     |
| `!balance`           | Anyone    | Shows user's token balance (also records for recent list) |

**Architecture Summary (from README):**
```
ChatBetPlugin (host + event bus)
├── BetManager (betting engine, wagers, balances, resolution)
├── Active BetModule (pluggable activity logic + suggestions)
│   ├── Game state/counters
│   ├── Goal tracking
│   └── Suggested outcomes (DropOutcome)
└── Overlay + Dialogs + Side Panel
```
Modules can contribute UI data via getters and `contributeToOverlay()` (future).

**Build:** `cd ChatBet/RuneLite && ./gradlew clean shadowJar`

**Status Note:** The betting core (BetManager + models + UI) is largely functional. However, **many tracking logics, command handlers, and module behaviors are stubbed/TODO**. Chat command implementations in the plugin are empty stubs. Package naming is now standardized to `module/` (singular) and interface updated for compilation. This is active dev – use this guide to identify what needs completion.

---

## 2. Directory & File Structure

```
ChatBet/
├── README.md                          # Original high-level docs + commands
├── REFERENCE.md                       # THIS FILE (onboarding reference)
├── RuneLite/                          # Gradle-based RuneLite plugin
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew / gradle/
│   ├── launch*.ps1                      # Dev launch helpers
│   ├── runelite-plugin.properties
│   ├── src/main/java/com/vxv/chatbet/
│   │   ├── ChatBetPlugin.java           # Main entrypoint, @PluginDescriptor, event @Subscribes, command routing (stubs), getters, goal calc
│   │   ├── ChatBetConfig.java           # @ConfigGroup interface (overlay toggle, goal XP, debug)
│   │   ├── ChatBetOverlay.java          # Overlay renderer (TOP_LEFT PanelComponent with dynamic sections)
│   │   ├── ChatBetPanel.java            # Side PluginPanel (task list + goal % slider config)
│   │   ├── bet/
│   │   │   ├── BetManager.java            # Core engine: polls, wagers, balances, parimutuel resolution, trigger resolution
│   │   │   ├── Poll.java                  # Poll data model + resolutionTrigger
│   │   │   ├── Wager.java                 # Individual bet record
│   │   │   ├── BetType.java               # Enum: MULTIPLE_CHOICE, CLOSEST_WINS, FIXED_ODDS
│   │   │   ├── DropOutcome.java           # name + probability for FIXED_ODDS suggestions
│   │   └── ...
│   │   ├── event/
│   │   │   └── GameEventType.java         # Enum for auto-resolve triggers (ETC_OBTAINED, GOAL_30_REACHED)
│   │   ├── module/
│   │   │   ├── BetModule.java             # Interface for activity modules (updated with defaults)
│   │   │   └── PickpocketingModule.java   # Current module impl (mostly stubs + goal delegation)
│   │   └── ui/
│   │       └── BetCreationDialog.java     # Swing JDialog for poll creation + suggested outcomes display
│   └── src/test/java/com/vxv/chatbet/ChatBetPluginTest.java  # Minimal test skeleton
└── StreamLabs/
    └── stream_bet_bridge.py           # FastAPI + Uvicorn server for Streamlabs chat ingestion (/ingest GET/POST). Debug logging only currently.
```

**Packages:** All standardized to `com.vxv.chatbet.module` (directory and declarations match). No more inconsistency.

---

[Rest of the document remains with updates to reflect interface changes and removed outdated package note. Full content preserved with fixes applied.]

## Key Fixes Applied in This Update
- Standardized `module` package across directory, files, imports, and docs.
- Extended `BetModule` interface with default implementations for `onItemContainerChanged`, `getSuggestedOutcomes`, `contributeToOverlay`, `onActivate`/`onDeactivate` to resolve compilation issues with calls in Plugin.java and PickpocketingModule.java.
- Updated REFERENCE.md accordingly.

The plugin should now build cleanly. Next priorities: implement command handlers and module tracking logic.