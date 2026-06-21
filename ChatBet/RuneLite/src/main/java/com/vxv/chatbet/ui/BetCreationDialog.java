package com.vxv.chatbet.ui;

import com.vxv.chatbet.bet.BetManager;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BetCreationDialog extends JDialog {

    private final BetManager betManager;

    private final JTextField questionField = new JTextField(30);
    private final JComboBox<BetType> typeCombo = new JComboBox<>(BetType.values());
    private final JTextField optionsField = new JTextField(30);
    private final JComboBox<String> triggerCombo = new JComboBox<>(new String[]{"None", "ETC", "GOAL_30"});
    private final JButton createButton = new JButton("Create Poll");

    public BetCreationDialog(Frame owner, BetManager betManager) {
        super(owner, "Create New Bet Poll", true);
        this.betManager = betManager;

        setLayout(new BorderLayout(10, 10));
        setSize(450, 220);
        setLocationRelativeTo(owner);

        JPanel panel = new JPanel(new GridLayout(5, 2, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        panel.add(new JLabel("Question:"));
        panel.add(questionField);

        panel.add(new JLabel("Bet Type:"));
        panel.add(typeCombo);

        panel.add(new JLabel("Options (comma separated):"));
        panel.add(optionsField);

        // Note: When Fixed Odds + module data is available, probabilities are shown automatically.

        panel.add(new JLabel("Auto-Resolve Trigger:"));
        panel.add(triggerCombo);

        add(panel, BorderLayout.CENTER);

        suggestedOutcomesArea.setEditable(false);
        suggestedOutcomesArea.setLineWrap(true);
        suggestedOutcomesArea.setWrapStyleWord(true);
        suggestedOutcomesArea.setBorder(BorderFactory.createTitledBorder("Suggested Outcomes + Probabilities (from active module)"));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(suggestedOutcomesArea, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(createButton);

        add(bottomPanel, BorderLayout.SOUTH);
        add(buttonPanel, BorderLayout.PAGE_END);

        createButton.addActionListener(e -> createPoll());

        typeCombo.addActionListener(e -> updateOptionsForFixedOdds());

        // Default values for quick testing
        questionField.setText("Will I get an ETC in the next 200 pickpockets?");
        optionsField.setText("Yes,No");
        typeCombo.setSelectedItem(BetType.MULTIPLE_CHOICE);
        triggerCombo.setSelectedItem("ETC");
    }

    public void setSuggestedOutcomes(java.util.List<com.vxv.chatbet.bet.DropOutcome> outcomes) {
        this.suggestedOutcomes = outcomes != null ? outcomes : java.util.Collections.emptyList();
        updateOptionsForFixedOdds();
    }

    private void updateOptionsForFixedOdds() {
        if (typeCombo.getSelectedItem() == BetType.FIXED_ODDS && !suggestedOutcomes.isEmpty()) {
            // Pre-fill options with probabilities
            String joined = suggestedOutcomes.stream()
                    .map(o -> String.format("%s (%.2f%%)", o.getName(), o.getProbability() * 100))
                    .collect(java.util.stream.Collectors.joining(", "));
            optionsField.setText(joined);

            // Show formatted list
            StringBuilder sb = new StringBuilder();
            for (com.vxv.chatbet.bet.DropOutcome o : suggestedOutcomes) {
                sb.append(String.format("• %s — %.2f%%\n", o.getName(), o.getProbability() * 100));
            }
            suggestedOutcomesArea.setText(sb.toString());
        } else {
            suggestedOutcomesArea.setText("");
        }
    }

    private void createPoll() {
        String question = questionField.getText().trim();
        BetType type = (BetType) typeCombo.getSelectedItem();
        String optionsText = optionsField.getText().trim();

        if (question.isEmpty() || optionsText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in question and options.");
            return;
        }

        List<String> options = Arrays.stream(optionsText.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (options.size() < 2) {
            JOptionPane.showMessageDialog(this, "Please provide at least 2 options.");
            return;
        }

        Poll poll = betManager.createPoll(question, type, options);

        // Set resolution trigger if not "None"
        String trigger = (String) triggerCombo.getSelectedItem();
        if (!"None".equals(trigger)) {
            poll.withResolutionTrigger(trigger);
        }

        JOptionPane.showMessageDialog(this,
                "Poll #" + poll.getId() + " created successfully!");

        dispose();
    }
}