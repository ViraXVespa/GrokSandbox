package com.vxv.chatbet.module.auto;

import com.vxv.chatbet.ChatBetPlugin;
import java.awt.Color;

/** Successful cooks before burning N foods (or invent empties). */
public class CookingBurnsModule extends AutoPollModule {

    private int burns;
    private int cooks;
    private static final int BURN_LIMIT = 3;

    public CookingBurnsModule(ChatBetPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "Cooking Burns"; }

    @Override
    public String getDescription() { return "Successful cooks before " + BURN_LIMIT + " burns"; }

    @Override
    public String getCategory() { return "Production"; }

    @Override
    public Color getAccentColor() { return new Color(230, 140, 60); }

    @Override
    protected void onModuleActivate() {
        openBracketPoll("Successful cooks before " + BURN_LIMIT + " burns?", BRACKETS_MED, "COOK_BURNS");
        burns = 0;
        cooks = 0;
    }

    @Override
    protected void handleChat(String raw, String lower) {
        if (containsAny(lower, "you successfully cook", "you manage to cook", "you cook the")) {
            cooks++;
            counter.set(cooks);
            statusLine = "Cooks " + cooks + " / burns " + burns;
        } else if (containsAny(lower, "you accidentally burn", "you burn the", "you burn ")) {
            burns++;
            statusLine = "Cooks " + cooks + " / burns " + burns;
            if (burns >= BURN_LIMIT && hasOpenPoll()) {
                resolveBracket(cooks, "Cooks before burn-out");
                burns = 0;
                cooks = 0;
                openBracketPoll("Successful cooks before " + BURN_LIMIT + " burns?", BRACKETS_MED, "COOK_BURNS");
            }
        }
    }
}
