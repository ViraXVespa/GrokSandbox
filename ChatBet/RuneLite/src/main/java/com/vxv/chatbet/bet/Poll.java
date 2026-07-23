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

    /** Optional reel symbol names shown to bettors before a slot spin. */
    private List<String> reelSymbols = List.of();
    /** Epoch millis when betting auto-closes (slot / timed polls). 0 = no timer. */
    private long bettingClosesAtMs;
    private SlotSpinResult spinResult;
    private Integer resolvedValue;

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

    public Poll withReelSymbols(List<String> symbols) {
        this.reelSymbols = symbols == null ? List.of() : List.copyOf(symbols);
        return this;
    }

    public Poll withBettingClosesAt(long epochMs) {
        this.bettingClosesAtMs = epochMs;
        return this;
    }

    public int getId() { return id; }
    public String getQuestion() { return question; }
    public BetType getType() { return type; }
    public List<String> getOptions() { return options; }
    public boolean isOpen() { return isOpen; }
    public String getResolutionTrigger() { return resolutionTrigger; }

    public List<String> getReelSymbols() {
        return reelSymbols == null ? List.of() : reelSymbols;
    }

    public long getBettingClosesAtMs() {
        return bettingClosesAtMs;
    }

    public boolean isBettingOpen() {
        if (!isOpen) {
            return false;
        }
        if (bettingClosesAtMs <= 0) {
            return true;
        }
        return System.currentTimeMillis() < bettingClosesAtMs;
    }

    public SlotSpinResult getSpinResult() {
        return spinResult;
    }

    public void setSpinResult(SlotSpinResult spinResult) {
        this.spinResult = spinResult;
    }

    public Integer getResolvedValue() {
        return resolvedValue;
    }

    public void setResolvedValue(Integer resolvedValue) {
        this.resolvedValue = resolvedValue;
    }

    public void close() { this.isOpen = false; }
}