package com.vxv.chatbet;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

public class ChatBetDebugPanel extends PluginPanel {

    private final ChatBetPlugin plugin;

    private JTextArea debugArea;

    public ChatBetDebugPanel(ChatBetPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("ChatBet Debug");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);

        debugArea = new JTextArea();
        debugArea.setEditable(false);
        debugArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        debugArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        debugArea.setFont(FontManager.getRunescapeSmallFont());
        debugArea.setLineWrap(true);
        debugArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(debugArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void refreshDebugInfo() {
        if (debugArea == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Active Task: ").append(plugin.getActiveTaskName()).append("\n");
        sb.append("Current Goal %: ").append(plugin.getCurrentGoalPercentage()).append("\n");
        sb.append("Module Active: ").append(plugin.getActiveModule() != null).append("\n\n");
        sb.append("(More values coming in next commits)");

        debugArea.setText(sb.toString());
    }

    public JTextArea getDebugArea() {
        return debugArea;
    }
}