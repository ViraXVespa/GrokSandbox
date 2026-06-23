package com.vxv.chatbet;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class ChatBetOverlay extends Overlay
{
    @Inject
    private ChatBetConfig config;

    @Inject
    private ChatBetPlugin plugin;

    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public ChatBetOverlay()
    {
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(280, 0));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 150));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        String activeTask = plugin.getActiveTaskName();
        boolean isPickpocketingTask = activeTask != null && activeTask.contains("Pickpocketing");

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("ChatBet" + (isPickpocketingTask ? " - Pickpocketing Elves" : ""))
            .build());

        if (!isPickpocketingTask) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Select Pickpocketing Elves task in side panel to view stats")
                .build());
            return panelComponent.render(graphics);
        }

        // Active Bets Section
        var activePolls = plugin.getActivePolls();
        if (!activePolls.isEmpty())
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Active Bets")
                .build());

            for (var poll : activePolls)
            {
                String shortQuestion = poll.getQuestion().length() > 35 
                    ? poll.getQuestion().substring(0, 32) + "..." 
                    : poll.getQuestion();

                panelComponent.getChildren().add(LineComponent.builder()
                    .left("#" + poll.getId() + " [" + poll.getType() + "]")
                    .right(shortQuestion)
                    .build());
            }

            panelComponent.getChildren().add(LineComponent.builder().left("").build()); // spacer
        }

        // XP Goal Section (now dynamic)
        int goalPct = plugin.getCurrentGoalPercentage();
        panelComponent.getChildren().add(LineComponent.builder()
            .left("XP to " + goalPct + "% Goal")
            .right(plugin.getXpToGoal() + " XP")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Elves to Goal")
            .right(String.valueOf(plugin.getElvesToGoal()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder().left("").build()); // spacer

        // Session Stats
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Session Since Login")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Attempts")
            .right(String.valueOf(plugin.getAttempts()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Successes")
            .right(String.valueOf(plugin.getSuccesses()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Success Rate")
            .right(String.format("%.1f%%", plugin.getSuccessRate()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("ETCs Obtained")
            .right(String.valueOf(plugin.getEtcsObtained()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Est. ETCs to Goal")
            .right(String.format("%.2f", plugin.getEstimatedEtcsToGoal()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Actual vs Expected ETCs")
            .right(plugin.getEtcsObtained() + " / " + String.format("%.2f", plugin.getExpectedEtcs()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder().left("").build());

        // Since Last ETC
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Since Last ETC")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Attempts since last")
            .right(String.valueOf(plugin.getAttemptsSinceLastEtc()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Successes since last")
            .right(String.valueOf(plugin.getSuccessesSinceLastEtc()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Probability")
            .right(String.format("%.2f%%", plugin.getProbEtcFromSuccesses()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder().left("").build());

        // Consumables
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Consumables")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Dodgy Necklaces (total)")
            .right(String.valueOf(plugin.getDodgyConsumed()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Jugs of Wine (total)")
            .right(String.valueOf(plugin.getWineConsumed()))
            .build());

        // Token Balances
        panelComponent.getChildren().add(LineComponent.builder().left("").build());

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Top Balances")
            .build());

        var topBalances = plugin.getTopBalances(3);
        int rank = 1;
        for (var entry : topBalances) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left(rank + ". " + entry.getKey())
                .right(String.valueOf(entry.getValue()) + " tokens")
                .build());
            rank++;
        }

        var recentRequests = plugin.getRecentBalanceRequests();
        if (!recentRequests.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder().left("").build());
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Recent Checks")
                .build());

            for (String user : recentRequests) {
                long bal = plugin.getBalance(user);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(user)
                    .right(bal + " tokens")
                    .build());
            }
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Dodgy since last ETC")
            .right(String.valueOf(plugin.getDodgySinceLastEtc()))
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Wine since last ETC")
            .right(String.valueOf(plugin.getWineSinceLastEtc()))
            .build());

        return panelComponent.render(graphics);
    }
}