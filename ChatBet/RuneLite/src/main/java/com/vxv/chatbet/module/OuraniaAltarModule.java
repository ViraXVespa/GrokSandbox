package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

@Slf4j
public class OuraniaAltarModule implements BetModule {

    private final ChatBetPlugin plugin;

    // Essence item IDs
    private static final int PURE_ESSENCE = 7936;
    private static final int DAEYALT_ESSENCE = 24704;

    // Pouch item IDs
    private static final int SMALL_POUCH = 5509;
    private static final int MEDIUM_POUCH = 5510;
    private static final int LARGE_POUCH = 5511;
    private static final int GIANT_POUCH = 5512;

    // Raiments of the Eye set item IDs
    private static final int RAIMENTS_HAT = 26865;
    private static final int RAIMENTS_TOP = 26867;
    private static final int RAIMENTS_BOTTOMS = 26869;
    private static final int RAIMENTS_BOOTS = 26871;

    // Ourania altar bank area (approximate center)
    private static final WorldPoint OURANIA_BANK = new WorldPoint(2453, 3231, 0);
    // Actual Ourania Altar location (for future crafting / end detection)
    private static final WorldPoint OURANIA_ALTAR = new WorldPoint(2460, 3245, 0);

    // Basic tracking for essence and pouches
    private final Map<Integer, Integer> lastInventoryQtys = new HashMap<>();
    private final Map<Integer, Integer> lastPouchQtys = new HashMap<>();
    private final AtomicInteger totalEssenceCarried = new AtomicInteger(0);

    private boolean runActive = false;
    private boolean firstRuneCrafted = false;
    private List<String> currentRuneOptions = new ArrayList<>();
    private final Map<String, Integer> runeCraftCounts = new HashMap<>();

    // State for reliable run starting after banking
    private boolean waitingForEssenceAfterBank = false;
    private int gracePeriodTicks = 0;

    public OuraniaAltarModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Ourania Altar Runes";
    }

    @Override
    public void onGameTick(GameTick event) {
        if (plugin.getClient() == null || plugin.getClient().getLocalPlayer() == null) return;

        // Grace period handling — prevents immediate auto-resolve right after bank-triggered start
        if (gracePeriodTicks > 0) {
            gracePeriodTicks--;
        }
    }

    @Override
    public void onStatChanged(StatChanged event) {
        // TODO: Runecraft level changes if needed for odds
    }

    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        ItemContainer container = event.getItemContainer();
        if (container == null) return;

        if (container == plugin.getClient().getItemContainer(InventoryID.INVENTORY)) {
            updateInventoryTracking(container);
        } else if (isPouchContainer(container)) {
            updatePouchTracking(container);
        }
    }

    private void updateInventoryTracking(ItemContainer container) {
        Map<Integer, Integer> currentQtys = new HashMap<>();

        for (var item : container.getItems()) {
            if (item.getId() > 0) {
                currentQtys.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        // Calculate deltas for essence types
        int pureDelta = calculateDelta(lastInventoryQtys, currentQtys, PURE_ESSENCE);
        int daeyaltDelta = calculateDelta(lastInventoryQtys, currentQtys, DAEYALT_ESSENCE);

        // Improve tracking: count essence moved into pouches as still carried
        if (isNearBank() || isAtAltar()) {
            if (pureDelta != 0) totalEssenceCarried.addAndGet(Math.abs(pureDelta));
            if (daeyaltDelta != 0) totalEssenceCarried.addAndGet(Math.abs(daeyaltDelta));
        } else {
            if (pureDelta > 0) totalEssenceCarried.addAndGet(pureDelta);
            if (daeyaltDelta > 0) totalEssenceCarried.addAndGet(daeyaltDelta);
        }

        // Fast path: start run if essence added while near bank
        if ((pureDelta > 0 || daeyaltDelta > 0) && (isNearBank() || isAtAltar()) && !runActive) {
            startNewRun();
        }

        lastInventoryQtys.clear();
        lastInventoryQtys.putAll(currentQtys);
    }

    private void updatePouchTracking(ItemContainer container) {
        Map<Integer, Integer> currentQtys = new HashMap<>();

        for (var item : container.getItems()) {
            if (item.getId() > 0) {
                currentQtys.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        lastPouchQtys.clear();
        lastPouchQtys.putAll(currentQtys);
    }

    private boolean isPouchContainer(ItemContainer container) {
        if (container == null) return false;
        for (var item : container.getItems()) {
            int id = item.getId();
            if (id == SMALL_POUCH || id == MEDIUM_POUCH || id == LARGE_POUCH || id == GIANT_POUCH) {
                return true;
            }
        }
        return false;
    }

    private int calculateDelta(Map<Integer, Integer> previous, Map<Integer, Integer> current, int itemId) {
        int prev = previous.getOrDefault(itemId, 0);
        int now = current.getOrDefault(itemId, 0);
        return now - prev;
    }

    private boolean isNearBank() {
        if (plugin.getClient() == null || plugin.getClient().getLocalPlayer() == null) return false;
        WorldPoint playerLoc = plugin.getClient().getLocalPlayer().getWorldLocation();
        return playerLoc != null && playerLoc.distanceTo(OURANIA_BANK) < 15;
    }

    private boolean isAtAltar() {
        if (plugin.getClient() == null || plugin.getClient().getLocalPlayer() == null) return false;
        WorldPoint playerLoc = plugin.getClient().getLocalPlayer().getWorldLocation();
        return playerLoc != null && playerLoc.distanceTo(OURANIA_ALTAR) < 10;
    }

    private boolean isAtOuraniaAltar() {
        return isNearBank();
    }

    private void startNewRun() {
        runActive = true;
        firstRuneCrafted = false;
        runeCraftCounts.clear();
        gracePeriodTicks = 8; // Grace period (~5s) to stabilize poll after bank message startNewRun()

        int rcLevel = 0;
        if (plugin.getClient() != null) {
            rcLevel = plugin.getClient().getRealSkillLevel(Skill.RUNECRAFT);
        }

        currentRuneOptions = getRuneOptionsForLevel(rcLevel);
        plugin.createOuraniaPoll(currentRuneOptions);
    }

    private void endCurrentRun() {
        if (!runActive) return;

        runActive = false;
        firstRuneCrafted = false;
        currentRuneOptions.clear();
        runeCraftCounts.clear();
    }

    public int getTotalEssenceCarried() {
        return totalEssenceCarried.get();
    }

    private boolean hasEssenceInInventory() {
        if (plugin.getClient() == null) return false;
        ItemContainer inv = plugin.getClient().getItemContainer(InventoryID.INVENTORY);
        if (inv == null) return false;

        for (Item item : inv.getItems()) {
            if (item.getId() == PURE_ESSENCE || item.getId() == DAEYALT_ESSENCE) {
                return true;
            }
        }
        return false;
    }

    public void resolveCurrentRun(int winningOptionIndex) {
        if (!runActive) return;

        int finalIndex = winningOptionIndex;

        if (finalIndex < 0 && !runeCraftCounts.isEmpty()) {
            String mostCrafted = runeCraftCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

            if (mostCrafted != null) {
                finalIndex = currentRuneOptions.indexOf(mostCrafted);
                if (finalIndex < 0) finalIndex = 0;
            }
        }

        if (finalIndex < 0) finalIndex = 0;

        plugin.resolveOuraniaPoll(finalIndex);
        endCurrentRun();
    }

    public List<String> getCurrentRuneOptions() {
        return new ArrayList<>(currentRuneOptions);
    }

    private List<String> getRuneOptionsForLevel(int level) {
        List<String> options = new ArrayList<>();

        options.add("Air rune");
        options.add("Mind rune");
        options.add("Water rune");
        options.add("Earth rune");
        options.add("Fire rune");
        options.add("Body rune");

        if (level >= 14) options.add("Cosmic rune");
        if (level >= 20) options.add("Chaos rune");
        if (level >= 27) options.add("Astral rune");
        if (level >= 35) options.add("Nature rune");
        if (level >= 44) options.add("Law rune");
        if (level >= 54) options.add("Death rune");
        if (level >= 65) options.add("Blood rune");
        if (level >= 70) options.add("Soul rune");

        return options;
    }

    public Map<String, Double> getRuneOdds(int rcLevel, boolean wearingFullRaiments) {
        List<String> options = getRuneOptionsForLevel(rcLevel);
        Map<String, Double> weights = new HashMap<>();

        double multiplier = wearingFullRaiments ? 1.6 : 1.0;

        // Real Ourania Altar (ZMI) distributions from OSRS Wiki (exact % per level band)
        if (rcLevel >= 99) {
            weights.put("Air rune", 1.0 * multiplier);
            weights.put("Mind rune", 1.0 * multiplier);
            weights.put("Water rune", 2.0 * multiplier);
            weights.put("Earth rune", 3.0 * multiplier);
            weights.put("Fire rune", 3.0 * multiplier);
            weights.put("Body rune", 4.0 * multiplier);
            weights.put("Cosmic rune", 5.0 * multiplier);
            weights.put("Chaos rune", 6.0 * multiplier);
            weights.put("Astral rune", 9.5 * multiplier);
            weights.put("Nature rune", 13.5 * multiplier);
            weights.put("Law rune", 14.5 * multiplier);
            weights.put("Death rune", 15.5 * multiplier);
            weights.put("Blood rune", 13.0 * multiplier);
            weights.put("Soul rune", 9.0 * multiplier);
        } else if (rcLevel >= 90) {
            weights.put("Air rune", 1.0 * multiplier);
            weights.put("Mind rune", 1.0 * multiplier);
            weights.put("Water rune", 2.0 * multiplier);
            weights.put("Earth rune", 3.0 * multiplier);
            weights.put("Fire rune", 4.0 * multiplier);
            weights.put("Body rune", 5.0 * multiplier);
            weights.put("Cosmic rune", 6.0 * multiplier);
            weights.put("Chaos rune", 7.0 * multiplier);
            weights.put("Astral rune", 10.0 * multiplier);
            weights.put("Nature rune", 13.5 * multiplier);
            weights.put("Law rune", 14.5 * multiplier);
            weights.put("Death rune", 16.5 * multiplier);
            weights.put("Blood rune", 10.0 * multiplier);
            weights.put("Soul rune", 6.5 * multiplier);
        } else if (rcLevel >= 80) {
            weights.put("Air rune", 2.0 * multiplier);
            weights.put("Mind rune", 2.0 * multiplier);
            weights.put("Water rune", 3.0 * multiplier);
            weights.put("Earth rune", 4.0 * multiplier);
            weights.put("Fire rune", 5.0 * multiplier);
            weights.put("Body rune", 6.0 * multiplier);
            weights.put("Cosmic rune", 7.0 * multiplier);
            weights.put("Chaos rune", 8.0 * multiplier);
            weights.put("Astral rune", 10.5 * multiplier);
            weights.put("Nature rune", 13.5 * multiplier);
            weights.put("Law rune", 14.5 * multiplier);
            weights.put("Death rune", 14.5 * multiplier);
            weights.put("Blood rune", 6.0 * multiplier);
            weights.put("Soul rune", 4.0 * multiplier);
        } else if (rcLevel >= 70) {
            weights.put("Air rune", 3.0 * multiplier);
            weights.put("Mind rune", 3.0 * multiplier);
            weights.put("Water rune", 3.0 * multiplier);
            weights.put("Earth rune", 4.0 * multiplier);
            weights.put("Fire rune", 4.0 * multiplier);
            weights.put("Body rune", 5.0 * multiplier);
            weights.put("Cosmic rune", 7.0 * multiplier);
            weights.put("Chaos rune", 9.0 * multiplier);
            weights.put("Astral rune", 12.0 * multiplier);
            weights.put("Nature rune", 15.0 * multiplier);
            weights.put("Law rune", 18.0 * multiplier);
            weights.put("Death rune", 10.0 * multiplier);
            weights.put("Blood rune", 5.0 * multiplier);
            weights.put("Soul rune", 2.0 * multiplier);
        } else if (rcLevel >= 60) {
            weights.put("Air rune", 4.5 * multiplier);
            weights.put("Mind rune", 5.0 * multiplier);
            weights.put("Water rune", 5.5 * multiplier);
            weights.put("Earth rune", 6.0 * multiplier);
            weights.put("Fire rune", 7.0 * multiplier);
            weights.put("Body rune", 7.5 * multiplier);
            weights.put("Cosmic rune", 9.5 * multiplier);
            weights.put("Chaos rune", 10.5 * multiplier);
            weights.put("Astral rune", 14.0 * multiplier);
            weights.put("Nature rune", 15.5 * multiplier);
            weights.put("Law rune", 8.0 * multiplier);
            weights.put("Death rune", 4.0 * multiplier);
            weights.put("Blood rune", 2.0 * multiplier);
            weights.put("Soul rune", 1.0 * multiplier);
        } else if (rcLevel >= 50) {
            weights.put("Air rune", 5.0 * multiplier);
            weights.put("Mind rune", 5.5 * multiplier);
            weights.put("Water rune", 6.0 * multiplier);
            weights.put("Earth rune", 6.5 * multiplier);
            weights.put("Fire rune", 7.0 * multiplier);
            weights.put("Body rune", 7.5 * multiplier);
            weights.put("Cosmic rune", 10.0 * multiplier);
            weights.put("Chaos rune", 11.0 * multiplier);
            weights.put("Astral rune", 15.0 * multiplier);
            weights.put("Nature rune", 13.5 * multiplier);
            weights.put("Law rune", 7.0 * multiplier);
            weights.put("Death rune", 3.5 * multiplier);
            weights.put("Blood rune", 1.7 * multiplier);
            weights.put("Soul rune", 0.8 * multiplier);
        } else if (rcLevel >= 40) {
            weights.put("Air rune", 6.0 * multiplier);
            weights.put("Mind rune", 6.5 * multiplier);
            weights.put("Water rune", 7.0 * multiplier);
            weights.put("Earth rune", 7.5 * multiplier);
            weights.put("Fire rune", 8.0 * multiplier);
            weights.put("Body rune", 10.0 * multiplier);
            weights.put("Cosmic rune", 15.0 * multiplier);
            weights.put("Chaos rune", 20.0 * multiplier);
            weights.put("Astral rune", 10.0 * multiplier);
            weights.put("Nature rune", 5.0 * multiplier);
            weights.put("Law rune", 2.6 * multiplier);
            weights.put("Death rune", 1.2 * multiplier);
            weights.put("Blood rune", 0.8 * multiplier);
            weights.put("Soul rune", 0.4 * multiplier);
        } else if (rcLevel >= 30) {
            weights.put("Air rune", 7.0 * multiplier);
            weights.put("Mind rune", 8.0 * multiplier);
            weights.put("Water rune", 9.0 * multiplier);
            weights.put("Earth rune", 11.0 * multiplier);
            weights.put("Fire rune", 12.0 * multiplier);
            weights.put("Body rune", 13.0 * multiplier);
            weights.put("Cosmic rune", 20.0 * multiplier);
            weights.put("Chaos rune", 10.0 * multiplier);
            weights.put("Astral rune", 5.0 * multiplier);
            weights.put("Nature rune", 2.5 * multiplier);
            weights.put("Law rune", 1.3 * multiplier);
            weights.put("Death rune", 0.6 * multiplier);
            weights.put("Blood rune", 0.4 * multiplier);
            weights.put("Soul rune", 0.2 * multiplier);
        } else if (rcLevel >= 20) {
            weights.put("Air rune", 12.0 * multiplier);
            weights.put("Mind rune", 13.0 * multiplier);
            weights.put("Water rune", 13.5 * multiplier);
            weights.put("Earth rune", 14.0 * multiplier);
            weights.put("Fire rune", 15.0 * multiplier);
            weights.put("Body rune", 16.0 * multiplier);
            weights.put("Cosmic rune", 8.0 * multiplier);
            weights.put("Chaos rune", 4.2 * multiplier);
            weights.put("Astral rune", 2.1 * multiplier);
            weights.put("Nature rune", 1.1 * multiplier);
            weights.put("Law rune", 0.55 * multiplier);
            weights.put("Death rune", 0.32 * multiplier);
            weights.put("Blood rune", 0.15 * multiplier);
            weights.put("Soul rune", 0.08 * multiplier);
        } else if (rcLevel >= 10) {
            weights.put("Air rune", 15.0 * multiplier);
            weights.put("Mind rune", 18.0 * multiplier);
            weights.put("Water rune", 21.0 * multiplier);
            weights.put("Earth rune", 24.0 * multiplier);
            weights.put("Fire rune", 12.0 * multiplier);
            weights.put("Body rune", 6.0 * multiplier);
            weights.put("Cosmic rune", 1.75 * multiplier);
            weights.put("Chaos rune", 0.8 * multiplier);
            weights.put("Astral rune", 0.6 * multiplier);
            weights.put("Nature rune", 0.4 * multiplier);
            weights.put("Law rune", 0.24 * multiplier);
            weights.put("Death rune", 0.12 * multiplier);
            weights.put("Blood rune", 0.06 * multiplier);
            weights.put("Soul rune", 0.03 * multiplier);
        } else {
            // 1-9
            weights.put("Air rune", 50.0 * multiplier);
            weights.put("Mind rune", 25.0 * multiplier);
            weights.put("Water rune", 12.0 * multiplier);
            weights.put("Earth rune", 6.0 * multiplier);
            weights.put("Fire rune", 3.0 * multiplier);
            weights.put("Body rune", 1.5 * multiplier);
            weights.put("Cosmic rune", 0.85 * multiplier);
            weights.put("Chaos rune", 0.6 * multiplier);
            weights.put("Astral rune", 0.45 * multiplier);
            weights.put("Nature rune", 0.3 * multiplier);
            weights.put("Law rune", 0.15 * multiplier);
            weights.put("Death rune", 0.08 * multiplier);
            weights.put("Blood rune", 0.05 * multiplier);
            weights.put("Soul rune", 0.02 * multiplier);
        }

        return weights;
    }

    public boolean isWearingFullRaiments() {
        if (plugin.getClient() == null) return false;

        ItemContainer equipment = plugin.getClient().getItemContainer(InventoryID.EQUIPMENT);
        if (equipment == null) return false;

        boolean hasHat = false, hasTop = false, hasBottoms = false, hasBoots = false;

        for (var item : equipment.getItems()) {
            if (item.getId() <= 0) continue;
            if (item.getId() == RAIMENTS_HAT) hasHat = true;
            else if (item.getId() == RAIMENTS_TOP) hasTop = true;
            else if (item.getId() == RAIMENTS_BOTTOMS) hasBottoms = true;
            else if (item.getId() == RAIMENTS_BOOTS) hasBoots = true;
        }

        return hasHat && hasTop && hasBottoms && hasBoots;
    }

    public void onFirstRuneCrafted() {
        if (runActive && !firstRuneCrafted) {
            firstRuneCrafted = true;
        }
    }

    public boolean isBettingLocked() {
        return firstRuneCrafted;
    }

    @Override
    public void onChatMessage(ChatMessage event) {
        String msg = event.getMessage();

        // Detect when player opens the Ourania bank (case-insensitive)
        if (msg.toLowerCase().contains("eniola takes your payment")) {
            log.info("[OuraniaAltar] Bank payment message detected: " + msg);
            waitingForEssenceAfterBank = true;

            if (!runActive) {
                startNewRun();
            }
            return;
        }

        if (!runActive) return;

        // Basic detection for Ourania rune crafting messages
        if (msg.contains("You bind the temple's power into")) {
            for (String option : currentRuneOptions) {
                if (msg.contains(option)) {
                    runeCraftCounts.merge(option, 1, Integer::sum);
                    break;
                }
            }
        }
    }

    @Override
    public long getElvesToGoal() {
        return 0L;
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        if (!runActive) {
            panel.getChildren().add(LineComponent.builder()
                .left("Ourania Altar")
                .right("No active run")
                .build());
            return;
        }

        panel.getChildren().add(TitleComponent.builder()
            .text("Ourania Altar Run")
            .build());

        int rcLevel = 0;
        if (plugin.getClient() != null) {
            rcLevel = plugin.getClient().getRealSkillLevel(Skill.RUNECRAFT);
        }

        panel.getChildren().add(LineComponent.builder()
            .left("Runecraft Level")
            .right(String.valueOf(rcLevel))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Essence Carried")
            .right(getTotalEssenceCarried() + " essence")
            .build());

        panel.getChildren().add(LineComponent.builder().left("").build());

        panel.getChildren().add(LineComponent.builder()
            .left("Rune Options (Odds)")
            .right(String.valueOf(currentRuneOptions.size()))
            .build());

        Map<String, Double> odds = getRuneOdds(rcLevel, isWearingFullRaiments());
        double totalWeight = odds.values().stream().mapToDouble(Double::doubleValue).sum();

        for (String option : currentRuneOptions) {
            int count = runeCraftCounts.getOrDefault(option, 0);
            double weight = odds.getOrDefault(option, 1.0);
            int probability = totalWeight > 0 ? (int) Math.round((weight / totalWeight) * 100) : 0;

            panel.getChildren().add(LineComponent.builder()
                .left("  " + option)
                .right(count + " crafted | ~" + probability + "%")
                .build());
        }

        panel.getChildren().add(LineComponent.builder().left("").build());

        panel.getChildren().add(LineComponent.builder()
            .left("Betting Locked")
            .right(isBettingLocked() ? "Yes (first rune crafted)" : "No")
            .build());

        boolean hasRaiments = isWearingFullRaiments();
        panel.getChildren().add(LineComponent.builder()
            .left("Raiments Bonus")
            .right(hasRaiments ? "Active (+35% weight)" : "Inactive")
            .build());
    }

    // === DebugInfoProvider implementation ===
    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> vars = new LinkedHashMap<>();
        vars.put("Run Active", () -> runActive);
        vars.put("Essence Carried", this::getTotalEssenceCarried);
        vars.put("Rune Options Count", () -> currentRuneOptions.size());
        vars.put("Betting Locked", this::isBettingLocked);
        vars.put("Wearing Full Raiments", this::isWearingFullRaiments());
        vars.put("First Rune Crafted", () -> firstRuneCrafted);
        vars.put("Waiting For Essence After Bank", () -> waitingForEssenceAfterBank);
        vars.put("Near Bank", this::isNearBank);
        vars.put("At Altar", this::isAtAltar);
        if (plugin.getClient() != null) {
            vars.put("Runecraft Level", () -> plugin.getClient().getRealSkillLevel(Skill.RUNECRAFT));
        }
        return vars;
    }
}