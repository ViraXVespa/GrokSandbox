package com.vxv.chatbet.bet;

import java.util.*;
import java.util.stream.Collectors;

import com.vxv.chatbet.event.GameEventType;

public class BetManager {

    private final List<Poll> activePolls = new ArrayList<>();
    private final Map<Integer, List<Wager>> pollWagers = new HashMap<>();
    private final Map<String, Long> userBalances = new HashMap<>();

    private int nextPollId = 1;
    private static final long STARTING_BALANCE = 10_000;

    public Poll createPoll(String question, BetType type, List<String> options) {
        Poll poll = new Poll(nextPollId++, question, type, options);
        activePolls.add(poll);
        pollWagers.put(poll.getId(), new ArrayList<>());
        pruneClosedPolls();
        return poll;
    }

    public List<Poll> getActivePolls() {
        return activePolls.stream()
            .filter(Poll::isOpen)
            // Newest first — better default for multi-poll betting
            .sorted((a, b) -> Integer.compare(b.getId(), a.getId()))
            .collect(Collectors.toList());
    }

    public Optional<Poll> getPollById(int id) {
        return activePolls.stream().filter(p -> p.getId() == id && p.isOpen()).findFirst();
    }

    /** Open or closed (for diagnostics / late display). */
    public Optional<Poll> findPoll(int id) {
        return activePolls.stream().filter(p -> p.getId() == id).findFirst();
    }

    public void closePoll(int id) {
        getPollById(id).ifPresent(Poll::close);
    }

    /**
     * Keep closed-poll history bounded so long streams don't leak memory.
     */
    private void pruneClosedPolls() {
        List<Poll> closed = activePolls.stream().filter(p -> !p.isOpen()).collect(Collectors.toList());
        if (closed.size() <= 40) {
            return;
        }
        closed.sort(Comparator.comparingInt(Poll::getId));
        int remove = closed.size() - 40;
        for (int i = 0; i < remove; i++) {
            Poll p = closed.get(i);
            activePolls.remove(p);
            pollWagers.remove(p.getId());
        }
    }

    /**
     * Best-effort option match: exact → case-insensitive → unique contains → line shorthand.
     * Avoids "1" matching "11-15" via naive contains.
     */
    public static int findOptionIndex(List<String> options, String optionText) {
        if (options == null || optionText == null || optionText.isBlank()) {
            return -1;
        }
        String needle = optionText.trim();
        String needleLower = needle.toLowerCase();

        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equals(needle)) {
                return i;
            }
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(needle)) {
                return i;
            }
        }

        // Slot shorthand: "1" / "3" / "5" → "1 line" / "3 lines"
        if (needle.matches("\\d+")) {
            for (int i = 0; i < options.size(); i++) {
                if (parseLineCount(options.get(i)) == Integer.parseInt(needle)
                    && options.get(i).toLowerCase().contains("line")) {
                    return i;
                }
            }
            // bare numeric option (e.g. option index 1-based)
            try {
                int oneBased = Integer.parseInt(needle);
                if (oneBased >= 1 && oneBased <= options.size()) {
                    return oneBased - 1;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }

        // Unique substring match (case-insensitive)
        int found = -1;
        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i).toLowerCase();
            if (opt.contains(needleLower) || needleLower.contains(opt)) {
                if (found >= 0) {
                    return -1; // ambiguous
                }
                found = i;
            }
        }
        return found;
    }

    /**
     * Pick the best open poll for a free-text option (and optional preferred id).
     */
    public Optional<Poll> resolveTargetPoll(Integer preferredPollId, String optionText) {
        List<Poll> open = getActivePolls();
        if (open.isEmpty()) {
            return Optional.empty();
        }
        if (preferredPollId != null) {
            Optional<Poll> preferred = getPollById(preferredPollId);
            if (preferred.isPresent()) {
                if (optionText == null || optionText.isBlank()
                    || findOptionIndex(preferred.get().getOptions(), optionText) >= 0) {
                    return preferred;
                }
            }
        }
        if (optionText != null && !optionText.isBlank()) {
            List<Poll> matches = new ArrayList<>();
            for (Poll p : open) {
                if (findOptionIndex(p.getOptions(), optionText) >= 0) {
                    matches.add(p);
                }
            }
            if (matches.size() == 1) {
                return Optional.of(matches.get(0));
            }
            if (matches.size() > 1) {
                // Prefer poll accepting bets, then newest
                return matches.stream()
                    .filter(Poll::isBettingOpen)
                    .findFirst()
                    .or(() -> Optional.of(matches.get(0)));
            }
        }
        // Default: newest open poll that still accepts bets, else newest
        return open.stream().filter(Poll::isBettingOpen).findFirst()
            .or(() -> Optional.of(open.get(0)));
    }

    public boolean placeWager(String username, int pollId, int optionIndex, long amount) {
        Optional<Poll> pollOpt = getPollById(pollId);
        if (pollOpt.isEmpty()) return false;
        Poll poll = pollOpt.get();
        if (!poll.isOpen() || !poll.isBettingOpen()) return false;
        if (optionIndex < 0 || optionIndex >= poll.getOptions().size()) return false;

        long balance = getOrCreateBalance(username);
        if (balance < amount || amount <= 0) return false;

        // Slot machine: stake scales with number of lines encoded in the option label
        long stake = amount;
        if (poll.getType() == BetType.SLOT_MACHINE) {
            int lines = parseLineCount(poll.getOptions().get(optionIndex));
            stake = amount * Math.max(1, lines);
            if (balance < stake) return false;
        }

        userBalances.put(username, balance - stake);
        pollWagers.get(pollId).add(new Wager(username, pollId, optionIndex, stake));
        return true;
    }

    /**
     * Parse "1 line", "3 lines", or plain "3" into a line count.
     */
    public static int parseLineCount(String option) {
        if (option == null || option.isBlank()) {
            return 1;
        }
        String digits = option.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public List<Wager> getWagersForPoll(int pollId) {
        return pollWagers.getOrDefault(pollId, Collections.emptyList());
    }

    public long getTotalPoolForPoll(int pollId) {
        return getWagersForPoll(pollId).stream().mapToLong(Wager::getAmount).sum();
    }

    public int getWagerCountForPoll(int pollId) {
        return getWagersForPoll(pollId).size();
    }

    private long getOrCreateBalance(String username) {
        return userBalances.computeIfAbsent(username, k -> STARTING_BALANCE);
    }

    public long getBalance(String username) {
        return getOrCreateBalance(username);
    }

    /** Credit tokens (stream engagement rewards, etc.). */
    public long creditBalance(String username, long amount) {
        if (username == null || username.isBlank() || amount <= 0) {
            return username == null || username.isBlank() ? 0 : getOrCreateBalance(username);
        }
        long bal = getOrCreateBalance(username) + amount;
        userBalances.put(username, bal);
        return bal;
    }

    public List<Map.Entry<String, Long>> getTopBalances(int n) {
        return userBalances.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(n)
            .collect(Collectors.toList());
    }

    private final LinkedList<String> recentBalanceRequests = new LinkedList<>();
    private static final int MAX_RECENT = 7;

    public void recordBalanceRequest(String username) {
        recentBalanceRequests.remove(username);
        recentBalanceRequests.addLast(username);
        while (recentBalanceRequests.size() > MAX_RECENT) recentBalanceRequests.removeFirst();
    }

    public List<String> getRecentBalanceRequests() {
        return new ArrayList<>(recentBalanceRequests);
    }

    /**
     * Optional listener fired after a poll is closed (win or no-winners).
     * Used to sync resolutions to the Discord hub.
     */
    private java.util.function.BiConsumer<Integer, Integer> onResolvedListener;

    public void setOnResolvedListener(java.util.function.BiConsumer<Integer, Integer> listener) {
        this.onResolvedListener = listener;
    }

    public void resolvePoll(int pollId, int winningOptionIndex) {
        Optional<Poll> pollOpt = getPollById(pollId);
        if (pollOpt.isEmpty()) return;
        Poll poll = pollOpt.get();
        if (!poll.isOpen()) return;

        List<Wager> wagers = getWagersForPoll(pollId);
        if (wagers.isEmpty()) {
            poll.close();
            notifyResolved(pollId, winningOptionIndex);
            return;
        }

        long totalPool = wagers.stream().mapToLong(Wager::getAmount).sum();
        List<Wager> winners = wagers.stream().filter(w -> w.getOptionIndex() == winningOptionIndex).collect(Collectors.toList());

        if (winners.isEmpty()) {
            poll.close();
            notifyResolved(pollId, winningOptionIndex);
            return;
        }

        long totalWinning = winners.stream().mapToLong(Wager::getAmount).sum();
        for (Wager winner : winners) {
            double share = (double) winner.getAmount() / totalWinning;
            long winnings = (long)(totalPool * share);
            userBalances.put(winner.getUsername(), getBalance(winner.getUsername()) + winnings);
        }
        poll.close();
        notifyResolved(pollId, winningOptionIndex);
    }

    private void notifyResolved(int pollId, int winningOptionIndex) {
        if (onResolvedListener != null) {
            try {
                onResolvedListener.accept(pollId, winningOptionIndex);
            } catch (Exception ignored) {
                // never break resolution for hub sync failures
            }
        }
    }

    /**
     * Resolve a CLOSEST_WINS / bracket poll from a numeric outcome (e.g. laps completed).
     * Options should look like "0-2", "3-5", "6-10", "26+" etc.
     */
    public void resolveClosestWins(int pollId, int actualValue) {
        Optional<Poll> pollOpt = getPollById(pollId);
        if (pollOpt.isEmpty()) return;
        Poll poll = pollOpt.get();
        if (!poll.isOpen()) return;

        poll.setResolvedValue(actualValue);
        int bestIdx = findClosestOptionIndex(poll.getOptions(), actualValue);
        if (bestIdx < 0) {
            poll.close();
            notifyResolved(pollId, 0);
            return;
        }
        resolvePoll(pollId, bestIdx);
    }

    static int findClosestOptionIndex(List<String> options, int actual) {
        int bestIdx = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < options.size(); i++) {
            int[] range = parseRange(options.get(i));
            if (range == null) continue;
            int lo = range[0];
            int hi = range[1];
            if (actual >= lo && actual <= hi) {
                return i;
            }
            int dist = actual < lo ? lo - actual : actual - hi;
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** Parse "0-2", "26+", "10" into [lo, hi]. */
    static int[] parseRange(String option) {
        if (option == null) return null;
        String s = option.trim().toLowerCase().replace("laps", "").replace("lap", "").trim();
        try {
            if (s.endsWith("+")) {
                int lo = Integer.parseInt(s.substring(0, s.length() - 1).trim());
                return new int[]{lo, Integer.MAX_VALUE};
            }
            if (s.contains("-")) {
                String[] parts = s.split("-", 2);
                int lo = Integer.parseInt(parts[0].trim());
                int hi = Integer.parseInt(parts[1].trim());
                return new int[]{lo, hi};
            }
            int v = Integer.parseInt(s.replaceAll("[^0-9-]", ""));
            return new int[]{v, v};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve a SLOT_MACHINE poll after reels have been spun.
     * Pays each wager: stake * (payoutMult of matching symbol) if that line hits,
     * scaled by lines purchased (partial credit if fewer winning lines than bought).
     *
     * @param symbolMultipliers map of symbol name (case-insensitive) → payout multiplier
     */
    public void resolveSlotMachine(int pollId, SlotSpinResult spin, Map<String, Double> symbolMultipliers) {
        Optional<Poll> pollOpt = getPollById(pollId);
        if (pollOpt.isEmpty()) return;
        Poll poll = pollOpt.get();
        if (!poll.isOpen()) return;

        poll.setSpinResult(spin);
        List<Wager> wagers = getWagersForPoll(pollId);
        if (wagers.isEmpty()) {
            poll.close();
            notifyResolved(pollId, 0);
            return;
        }

        String[] reels = spin.getReelsArray();
        boolean threeMatch = reels.length >= 3
            && reels[0] != null
            && reels[0].equalsIgnoreCase(reels[1])
            && reels[1].equalsIgnoreCase(reels[2]);
        String hitSymbol = threeMatch ? reels[0] : null;
        double mult = 0.0;
        if (hitSymbol != null && symbolMultipliers != null) {
            for (Map.Entry<String, Double> e : symbolMultipliers.entrySet()) {
                if (e.getKey().equalsIgnoreCase(hitSymbol)) {
                    mult = e.getValue() != null ? e.getValue() : 0.0;
                    break;
                }
            }
        }

        // Line wins: for a 3-reel single-row machine, a hit counts as 1 winning line
        // (expandable later). Bettors who bought L lines get min(L, linesWon) share.
        int machineLinesWon = threeMatch ? Math.max(1, spin.getLinesWon()) : 0;

        for (Wager w : wagers) {
            String opt = poll.getOptions().get(w.getOptionIndex());
            int linesBought = parseLineCount(opt);
            // Stake was already amount * lines at bet time; base unit = stake / lines
            long baseStake = Math.max(1, w.getAmount() / Math.max(1, linesBought));
            if (machineLinesWon <= 0 || mult <= 0) {
                continue; // loss
            }
            int effectiveLines = Math.min(linesBought, machineLinesWon);
            long payout = (long) (baseStake * effectiveLines * mult);
            if (payout > 0) {
                userBalances.put(w.getUsername(), getBalance(w.getUsername()) + payout);
            }
        }

        poll.close();
        notifyResolved(pollId, threeMatch ? 1 : 0);
    }

    public void onGameEvent(GameEventType event) {
        if (event == GameEventType.ETC_OBTAINED) resolveByTrigger("ETC", 0);
        if (event == GameEventType.GOAL_30_REACHED) resolveByTrigger("GOAL_30", 0);
    }

    public void resolveByTrigger(String trigger, int winningOptionIndex) {
        if (trigger == null) return;
        for (Poll poll : new ArrayList<>(activePolls)) {
            if (poll.isOpen() && trigger.equalsIgnoreCase(poll.getResolutionTrigger())) {
                resolvePoll(poll.getId(), winningOptionIndex);
            }
        }
    }

    public void resolveEtcPolls(int ignored) {
        onGameEvent(GameEventType.ETC_OBTAINED);
    }
}