package com.vxv.chatbet;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

public class ChatBetPanel extends PluginPanel {

    private final ChatBetPlugin plugin;

    private JPanel mainPanel;
    private JPanel goalPanel;

    private JLabel activeTaskLabel;
    private JSlider goalSlider;
    private JLabel goalValueLabel;

    private String currentTask = "Pickpocketing Elves";
    private int currentGoalPercentage = 30;

    @Inject
    public ChatBetPanel(ChatBetPlugin plugin) {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildMainPanel();
        buildGoalPanel();

        // Start on main task list
        showTaskList();
    }

    private void buildMainPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("ChatBet Tasks");
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(20));

        // Task button - Pickpocketing Elves
        JButton elvesButton = createTaskButton("Pickpocketing Elves");
        elvesButton.addActionListener(e -> showGoalConfig("Pickpocketing Elves"));
        mainPanel.add(elvesButton);

        mainPanel.add(Box.createVerticalStrut(8));

        // Task button - Ourania Altar Runes
        JButton ouraniaButton = createTaskButton("Ourania Altar Runes");
        ouraniaButton.addActionListener(e -> showGoalConfig("Ourania Altar Runes"));
        mainPanel.add(ouraniaButton);

        mainPanel.add(Box.createVerticalGlue());

        activeTaskLabel = new JLabel("Active: None");
        activeTaskLabel.setFont(FontManager.getRunescapeSmallFont());
        activeTaskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        activeTaskLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(activeTaskLabel);

        mainPanel.add(Box.createVerticalStrut(10));

        // Cancel Current Task button
        JButton cancelButton = new JButton("Cancel Current Task");
        cancelButton.setFont(FontManager.getRunescapeSmallFont());
        cancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelButton.setMaximumSize(new Dimension(220, 36));
        cancelButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cancelButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> cancelCurrentTask());
        mainPanel.add(cancelButton);
    }

    private JButton createTaskButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(240, 42));
        button.setBackground(ColorScheme.BRAND_ORANGE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return button;
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

        goalSlider.addChangeListener(e -> {
            goalValueLabel.setText(goalSlider.getValue() + "%"); 
        });

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

    private void showTaskList() {
        removeAll();
        add(mainPanel, BorderLayout.CENTER);
        updateActiveTaskLabel();
        revalidate();
        repaint();
    }

    private void showGoalConfig(String taskName) {
        this.currentTask = taskName;

        // Pre-fill slider with current value if this is the active task
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
            activeTaskLabel.setText("Active: " + active + " (" + plugin.getCurrentGoalPercentage() + "%) ");
        } else {
            activeTaskLabel.setText("Active: None");
        }
    }

    private void cancelCurrentTask() {
        if (plugin != null) {
            // Clear active task
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