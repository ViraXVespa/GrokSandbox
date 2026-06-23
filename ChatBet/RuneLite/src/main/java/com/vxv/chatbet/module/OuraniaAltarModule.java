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
            if (playerLoc != null && playerLoc.distanceTo(OURANIA_BANK) < 15) {
                // Player is near Ourania altar bank area
                // TODO: Add bank-close detection + run start logic here
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

        // Start run if essence added while near bank
        if ((pureDelta > 0 || daeyaltDelta > 0) && isNearBank() && !runActive) {
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

        int rcLevel = 0;
        if (plugin.getClient() != null) {
            rcLevel = plugin.getClient().getRealSkillLevel(Skill.RUNECRAFT);
        }

        currentRuneOptions = getRuneOptionsForLevel(rcLevel);

        plugin.createOuraniaPoll(currentRuneOptions);
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
        // TODO: Detect bank close, rune crafting messages, etc.
    }

    @Override
    public long getElvesToGoal() {
        return 0L; // Not applicable for this module
    }

    // Future getters for poll odds, current rune counts, etc. will go here
}