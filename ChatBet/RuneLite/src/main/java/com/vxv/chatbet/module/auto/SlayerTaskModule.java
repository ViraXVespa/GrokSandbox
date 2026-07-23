package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a slayer task is assigned, open polls on task size brackets and rare drop odds.
 */
public class SlayerTaskModule extends AutoPollModule {

    private static final Pattern ASSIGNED = Pattern.compile(
        ".*(?:you're assigned to kill|assigned to kill)\\s+(\\d+)\\s+(.+)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETE = Pattern.compile(
        ".*you('ve| have) completed .+ slayer task.*", Pattern.CASE_INSENSITIVE);

    private int taskSize;
    private String taskName = "";
    private int killsOnTask;

    public SlayerTaskModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Slayer Task"; }

    @Override
    public String getDescription() { return "Task size / rare on this task"; }

    @Override
    public String getCategory() { return "Combat"; }

    @Override
    public Color getAccentColor() { return new Color(160, 90, 200); }

    @Override
    protected void handleChat(String raw, String lower) {
        Matcher m = ASSIGNED.matcher(raw);
        if (m.find()) {
            try {
                taskSize = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                taskSize = 0;
            }
            taskName = m.group(2).replaceAll("<[^>]+>", "").trim();
            killsOnTask = 0;
            // Poll 1: will you get a superior / rare this task?
            openChoicePoll(
                "Rare/superior on this " + taskName + " task (" + taskSize + ")?",
                YES_NO,
                "SLAYER_RARE");
            return;
        }

        if (COMPLETE.matcher(lower).matches()) {
            if (hasOpenPoll()) {
                // No rare detected → No
                resolveYesNo(false);
            }
            statusLine = "Task complete";
            return;
        }

        if (containsAny(lower, "superior", "you have a rare drop", "brimstone key",
            "you receive a mystic", "you receive a")) {
            if (hasOpenPoll() && containsAny(lower, "superior", "brimstone", "rare drop", "mystic")) {
                resolveYesNo(true);
            }
        }

        // Rough kill tracking via residual task messages
        if (lower.contains("only ") && lower.contains("more to go")) {
            killsOnTask++;
            counter.set(killsOnTask);
            statusLine = taskName + " progress";
        }
    }
}
