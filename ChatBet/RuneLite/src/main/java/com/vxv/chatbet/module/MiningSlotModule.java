package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;
import com.vxv.chatbet.bet.SlotSpinResult;
import com.vxv.chatbet.bet.SlotSymbol;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Mining slot machine for amethyst / runite sessions.
 *
 * <p>Flow:
 * <ol>
 *   <li>While mining, loot deltas fill a weighted pool (ores common, gems rare, glory-boosted).</li>
 *   <li>After {@code poolThreshold} units and a quiet lag period with no new loot, open a
 *       SLOT_MACHINE poll listing reel symbols and line options.</li>
 *   <li>Viewers bet with {@code !bet <per-line> on <1|3|5>} for a few minutes.</li>
 *   <li>When the window ends, spin 3 reels and pay matching lines.</li>
 * </ol>
 */
@Slf4j
public class MiningSlotModule implements BetModule {

    // --- OSRS item IDs (stable) ---
    private static final int RUNITE_ORE = 451;
    private static final int AMETHYST = 21347;
    private static final int UNCUT_SAPPHIRE = 1623;
    private static final int UNCUT_EMERALD = 1621;
    private static final int UNCUT_RUBY = 1619;
    private static final int UNCUT_DIAMOND = 1617;
    private static final int UNCUT_DRAGONSTONE = 1631;
    private static final int UNCUT_ONYX = 6571;

    /** Charged glories (1–6 charges) + eternal. Uncharged (1704) excluded. */
    private static final int[] CHARGED_GLORY_IDS = {
        1706, 1708, 1710, 1712, // (1)–(4)
        11976, 11978,           // (5)–(6)
        19707                   // eternal glory
    };

    private static final List<String> LINE_OPTIONS = Arrays.asList("1 line", "3 lines", "5 lines");

    private final ChatBetPlugin plugin;
    private final Random random = new Random();

    /** itemId → units collected toward the current pool. */
    private final Map<Integer, Integer> lootPool = new HashMap<>();
    private final Map<Integer, Integer> lastInventoryQtys = new HashMap<>();

    private final AtomicInteger sessionMined = new AtomicInteger(0);
    private final AtomicInteger slotsSpun = new AtomicInteger(0);

    private enum Phase {
        ACCUMULATING,
        LAG,
        BETTING,
        SPINNING
    }

    private Phase phase = Phase.ACCUMULATING;
    private Instant lastLootAt = Instant.EPOCH;
    private Instant lagStartedAt;
    private Instant bettingEndsAt;
    private int activePollId = -1;
    private List<String> currentReelTable = List.of();
    private Map<String, Double> currentMultipliers = Map.of();
    private SlotSpinResult lastSpin;

    public MiningSlotModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Mining Slots";
    }

    @Override
    public void onActivate() {
        resetPool(false);
        plugin.announce("[ChatBet] Mining Slots armed — fill the pool with amethyst/runite/gems, then wait for the lag timer.");
    }

    @Override
    public void onDeactivate() {
        // Keep pool if they switch away mid-session? Clear to avoid stale state.
        phase = Phase.ACCUMULATING;
        activePollId = -1;
    }

    @Override
    public void onGameTick(GameTick event) {
        switch (phase) {
            case ACCUMULATING:
                if (poolUnits() >= poolThreshold() && lastLootAt != Instant.EPOCH) {
                    phase = Phase.LAG;
                    lagStartedAt = Instant.now();
                    plugin.announce(String.format(
                        "[ChatBet] Slot pool ready (%d units). Mining lag %ds — stop mining to lock the reel.",
                        poolUnits(), lagSeconds()));
                }
                break;
            case LAG:
                // New loot cancels lag and returns to accumulating
                if (lastLootAt != null && lagStartedAt != null
                    && lastLootAt.isAfter(lagStartedAt)) {
                    phase = Phase.ACCUMULATING;
                    lagStartedAt = null;
                    log.debug("[Slots] Lag cancelled — new loot");
                    break;
                }
                if (lagStartedAt != null
                    && Duration.between(lagStartedAt, Instant.now()).getSeconds() >= lagSeconds()) {
                    openSlotPoll();
                }
                break;
            case BETTING:
                if (bettingEndsAt != null && Instant.now().isAfter(bettingEndsAt)) {
                    spinAndResolve();
                }
                break;
            case SPINNING:
            default:
                break;
        }
    }

    @Override
    public void onStatChanged(StatChanged event) {
        // Mining XP alone doesn't add to pool; inventory deltas are authoritative.
        if (event.getSkill() == Skill.MINING) {
            log.debug("[Slots] Mining XP tick");
        }
    }

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (plugin.getClient() == null) {
            return;
        }
        ItemContainer inv = plugin.getClient().getItemContainer(InventoryID.INVENTORY);
        if (event.getItemContainer() != inv) {
            return;
        }
        if (phase == Phase.BETTING || phase == Phase.SPINNING) {
            // Ignore loot during betting/spin so the locked reel stays fair
            snapshotInventory(inv);
            return;
        }

        Map<Integer, Integer> current = qtyMap(inv);
        boolean glory = isChargedGloryEquipped();

        for (SlotSymbol sym : catalog()) {
            int delta = current.getOrDefault(sym.getItemId(), 0)
                - lastInventoryQtys.getOrDefault(sym.getItemId(), 0);
            if (delta <= 0) {
                continue;
            }
            // Soft-cap per symbol so runite/amethyst cannot monopolise the reel
            int existing = lootPool.getOrDefault(sym.getItemId(), 0);
            int room = Math.max(0, sym.getPoolCap() - existing);
            int add = Math.min(delta, room);
            // Glory: boost gem contributions (not ore)
            if (glory && isGem(sym.getItemId())) {
                add = Math.min(room, add + Math.max(1, add / 2));
            }
            if (add > 0) {
                lootPool.merge(sym.getItemId(), add, Integer::sum);
                sessionMined.addAndGet(add);
                lastLootAt = Instant.now();
                if (phase == Phase.LAG) {
                    // handled on next tick as lag cancel via lastLootAt > lagStartedAt
                }
                log.debug("[Slots] +{} {} (pool={})", add, sym.getName(), poolUnits());
            }
        }

        lastInventoryQtys.clear();
        lastInventoryQtys.putAll(current);
    }

    private void snapshotInventory(ItemContainer inv) {
        lastInventoryQtys.clear();
        lastInventoryQtys.putAll(qtyMap(inv));
    }

    private static Map<Integer, Integer> qtyMap(ItemContainer inv) {
        Map<Integer, Integer> map = new HashMap<>();
        if (inv == null) {
            return map;
        }
        for (Item item : inv.getItems()) {
            if (item != null && item.getId() > 0) {
                map.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
        return map;
    }

    private void openSlotPoll() {
        phase = Phase.BETTING;
        buildReelTable();

        int windowSec = bettingWindowSeconds();
        long closesAt = System.currentTimeMillis() + windowSec * 1000L;
        bettingEndsAt = Instant.now().plusSeconds(windowSec);

        String reelPreview = currentReelTable.stream().distinct().limit(8)
            .collect(Collectors.joining(", "));
        if (currentReelTable.size() > 8) {
            reelPreview += ", …";
        }

        Poll poll = plugin.createManagedPoll(
            "Mining slots — pick lines (per-line stake × lines). Reels: " + reelPreview,
            BetType.SLOT_MACHINE,
            LINE_OPTIONS
        );
        poll.withReelSymbols(new ArrayList<>(currentReelTable));
        poll.withBettingClosesAt(closesAt);
        poll.withResolutionTrigger("MINING_SLOT_SPIN");
        activePollId = poll.getId();

        // Re-publish with reel metadata (hub may ignore extra fields)
        plugin.onPollCreated(poll);

        plugin.announce(String.format(
            "[ChatBet] Slot poll #%d OPEN for %ds — !bet <per-line> on 1|3|5  |  Reels weighted from your loot (glory: %s)",
            activePollId, windowSec, isChargedGloryEquipped() ? "yes" : "no"));
    }

    private void spinAndResolve() {
        if (activePollId <= 0) {
            phase = Phase.ACCUMULATING;
            return;
        }
        phase = Phase.SPINNING;

        String[] reels = new String[3];
        for (int i = 0; i < 3; i++) {
            reels[i] = drawSymbol();
        }
        boolean hit = reels[0].equalsIgnoreCase(reels[1]) && reels[1].equalsIgnoreCase(reels[2]);
        int linesWon = hit ? 1 : 0;
        String summary = hit
            ? "JACKPOT line: " + reels[0]
            : "No line — " + reels[0] + " | " + reels[1] + " | " + reels[2];

        lastSpin = new SlotSpinResult(reels, linesWon, summary);
        plugin.resolveSlotPoll(activePollId, lastSpin, currentMultipliers);
        slotsSpun.incrementAndGet();

        plugin.announce("[ChatBet] SPIN → " + lastSpin.formatReels() + " — " + summary
            + " (poll #" + activePollId + ")");

        activePollId = -1;
        resetPool(true);
        phase = Phase.ACCUMULATING;
        bettingEndsAt = null;
        lagStartedAt = null;
    }

    private void buildReelTable() {
        // Expand pool into a weighted list of symbol names for drawing
        List<String> table = new ArrayList<>();
        Map<String, Double> mults = new LinkedHashMap<>();

        for (SlotSymbol sym : catalog()) {
            int units = lootPool.getOrDefault(sym.getItemId(), 0);
            if (units <= 0 && (sym.getItemId() == RUNITE_ORE || sym.getItemId() == AMETHYST)) {
                // Always include ores lightly so empty gem-only edge cases still spin
                units = 1;
            }
            if (units <= 0) {
                continue;
            }
            // weight = baseWeight * min(units, cap) — gems keep low baseWeight
            int w = Math.max(1, sym.getBaseWeight() * Math.min(units, sym.getPoolCap()));
            // Soft-cap ore dominance: ores already have high poolCap but lower payout
            for (int i = 0; i < w; i++) {
                table.add(sym.getName());
            }
            mults.put(sym.getName(), sym.getPayoutMult());
        }

        if (table.isEmpty()) {
            table.add("Amethyst");
            table.add("Runite ore");
            mults.put("Amethyst", 2.0);
            mults.put("Runite ore", 2.5);
        }

        // Shuffle for variety in displayed order
        Collections.shuffle(table, random);
        currentReelTable = table;
        currentMultipliers = mults;
    }

    private String drawSymbol() {
        if (currentReelTable.isEmpty()) {
            return "Amethyst";
        }
        return currentReelTable.get(random.nextInt(currentReelTable.size()));
    }

    private void resetPool(boolean announce) {
        lootPool.clear();
        lastLootAt = Instant.EPOCH;
        currentReelTable = List.of();
        if (announce) {
            plugin.announce("[ChatBet] Slot pool cleared — mine to fill the next machine.");
        }
    }

    private int poolUnits() {
        return lootPool.values().stream().mapToInt(Integer::intValue).sum();
    }

    private boolean isChargedGloryEquipped() {
        if (plugin.getClient() == null) {
            return false;
        }
        ItemContainer eq = plugin.getClient().getItemContainer(InventoryID.EQUIPMENT);
        if (eq == null) {
            return false;
        }
        for (Item item : eq.getItems()) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            int id = item.getId();
            for (int gloryId : CHARGED_GLORY_IDS) {
                if (id == gloryId) {
                    return true;
                }
            }
            try {
                String name = plugin.getClient().getItemDefinition(id).getName();
                if (name == null) {
                    continue;
                }
                String lower = name.toLowerCase();
                if (lower.contains("eternal glory")) {
                    return true;
                }
                // "Amulet of glory (4)" etc. — not uncharged "(0)" / plain uncharged
                if (lower.contains("amulet of glory") && lower.contains("(") && !lower.contains("(0)")) {
                    return true;
                }
            } catch (Exception ignored) {
                // ignore definition failures
            }
        }
        return false;
    }

    private static boolean isGem(int itemId) {
        return itemId == UNCUT_SAPPHIRE || itemId == UNCUT_EMERALD || itemId == UNCUT_RUBY
            || itemId == UNCUT_DIAMOND || itemId == UNCUT_DRAGONSTONE || itemId == UNCUT_ONYX;
    }

    /**
     * Catalog of symbols. Ore: high cap, low mult. Gems: low cap, high mult.
     */
    private static List<SlotSymbol> catalog() {
        return Arrays.asList(
            new SlotSymbol("Amethyst", AMETHYST, 4, 2.0, 40),
            new SlotSymbol("Runite ore", RUNITE_ORE, 3, 2.5, 30),
            new SlotSymbol("Sapphire", UNCUT_SAPPHIRE, 2, 4.0, 12),
            new SlotSymbol("Emerald", UNCUT_EMERALD, 2, 5.0, 10),
            new SlotSymbol("Ruby", UNCUT_RUBY, 1, 8.0, 8),
            new SlotSymbol("Diamond", UNCUT_DIAMOND, 1, 12.0, 6),
            new SlotSymbol("Dragonstone", UNCUT_DRAGONSTONE, 1, 20.0, 3),
            new SlotSymbol("Onyx", UNCUT_ONYX, 1, 50.0, 1)
        );
    }

    private int poolThreshold() {
        return Math.max(5, plugin.getConfig().slotPoolThreshold());
    }

    private int lagSeconds() {
        return Math.max(5, plugin.getConfig().slotLagSeconds());
    }

    private int bettingWindowSeconds() {
        return Math.max(30, plugin.getConfig().slotBettingWindowSeconds());
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        panel.getChildren().add(TitleComponent.builder()
            .text("Mining Slots")
            .color(new Color(180, 120, 255))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Phase")
            .right(phase.name())
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Pool")
            .right(poolUnits() + " / " + poolThreshold())
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Glory charged")
            .right(isChargedGloryEquipped() ? "yes" : "no")
            .build());
        if (phase == Phase.LAG && lagStartedAt != null) {
            long left = lagSeconds() - Duration.between(lagStartedAt, Instant.now()).getSeconds();
            panel.getChildren().add(LineComponent.builder()
                .left("Lag lock-in")
                .right(Math.max(0, left) + "s")
                .build());
        }
        if (phase == Phase.BETTING && bettingEndsAt != null) {
            long left = Duration.between(Instant.now(), bettingEndsAt).getSeconds();
            panel.getChildren().add(LineComponent.builder()
                .left("Betting closes")
                .right(Math.max(0, left) + "s")
                .build());
            panel.getChildren().add(LineComponent.builder()
                .left("Poll")
                .right("#" + activePollId)
                .build());
        }
        if (lastSpin != null) {
            panel.getChildren().add(LineComponent.builder()
                .left("Last spin")
                .right(lastSpin.formatReels())
                .build());
        }
        // Pool breakdown (top few)
        lootPool.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(4)
            .forEach(e -> {
                String name = catalog().stream()
                    .filter(s -> s.getItemId() == e.getKey())
                    .map(SlotSymbol::getName)
                    .findFirst().orElse("id" + e.getKey());
                panel.getChildren().add(LineComponent.builder()
                    .left("  " + name)
                    .right(String.valueOf(e.getValue()))
                    .build());
            });
    }

    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> m = new LinkedHashMap<>();
        m.put("Phase", () -> phase);
        m.put("Pool units", this::poolUnits);
        m.put("Active poll", () -> activePollId);
        m.put("Glory", this::isChargedGloryEquipped);
        m.put("Session mined units", sessionMined::get);
        m.put("Slots spun", slotsSpun::get);
        m.put("Reel size", () -> currentReelTable.size());
        return m;
    }
}
