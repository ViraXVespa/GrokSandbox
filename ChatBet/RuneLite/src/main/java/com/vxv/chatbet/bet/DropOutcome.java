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
}