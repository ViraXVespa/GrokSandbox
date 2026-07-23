package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;
import java.awt.Color;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Automated poll: how many rooftop agility laps before the next failed obstacle.
 *
 * <p>Opens a bracket poll when a lap run starts (first obstacle / first lap of a session)
 * and resolves it on the first fail, using the completed-lap count.
 */
@Slf4j
public class RooftopAgilityModule implements BetModule {

    private static final List<String> LAP_BRACKETS = Arrays.asList(
        "0-2", "3-5", "6-10", "11-15", "16-25", "26+"
    );

    private static final Pattern LAP_COMPLETE = Pattern.compile(
        ".*you (have )?(completed|finish(ed)?) (a |the )?lap.*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MARK_OF_GRACE = Pattern.compile(
        ".*mark of grace.*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FAIL = Pattern.compile(
        ".*(you fail|you lose your footing|you slip|you fall|you lose your balance|you can't quite).*",
        Pattern.CASE_INSENSITIVE
    );
    /** Common rooftop start / obstacle interact messages */
    private static final Pattern COURSE_ACTIVITY = Pattern.compile(
        ".*(you jump|you climb|you cross|you grab|you leap|you balance).*",
        Pattern.CASE_INSENSITIVE
    );

    private final ChatBetPlugin plugin;
    private final AtomicInteger lapsThisRun = new AtomicInteger(0);
    private final AtomicInteger totalLaps = new AtomicInteger(0);
    private final AtomicInteger totalFails = new AtomicInteger(0);

    private boolean runActive;
    private int activePollId = -1;
    private int lastAgilityXp = -1;

    public RooftopAgilityModule(ChatBetPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Rooftop Agility";
    }

    @Override
    public void onActivate() {
        plugin.announce("[ChatBet] Rooftop Agility armed — poll opens when you start a course.");
    }

    @Override
    public void onDeactivate() {
        // Leave open polls alone so bettors aren't robbed mid-run; just stop tracking new ones.
        runActive = false;
    }

    @Override
    public void onGameTick(GameTick event) {
        // no-op; chat / XP driven
    }

    @Override
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() != Skill.AGILITY) {
            return;
        }
        int xp = event.getXp();
        if (lastAgilityXp >= 0 && xp > lastAgilityXp) {
            // XP gain while not yet in a run can mark course start
            if (!runActive) {
                maybeStartRun("agility XP");
            }
        }
        lastAgilityXp = xp;
    }

    @Override
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM
            && event.getType() != ChatMessageType.CONSOLE) {
            // Still accept filtered game spam
        }

        String raw = event.getMessage();
        if (raw == null || raw.isBlank()) {
            return;
        }
        String msg = raw.toLowerCase(Locale.ROOT);

        if (FAIL.matcher(msg).matches()) {
            onFail();
            return;
        }

        if (LAP_COMPLETE.matcher(msg).matches()) {
            onLapComplete();
            return;
        }

        // Marks often drop at end of obstacles on rooftops — not a full lap, but activity
        if (MARK_OF_GRACE.matcher(msg).matches() || COURSE_ACTIVITY.matcher(msg).matches()) {
            if (!runActive) {
                maybeStartRun("course activity");
            }
        }
    }

    private void maybeStartRun(String reason) {
        if (runActive) {
            return;
        }
        runActive = true;
        lapsThisRun.set(0);
        openPoll();
        log.info("[Agility] Run started ({})", reason);
    }

    private void openPoll() {
        if (activePollId > 0 && plugin.getBetManager().getPollById(activePollId).isPresent()) {
            return;
        }
        Poll poll = plugin.createManagedPoll(
            "How many rooftop laps before the next fail?",
            BetType.CLOSEST_WINS,
            LAP_BRACKETS
        );
        poll.withResolutionTrigger("AGILITY_FAIL");
        activePollId = poll.getId();
        plugin.announce("[ChatBet] Agility poll #" + activePollId
            + " open — !bet <amount> on <bracket> e.g. 6-10");
    }

    private void onLapComplete() {
        if (!runActive) {
            maybeStartRun("lap complete");
        }
        int n = lapsThisRun.incrementAndGet();
        totalLaps.incrementAndGet();
        log.debug("[Agility] Lap complete — run total {}", n);
    }

    private void onFail() {
        totalFails.incrementAndGet();
        int laps = lapsThisRun.get();
        if (activePollId > 0) {
            plugin.resolveClosestPoll(activePollId, laps);
            plugin.announce(String.format(
                "[ChatBet] Agility fail after %d lap%s — poll #%d resolved.",
                laps, laps == 1 ? "" : "s", activePollId));
            activePollId = -1;
        }
        // Immediately open the next "laps until fail" poll for continuous rooftop grinding
        runActive = false;
        lapsThisRun.set(0);
        maybeStartRun("post-fail restart");
    }

    @Override
    public void contributeToOverlay(PanelComponent panel) {
        panel.getChildren().add(TitleComponent.builder()
            .text("Rooftop Agility")
            .color(new Color(120, 190, 255))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Laps this run")
            .right(String.valueOf(lapsThisRun.get()))
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Session laps / fails")
            .right(totalLaps.get() + " / " + totalFails.get())
            .build());
        panel.getChildren().add(LineComponent.builder()
            .left("Poll")
            .right(activePollId > 0 ? "#" + activePollId + " open" : "—")
            .build());
    }

    @Override
    public Map<String, Supplier<Object>> getDebugVariables() {
        Map<String, Supplier<Object>> m = new LinkedHashMap<>();
        m.put("Run active", () -> runActive);
        m.put("Laps this run", lapsThisRun::get);
        m.put("Active poll", () -> activePollId);
        m.put("Total laps", totalLaps::get);
        m.put("Total fails", totalFails::get);
        return m;
    }
}
