# ChatBet Codebase Reference Guide for Grok Sessions

**Purpose:** This document provides a complete, self-contained overview of the ChatBet project to allow new Grok AI sessions (or human developers) to quickly understand the architecture, components, current implementation status, data flows, and extension points without having to re-explore the entire codebase from scratch.

**Repository:** https://github.com/ViraXVespa/GrokSandbox/tree/main/ChatBet
**RuneLite Plugin:** ChatBet/RuneLite
**StreamLabs Bridge:** ChatBet/StreamLabs
**Last Updated:** 2026-06-22

## High-Level Overview

ChatBet is a RuneLite plugin for Old School RuneScape that allows streamers to run betting games/polls via Twitch chat commands. Viewers bet virtual currency on in-game events/outcomes. The plugin tracks events, calculates odds, and displays UI overlays/panels. A Python bridge integrates with StreamLabs for chat interaction and notifications.

**Key Features (Planned/Partial):**
- Chat command handling (!chatbet, !bet, etc.)
- Modular event tracking (e.g., PickpocketingModule)
- Betting manager with parimutuel/fixed odds
- UI Overlay and Control Panel
- StreamLabs integration for real-time bets

**Current Status:** Core plugin skeleton exists. Many command handlers and tracking logic are stubs. Package structure has `module` vs `modules` inconsistency. REFERENCE.md is the onboarding doc.

## Directory Structure

```
ChatBet/
├── README.md
├── RuneLite/                  # Java RuneLite plugin
│   └── src/main/java/com/viravespa/chatbet/
│       ├── ChatBetPlugin.java
│       ├── BetManager.java
│       ├── config/
│       ├── model/
│       ├── module/             # Note: inconsistency with 'modules'
│       ├── overlay/
│       ├── ui/
│       └── ChatBetConfig.java
├── StreamLabs/                # Python bridge
│   └── ...
└── REFERENCE.md               # This file
```

## Core Components & Data Flow

1. **ChatBetPlugin.java**: Entry point, subscribes to chat messages, game events. Registers modules and managers.
2. **BetManager.java**: Handles betting lifecycle - start bet, place bets, resolve, payout.
3. **Modules** (e.g. PickpocketingModule): Implement event tracking for specific OSRS activities. Contribute to odds calculation.
4. **UI**: Overlay for live odds, Panel for control.
5. **Models**: Bet, Outcome, etc.
6. **Flow**: Chat command → BetManager → Modules track → UI update → StreamLabs notify.

## Key Classes

- **ChatBetPlugin**: Extends Plugin, @Inject dependencies, @Subscribe events.
- **BetManager**: Core logic for bets.
- **BetModule**: Interface for activity-specific modules.
- **PickpocketingModule**: Example stub for pickpocketing tracking.

## Gaps & TODOs
- Implement command handlers in plugin.
- Flesh out module tracking.
- Fix package naming.
- Wire UI and bridge.

## Extension Guide
To add a new module: Implement BetModule interface, register in plugin.

For detailed code, see files on GitHub.

**Grok Workflow:** Read this REFERENCE.md first, then use GitHub tools for updates. Always push full content via github___push_files before replying.