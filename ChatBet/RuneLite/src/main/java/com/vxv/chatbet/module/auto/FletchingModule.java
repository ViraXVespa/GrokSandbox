package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class FletchingModule extends AutoPollModule {

    public FletchingModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Fletching Session"; }

    @Override
    public String getDescription() { return "Items fletched this session"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(170, 130, 70); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("How many items will you fletch this session?", BRACKETS_LARGE, "FLETCH_SESSION");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you carefully cut", "you attach", "you fletch", "you string",
            "you finish making", "you successfully fletch")) {
            if (!hasOpenPoll()) {
                openBracketPoll("How many items will you fletch this session?", BRACKETS_LARGE, "FLETCH_SESSION");
            }
            bump();
            statusLine = "Fletched: " + counter.get();
        }
        if (containsAny(lower, "you have run out of") && hasOpenPoll() && counter.get() > 0) {
            resolveBracket(counter.get(), "Fletching session");
            openBracketPoll("How many items will you fletch this session?", BRACKETS_LARGE, "FLETCH_SESSION");
        }
    }
}
