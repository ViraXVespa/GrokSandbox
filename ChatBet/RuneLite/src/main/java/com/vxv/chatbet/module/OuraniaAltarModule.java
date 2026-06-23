package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    public OuraniaAltarModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Ourania Altar Runes";
    }

    @Override
    public void onGameTick(GameTick event) {
        if (plugin.getClient() != null) {
            WorldPoint playerLoc = plugin.getClient().getLocalPlayer().getWorldLocation();
            if (playerLoc != null) {
                boolean nearBank = playerLoc.distanceTo(OURANIA_BANK) < 15;
                boolean atAltar = playerLoc.distanceTo(OURANIA_ALTAR) < 10;

                if (nearBank) {
                    // Player is near Ourania altar bank area
                    // TODO: Add bank-close detection + run start logic here
                }

                // Automatic resolution on run end: player left the Ourania area
                if (runActive && !nearBank && !atAltar) {
                    resolveCurrentRun(-1); // -1 means auto-detect most crafted
                }
            }
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

        // Update total carried (simple accumulation for now)
        if (pureDelta > 0) totalEssenceCarried.addAndGet(pureDelta);
        if (daeyaltDelta > 0) totalEssenceCarried.addAndGet(daeyaltDelta);

        // Start run if essence added while near bank or at altar
        if ((pureDelta > 0 || daeyaltDelta > 0) && (isNearBank() || isAtAltar()) && !runActive) {
            startNewRun();
        }

        // Save current state for next comparison
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

        // Track pouch item quantities (foundation for essence-inside tracking).
        // Full essence capacity + fill/empty logic can be added in a later atomic commit.
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

    /**
     * Helper to calculate the difference in quantity for a specific item ID
     * between two maps (previous vs current).
     */
    private int calculateDelta(Map<Integer, Integer> previous, Map<Integer, Integer> current, int itemId) {
        int prev = previous.getOrDefault(itemId, 0);
        int now = current.getOrDefault(itemId, 0);
        return now - prev;
    }

    private boolean isNearBank() {
        if (plugin.getClient() == null || plugin.getClient().getLocalPlayer() == null) {
            return false;
        }
        WorldPoint playerLoc = plugin.getClient().getLocalPlayer().getWorldLocation();
        return playerLoc != null && playerLoc.distanceTo(OURANIA_BANK) < 15;
    }

    private boolean isAtAltar() {
        if (plugin.getClient() == null || plugin.getClient().getLocalPlayer() == null) {
            return false;
        }
        WorldPoint playerLoc = plugin.getClient().getLocalPlayer().getWorldLocation();
        return playerLoc != null && playerLoc.distanceTo(OURANIA_ALTAR) < 10;
    }

    // Kept for compatibility; prefer isNearBank() or isAtAltar() in new code
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
        // totalEssenceCarried can be reset here if desired in a future commit
    }

    /**
     * Resolves the current Ourania run poll using the actual most-crafted rune.
     * If winningOptionIndex >= 0, uses that index. Otherwise auto-detects from runeCraftCounts.
     */
    public void resolveCurrentRun(int winningOptionIndex) {
        if (!runActive) return;

        int finalIndex = winningOptionIndex;

        if (finalIndex < 0 && !runeCraftCounts.isEmpty()) {
            // Find the rune with the highest craft count
            String mostCrafted = runeCraftCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey())
                .orElse(null);

            if (mostCrafted != null) {
                finalIndex = currentRuneOptions.indexOf(mostCrafted);
                if (finalIndex < 0) finalIndex = 0;
            }
        }

        if (finalIndex < 0) finalIndex = 0; // fallback

        plugin.resolveOuraniaPoll(finalIndex);
        endCurrentRun();
    }

    public List<String> getCurrentRuneOptions() {
        return new ArrayList<>(currentRuneOptions);
    }

    private List<String> getRuneOptionsForLevel(int level) {
        List<String> options = new ArrayList<>();

        // Common Ourania runes (simplified for now)
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

    /**
     * Returns odds/weights for the current rune options.
     * Applies a small bias when the player is wearing the full Raiments of the Eye set
     * (representing the set's extra rune chance on Ourania).
     */
    public Map<String, Double> getRuneOdds(int rcLevel, boolean wearingFullRaiments) {
        List<String> options = getRuneOptionsForLevel(rcLevel);
        Map<String, Double> weights = new HashMap<>();

        double base = 1.0;
        double setBonus = wearingFullRaiments ? 0.35 : 0.0; // small bias from the set effect

        for (String option : options) {
            weights.put(option, base + setBonus);
        }

        return weights;
    }

    /**
     * Checks if the player is wearing the full Raiments of the Eye set.
     * Useful for future odds weighting and other set bonuses.
     */
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

    /**
     * Called when the first rune is crafted this run.
     * Locks further betting for the remainder of the run.
     */
    public void onFirstRuneCrafted() {
        if (runActive && !firstRuneCrafted) {
            firstRuneCrafted = true;
            // Betting is now locked for this run
        }
    }

    public boolean isBettingLocked() {
        return firstRuneCrafted;
    }

    @Override
    public void onChatMessage(ChatMessage event) {
        if (!runActive) return;

        String msg = event.getMessage();
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
        return 0L; // Not applicable for this module
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

        // Active run header
        panel.getChildren().add(TitleComponent.builder()
            .text("Ourania Altar Run")
            .build());

        // Current RC level
        int rcLevel = 0;
        if (plugin.getClient() != null) {
            rcLevel = plugin.getClient().getRealSkillLevel(Skill.RUNECRAFT);
        }
        panel.getChildren().add(LineComponent.builder()
            .left("Runecraft Level")
            .right(String.valueOf(rcLevel))
            .build());

        // Total essence carried this run
        panel.getChildren().add(LineComponent.builder()
            .left("Essence Carried")
            .right(totalEssenceCarried.get() + " essence")
            .build());

        panel.getChildren().add(LineComponent.builder().left("").build()); // spacer

        // Current rune options (what the poll is on)
        panel.getChildren().add(LineComponent.builder()
            .left("Rune Options")
            .right(String.valueOf(currentRuneOptions.size()))
            .build());

        for (String option : currentRuneOptions) {
            int count = runeCraftCounts.getOrDefault(option, 0);
            panel.getChildren().add(LineComponent.builder()
                .left("  " + option)
                .right(count + " crafted")
                .build());
        }

        panel.getChildren().add(LineComponent.builder().left("").build()); // spacer

        // Betting status
        panel.getChildren().add(LineComponent.builder()
            .left("Betting Locked")
            .right(isBettingLocked() ? "Yes (first rune crafted)" : "No")
            .build());

        // Raiments bonus
        boolean hasRaiments = isWearingFullRaiments();
        panel.getChildren().add(LineComponent.builder()
            .left("Raiments Bonus")
            .right(hasRaiments ? "Active (+35% weight)" : "Inactive")
            .build());
    }

    // Future getters for poll odds, current rune counts, etc. will go here
}