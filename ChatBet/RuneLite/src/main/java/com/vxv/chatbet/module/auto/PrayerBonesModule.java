package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

public class PrayerBonesModule extends AutoPollModule {

    public PrayerBonesModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Prayer Bones"; }

    @Override
    public String getDescription() { return "Bones offered this trip"; }

    @Override
    public String getCategory() { return "Combat"; }

    @Override
    public Color getAccentColor() { return new Color(230, 230, 200); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Bones offered before invent empty?", BRACKETS_MED, "PRAY_BONES");
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "the gods", "you bury the", "bones are crushed",
            "you offer the", "your prayer is restored")) {
            // "The gods are very pleased with your offering."
            if (containsAny(lower, "gods are", "bury the", "offer the", "bones are crushed")) {
                if (!hasOpenPoll()) {
                    openBracketPoll("Bones offered before invent empty?", BRACKETS_MED, "PRAY_BONES");
                }
                bump();
                statusLine = "Bones: " + counter.get();
            }
        }
        if (containsAny(lower, "you have run out of bones", "you need bones")
            && hasOpenPoll() && counter.get() > 0) {
            resolveBracket(counter.get(), "Bones offered");
            openBracketPoll("Bones offered before invent empty?", BRACKETS_MED, "PRAY_BONES");
        }
    }
}
