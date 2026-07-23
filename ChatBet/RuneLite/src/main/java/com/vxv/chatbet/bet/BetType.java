package com.vxv.chatbet.bet;

public enum BetType {
    MULTIPLE_CHOICE,
    /** Bracket / numeric options; resolver picks the matching (or closest) option. */
    CLOSEST_WINS,
    FIXED_ODDS,
    /**
     * Slot-machine poll: options are line counts (e.g. "1 line", "3 lines").
     * Stake is deducted at bet time; payouts use symbol multipliers after the spin.
     */
    SLOT_MACHINE
}