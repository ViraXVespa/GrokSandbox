package com.vxv.chatbet;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@PluginDescriptor(
    name = "ChatBet",
    description = "Tracks elf pickpocketing for Thieving goals + ETC odds/probabilities. Integrates with XP Tracker goals. Built for stream tracking.",
    tags = {"thieving", "pickpocket", "elf", "prifddinas", "etc", "probability", "tracker", "stream"}
)
@Slf4j
public class ChatBetPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ChatBetConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ChatBetOverlay overlay;

    @Inject
    private com.google.inject.Injector injector;

    @Inject
    private net.runelite.client.plugins.PluginManager pluginManager;

    // Lazily fetched (may remain null)
    private net.runelite.client.plugins.xptracker.XpTrackerService xpTrackerService;
    private boolean xpTrackerServiceLookupAttempted = false;

    // --- Session counters (since login / plugin enable) ---
    @Getter private final AtomicInteger attempts = new AtomicInteger(0);
    @Getter private final AtomicInteger successes = new AtomicInteger(0);
    @Getter private final AtomicInteger etcsObtained = new AtomicInteger(0);

    @Getter private final AtomicInteger attemptsSinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger successesSinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger dodgyConsumed = new AtomicInteger(0);
    @Getter private final AtomicInteger wineConsumed = new AtomicInteger(0);
    @Getter private final AtomicInteger dodgySinceLastEtc = new AtomicInteger(0);
    @Getter private final AtomicInteger wineSinceLastEtc = new AtomicInteger(0);

    // Snapshot at login/start
    private int loginThievingXp = 0;

    // Goal data read from VARPs (via XpTrackerPlugin)
    private int cachedGoalStartXp = 0;
    private int cachedGoalEndXp = 0;
    private boolean goalDataInitialized = false;

    // For delta tracking
    private int lastThievingXp = 0;

    // Inventory quantity tracking for deltas (itemId -> last known qty)
    private final Map<Integer, Integer> lastInventoryQtys = new HashMap<>();
    private final Map<Integer, Integer> lastEquipmentQtys = new HashMap<>();

    // Item IDs (from OSRS Wiki / game data)
    private static final int ITEM_ETC = 23959;           // Enhanced crystal teleport seed
    private static final int ITEM_DODGY_NECKLACE = 21143;
    private static final int ITEM_JUG_OF_WINE = 1993;

    private static final double XP_PER_SUCCESS = 353.3; // From wiki

    @Provides
    ChatBetConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ChatBetConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        overlayManager.add(overlay);
        resetSession();
        snapshotLoginState();
        log.info("ChatBet started - tracking elf pickpocketing + ETC odds");
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(overlay);
        log.info("ChatBet stopped");
    }

    private void resetSession()
    {
        attempts.set(0);
        successes.set(0);
        etcsObtained.set(0);
        attemptsSinceLastEtc.set(0);
        successesSinceLastEtc.set(0);
        dodgyConsumed.set(0);
        wineConsumed.set(0);
        dodgySinceLastEtc.set(0);
        wineSinceLastEtc.set(0);
        lastInventoryQtys.clear();
        lastEquipmentQtys.clear();

        // Don't blindly set to 0 — snapshotLoginState() will set it to current XP right after
        // This prevents huge deltas on the first StatChanged after reset
        lastThievingXp = 0;
    }

    private void snapshotLoginState()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return;
        }

        loginThievingXp = client.getSkillExperience(Skill.THIEVING);
        lastThievingXp = loginThievingXp;

        // Try to read goal data directly from VARPs (via XpTrackerPlugin helpers)
        updateGoalDataFromVarps();

        goalDataInitialized = true;

        // Initial inventory snapshot
        snapshotInventory();
    }

    /**
     * Attempts to read current Thieving goal start/end XP directly from client VARPs
     * by using the same helper methods that XpTrackerPlugin uses.
     */
    private void updateGoalDataFromVarps()
    {
        if (pluginManager == null) return;

        for (Plugin p : pluginManager.getPlugins())
        {
            if (p instanceof net.runelite.client.plugins.xptracker.XpTrackerPlugin)
            {
                try
                {
                    // Use reflection to call the package-private helper methods
                    java.lang.reflect.Method startMethod = p.getClass().getDeclaredMethod("startGoalVarpForSkill", Skill.class);
                    java.lang.reflect.Method endMethod = p.getClass().getDeclaredMethod("endGoalVarpForSkill", Skill.class);

                    startMethod.setAccessible(true);
                    endMethod.setAccessible(true);

                    int startVarp = (int) startMethod.invoke(p, Skill.THIEVING);
                    int endVarp = (int) endMethod.invoke(p, Skill.THIEVING);

                    int startXp = client.getVarpValue(startVarp);
                    int endXp = client.getVarpValue(endVarp);

                    if (endXp > startXp)
                    {
                        cachedGoalStartXp = startXp;
                        cachedGoalEndXp = endXp;
                        log.debug("Read goal from VARPs: start={} end={}", startXp, endXp);
                        return;
                    }
                }
                catch (Exception e)
                {
                    log.debug("Failed to read goal VARPs via reflection", e);
                }
            }
        }
    }

    /**
     * Lazily attempts to obtain XpTrackerService from the injector.
     * This avoids Guice binding issues at plugin instantiation time.
     */
    private net.runelite.client.plugins.xptracker.XpTrackerService getXpTrackerService()
    {
        if (xpTrackerService != null)
        {
            return xpTrackerService;
        }

        if (xpTrackerServiceLookupAttempted)
        {
            return xpTrackerService;
        }

        xpTrackerServiceLookupAttempted = true;

        // Try via PluginManager first (more reliable for external plugins)
        if (pluginManager != null)
        {
            for (Plugin p : pluginManager.getPlugins())
            {
                if (p instanceof net.runelite.client.plugins.xptracker.XpTrackerPlugin)
                {
                    // XpTrackerPlugin does not publicly expose its service.
                    // We'll rely on our improved fallback calculation instead.
                    log.debug("XpTrackerPlugin detected — using local goal estimation");
                }
            }
        }

        // Fallback to direct injector lookup
        if (injector != null)
        {
            try
            {
                xpTrackerService = injector.getInstance(net.runelite.client.plugins.xptracker.XpTrackerService.class);
                log.debug("Successfully obtained XpTrackerService via injector");
            }
            catch (Exception e)
            {
                log.debug("XpTrackerService not available (using fallback goal calculation)");
            }
        }

        return xpTrackerService;
    }

    private void snapshotInventory()
    {
        lastInventoryQtys.clear();
        lastEquipmentQtys.clear();

        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv != null)
        {
            for (Item item : inv.getItems())
            {
                if (item != null && item.getId() > 0)
                {
                    lastInventoryQtys.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equip != null)
        {
            for (Item item : equip.getItems())
            {
                if (item != null && item.getId() > 0)
                {
                    lastEquipmentQtys.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();

        if (state == GameState.LOGGED_IN)
        {
            if (config.resetOnLogin())
            {
                resetSession();
                log.debug("Session counters reset on login (config enabled)");
            }
            snapshotLoginState();
        }
        else if (state == GameState.LOGGING_IN || state == GameState.CONNECTION_LOST)
        {
            // Extra safety reset on login/connection issues
            if (config.resetOnLogin())
            {
                resetSession();
                log.debug("Session counters reset due to login/connection state change");
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // Try to initialize goal data once after login
        if (!goalDataInitialized && client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
        {
            snapshotLoginState();
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (event.getSkill() != Skill.THIEVING)
        {
            return;
        }

        int currentXp = event.getXp();
        int delta = currentXp - lastThievingXp;

        if (delta > 0)
        {
            // Guard against huge deltas right after login/reset (lastThievingXp may be stale or 0)
            if (lastThievingXp == 0 || lastThievingXp > currentXp)
            {
                lastThievingXp = currentXp;
                return;
            }

            // Count successes based on XP (most reliable for pickpocketing)
            int newSuccesses = (int) Math.round(delta / XP_PER_SUCCESS);
            if (newSuccesses > 0)
            {
                successes.addAndGet(newSuccesses);
                successesSinceLastEtc.addAndGet(newSuccesses);

                // Option A: Count successes toward attempts (Attempts ≈ Successes + detected fails from chat)
                attempts.addAndGet(newSuccesses);
                attemptsSinceLastEtc.addAndGet(newSuccesses);

                log.debug("Thieving XP gain detected: +{} XP → {} new successes (total successes={})",
                    delta, newSuccesses, successes.get());
            }
        }

        lastThievingXp = currentXp;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String msg = event.getMessage().toLowerCase();

        // Detect pickpocket attempts/fails - made stricter to reduce noise during login/sync
        boolean isPickpocketMsg = msg.contains("pickpocket") || msg.contains("fail to pick");
        boolean isStunMsg = msg.contains("stunned");

        if ((isPickpocketMsg && msg.contains("elf")) || (isStunMsg && (isPickpocketMsg || msg.contains("thieving"))))
        {
            attempts.incrementAndGet();
            attemptsSinceLastEtc.incrementAndGet();

            log.debug("Chat pickpocket/stun message detected → attempts incremented (total={})", attempts.get());
        }

        // Optional: detect dodgy necklace save (common message)
        if (msg.contains("dodgy necklace"))
        {
            // Could be a save - we still count the fail attempt above
        }

        // === Consumables via specific chat messages ===
        if (msg.contains("you drink the wine."))
        {
            wineConsumed.incrementAndGet();
            wineSinceLastEtc.incrementAndGet();
        }

        if (msg.contains("your dodgy necklace protects you. it then crumbles to dust."))
        {
            dodgyConsumed.incrementAndGet();
            dodgySinceLastEtc.incrementAndGet();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        InventoryID id = event.getContainerId() == InventoryID.INVENTORY.getId() ? InventoryID.INVENTORY :
                         (event.getContainerId() == InventoryID.EQUIPMENT.getId() ? InventoryID.EQUIPMENT : null);

        if (id == null)
        {
            return;
        }

        Map<Integer, Integer> lastQtys = (id == InventoryID.INVENTORY) ? lastInventoryQtys : lastEquipmentQtys;
        ItemContainer container = client.getItemContainer(id);
        if (container == null)
        {
            return;
        }

        Map<Integer, Integer> currentQtys = new HashMap<>();
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() > 0)
            {
                currentQtys.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        // Check tracked items
        checkDelta(lastQtys, currentQtys, ITEM_ETC, true);      // ETC obtained only
        // Wine and Dodgy Necklace are now tracked exclusively via chat messages (more accurate)

        // Update last
        lastQtys.clear();
        lastQtys.putAll(currentQtys);
    }

    private void checkDelta(Map<Integer, Integer> oldQtys, Map<Integer, Integer> newQtys, int itemId, boolean isObtain)
    {
        int oldQty = oldQtys.getOrDefault(itemId, 0);
        int newQty = newQtys.getOrDefault(itemId, 0);
        int delta = newQty - oldQty;

        if (delta == 0)
        {
            return;
        }

        if (isObtain && delta > 0 && itemId == ITEM_ETC)
        {
            etcsObtained.addAndGet(delta);
            // Reset "since last ETC" counters when we obtain one (or more)
            attemptsSinceLastEtc.set(0);
            successesSinceLastEtc.set(0);
            dodgySinceLastEtc.set(0);
            wineSinceLastEtc.set(0);
            log.debug("ETC obtained! Resetting since-last counters.");
        }
        else if (!isObtain && delta < 0)
        {
            int consumed = -delta;
            if (itemId == ITEM_DODGY_NECKLACE)
            {
                dodgyConsumed.addAndGet(consumed);
                dodgySinceLastEtc.addAndGet(consumed);
            }
            else if (itemId == ITEM_JUG_OF_WINE)
            {
                wineConsumed.addAndGet(consumed);
                wineSinceLastEtc.addAndGet(consumed);
            }
        }
    }

    // --- Calculated getters for overlay ---

    public int getXpToThirtyPct()
    {
        if (client == null) return 0;

        int currentXp = client.getSkillExperience(Skill.THIEVING);

        int start = cachedGoalStartXp;
        int end = cachedGoalEndXp;

        if (end > start && end > currentXp && start < currentXp)
        {
            double totalDist = end - start;
            double currentProgress = (currentXp - start) / totalDist;

            if (currentProgress >= 0.30) return 0;

            double thirtyPctMark = start + 0.30 * totalDist;
            return (int) Math.ceil(thirtyPctMark - currentXp);
        }

        // Fallback to config target
        int target = config.thievingGoalXp();
        if (target > currentXp && currentXp > 0)
        {
            double remaining = target - currentXp;
            return (int) Math.max(1, Math.ceil(remaining * 0.20));
        }

        return 0;
    }

    public int getElvesToThirtyPct()
    {
        int xpNeeded = getXpToThirtyPct();
        if (xpNeeded <= 0)
        {
            return 0;
        }
        return (int) Math.ceil(xpNeeded / XP_PER_SUCCESS);
    }

    public double getSuccessRate()
    {
        int att = attempts.get();
        int suc = successes.get();
        if (att == 0)
        {
            return 0.0;
        }
        return (suc * 100.0) / att;
    }

    public double getEstimatedEtcsToThirtyPct()
    {
        int elvesNeeded = getElvesToThirtyPct();
        if (elvesNeeded <= 0)
        {
            return 0;
        }
        double p = 1.0 / Math.max(1, config.etcRate());
        return elvesNeeded * p;
    }

    public double getExpectedEtcs()
    {
        int suc = successes.get();
        if (suc == 0)
        {
            return 0;
        }
        double p = 1.0 / Math.max(1, config.etcRate());
        return suc * p;
    }

    public double getProbEtcFromAttempts()
    {
        int n = attemptsSinceLastEtc.get();
        if (n <= 0)
        {
            return 0;
        }
        // Use attempts or successes? Wiki indicates per successful pickpocket, but user asked for attempts too.
        // For conservatism we'll use a blended or successes-based. Here we use attempts with same p for simplicity.
        double p = 1.0 / Math.max(1, config.etcRate());
        return (1 - Math.pow(1 - p, n)) * 100.0;
    }

    public double getProbEtcFromSuccesses()
    {
        int n = successesSinceLastEtc.get();
        if (n <= 0)
        {
            return 0;
        }
        double p = 1.0 / Math.max(1, config.etcRate());
        return (1 - Math.pow(1 - p, n)) * 100.0;
    }

    // Expose raw counters for overlay (already @Getter on atomics)
}