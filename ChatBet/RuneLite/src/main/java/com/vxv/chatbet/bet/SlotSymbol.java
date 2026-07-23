package com.vxv.chatbet.bet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Weighted reel symbol for the mining slot machine.
 * {@code baseWeight} is relative rarity on the reel; {@code payoutMult} is the
 * stake multiplier when a full line hits this symbol (3-of-a-kind).
 */
@Getter
@RequiredArgsConstructor
public class SlotSymbol {
    private final String name;
    private final int itemId;
    private final int baseWeight;
    private final double payoutMult;

    /** Soft-cap contribution when building reels from the loot pool. */
    private final int poolCap;
}
