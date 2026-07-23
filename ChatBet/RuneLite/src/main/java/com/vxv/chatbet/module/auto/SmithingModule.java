package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class SmithingModule extends AutoPollModule {

    public SmithingModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Smithing Session"; }

    @Override
    public String getDescription() { return "Items smithed this session"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(180, 100, 60); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("How many items will you smith this session?", BRACKETS_MED, "SMITH_SESSION");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you hammer the", "you smelt the", "you smith a", "you make a")) {
            if (lower.contains("smith") || lower.contains("hammer") || lower.contains("smelt")) {
                if (!hasOpenPoll()) {
                    openBracketPoll("How many items will you smith this session?", BRACKETS_MED, "SMITH_SESSION");
                }
                bump();
                statusLine = "Smithed: " + counter.get();
            }
        }
        if (containsAny(lower, "you bank your", "the bank of") && counter.get() > 0 && hasOpenPoll()) {
            resolveBracket(counter.get(), "Smithing session");
            openBracketPoll("How many items will you smith this session?", BRACKETS_MED, "SMITH_SESSION");
        }
    }
}
