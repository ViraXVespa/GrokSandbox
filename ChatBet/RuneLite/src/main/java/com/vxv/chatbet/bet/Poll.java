package com.vxv.chatbet.bet;

import java.util.ArrayList;
import java.util.List;

public class Poll {
    private final int id;
    private final String question;
    private final BetType type;
    private final List<String> options;
    private boolean isOpen = true;
    private String resolutionTrigger;

    public Poll(int id, String question, BetType type, List<String> options) {
        this.id = id;
        this.question = question;
        this.type = type;
        this.options = new ArrayList<>(options);
    }

    public Poll withResolutionTrigger(String trigger) {
        this.resolutionTrigger = trigger;
        return this;
    }

    public int getId() { return id; }
    public String getQuestion() { return question; }
    public BetType getType() { return type; }
    public List<String> getOptions() { return options; }
    public boolean isOpen() { return isOpen; }
    public String getResolutionTrigger() { return resolutionTrigger; }

    public void close() { this.isOpen = false; }
}