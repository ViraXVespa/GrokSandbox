package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

/** Logs burned before invent runs out / firemaking stops. */
public class FiremakingModule extends AutoPollModule {

    public FiremakingModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Firemaking Streak"; }

    @Override
    public String getDescription() { return "Logs burned this streak"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(255, 120, 40); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("How many logs will you burn this streak?", BRACKETS_MED, "FM_STREAK");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "the fire catches", "you light a fire", "you burn the")) {
            if (!hasOpenPoll()) {
                openBracketPoll("How many logs will you burn this streak?", BRACKETS_MED, "FM_STREAK");
            }
            bump();
            statusLine = "Burned: " + counter.get();
        }
        if (containsAny(lower, "you can't light", "you need a tinderbox",
            "you do not have any logs")) {
            if (hasOpenPoll() && counter.get() > 0) {
                resolveBracket(counter.get(), "Burn streak");
                openBracketPoll("How many logs will you burn this streak?", BRACKETS_MED, "FM_STREAK");
            }
        }
        // Wintertodt
        if (containsAny(lower, "wintertodt", "you have helped subdue")) {
            if (hasOpenPoll()) {
                resolveBracket(counter.get(), "Wintertodt contribution proxy");
            }
        }
    }
}
