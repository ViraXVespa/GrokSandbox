package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;
import com.vxv.chatbet.module.BetModule;
import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shared helpers for automated skill polls: open/resolve bracket or multi-choice polls,
 * track a simple counter, and render a standard overlay block.
 */
@Slf4j
public abstract class AutoPollModule implements BetModule {

    public static final List<String> BRACKETS_SMALL = Arrays.asList("0-2", "3-5", "6-10", "11-20", "21-40", "41+");
    public static final List<String> BRACKETS_MED = Arrays.asList("0-5", "6-15", "16-30", "31-50", "51-100", "101+");
    public static final List<String> BRACKETS_LARGE = Arrays.asList("0-10", "11-25", "26-50", "51-100", "101-200", "201+");
    public static final List<String> YES_NO = Arrays.asList("Yes", "No");

    protected final ChatBetPlugin plugin;
    protected final AtomicInteger counter = new AtomicInteger(0);
    protected final AtomicInteger sessionEvents = new AtomicInteger(0);
    protected int activePollId = -1;
    protected String statusLine = "Idle";
    protected boolean armed;

    protected AutoPollModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public abstract String getName();

    /** Short blurb for the side panel. */
    public abstract String getDescription();

    public abstract String getCategory();

    public abstract Color getAccentColor();

    @Override
    public void onActivate() {
        armed = true;
        counter.set(0);
        statusLine = "Armed";
        plugin.announce("[ChatBet] " + getName() + " armed — " + getDescription());
        onModuleActivate();
    }

    @Override
    public void onDeactivate() {
        armed = false;
        // Leave open polls for viewers — they stay resolvable via !resolve or game events
        // if another system picks them up. Clear local tracking so we don't double-resolve.
        if (activePollId > 0) {
            plugin.announce("[ChatBet] " + getName() + " deactivated — poll #" + activePollId
                + " left open for betting/resolve.");
        }
        onModuleDeactivate();
        activePollId = -1;
        statusLine = "Off";
    }

    protected void onModuleActivate() {}

    protected void onModuleDeactivate() {}

    @Override
    public void onGameTick(GameTick event) {}

    @Override
    public void onStatChanged(StatChanged event) {}

    @Override
    public void onChatMessage(ChatMessage event) {
        if (!armed) {
            return;
        }
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM
            && event.getType() != ChatMessageType.ENGINE) {
            // Most skill feedback is GAMEMESSAGE/SPAM; ignore player chat noise
        }
        String msg = event.getMessage();
        if (msg == null || msg.isBlank()) {
            return;
        }
        handleChat(msg, msg.toLowerCase(Locale.ROOT));
    }

    /** Override to react to game chat. */
    protected void handleChat(String raw, String lower) {}

    protected boolean hasOpenPoll() {
        return activePollId > 0 && plugin.getBetManager().getPollById(activePollId).isPresent();
    }

    protected Poll openBracketPoll(String question, List<String> brackets, String trigger) {
        if (hasOpenPoll()) {
            return plugin.getBetManager().getPollById(activePollId).orElse(null);
        }
        Poll poll = plugin.createManagedPoll(question, BetType.CLOSEST_WINS, brackets);
        if (trigger != null) {
            poll.withResolutionTrigger(trigger);
        }
        activePollId = poll.getId();
        statusLine = "Poll #" + activePollId + " open";
        plugin.announce("[ChatBet] #" + activePollId + " " + question
            + " — !bet <amt> on <bracket>");
        return poll;
    }

    protected Poll openChoicePoll(String question, List<String> options, String trigger) {
        if (hasOpenPoll()) {
            return plugin.getBetManager().getPollById(activePollId).orElse(null);
        }
        Poll poll = plugin.createManagedPoll(question, BetType.MULTIPLE_CHOICE, options);
        if (trigger != null) {
            poll.withResolutionTrigger(trigger);
        }
        activePollId = poll.getId();
        statusLine = "Poll #" + activePollId + " open";
        plugin.announce("[ChatBet] #" + activePollId + " " + question
            + " — options: " + String.join(", ", options));
        return poll;
    }

    protected void resolveBracket(int actualValue, String announcePrefix) {
        if (activePollId <= 0) {
            return;
        }
        int id = activePollId;
        plugin.resolveClosestPoll(id, actualValue);
        plugin.announce(String.format("[ChatBet] %s %d → poll #%d resolved.",
            announcePrefix != null ? announcePrefix : "Result", actualValue, id));
        activePollId = -1;
        statusLine = "Resolved @" + actualValue;
        counter.set(0);
    }

    protected void resolveChoice(int optionIndex, String label) {
        if (activePollId <= 0) {
            return;
        }
        int id = activePollId;
        plugin.getBetManager().resolvePoll(id, optionIndex);
        plugin.announce(String.format("[ChatBet] %s → poll #%d resolved (%s).",
            getName(), id, label != null ? label : ("opt " + optionIndex)));
        activePollId = -1;
        statusLine = "Resolved: " + label;
        counter.set(0);
    }

    protected void resolveYesNo(boolean yes) {
        resolveChoice(yes ? 0 : 1, yes ? "Yes" : "No");
    }

    protected int bump() {
        sessionEvents.incrementAndGet();
        return counter.incrementAndGet();
    }

    protected static boolean containsAny(String lower, String... needles) {
        for (String n : needles) {
            if (lower.contains(n)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        panel.getChildren().add(TitleComponent.builder()
            .text(getName())
            .color(getAccentColor())
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Status")
            .right(statusLine)
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Counter")
            .right(String.valueOf(counter.get()))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Poll")
            .right(activePollId > 0 ? "#" + activePollId : "—")
            .build());
        contributeExtraOverlay(panel);
    }

    protected void contributeExtraOverlay(PanelComponent panel) {}

    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> m = new LinkedHashMap<>();
        m.put("Module", this::getName);
        m.put("Category", this::getCategory);
        m.put("Armed", () -> armed);
        m.put("Status", () -> statusLine);
        m.put("Counter", counter::get);
        m.put("Session events", sessionEvents::get);
        m.put("Active poll", () -> activePollId);
        addDebug(m);
        return m;
    }

    protected void addDebug(Map<String, Supplier<Object>> m) {}
}
