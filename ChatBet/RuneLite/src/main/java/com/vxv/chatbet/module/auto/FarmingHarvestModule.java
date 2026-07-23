package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import java.util.Arrays;

public class FarmingHarvestModule extends AutoPollModule {

    public FarmingHarvestModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Farming Harvest"; }

    @Override
    public String getDescription() { return "Harvest size / disease bets"; }

    @Override
    public String getCategory() { return "Gathering"; }

    @Override
    public Color getAccentColor() { return new Color(70, 150, 70); }

    @Override
    protected void onModuleActivate() {
        openChoicePoll(
            "Will the next patch you check be diseased/dead?",
            YES_NO,
            "FARM_DISEASE");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "diseased", "the patch has become", "dead ", "you can dig up")) {
            if (hasOpenPoll() && containsAny(lower, "diseased", "dead")) {
                resolveYesNo(true);
                openChoicePoll("Will the next patch you check be diseased/dead?", YES_NO, "FARM_DISEASE");
                return;
            }
        }
        if (containsAny(lower, "you harvest", "you pick ", "you've harvested")) {
            if (hasOpenPoll()) {
                // healthy harvest → No on disease poll
                resolveYesNo(false);
            }
            // Open yield bracket for this harvest
            openBracketPoll("How many produce from this harvest?", BRACKETS_SMALL, "FARM_YIELD");
            // Can't always count — resolve yield on next invent-full style is weak.
            // Bump on each harvest message as 1 "action"
            bump();
        }
        if (containsAny(lower, "this patch is fully grown", "looks healthy") && hasOpenPoll()) {
            // still open disease poll until harvest
            statusLine = "Patch healthy — disease poll open";
        }
        // If yield poll open and they finish
        if (hasOpenPoll() && containsAny(lower, "there's nothing interesting", "empty")) {
            // leave
        }
        // Auto-resolve yield polls after harvest count messages in same action often once
        if (activePollId > 0 && plugin.getBetManager().getPollById(activePollId)
            .map(p -> p.getResolutionTrigger() != null && p.getResolutionTrigger().equals("FARM_YIELD"))
            .orElse(false)) {
            if (containsAny(lower, "you harvest")) {
                // keep counting multi-harvest; resolve on bank
            }
        }
        if (containsAny(lower, "you bank") && hasOpenPoll()) {
            var poll = plugin.getBetManager().getPollById(activePollId);
            if (poll.isPresent() && "FARM_YIELD".equals(poll.get().getResolutionTrigger())) {
                resolveBracket(Math.max(1, counter.get()), "Harvest yield actions");
            }
        }
    }
}
