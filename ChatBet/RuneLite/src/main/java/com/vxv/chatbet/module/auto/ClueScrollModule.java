package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import java.util.Arrays;

public class ClueScrollModule extends AutoPollModule {

    public ClueScrollModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Clue Scrolls"; }

    @Override
    public String getDescription() { return "Clue tier / steps / casket luck"; }

    @Override
    public String getCategory() { return "Other"; }

    @Override
    public Color getAccentColor() { return new Color(100, 180, 220); }

    @Override
    protected void onModuleActivate() {
        openChoicePoll(
            "Next clue you open — which tier?",
            Arrays.asList("Beginner", "Easy", "Medium", "Hard", "Elite", "Master"),
            "CLUE_TIER");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "clue scroll")) {
            int idx = -1;
            if (lower.contains("beginner")) idx = 0;
            else if (lower.contains("easy")) idx = 1;
            else if (lower.contains("medium")) idx = 2;
            else if (lower.contains("hard")) idx = 3;
            else if (lower.contains("elite")) idx = 4;
            else if (lower.contains("master")) idx = 5;
            if (idx >= 0 && hasOpenPoll()) {
                resolveChoice(idx, "Tier");
                openBracketPoll("How many steps on this clue?", BRACKETS_SMALL, "CLUE_STEPS");
            }
        }
        if (containsAny(lower, "you receive a casket", "you've completed the treasure trail",
            "you open the casket")) {
            if (hasOpenPoll()) {
                var p = plugin.getBetManager().getPollById(activePollId);
                if (p.isPresent() && "CLUE_STEPS".equals(p.get().getResolutionTrigger())) {
                    resolveBracket(Math.max(1, counter.get()), "Clue steps");
                }
            }
            openChoicePoll("Did this casket hit a big unique?", YES_NO, "CLUE_UNIQUE");
        }
        if (containsAny(lower, "you receive a third age", "bloodhound", "gilded",
            "3rd age", "you find a")) {
            if (hasOpenPoll()) {
                var p = plugin.getBetManager().getPollById(activePollId);
                if (p.isPresent() && "CLUE_UNIQUE".equals(p.get().getResolutionTrigger())) {
                    resolveYesNo(true);
                }
            }
        }
        // step progress
        if (containsAny(lower, "you dig", "you search", "emote", "you talk to") && hasOpenPoll()) {
            if (plugin.getBetManager().getPollById(activePollId)
                .map(x -> "CLUE_STEPS".equals(x.getResolutionTrigger())).orElse(false)) {
                bump();
            }
        }
    }
}
