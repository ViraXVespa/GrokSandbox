package com.vxv.chatbet.bet;

public class DropOutcome {
    private final String name;
    private final double probability;

    public DropOutcome(String name, double probability) {
        this.name = name;
        this.probability = probability;
    }

    public String getName() { return name; }
    public double getProbability() { return probability; }

    @Override
    public String toString() {
        return String.format("%s (%.2f%%)", name, probability * 100);
    }
}