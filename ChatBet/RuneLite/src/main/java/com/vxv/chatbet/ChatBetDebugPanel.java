package com.vxv.chatbet;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import com.vxv.chatbet.debug.DebugInfoProvider;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ChatBetDebugPanel extends PluginPanel {

    private final ChatBetPlugin plugin;
    private JTextArea debugArea;

    // Cached getters discovered via reflection (fallback only)
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

        boolean hasActiveModule = plugin.getActiveModule() != null;
        Object currentTarget = hasActiveModule
                ? plugin.getActiveModule()
                : (Object) plugin;

        StringBuilder sb = new StringBuilder();
        if (hasActiveModule) {
            sb.append("=== Active Module: ")
              .append(plugin.getActiveTaskName())
              .append(" ===\n\n");
        } else {
            sb.append("=== ChatBetPlugin (No Active Task) ===\n\n");
        }

        // Preferred path: Use explicit DebugInfoProvider if available
        if (currentTarget instanceof DebugInfoProvider) {
            DebugInfoProvider provider = (DebugInfoProvider) currentTarget;
            Map<String, Supplier<Object>> vars = provider.getDebugVariables();

            if (vars != null && !vars.isEmpty()) {
                for (Map.Entry<String, Supplier<Object>> entry : vars.entrySet()) {
                    try {
                        Object value = entry.getValue().get();
                        sb.append(entry.getKey()).append(": ").append(value).append("\n");
                    } catch (Exception ignored) {}
                }
                debugArea.setText(sb.toString());
                return; // Done - no need for reflection fallback
            }
        }

        // Fallback: reflection-based discovery (for backward compatibility during transition)
        if (lastWatchedTarget != currentTarget) {
            cachedGetters.clear();
            discoverGetters(currentTarget);
            lastWatchedTarget = currentTarget;
        }

        for (Method m : cachedGetters) {
            try {
                Object value = m.invoke(currentTarget);
                sb.append(m.getName()).append(": ").append(value).append("\n");
            } catch (Exception ignored) {}
        }

        debugArea.setText(sb.toString());
    }

    private void discoverGetters(Object target) {
        if (target == null) return;

        for (Method m : target.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;

            String name = m.getName();
            if ((name.startsWith("get") || name.startsWith("is"))) {
                if (m.getDeclaringClass() == Object.class) continue;
                if (name.equals("getClass") || name.equals("hashCode") ||
                    name.equals("equals") || name.equals("toString") ||
                    name.equals("clone") || name.equals("finalize")) continue;
                if (name.startsWith("notify") || name.startsWith("wait")) continue;

                cachedGetters.add(m);
            }
        }
    }

    public JTextArea getDebugArea() {
        return debugArea;
    }
}