package com.vxv.chatbet.module;

import com.vxv.chatbet.ChatBetPlugin;
import com.vxv.chatbet.module.auto.AgilityMarksModule;
import com.vxv.chatbet.module.auto.ClueScrollModule;
import com.vxv.chatbet.module.auto.CombatKillstreakModule;
import com.vxv.chatbet.module.auto.ConstructionModule;
import com.vxv.chatbet.module.auto.CookingBurnsModule;
import com.vxv.chatbet.module.auto.CraftingModule;
import com.vxv.chatbet.module.auto.FarmingHarvestModule;
import com.vxv.chatbet.module.auto.FiremakingModule;
import com.vxv.chatbet.module.auto.FishingCatchModule;
import com.vxv.chatbet.module.auto.FletchingModule;
import com.vxv.chatbet.module.auto.HerbloreModule;
import com.vxv.chatbet.module.auto.HunterCatchModule;
import com.vxv.chatbet.module.auto.LevelUpRaceModule;
import com.vxv.chatbet.module.auto.MagicAlchModule;
import com.vxv.chatbet.module.auto.MiningInventoryModule;
import com.vxv.chatbet.module.auto.PrayerBonesModule;
import com.vxv.chatbet.module.auto.RareDropModule;
import com.vxv.chatbet.module.auto.RunecraftTripModule;
import com.vxv.chatbet.module.auto.SlayerTaskModule;
import com.vxv.chatbet.module.auto.SmithingModule;
import com.vxv.chatbet.module.auto.ThievingStallModule;
import com.vxv.chatbet.module.auto.WoodcuttingNestModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of every ChatBet activity module (legacy + auto skill polls).
 */
public final class ModuleCatalog {

    public static final class Entry {
        public final String id;
        public final String displayName;
        public final String category;
        public final String description;
        public final boolean usesGoalSlider;
        public final Function<ChatBetPlugin, BetModule> factory;

        Entry(String id, String displayName, String category, String description,
            boolean usesGoalSlider, Function<ChatBetPlugin, BetModule> factory) {
            this.id = id;
            this.displayName = displayName;
            this.category = category;
            this.description = description;
            this.usesGoalSlider = usesGoalSlider;
            this.factory = factory;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        // --- Existing specialised modules ---
        add("Pickpocketing Elves", "Thieving", "Elf pickpocket ETC / goal tracking", true,
            PickpocketingModule::new);
        add("Ourania Altar Runes", "Runecraft", "ZMI most-crafted rune polls", true,
            OuraniaAltarModule::new);
        add("Rooftop Agility", "Agility", "Laps before next obstacle fail", false,
            RooftopAgilityModule::new);
        add("Mining Slots", "Mining", "Amethyst/runite loot slot machine", false,
            MiningSlotModule::new);

        // --- Auto skill catalog ---
        add("Combat Killstreak", "Combat", "NPC kills before you die", false,
            CombatKillstreakModule::new);
        add("Slayer Task", "Combat", "Task kills / rare on task", false,
            SlayerTaskModule::new);
        add("Fishing Haul", "Gathering", "Catch count before invent full + most fish", false,
            FishingCatchModule::new);
        add("Cooking Burns", "Production", "Successful cooks before N burns (or invent done)", false,
            CookingBurnsModule::new);
        add("Woodcutting Nests", "Gathering", "Logs chopped until bird nest", false,
            WoodcuttingNestModule::new);
        add("Firemaking Streak", "Production", "Logs burned before inventory empty", false,
            FiremakingModule::new);
        add("Mining Inventory", "Gathering", "Ores mined this invent before bank/drop", false,
            MiningInventoryModule::new);
        add("Smithing Session", "Production", "Bars/items smith before invent clears", false,
            SmithingModule::new);
        add("Crafting Session", "Production", "Crafts completed this session", false,
            CraftingModule::new);
        add("Fletching Session", "Production", "Fletched items this session", false,
            FletchingModule::new);
        add("Herblore Session", "Production", "Potions / cleaned herbs this trip", false,
            HerbloreModule::new);
        add("Prayer Bones", "Combat", "Bones offered before invent empty", false,
            PrayerBonesModule::new);
        add("Magic High Alchs", "Combat", "High alchs before invent empty", false,
            MagicAlchModule::new);
        add("Hunter Catches", "Gathering", "Successful catches before a fail", false,
            HunterCatchModule::new);
        add("Farming Harvest", "Gathering", "Harvest yield / disease poll", false,
            FarmingHarvestModule::new);
        add("Construction Build", "Production", "Construction XP burst brackets", false,
            ConstructionModule::new);
        add("Runecraft Trip", "Runecraft", "Non-ZMI essence trip rune count", false,
            RunecraftTripModule::new);
        add("Thieving Stalls", "Thieving", "Stall thefts before you're caught", false,
            ThievingStallModule::new);
        add("Agility Marks", "Agility", "Marks of grace until N / invent", false,
            AgilityMarksModule::new);
        add("Clue Scrolls", "Other", "Clue step / casket completion bets", false,
            ClueScrollModule::new);
        add("Rare Drops", "Other", "Will the next rare drop land soon?", false,
            RareDropModule::new);
        add("Level-Up Race", "Other", "Which skill levels next?", false,
            LevelUpRaceModule::new);
    }

    private static void add(String name, String category, String description,
        boolean usesGoal, Function<ChatBetPlugin, BetModule> factory) {
        ENTRIES.add(new Entry(name, name, category, description, usesGoal, factory));
    }

    private ModuleCatalog() {}

    public static List<Entry> all() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static Map<String, List<Entry>> byCategory() {
        Map<String, List<Entry>> map = new LinkedHashMap<>();
        for (Entry e : ENTRIES) {
            map.computeIfAbsent(e.category, k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    public static Entry find(String name) {
        if (name == null) {
            return null;
        }
        for (Entry e : ENTRIES) {
            if (e.displayName.equalsIgnoreCase(name) || e.id.equalsIgnoreCase(name)) {
                return e;
            }
        }
        // fuzzy
        String lower = name.toLowerCase();
        for (Entry e : ENTRIES) {
            if (e.displayName.toLowerCase().contains(lower) || lower.contains(e.displayName.toLowerCase())) {
                return e;
            }
        }
        return null;
    }

    public static BetModule create(String name, ChatBetPlugin plugin) {
        Entry e = find(name);
        if (e == null) {
            return null;
        }
        return e.factory.apply(plugin);
    }
}
