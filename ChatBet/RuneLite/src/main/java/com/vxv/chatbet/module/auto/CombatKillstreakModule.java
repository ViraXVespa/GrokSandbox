package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;
import java.util.regex.Pattern;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;

/**
 * How many NPC kills before the local player dies.
 */
public class CombatKillstreakModule extends AutoPollModule {

    private static final Pattern KILL_CHAT = Pattern.compile(
        ".*your .+ (is dead|has been slain|dies).*", Pattern.CASE_INSENSITIVE);

    private boolean runActive;

    public CombatKillstreakModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Combat Killstreak"; }

    @Override
    public String getDescription() { return "Kills before you die"; }

    @Override
    public String getCategory() { return "Combat"; }

    @Override
    public Color getAccentColor() { return new Color(220, 80, 80); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("How many NPC kills before you die?", BRACKETS_MED, "PLAYER_DEATH");
        runActive = true;
    }

    @Override
    public void onGameTick(GameTick event) {
        // ActorDeath is not in BetModule — use chat + optional client scan below via chat only
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (!runActive) {
            return;
        }
        // Player death messages
        if (containsAny(lower,
            "oh dear, you are dead",
            "you have died",
            "you were slain")) {
            resolveBracket(counter.get(), "Killstreak ended at");
            // re-arm
            counter.set(0);
            openBracketPoll("How many NPC kills before you die?", BRACKETS_MED, "PLAYER_DEATH");
            return;
        }
        // Some clients emit kill lines; also count "I have defeated" style
        if (containsAny(lower, "i have defeated", "you have defeated")
            || KILL_CHAT.matcher(lower).matches()) {
            bump();
            statusLine = "Kills: " + counter.get();
        }
    }

    /**
     * Optional hook if plugin later forwards ActorDeath — keep package method for flexibility.
     */
    public void onActorDeath(Actor actor) {
        if (!armed || !runActive) {
            return;
        }
        Player local = plugin.getClient() != null ? plugin.getClient().getLocalPlayer() : null;
        if (local != null && actor == local) {
            resolveBracket(counter.get(), "Killstreak ended at");
            counter.set(0);
            openBracketPoll("How many NPC kills before you die?", BRACKETS_MED, "PLAYER_DEATH");
            return;
        }
        if (actor instanceof NPC) {
            // Only count if local player was interacting
            if (local != null && local.getInteracting() == actor) {
                bump();
                statusLine = "Kills: " + counter.get();
            }
        }
    }
}
