package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class ThievingStallModule extends AutoPollModule {

    public ThievingStallModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Thieving Stalls"; }

    @Override
    public String getDescription() { return "Stall thefts before you're caught"; }

    @Override
    public String getCategory() { return "Thieving"; }

    @Override
    public Color getAccentColor() { return new Color(180, 80, 160); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Successful stalls before you're stunned?", BRACKETS_MED, "THIEVE_STALL");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you steal", "you successfully steal", "you take some")) {
            if (!hasOpenPoll()) {
                openBracketPoll("Successful stalls before you're stunned?", BRACKETS_MED, "THIEVE_STALL");
            }
            bump();
            statusLine = "Thefts: " + counter.get();
        }
        if (containsAny(lower, "you have been stunned", "you've been stunned",
            "you fail to pick", "you fail to steal", "guards")) {
            if (hasOpenPoll()) {
                resolveBracket(counter.get(), "Thefts before stun");
            }
            openBracketPoll("Successful stalls before you're stunned?", BRACKETS_MED, "THIEVE_STALL");
        }
    }
}
