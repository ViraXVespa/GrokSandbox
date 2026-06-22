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

**Status Note:** The betting core (BetManager + models + UI) is largely functional. However, **many tracking logics, command handlers, and module behaviors are stubbed/TODO**. Chat command implementations in the plugin are empty stubs. Package naming inconsistency exists (`module/` dir vs `modules` package). This is active dev – use this guide to identify what needs completion.

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
│   │   │   ├── BetModule.java             # Interface for activity modules
│   │   │   └── PickpocketingModule.java   # Current module impl (mostly stubs + goal delegation)
│   │   └── ui/
│   │       └── BetCreationDialog.java     # Swing JDialog for poll creation + suggested outcomes display
│   └── src/test/java/com/vxv/chatbet/ChatBetPluginTest.java  # Minimal test skeleton
│   └── browsed_files/                 # Cached external code (e.g. XPTrackerPlugin.java excerpts) for API reference
└── StreamLabs/
    └── stream_bet_bridge.py           # FastAPI + Uvicorn server for Streamlabs chat ingestion (/ingest GET/POST). Debug logging only currently.
```

**Important Note on Packages:** Directory uses `module/` (singular) but `PickpocketingModule.java` declares `package com.vxv.chatbet.modules;` (plural) and `ChatBetPlugin.java` imports from `modules.*`. This will prevent compilation. Fix by aligning package/dir names when editing.

---

## 3. Core Data Flow

1. **Initialization (startUp):**
   - Plugin registers overlay + NavigationButton for side panel.
   - Defaults `activeModule = new PickpocketingModule(this)`.

2. **In-Game Events (client thread):**
   - `onChatMessage` (GAMEMESSAGE/SPAM/PUBLICCHAT): Routes `!bet`, `!balance`, `!bets`, `!chatbet`, `!resolve` to stub `handle*` methods.
   - `onStatChanged(THIEVING)`: Updates `lastThievingXp`, delegates to `activeModule.onStatChanged`.
   - `onGameTick`: Delegates to module; refreshes `lastThievingXp` from client; optional debug logging of goal vars.
   - `onItemContainerChanged(INVENTORY)`: Calls stub `updateItemTracking` (intended for delta tracking of ETC 23959, dodgy necklace 21143, wine 1993).

3. **Betting Lifecycle:**
   - `!chatbet` (intended): Show `BetCreationDialog` (Swing, needs EDT handling).
   - Dialog → `betManager.createPoll(...)` + optional `withResolutionTrigger` ("ETC" or "GOAL_30").
   - Viewers `!bet` (intended): Parse → `betManager.placeWager` (balance check/deduct → add Wager).
   - Resolution:
     - Manual `!resolve` or dialog → `betManager.resolvePoll(id, winningIdx)`.
     - Auto: Module/plugin detects event → `betManager.onGameEvent(GameEventType)` or `resolveByTrigger(trigger, 0)` → finds matching open polls by `resolutionTrigger` → calls `resolvePoll`.
   - Payout (parimutuel): Sum totalPool, filter winners, compute share of pool based on their wager / totalWinning, credit balances.

4. **UI Updates:**
   - Overlay `render()`: Clears panel, adds sections by querying plugin getters (`getActivePolls()`, `getXpToGoal()`, `getElvesToGoal()`, `getTopBalances()`, `getBalance()`, legacy counters like `getEtcsObtained()`, `getSuccessRate()` etc.).
   - Panel: Task buttons → goal slider → `plugin.setActiveTask(task, pct)` → updates label.
   - Dialog: On FIXED_ODDS type change, shows `suggestedOutcomes` (set via `setSuggestedOutcomes` – currently never called from plugin/module).

5. **Module Contribution:**
   - `BetModule` interface: `getName()`, `onGameTick()`, `onStatChanged()`, `getElvesToGoal()` (default 0).
   - PickpocketingModule: Delegates goal calc to plugin (xpNeeded / 353.3 xp-per-elf). Stubs for tick/stat logic.
   - Future: Modules should provide `List<DropOutcome>` suggestions, more metrics, and call back into BetManager for resolutions.

6. **StreamLabs Bridge:**
   - Standalone FastAPI app (`stream_bet_bridge.py`).
   - Endpoints: GET/POST `/ingest` (platform, user, message, timestamp).
   - Currently: Pretty-prints in debug mode only. No forwarding to RuneLite yet.
   - Intended: JS in Streamlabs Chatbox forwards stream chat → bridge → (future) RuneLite plugin (e.g. local HTTP server in plugin or shared state).

**Balances:** In-memory `Map<String, Long>` (default 10_000 starting). `getTopBalances(n)`, recent request list (capped at 7 for overlay). No persistence yet.

---

## 4. Key Classes & Responsibilities (Detailed)

### ChatBetPlugin.java
- **Role:** Central host. Subscribes to RuneLite events, holds BetManager + activeModule, exposes state via getters for UI, calculates XP goals using XpTrackerService or config fallback.
- **Key Fields:** `BetManager betManager`, `BetModule activeModule`, `AtomicInteger` counters (attempts, successes, etcsObtained, attemptsSinceLastEtc, dodgy/wine consumed + since last, etc.), `lastInventoryQtys`/`lastEquipmentQtys` maps, `currentGoalPercentage`.
- **Constants:** ITEM_ETC=23959, DODGY=21143, WINE=1993.
- **Important Methods:**
  - `getXpToGoal()`: Uses XpTrackerService start/endGoal + percentage, or fallback `config.thievingGoalXp() * (pct/100) - current`.
  - `getElvesToGoal()`: Delegates to module or `xpNeeded / 353.3`.
  - Many getters for overlay (some return 0 or legacy values until tracking implemented).
  - `setActiveTask(String, int)` / `getActiveTaskName()` for panel sync.
- **Stubs:** All `handle*Command(...)` methods and `updateItemTracking(...)` are empty (`/* implementation */`).
- **Dependencies:** `@PluginDependency(XpTrackerPlugin.class)`, injects `XpTrackerService`.

### BetManager.java
- **Role:** Pure betting engine. No RuneLite deps. Handles creation, wagering, resolution, balances.
- **State:** `List<Poll> activePolls`, `Map<Integer, List<Wager>> pollWagers`, `Map<String, Long> userBalances`, `nextPollId`, recentBalanceRequests (LinkedList capped).
- **Core Logic:**
  - `createPoll(...)`: Auto ID, creates Poll, registers empty wager list.
  - `placeWager(...)`: Validates poll open + option + balance >= amount → deducts → adds Wager.
  - `resolvePoll(id, winningOptionIndex)`: If wagers exist, computes totalPool + totalWinning, distributes `totalPool * (wagerAmount / totalWinning)` to each winner's balance, closes poll.
  - `resolveByTrigger(trigger, idx)`: Scans open polls for matching `resolutionTrigger`, resolves them.
  - `onGameEvent(GameEventType)`: Hardcoded mapping (ETC_OBTAINED → resolveByTrigger("ETC",0), GOAL_30_REACHED → ...).
  - `resolveEtcPolls(...)` legacy wrapper.
- **Balance Helpers:** `getOrCreateBalance` (STARTING_BALANCE=10000), `getTopBalances`, `recordBalanceRequest` + `getRecentBalanceRequests`.

### Poll.java / Wager.java / BetType.java / DropOutcome.java
- Simple immutable-ish models. Poll has fluent `withResolutionTrigger(String)`.
- BetType enum drives dialog behavior (esp. FIXED_ODDS shows probs).

### GameEventType.java
- Enum triggers for auto-resolution linking game events to polls.

### BetModule.java (Interface)
- `String getName();`
- `void onGameTick(GameTick); void onStatChanged(StatChanged);`
- `default long getElvesToGoal() { return 0; }`
- Extend this for new activities. Add methods for suggestions, other metrics as needed.

### PickpocketingModule.java
- Current concrete module (package mismatch noted above).
- `getName()` = "Pickpocketing (Elves)"
- `on*` methods: Currently just debug logs + TODO comments. Placeholder for animation/inv-delta attempt detection and success counting on XP gain.
- `getElvesToGoal()`: Delegates to `plugin.getXpToGoal() / 353.3`.
- **To expand:** Implement real tracking, return suggested `List<DropOutcome>` (e.g. ETC prob ~0.000976 or better empirical), trigger resolutions on drops/goals.

### ChatBetOverlay.java
- Extends `Overlay`, position TOP_LEFT.
- `render(Graphics2D)`: If `config.showOverlay()`, builds `PanelComponent` with Title/LineComponents:
  - Active Bets list (id [type] truncated question)
  - XP to % Goal + Elves to Goal
  - Session Since Login stats (attempts/successes/rate/ETCs/est ETCs)
  - Since Last ETC section + prob
  - Consumables (dodgy/wine totals + since last)
  - Top Balances (ranked) + Recent Checks
- Relies heavily on plugin getters (many currently return 0 or legacy values).

### ChatBetPanel.java
- Extends `PluginPanel`.
- Two views: Task list (JButtons for activities e.g. "Pickpocketing Elves") and Goal config (JSlider 5-100% + Save button).
- On save: `plugin.setActiveTask(currentTask, sliderValue)` then back to list + refresh label showing active %.
- Pre-fills slider from `plugin.getCurrentGoalPercentage()`.

### BetCreationDialog.java
- Modal JDialog.
- Fields: question, BetType combo, options (CSV), trigger combo (None/ETC/GOAL_30), create button.
- Suggested outcomes textarea (populated only for FIXED_ODDS via `setSuggestedOutcomes(List<DropOutcome>)` and `updateOptionsForFixedOdds()`).
- On create: Parses options → `betManager.createPoll` → sets trigger if chosen → success message + dispose.
- Default example: "Will I get an ETC in the next 200 pickpockets?" Yes,No + ETC trigger.
- **Gap:** `setSuggestedOutcomes` is never invoked from plugin or module currently.

### ChatBetConfig.java
- Standard RuneLite @ConfigGroup("chatbet"):
  - `showOverlay()` default true
  - `thievingGoalXp()` default 13034431 (99)
  - `showDebugVars()` default false (enables verbose GameTick logging of goal calcs)

### stream_bet_bridge.py
- FastAPI app with CORS.
- Pydantic ChatMessage model.
- `/ingest` GET (query params) and POST (JSON body).
- `log_message` helper: pretty prints only if DEBUG env or --debug flag.
- Runs uvicorn on 127.0.0.1:8765 (configurable).
- **Future work:** Parse betting commands from messages and forward to RuneLite (e.g. via plugin exposing local endpoint or using a RuneLite "external" plugin pattern).

---

## 5. Current Implementation Gaps & TODOs (Identified from Code Review)

1. **Command Handlers (High Priority):** `handleBetCommand`, `handleChatBetCommand`, `handleBetsCommand`, `handleResolveCommand`, `handleBalanceCommand` in ChatBetPlugin are empty. Need arg parsing, validation, calls to BetManager, and user feedback (chat messages?). `!chatbet` needs to instantiate/show BetCreationDialog (Swing EDT safety).
2. **Tracking Logic:** `updateItemTracking` stub. Module `onGameTick`/`onStatChanged` are placeholders. Need:
   - Animation detection for pickpocket attempts (client.getLocalPlayer().getAnimation()).
   - XP delta or success message parsing for successes.
   - Inventory delta for ETC drops, dodgy necklace charges, wine consumption.
   - Update AtomicIntegers and "since last ETC" counters.
3. **Module Suggestions:** No code path calls `dialog.setSuggestedOutcomes(...)`. Add interface method or plugin query to activeModule.
4. **Package Inconsistency:** `module/` vs `modules` – breaks build. Standardize to `module` or `modules`.
5. **Fixed Odds Resolution:** BetManager.resolvePoll works for any type, but FIXED_ODDS may need different payout math (e.g. fixed odds multiplier instead of pure parimutuel).
6. **Persistence:** No saving of balances, active polls, or state across sessions/logins.
7. **StreamLabs ↔ RuneLite:** Bridge receives but does not forward. Plugin has no inbound HTTP/websocket yet.
8. **Legacy vs Module:** Many counters live in plugin (legacy). Move to modules or unify.
9. **Dialog/Panel Polish:** Better error handling, dynamic task list from registered modules, refresh on changes.
10. **Tests:** Only skeleton exists.

Prioritize wiring commands + completing PickpocketingModule tracking for a minimal working demo.

---

## 6. How to Extend / Add Features (Guidance for New Sessions)

**Adding a New Activity Module (e.g. Skilling or Combat):**
1. Create `NewActivityModule.java` in `module/` (fix package first!).
2. `implements BetModule`.
3. Implement tracking in `onGameTick`/`onStatChanged` using `plugin.getClient()` or injected client if refactored.
4. Add `get*ToGoal()` or other metrics as needed.
5. (Future) Add method `List<DropOutcome> getSuggestedOutcomes()` or similar; have plugin expose it.
6. In plugin `startUp` or via command, allow switching `setActiveModule(new NewActivityModule(this))`.
7. Update overlay/panel if new stats needed.

**Implementing Command Handlers:**
- Parse message in `handleBetCommand`: split, validate pollId int, option (map to index or name), amount long.
- Call `betManager.placeWager` or `getBalance` etc.
- For feedback: Use `client.addChatMessage(ChatMessageType.GAMEMESSAGE, "Bet placed! ...", "ChatBet", false);` or similar (check RuneLite patterns).
- For `!chatbet`: Create and `setVisible(true)` the dialog (use `SwingUtilities.invokeLater` for thread safety).

**Improving Auto-Resolution:**
- In module or plugin event handlers, after detecting key event (ETC drop, goal reached), call `betManager.onGameEvent(GameEventType.XXX)` or directly `resolveByTrigger`.
- Expand GameEventType enum as needed.

**Stream Integration:**
- Extend bridge to filter betting commands and POST/forward to a new endpoint in the plugin (add local server with e.g. SparkJava or use RuneLite's built-in HTTP if available via plugins).
- Or keep simple: Stream chat commands are typed in-game by moderator/streamer for now.

**General Best Practices:**
- Always read full file before editing (use get_file_contents tool).
- When pushing updates: Use `github___push_files` with **complete file content** (no partial diffs). Verify commit SHA in response.
- Test build with `./gradlew build` or shadowJar after changes.
- Refer to browsed_files/ for RuneLite API examples (e.g. how XPTrackerPlugin handles StatChanged/GameTick).
- Consult official docs: RuneLite API apidocs, plugin-hub examples.

---

## 7. Useful External References
- RuneLite Plugin Hub: https://github.com/runelite/plugin-hub (examples of overlays, panels, event handling, config)
- RuneLite API Docs: https://static.runelite.net/runelite-api/apidocs/
- RuneLite Client Docs: https://static.runelite.net/runelite-client/apidocs/
- XP Tracker source (cached in browsed_files/): Good pattern for StatChanged, goal varps, XpTrackerService usage.
- OSRS Thieving data (for realistic probs): Wiki or empirical from streams.

---

## 8. Recommended Workflow for Grok Sessions
1. **Read this REFERENCE.md first** (and README.md).
2. Use `github___get_repository_tree` + `github___get_file_contents` to inspect specific files as needed.
3. Identify stubs/TODOs from this guide.
4. Make targeted changes (full file content only).
5. **Push with github___push_files BEFORE any user reply** that mentions the change. Include full content. Commit message should be descriptive.
6. In final user response: Confirm push success + provide commit SHA/URL. Never say "I'll push now, wait...".
7. Update this REFERENCE.md (or add sections) when architecture evolves.

This ensures continuity across sessions and maintains the "push before reply" contract.

---

**End of Reference.** Questions about specific files or next task? Provide the task and I'll dive in with full context loaded. Let's make ChatBet the best OSRS stream interaction tool! 🎲✨
