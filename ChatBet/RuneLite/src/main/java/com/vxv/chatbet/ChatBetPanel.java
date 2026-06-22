package com.vxv.chatbet;

import net.runelite.client.ui.PluginPanel;

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

        buildMainPanel();
        buildGoalPanel();

        // Start on main task list
        showTaskList();
    }

    private void buildMainPanel() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("ChatBet Tasks");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(title);
        mainPanel.add(Box.createVerticalStrut(15));

        // Task button - Pickpocketing Elves
        JButton elvesButton = new JButton("Pickpocketing Elves");
        elvesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        elvesButton.setMaximumSize(new Dimension(200, 40));
        elvesButton.addActionListener(e -> showGoalConfig("Pickpocketing Elves"));
        mainPanel.add(elvesButton);

        mainPanel.add(Box.createVerticalGlue());

        activeTaskLabel = new JLabel("Active: None");
        activeTaskLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(activeTaskLabel);
    }

    private void buildGoalPanel() {
        goalPanel = new JPanel();
        goalPanel.setLayout(new BoxLayout(goalPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Set Goal");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(title);
        goalPanel.add(Box.createVerticalStrut(10));

        JLabel taskLabel = new JLabel();
        taskLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(taskLabel);

        goalPanel.add(Box.createVerticalStrut(15));

        JLabel percentLabel = new JLabel("Target Percentage:");
        percentLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(percentLabel);

        goalSlider = new JSlider(5, 100, 30);
        goalSlider.setMajorTickSpacing(10);
        goalSlider.setPaintTicks(true);
        goalSlider.setPaintLabels(true);
        goalSlider.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalSlider.setMaximumSize(new Dimension(220, 50));
        goalPanel.add(goalSlider);

        goalValueLabel = new JLabel("30%");
        goalValueLabel.setFont(goalValueLabel.getFont().deriveFont(Font.BOLD, 18f));
        goalValueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        goalPanel.add(goalValueLabel);

        goalSlider.addChangeListener(e -> {
            goalValueLabel.setText(goalSlider.getValue() + "%"); 
        });

        goalPanel.add(Box.createVerticalStrut(15));

        JButton saveButton = new JButton("Save Goal & Activate");
        saveButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        saveButton.addActionListener(e -> {
            currentGoalPercentage = goalSlider.getValue();
            plugin.setActiveTask(currentTask, currentGoalPercentage);
            JOptionPane.showMessageDialog(this, "Goal saved! Active task updated.");
            showTaskList();
        });
        goalPanel.add(saveButton);

        JButton backButton = new JButton("Back to Tasks");
        backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
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
        if (active != null && !active.isEmpty()) {
            activeTaskLabel.setText("Active: " + active + " (" + plugin.getCurrentGoalPercentage() + "%) ");
        } else {
            activeTaskLabel.setText("Active: None");
        }
    }

    public void refresh() {
        updateActiveTaskLabel();
        revalidate();
        repaint();
    }
}