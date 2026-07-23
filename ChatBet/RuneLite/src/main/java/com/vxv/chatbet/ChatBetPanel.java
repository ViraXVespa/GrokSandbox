package com.vxv.chatbet;

import com.vxv.chatbet.module.ModuleCatalog;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Side panel: scrollable catalog of every automated / specialised ChatBet module.
 */
public class ChatBetPanel extends PluginPanel {

    private final ChatBetPlugin plugin;

    private JPanel mainPanel;
    private JPanel goalPanel;
    private JPanel listHost;

    private JLabel activeTaskLabel;
    private JSlider goalSlider;
    private JLabel goalValueLabel;

    private String currentTask = "Pickpocketing Elves";
    private int currentGoalPercentage = 30;
    private boolean pendingGoalTask;

    @Inject
    public ChatBetPanel(ChatBetPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildMainPanel();
        buildGoalPanel();
        showTaskList();
    }

    private void buildMainPanel() {
        mainPanel = new JPanel(new BorderLayout(0, 8));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("ChatBet Activities", SwingConstants.CENTER);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        title.setForeground(ColorScheme.BRAND_ORANGE);
        mainPanel.add(title, BorderLayout.NORTH);

        listHost = new JPanel();
        listHost.setLayout(new BoxLayout(listHost, BoxLayout.Y_AXIS));
        listHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rebuildCatalog();

        JScrollPane scroll = new JScrollPane(listHost);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainPanel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setBackground(ColorScheme.DARK_GRAY_COLOR);

        activeTaskLabel = new JLabel("Active: None", SwingConstants.CENTER);
        activeTaskLabel.setFont(FontManager.getRunescapeSmallFont());
        activeTaskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        activeTaskLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        south.add(activeTaskLabel);
        south.add(Box.createVerticalStrut(6));

        JButton cancelButton = new JButton("Cancel Current Task");
        cancelButton.setFont(FontManager.getRunescapeSmallFont());
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        cancelButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cancelButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> cancelCurrentTask());
        south.add(cancelButton);

        mainPanel.add(south, BorderLayout.SOUTH);
    }

    private void rebuildCatalog() {
        listHost.removeAll();
        Map<String, List<ModuleCatalog.Entry>> byCat = ModuleCatalog.byCategory();
        for (Map.Entry<String, List<ModuleCatalog.Entry>> cat : byCat.entrySet()) {
            JLabel catLabel = new JLabel(cat.getKey());
            catLabel.setFont(FontManager.getRunescapeBoldFont());
            catLabel.setForeground(ColorScheme.BRAND_ORANGE);
            catLabel.setBorder(new EmptyBorder(10, 4, 4, 4));
            catLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            listHost.add(catLabel);

            for (ModuleCatalog.Entry entry : cat.getValue()) {
                listHost.add(createModuleCard(entry));
                listHost.add(Box.createVerticalStrut(4));
            }
        }
        listHost.add(Box.createVerticalGlue());
        listHost.revalidate();
        listHost.repaint();
    }

    private JPanel createModuleCard(ModuleCatalog.Entry entry) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 6, 8)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        JLabel name = new JLabel(entry.displayName);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setForeground(Color.WHITE);

        JLabel desc = new JLabel("<html><body style='width:180px'>" + entry.description + "</body></html>");
        desc.setFont(FontManager.getRunescapeSmallFont());
        desc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.add(name);
        text.add(desc);

        JButton go = new JButton("Go");
        go.setFocusPainted(false);
        go.setBackground(ColorScheme.BRAND_ORANGE);
        go.setForeground(Color.WHITE);
        go.addActionListener(e -> {
            if (entry.usesGoalSlider) {
                showGoalConfig(entry.displayName);
            } else {
                activateSimpleTask(entry.displayName);
            }
        });

        card.add(text, BorderLayout.CENTER);
        card.add(go, BorderLayout.EAST);
        return card;
    }

    private void buildGoalPanel() {
        goalPanel = new JPanel();
        goalPanel.setLayout(new BoxLayout(goalPanel, BoxLayout.Y_AXIS));
        goalPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Set Goal");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(title);
        goalPanel.add(Box.createVerticalStrut(15));

        JLabel taskLabel = new JLabel();
        taskLabel.setName("taskLabel");
        taskLabel.setFont(FontManager.getRunescapeBoldFont());
        taskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        taskLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(taskLabel);

        goalPanel.add(Box.createVerticalStrut(15));

        JLabel percentLabel = new JLabel("Target Percentage:");
        percentLabel.setFont(FontManager.getRunescapeSmallFont());
        percentLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        percentLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(percentLabel);

        goalSlider = new JSlider(5, 100, 30);
        goalSlider.setMajorTickSpacing(10);
        goalSlider.setPaintTicks(true);
        goalSlider.setPaintLabels(true);
        goalSlider.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalSlider.setMaximumSize(new Dimension(240, 50));
        goalSlider.setBackground(ColorScheme.DARK_GRAY_COLOR);
        goalPanel.add(goalSlider);

        goalValueLabel = new JLabel("30%");
        goalValueLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
        goalValueLabel.setForeground(ColorScheme.BRAND_ORANGE);
        goalValueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(goalValueLabel);

        goalSlider.addChangeListener(e -> goalValueLabel.setText(goalSlider.getValue() + "%"));

        goalPanel.add(Box.createVerticalStrut(20));

        JButton saveButton = new JButton("Save Goal & Activate");
        saveButton.setFont(FontManager.getRunescapeBoldFont());
        saveButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveButton.setMaximumSize(new Dimension(240, 40));
        saveButton.setBackground(ColorScheme.BRAND_ORANGE);
        saveButton.setForeground(Color.WHITE);
        saveButton.setFocusPainted(false);
        saveButton.addActionListener(e -> {
            currentGoalPercentage = goalSlider.getValue();
            plugin.setActiveTask(currentTask, currentGoalPercentage);
            JOptionPane.showMessageDialog(this, "Goal saved! Active task updated.");
            showTaskList();
        });
        goalPanel.add(saveButton);

        goalPanel.add(Box.createVerticalStrut(8));

        JButton backButton = new JButton("Back to Tasks");
        backButton.setFont(FontManager.getRunescapeSmallFont());
        backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        backButton.setMaximumSize(new Dimension(220, 36));
        backButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        backButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        backButton.setFocusPainted(false);
        backButton.addActionListener(e -> showTaskList());
        goalPanel.add(backButton);
    }

    private void activateSimpleTask(String taskName) {
        this.currentTask = taskName;
        plugin.setActiveTask(taskName, 0);
        JOptionPane.showMessageDialog(this, taskName + " activated.\nViewers can !bet on auto-polls.");
        showTaskList();
    }

    private void showTaskList() {
        removeAll();
        add(mainPanel, BorderLayout.CENTER);
        updateActiveTaskLabel();
        revalidate();
        repaint();
    }

    private void showGoalConfig(String taskName) {
        this.currentTask = taskName;
        pendingGoalTask = true;

        for (Component c : goalPanel.getComponents()) {
            if (c instanceof JLabel && "taskLabel".equals(c.getName())) {
                ((JLabel) c).setText(taskName);
            }
        }

        int existing = plugin.getCurrentGoalPercentage();
        if (existing > 0) {
            goalSlider.setValue(existing);
            goalValueLabel.setText(existing + "%");
        } else {
            goalSlider.setValue(30);
            goalValueLabel.setText("30%");
        }

        removeAll();
        add(goalPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void updateActiveTaskLabel() {
        String active = plugin.getActiveTaskName();
        if (active != null && !active.isEmpty() && !"None".equals(active)) {
            int pct = plugin.getCurrentGoalPercentage();
            if (pct > 0) {
                activeTaskLabel.setText("Active: " + active + " (" + pct + "%)");
            } else {
                activeTaskLabel.setText("Active: " + active);
            }
        } else {
            activeTaskLabel.setText("Active: None");
        }
    }

    private void cancelCurrentTask() {
        if (plugin != null) {
            plugin.setActiveTask("", 0);
            JOptionPane.showMessageDialog(this, "Current task cancelled.");
            refresh();
        }
    }

    public void refresh() {
        updateActiveTaskLabel();
        revalidate();
        repaint();
    }
}
