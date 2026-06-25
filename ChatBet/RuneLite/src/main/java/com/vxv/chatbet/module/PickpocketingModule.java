package com.vxv.chatbet.module;
import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.module.BetModule;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

@Slf4j
public class PickpocketingModule implements BetModule {
    private final ChatBetPlugin plugin;
    // Pickpocketing/Elves specific tracking
    private final AtomicInteger etcsObtained = new AtomicInteger(0);
    private final AtomicInteger attemptsSinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger successesSinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger dodgySinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger wineSinceLastEtc = new AtomicInteger(0);
    private final AtomicInteger dodgyConsumed = new AtomicInteger(0);
    private final AtomicInteger wineConsumed = new AtomicInteger(0);
    private final Map<Integer, Integer> lastInventoryQtys = new HashMap<>();
    private final Map<Integer, Integer> lastEquipmentQtys = new HashMap<>();
    private static final int ITEM_ETC = 23959;
    private static final int ITEM_DODGY_NECKLACE = 21143;
    private static final int ITEM_JUG_OF_WINE = 1993;
    public PickpocketingModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public String getName() {
        return "Pickpocketing (Elves)";
    }
    @Override
    public void onGameTick(GameTick event) {
        log.debug("PickpocketingModule onGameTick");
    }
    @Override
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.THIEVING) {
            log.debug("Thieving XP changed - potential success");
            // Removed duplicate increment - chat message is authoritative for successes
        }
    }
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getItemContainer() != plugin.getClient().getItemContainer(InventoryID.INVENTORY)) {
            return;
        }
        updateItemTracking(event);
    }
    private void updateItemTracking(ItemContainerChanged event) {
        ItemContainer container = event.getItemContainer();
        if (container == null) return;

        Map<Integer, Integer> currentQtys = new HashMap<>();
        for (var item : container.getItems()) {
            if (item.getId() > 0) {
                currentQtys.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }

        // Calculate deltas for key items
        int etcDelta = calculateDelta(lastInventoryQtys, currentQtys, ITEM_ETC);
        int dodgyDelta = calculateDelta(lastInventoryQtys, currentQtys, ITEM_DODGY_NECKLACE);
        int wineDelta = calculateDelta(lastInventoryQtys, currentQtys, ITEM_JUG_OF_WINE);

        if (etcDelta > 0) {
            etcsObtained.addAndGet(etcDelta);
            // Reset "since last ETC" counters when obtaining a new one
            attemptsSinceLastEtc.set(0);
            successesSinceLastEtc.set(0);
            dodgySinceLastEtc.set(0);
            wineSinceLastEtc.set(0);
        }

        if (dodgyDelta < 0) {
            int consumed = -dodgyDelta;
            dodgyConsumed.addAndGet(consumed);
            dodgySinceLastEtc.addAndGet(consumed);
        }

        if (wineDelta < 0) {
            int consumed = -wineDelta;
            wineConsumed.addAndGet(consumed);
            wineSinceLastEtc.addAndGet(consumed);
        }

        // Update last known state
        lastInventoryQtys.clear();
        lastInventoryQtys.putAll(currentQtys);
    }

    private int calculateDelta(Map<Integer, Integer> previous, Map<Integer, Integer> current, int itemId) {
        int prev = previous.getOrDefault(itemId, 0);
        int now = current.getOrDefault(itemId, 0);
        return now - prev;
    }

    @Override
    public void onChatMessage(ChatMessage event) {
        String msg = event.getMessage();
        if (msg.contains("You attempt to pick the elf's pocket.")) {
            attemptsSinceLastEtc.incrementAndGet();
            plugin.getAttempts().incrementAndGet();
        } else if (msg.contains("You pick the elf's pocket.")) {
            successesSinceLastEtc.incrementAndGet();
            plugin.getSuccesses().incrementAndGet();
        } else if (msg.contains("Your dodgy necklace protects you. It then crumbles to dust.")) {
            dodgyConsumed.incrementAndGet();
            dodgySinceLastEtc.incrementAndGet();
        }
        // Add wine if needed
        log.debug("Chat message processed in PickpocketingModule: " + msg);
    }
    public int getEtcsObtained() { return etcsObtained.get(); }
    public int getAttemptsSinceLastEtc() { return attemptsSinceLastEtc.get(); }
    public int getSuccessesSinceLastEtc() { return successesSinceLastEtc.get(); }
    public long getDodgyConsumed() { return dodgyConsumed.get(); }
    public long getWineConsumed() { return wineConsumed.get(); }
    public long getDodgySinceLastEtc() { return dodgySinceLastEtc.get(); }
    public long getWineSinceLastEtc() { return wineSinceLastEtc.get(); }
    @Override
    public long getElvesToGoal() {
        int xpNeeded = plugin.getXpToGoal();
        double xpPerElf = 353.3;
        return xpNeeded > 0 ? (long) Math.ceil(xpNeeded / xpPerElf) : 0L;
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        // XP / Elves Goal
        int goalPct = plugin.getCurrentGoalPercentage();
        panel.getChildren().add(LineComponent.builder()
            .left("XP to " + goalPct + "% Goal")
            .right(plugin.getXpToGoal() + " XP")
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Elves to Goal")
            .right(String.valueOf(getElvesToGoal()))
            .build());

        panel.getChildren().add(LineComponent.builder().left("").build()); // spacer

        // Session Stats
        panel.getChildren().add(TitleComponent.builder()
            .text("Session Since Login")
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Attempts")
            .right(String.valueOf(plugin.getAttempts()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Successes")
            .right(String.valueOf(plugin.getSuccesses()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Success Rate")
            .right(String.format("%.1f%%", plugin.getSuccessRate()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("ETCs Obtained")
            .right(String.valueOf(getEtcsObtained()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Est. ETCs to Goal")
            .right(String.format("%.2f", plugin.getEstimatedEtcsToGoal()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Actual vs Expected ETCs")
            .right(getEtcsObtained() + " / " + String.format("%.2f", plugin.getExpectedEtcs()))
            .build());

        panel.getChildren().add(LineComponent.builder().left("").build());

        // Since Last ETC
        panel.getChildren().add(TitleComponent.builder()
            .text("Since Last ETC")
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Attempts since last")
            .right(String.valueOf(getAttemptsSinceLastEtc()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Successes since last")
            .right(String.valueOf(getSuccessesSinceLastEtc()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Probability")
            .right(String.format("%.2f%%", plugin.getProbEtcFromSuccesses()))
            .build());

        panel.getChildren().add(LineComponent.builder().left("").build());

        // Consumables
        panel.getChildren().add(TitleComponent.builder()
            .text("Consumables")
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Dodgy Necklaces (total)")
            .right(String.valueOf(getDodgyConsumed()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Jugs of Wine (total)")
            .right(String.valueOf(getWineConsumed()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Dodgy since last ETC")
            .right(String.valueOf(getDodgySinceLastEtc()))
            .build());

        panel.getChildren().add(LineComponent.builder()
            .left("Wine since last ETC")
            .right(String.valueOf(getWineSinceLastEtc()))
            .build());
    }

    // === DebugInfoProvider implementation ===
    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> vars = new LinkedHashMap<>();
        vars.put("ETCs Obtained", this::getEtcsObtained);
        vars.put("Attempts Since Last ETC", this::getAttemptsSinceLastEtc());
        vars.put("Successes Since Last ETC", this::getSuccessesSinceLastEtc());
        vars.put("Dodgy Consumed", this::getDodgyConsumed());
        vars.put("Wine Consumed", this::getWineConsumed());
        vars.put("Dodgy Since Last ETC", this::getDodgySinceLastEtc());
        vars.put("Wine Since Last ETC", this::getWineSinceLastEtc());
        vars.put("Elves To Goal", this::getElvesToGoal());
        vars.put("Success Rate %", () -> String.format("%.1f", plugin.getSuccessRate()));
        vars.put("XP to Goal", plugin::getXpToGoal());
        if (plugin.getClient() != null) {
            vars.put("Thieving Level", () -> plugin.getClient().getRealSkillLevel(Skill.THIEVING));
        }
        return vars;
    }

    // Add getters/setters for plugin delegation
}