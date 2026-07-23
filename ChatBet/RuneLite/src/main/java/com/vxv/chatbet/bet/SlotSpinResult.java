package com.vxv.chatbet.bet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of a 3-reel slot spin. {@code reels} is length 3; each value is a symbol name.
 */
public class SlotSpinResult {
    private final String[] reels;
    private final int linesWon;
    private final String summary;

    public SlotSpinResult(String[] reels, int linesWon, String summary) {
        this.reels = reels != null ? Arrays.copyOf(reels, reels.length) : new String[0];
        this.linesWon = linesWon;
        this.summary = summary != null ? summary : "";
    }

    public List<String> getReels() {
        return Collections.unmodifiableList(Arrays.asList(reels));
    }

    public String[] getReelsArray() {
        return Arrays.copyOf(reels, reels.length);
    }

    public int getLinesWon() {
        return linesWon;
    }

    public String getSummary() {
        return summary;
    }

    public String formatReels() {
        if (reels.length == 0) {
            return "—";
        }
        return String.join(" | ", reels);
    }
}
