package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class CraftingModule extends AutoPollModule {

    public CraftingModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Crafting Session"; }

    @Override
    public String getDescription() { return "Crafts this session"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(200, 160, 80); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("How many crafts this session?", BRACKETS_MED, "CRAFT_SESSION");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you craft", "you cut the", "you make a", "you string the",
            "you shape the", "you successfully craft")) {
            if (!hasOpenPoll()) {
                openBracketPoll("How many crafts this session?", BRACKETS_MED, "CRAFT_SESSION");
            }
            bump();
            statusLine = "Crafts: " + counter.get();
        }
        if (containsAny(lower, "you have run out") && hasOpenPoll() && counter.get() > 0) {
            resolveBracket(counter.get(), "Crafting session");
            openBracketPoll("How many crafts this session?", BRACKETS_MED, "CRAFT_SESSION");
        }
    }
}
