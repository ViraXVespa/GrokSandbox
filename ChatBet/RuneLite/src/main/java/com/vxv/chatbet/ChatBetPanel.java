package com.vxv.chatbet;

import com.vxv.chatbet.module.PickpocketingModule;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

public class ChatBetPanel extends OverlayPanel {
    private final ChatBetPlugin plugin;

    @Inject
    public ChatBetPanel(ChatBetPlugin plugin) {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        getChildren().clear();  // Use inherited panel component

        String selectedTask = plugin.getSelectedTask();
        getChildren().add(TitleComponent.builder()
                .text("ChatBet - " + selectedTask)
                .color(Color.WHITE)
                .build());

        if ("Pickpocketing (Elves)".equals(selectedTask)) {
            PickpocketingModule module = plugin.getPickpocketingModule();
            getChildren().add(LineComponent.builder()
                    .left("Attempts:")
                    .right(String.valueOf(module.getAttemptsSinceLastEtc()))
                    .build());
            getChildren().add(LineComponent.builder()
                    .left("Successes:")
                    .right(String.valueOf(module.getSuccessesSinceLastEtc()))
                    .build());
            // Add more pickpocketing specific UI
            getChildren().add(LineComponent.builder()
                    .left("ETCs:")
                    .right(String.valueOf(module.getEtcsObtained()))
                    .build());
            getChildren().add(LineComponent.builder()
                    .left("Elves to Goal:")
                    .right(String.valueOf(module.getElvesToGoal()))
                    .build());
        } else {
            // Hide pickpocketing UI by not showing specific content or clear panel
            getChildren().add(LineComponent.builder()
                    .left("Select a task in the side panel to view stats")
                    .build());
        }

        return super.render(graphics);
    }
}