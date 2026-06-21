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
    private final ChatBetConfig config;
    private final ChatBetPlugin plugin;

    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public ChatBetOverlay(ChatBetConfig config, ChatBetPlugin plugin)
    {
        this.config = config;
        this.plugin = plugin;
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

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("ChatBet - Elf Pickpocket Tracker")
            .build());

        // XP Goal Section
        panelComponent.getChildren().add(LineComponent.builder()
            .left("XP to 30% Goal Mark")
            .right(plugin.getXpToThirtyPct() + " XP")
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Successes Remaining")
            .right(String.valueOf(plugin.getElvesToThirtyPct()))
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
            .left("Est. ETCs by 30% Mark")
            .right(String.format("%.2f", plugin.getEstimatedEtcsToThirtyPct()))
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