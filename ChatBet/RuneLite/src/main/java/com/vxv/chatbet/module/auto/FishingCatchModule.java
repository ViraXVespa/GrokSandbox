package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.bet.BetManager;
import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catch count this inventory + which fish type was most common.
 */
public class FishingCatchModule extends AutoPollModule {

    private static final Pattern CATCH = Pattern.compile(
        "you catch (?:a |an |some )?(.+?)(?:\\.|!|$)", Pattern.CASE_INSENSITIVE);

    private final Map<String, Integer> fishCounts = new HashMap<>();
    private boolean inventActive;

    public FishingCatchModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Fishing Haul"; }

    @Override
    public String getDescription() { return "Catches before invent full"; }

    @Override
    public String getCategory() { return "Gathering"; }

    @Override
    public Color getAccentColor() { return new Color(80, 160, 220); }

    @Override
    protected void onModuleActivate() {
        startInvent();
    }

    private void startInvent() {
        inventActive = true;
        counter.set(0);
        fishCounts.clear();
        openBracketPoll("How many fish this inventory?", BRACKETS_SMALL, "FISH_INVENT_FULL");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (lower.contains("you can't carry any more")
            || lower.contains("inventory is too full")
            || lower.contains("not enough inventory space")) {
            if (hasOpenPoll()) {
                resolveBracket(counter.get(), "Fish caught");
            }
            // Follow-up poll: which species dominated this invent (viewers can bet before next trip)
            if (fishCounts.size() >= 2) {
                java.util.List<String> topSpecies = fishCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList());
                openChoicePoll("Most caught fish last invent was?", topSpecies, "FISH_TOP");
                // Auto-resolve to the real winner after opening (viewers who bet on next invent use count poll)
                String winner = topSpecies.get(0);
                int idx = topSpecies.indexOf(winner);
                // Give a short window by NOT resolving immediately — resolve on next invent start
                statusLine = "Top fish: " + winner;
            }
            inventActive = false;
            statusLine = "Invent full @" + counter.get();
            return;
        }

        if (lower.contains("you bank") || lower.contains("the bank of")) {
            inventActive = false;
            if (hasOpenPoll()) {
                // If still on count poll, resolve count; if on FISH_TOP, resolve to true top
                var poll = plugin.getBetManager().getPollById(activePollId);
                if (poll.isPresent() && "FISH_TOP".equals(poll.get().getResolutionTrigger())
                    && !fishCounts.isEmpty()) {
                    java.util.List<String> opts = poll.get().getOptions();
                    String winner = fishCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(opts.get(0));
                    int idx = BetManager.findOptionIndex(opts, winner);
                    if (idx < 0) {
                        idx = 0;
                    }
                    resolveChoice(idx, winner);
                } else {
                    resolveBracket(counter.get(), "Trip ended with");
                }
            }
            startInvent();
            return;
        }

        Matcher m = CATCH.matcher(raw);
        if (m.find()) {
            // Closing a previous "top fish" poll when the next invent starts
            if (hasOpenPoll()) {
                var poll = plugin.getBetManager().getPollById(activePollId);
                if (poll.isPresent() && "FISH_TOP".equals(poll.get().getResolutionTrigger())
                    && !fishCounts.isEmpty()) {
                    java.util.List<String> opts = poll.get().getOptions();
                    String winner = fishCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(opts.get(0));
                    int idx = BetManager.findOptionIndex(opts, winner);
                    resolveChoice(Math.max(0, idx), winner);
                }
            }
            if (!inventActive || !hasOpenPoll()) {
                startInvent();
            }
            String fish = m.group(1).trim().toLowerCase(Locale.ROOT)
                .replaceAll("<[^>]+>", "");
            if (fish.length() > 24) {
                fish = fish.substring(0, 24);
            }
            fishCounts.merge(fish, 1, Integer::sum);
            bump();
            statusLine = "Catches: " + counter.get();
        }
    }
}
