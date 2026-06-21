package com.vxv.chatbet.bet;

import java.util.*;

import com.vxv.chatbet.event.GameEventType;

public class BetManager {

    private final List<Poll> activePolls = new ArrayList<>();
    private final Map<Integer, List<Wager>> pollWagers = new HashMap<>();
    private final Map<String, Long> userBalances = new HashMap<>();

    private int nextPollId = 1;

    private static final long STARTING_BALANCE = 10_000;

    // ==================== Poll Management ====================

    public Poll createPoll(String question, BetType type, List<String> options) {
        Poll poll = new Poll(nextPollId++, question, type, options);
        activePolls.add(poll);
        pollWagers.put(poll.getId(), new ArrayList<>());
        return poll;
    }

    public List<Poll> getActivePolls() {
        return activePolls.stream()
                .filter(Poll::isOpen)
                .toList();
    }

    public Optional<Poll> getPollById(int id) {
        return activePolls.stream()
                .filter(p -> p.getId() == id && p.isOpen())
                .findFirst();
    }

    public void closePoll(int id) {
        getPollById(id).ifPresent(Poll::close);
    }

    // ==================== Wager Management ====================

    public boolean placeWager(String username, int pollId, int optionIndex, long amount) {
        Optional<Poll> pollOpt = getPollById(pollId);
        if (pollOpt.isEmpty()) return false;

        Poll poll = pollOpt.get();
        if (!poll.isOpen()) return false;
        if (optionIndex < 0 || optionIndex >= poll.getOptions().size()) return false;

        long balance = getOrCreateBalance(username);
        if (balance < amount || amount <= 0) return false;

        userBalances.put(username, balance - amount);

        Wager wager = new Wager(username, pollId, optionIndex, amount);
        pollWagers.get(pollId).add(wager);

        return true;
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

    public List<Map.Entry<String, Long>> getTopBalances(int n) {
        return userBalances.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .collect(java.util.stream.Collectors.toList());
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

    public void resolvePoll(int pollId, int winningOptionIndex) {
        Optional<Poll> pollOpt = getPollById(pollId);
        if (pollOpt.isEmpty()) return;
        Poll poll = pollOpt.get();
        if (!poll.isOpen()) return;

        List<Wager> wagers = pollWagers.getOrDefault(pollId, Collections.emptyList());
        if (wagers.isEmpty()) { poll.close(); return; }

        long totalPool = wagers.stream().mapToLong(Wager::getAmount).sum();
        List<Wager> winners = wagers.stream().filter(w -> w.getOptionIndex() == winningOptionIndex).toList();

        if (winners.isEmpty()) { poll.close(); return; }

        long totalWinning = winners.stream().mapToLong(Wager::getAmount).sum();
        for (Wager winner : winners) {
            double share = (double) winner.getAmount() / totalWinning;
            long winnings = (long) (totalPool * share);
            long current = getOrCreateBalance(winner.getUsername());
            userBalances.put(winner.getUsername(), current + winnings);
        }
        poll.close();
    }

    public void onGameEvent(GameEventType event) {
        switch (event) {
            case ETC_OBTAINED -> resolveByTrigger("ETC", 0);
            case GOAL_30_REACHED -> resolveByTrigger("GOAL_30", 0);
            default -> {}
        }
    }

    public void resolveByTrigger(String trigger, int winningOptionIndex) {
        if (trigger == null) return;
        for (Poll poll : new ArrayList<>(activePolls)) {
            if (poll.isOpen() && trigger.equalsIgnoreCase(poll.getResolutionTrigger())) {
                resolvePoll(poll.getId(), winningOptionIndex);
            }
        }
    }
}