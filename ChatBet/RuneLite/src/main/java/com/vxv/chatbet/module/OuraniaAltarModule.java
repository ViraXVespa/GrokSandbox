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

import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

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
    private WorldPoint lastPlayerPosition = null;

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

        WorldPoint playerLoc = plugin.getClient().getLocalPlayer().getWorldLocation();
        if (playerLoc == null) return;

        boolean nearBank = playerLoc.distanceTo(OURANIA_BANK) < 15;
        boolean atAltar = playerLoc.distanceTo(OURANIA_ALTAR) < 10;

        // === New reliable triggering logic ===
        if (waitingForEssenceAfterBank && nearBank) {
            if (lastPlayerPosition != null && !playerLoc.equals(lastPlayerPosition)) {
                // Player has moved since we saw the bank payment message
                if (!runActive && (hasEssenceInInventory() || totalEssenceCarried.get() > 0)) {
                    startNewRun();
                    waitingForEssenceAfterBank = false;
                    lastPlayerPosition = null;
                    return;
                }
            }
            lastPlayerPosition = playerLoc;
        }

        if (!nearBank && !atAltar) {
            waitingForEssenceAfterBank = false;
            lastPlayerPosition = null;
        }

        // Existing auto-resolution when leaving the area
        if (runActive && !nearBank && !atAltar) {
            resolveCurrentRun(-1);
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

        // Start run if essence added while near bank or at altar (fast path)
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
                .map(Map.Entry::getKey())
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

        return options;
    }

    public Map<String, Double> getRuneOdds(int rcLevel, boolean wearingFullRaiments) {
        List<String> options = getRuneOptionsForLevel(rcLevel);
        Map<String, Double> weights = new HashMap<>();

        double base = 1.0;
        double setBonus = wearingFullRaiments ? 0.35 : 0.0;

        for (String option : options) {
            weights.put(option, base + setBonus);
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

        // Detect when player opens the Ourania bank (partial match - message can vary)
        if (msg.contains("Eniola takes your payment")) {
            waitingForEssenceAfterBank = true;
            lastPlayerPosition = null;
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
        vars.put("Wearing Full Raiments", this::isWearingFullRaiments);
        vars.put("First Rune Crafted", () -> firstRuneCrafted);
        return vars;
    }
}