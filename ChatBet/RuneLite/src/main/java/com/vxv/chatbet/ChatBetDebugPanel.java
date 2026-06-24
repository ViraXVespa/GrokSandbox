package com.vxv.chatbet;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ChatBetDebugPanel extends PluginPanel {

    private final ChatBetPlugin plugin;
    private JTextArea debugArea;

    // Cached getters discovered via reflection (populated once per target change)
    private final List<Method> cachedGetters = new ArrayList<>();
    private Object lastWatchedTarget = null;

    @Inject
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

        Object currentTarget = (plugin.getActiveModule() != null)
                ? plugin.getActiveModule()
                : plugin;

        // Only perform reflection when the watched object changes
        if (lastWatchedTarget != currentTarget) {
            cachedGetters.clear();
            discoverGetters(currentTarget);
            lastWatchedTarget = currentTarget;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== ChatBet Live Debug ===\n\n");

        for (Method m : cachedGetters) {
            try {
                Object value = m.invoke(currentTarget);
                sb.append(m.getName()).append(": ").append(value).append("\n");
            } catch (Exception ignored) {
                // Skip getters that fail
            }
        }

        debugArea.setText(sb.toString());
    }

    private void discoverGetters(Object target) {
        if (target == null) return;

        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;

            String name = m.getName();
            if ((name.startsWith("get") || name.startsWith("is")) && !name.equals("getClass")) {
                cachedGetters.add(m);
            }
        }
    }

    public JTextArea getDebugArea() {
        return debugArea;
    }
}