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
        boolean hasActiveTask = activeTask != null && !activeTask.isEmpty() && !"None".equals(activeTask);

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("ChatBet")
            .build());

        if (!hasActiveTask) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Select a task in the side panel to view stats")
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

        // Task-specific stats now come from the active BetModule (Pickpocketing, Ourania, etc.)
        // This removes the hardcoded elf pickpocketing UI when another module is enabled.
        if (plugin.getActiveModule() != null) {
            plugin.getActiveModule().contributeToOverlay(panelComponent);
        }

        // Token Balances (general section - useful for all modules)
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

        return panelComponent.render(graphics);
    }
}