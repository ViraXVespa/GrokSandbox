package com.vxv.chatbet.bet;

public class Wager {
    private final String username;
    private final int pollId;
    private final int optionIndex;
    private final long amount;

    public Wager(String username, int pollId, int optionIndex, long amount) {
        this.username = username;
        this.pollId = pollId;
        this.optionIndex = optionIndex;
        this.amount = amount;
    }

    public String getUsername() { return username; }
    public int getPollId() { return pollId; }
    public int getOptionIndex() { return optionIndex; }
    public long getAmount() { return amount; }
}