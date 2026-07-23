package com.vxv.runebridge.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Static catalog of common interactables. Not exhaustive — live scene scan supplements this.
 * Coordinates are OSRS world tiles (approx. booth/entrance tiles).
 */
public final class PoiCatalog
{
	private static final Map<PoiType, List<Poi>> BY_TYPE = new EnumMap<>(PoiType.class);

	static
	{
		// Banks (major)
		add(PoiType.BANK, "Varrock West Bank", 3185, 3436, 0, -1, "Bank");
		add(PoiType.BANK, "Varrock East Bank", 3253, 3420, 0, -1, "Bank");
		add(PoiType.BANK, "Edgeville Bank", 3094, 3491, 0, -1, "Bank");
		add(PoiType.BANK, "Grand Exchange Bank", 3164, 3487, 0, -1, "Bank");
		add(PoiType.BANK, "Falador East Bank", 3013, 3355, 0, -1, "Bank");
		add(PoiType.BANK, "Falador West Bank", 2946, 3368, 0, -1, "Bank");
		add(PoiType.BANK, "Draynor Bank", 3092, 3243, 0, -1, "Bank");
		add(PoiType.BANK, "Lumbridge Bank", 3208, 3220, 2, -1, "Bank");
		add(PoiType.BANK, "Al Kharid Bank", 3269, 3167, 0, -1, "Bank");
		add(PoiType.BANK, "Catherby Bank", 2808, 3441, 0, -1, "Bank");
		add(PoiType.BANK, "Seers Village Bank", 2727, 3493, 0, -1, "Bank");
		add(PoiType.BANK, "Ardougne North Bank", 2615, 3332, 0, -1, "Bank");
		add(PoiType.BANK, "Ardougne South Bank", 2655, 3283, 0, -1, "Bank");
		add(PoiType.BANK, "Yanille Bank", 2613, 3093, 0, -1, "Bank");
		add(PoiType.BANK, "Castle Wars Bank", 2443, 3083, 0, -1, "Bank");
		add(PoiType.BANK, "Shantay Pass Bank chest", 3308, 3120, 0, -1, "Use");
		add(PoiType.BANK, "Canifis Bank", 3512, 3480, 0, -1, "Bank");
		add(PoiType.BANK, "Burgh de Rott Bank", 3496, 3211, 0, -1, "Bank");
		add(PoiType.BANK, "Mos Le'Harmless Bank", 3680, 2982, 0, -1, "Bank");
		add(PoiType.BANK, "Port Phasmatys Bank", 3688, 3468, 0, -1, "Bank");
		add(PoiType.BANK, "Prifddinas Bank (Cadarn)", 3296, 6064, 0, -1, "Bank");
		add(PoiType.BANK, "Prifddinas Bank (Trahaearn)", 3273, 6058, 0, -1, "Bank");
		add(PoiType.BANK, "Fossil Island Bank", 3740, 3804, 0, -1, "Bank");
		add(PoiType.BANK, "Mount Karuulm Bank", 1324, 3824, 0, -1, "Bank");
		add(PoiType.BANK, "Wintertodt Bank", 1640, 3944, 0, -1, "Bank");
		add(PoiType.BANK, "Mining Guild Bank chest", 3013, 9718, 0, -1, "Use");
		add(PoiType.BANK, "Motherlode Mine Bank chest", 3758, 5666, 0, -1, "Use");
		add(PoiType.BANK, "ZMI Bank", 3018, 5625, 0, -1, "Bank");

		add(PoiType.GRAND_EXCHANGE, "Grand Exchange", 3164, 3487, 0, -1, "Exchange");
		add(PoiType.DEPOSIT_BOX, "Edgeville Deposit Box", 3097, 3496, 0, -1, "Deposit");
		add(PoiType.DEPOSIT_BOX, "GE Deposit Box", 3165, 3490, 0, -1, "Deposit");

		// Furnaces
		add(PoiType.FURNACE, "Edgeville Furnace", 3109, 3499, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Al Kharid Furnace", 3275, 3186, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Falador Furnace", 2975, 3369, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Lumbridge Furnace", 3227, 3255, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Neitiznot Furnace", 2341, 3810, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Prifddinas Furnace", 3273, 6055, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Port Phasmatys Furnace", 3687, 3474, 0, -1, "Smelt");
		add(PoiType.FURNACE, "Shilo Village Furnace", 2855, 2962, 0, -1, "Smelt");

		// Anvils
		add(PoiType.ANVIL, "Varrock Anvil", 3188, 3425, 0, -1, "Smith");
		add(PoiType.ANVIL, "Falador Anvil", 2972, 3370, 0, -1, "Smith");
		add(PoiType.ANVIL, "Seers Anvil", 2711, 3495, 0, -1, "Smith");
		add(PoiType.ANVIL, "Yanille Anvil", 2612, 3082, 0, -1, "Smith");

		// Ranges / cooking
		add(PoiType.RANGE, "Lumbridge Range", 3210, 3215, 0, -1, "Cook");
		add(PoiType.RANGE, "Rogues Den Fire", 3043, 4972, 1, -1, "Cook");
		add(PoiType.COOKING_RANGE, "Cooks' Guild Range", 3146, 3452, 0, -1, "Cook");
		add(PoiType.COOKING_RANGE, "Hosidius Kitchen", 1677, 3621, 0, -1, "Cook");
		add(PoiType.RANGE, "Catherby Range", 2817, 3443, 0, -1, "Cook");

		// Water
		add(PoiType.WATER_SOURCE, "Lumbridge Fountain", 3220, 3218, 0, -1, "Fill");
		add(PoiType.WATER_SOURCE, "Falador Fountain", 2983, 3382, 0, -1, "Fill");
		add(PoiType.WATER_SOURCE, "GE Fountain", 3164, 3489, 0, -1, "Fill");

		// Altars
		add(PoiType.ALTAR, "Varrock Church Altar", 3252, 3485, 0, -1, "Pray");
		add(PoiType.ALTAR, "Falador Altar", 2979, 3340, 0, -1, "Pray");
		add(PoiType.ALTAR, "Edgeville Monastery Altar", 3054, 3483, 1, -1, "Pray");
		add(PoiType.ALTAR, "House Altar (Yanille portal area)", 2544, 3096, 0, -1, "Pray");

		// Stores
		add(PoiType.GENERAL_STORE, "Lumbridge General Store", 3212, 3247, 0, -1, "Trade");
		add(PoiType.GENERAL_STORE, "Varrock General Store", 3217, 3414, 0, -1, "Trade");
		add(PoiType.GENERAL_STORE, "Edgeville General Store", 3080, 3508, 0, -1, "Trade");

		// Crafting
		add(PoiType.SPINNING_WHEEL, "Lumbridge Spinning Wheel", 3209, 3213, 1, -1, "Spin");
		add(PoiType.SPINNING_WHEEL, "Seers Spinning Wheel", 2715, 3471, 1, -1, "Spin");
		add(PoiType.LOOM, "Falador Loom", 3039, 3285, 0, -1, "Weave");
		add(PoiType.POTTERY_OVEN, "Barbarian Village Pottery", 3086, 3409, 0, -1, "Fire");
		add(PoiType.CRAFTING_GUILD, "Crafting Guild Entrance", 2933, 3288, 0, -1, "Open");

		// Mining hubs (representative tiles)
		add(PoiType.ORE_IRON, "Al Kharid Iron Mine", 3296, 3310, 0, -1, "Mine");
		add(PoiType.ORE_IRON, "Legends' Guild Iron", 2711, 3331, 0, -1, "Mine");
		add(PoiType.ORE_IRON, "Mining Guild Iron", 3030, 9737, 0, -1, "Mine");
		add(PoiType.ORE_COAL, "Mining Guild Coal", 3040, 9735, 0, -1, "Mine");
		add(PoiType.ORE_COAL, "Lovakengj Coal", 1437, 3838, 0, -1, "Mine");
		add(PoiType.ORE_MITHRIL, "Mining Guild Mithril", 3048, 9735, 0, -1, "Mine");
		add(PoiType.ORE_ADAMANTITE, "Mining Guild Adamant", 3052, 9735, 0, -1, "Mine");
		add(PoiType.ORE_RUNITE, "Mining Guild Runite", 3056, 9735, 0, -1, "Mine");
		add(PoiType.ORE_RUNITE, "Heroes' Guild Runite", 2920, 9890, 0, -1, "Mine");
		add(PoiType.ORE_RUNITE, "Lava Maze Runite", 3058, 3884, 0, -1, "Mine");
		add(PoiType.ORE_AMETHYST, "Mining Guild Amethyst", 3030, 9705, 0, -1, "Mine");
		add(PoiType.ORE_GOLD, "Crafting Guild Gold", 2938, 3281, 0, -1, "Mine");
		add(PoiType.ORE_SILVER, "Crafting Guild Silver", 2938, 3283, 0, -1, "Mine");
		add(PoiType.ORE_COPPER, "Lumbridge SW Copper", 3229, 3147, 0, -1, "Mine");
		add(PoiType.ORE_TIN, "Lumbridge SW Tin", 3226, 3147, 0, -1, "Mine");

		// Trees
		add(PoiType.TREE_NORMAL, "Lumbridge Trees", 3195, 3230, 0, -1, "Chop");
		add(PoiType.TREE_OAK, "Varrock Oaks", 3195, 3450, 0, -1, "Chop");
		add(PoiType.TREE_WILLOW, "Draynor Willows", 3087, 3234, 0, -1, "Chop");
		add(PoiType.TREE_MAPLE, "Seers Maples", 2728, 3500, 0, -1, "Chop");
		add(PoiType.TREE_YEW, "Edgeville Yews", 3087, 3474, 0, -1, "Chop");
		add(PoiType.TREE_MAGIC, "Sorcerer's Tower Magics", 2702, 3398, 0, -1, "Chop");

		// Fishing
		add(PoiType.FISHING_SPOT, "Barbarian Village Fishing", 3104, 3430, 0, -1, "Lure");
		add(PoiType.FISHING_SPOT, "Catherby Fishing", 2837, 3433, 0, -1, "Cage");
		add(PoiType.FISHING_SPOT, "Fishing Guild", 2600, 3415, 0, -1, "Harpoon");
		add(PoiType.FISHING_SPOT, "Karambwan Dock", 2905, 3115, 0, -1, "Fish");

		add(PoiType.TOOL_LEPRECHAUN, "Falador Farm Leprechaun", 3053, 3304, 0, -1, "Exchange");
		add(PoiType.TOOL_LEPRECHAUN, "Catherby Leprechaun", 2813, 3464, 0, -1, "Exchange");
	}

	private PoiCatalog()
	{
	}

	private static void add(PoiType type, String name, int x, int y, int plane, int objectId, String action)
	{
		BY_TYPE.computeIfAbsent(type, t -> new ArrayList<>())
			.add(new Poi(type, name, x, y, plane, objectId, action));
		// BANK also answers BANK_BOOTH / BANK_CHEST generically
		if (type == PoiType.BANK)
		{
			BY_TYPE.computeIfAbsent(PoiType.BANK_BOOTH, t -> new ArrayList<>())
				.add(new Poi(PoiType.BANK_BOOTH, name, x, y, plane, objectId, action));
		}
	}

	public static List<Poi> allOf(PoiType type)
	{
		if (type == null)
		{
			return Collections.emptyList();
		}
		List<Poi> list = BY_TYPE.get(type);
		if (list == null && type.name().startsWith("ORE_"))
		{
			// allow generic mining query via any ore list union for "nearest rock hub"
			return Collections.emptyList();
		}
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	public static List<Poi> resolveTypes(String targetName)
	{
		if (targetName == null || targetName.isBlank())
		{
			return Collections.emptyList();
		}
		String key = targetName.trim().toUpperCase(Locale.ROOT)
			.replace('-', '_')
			.replace(' ', '_');

		// ---- simple aliases ----
		if ("BANK".equals(key) || "BANKS".equals(key))
		{
			return allOf(PoiType.BANK);
		}
		if ("FURNACE".equals(key) || "FURNACES".equals(key) || "SMELT".equals(key) || "SMELTING".equals(key))
		{
			return allOf(PoiType.FURNACE);
		}
		if ("ANVIL".equals(key) || "ANVILS".equals(key) || "SMITH".equals(key) || "SMITHING".equals(key))
		{
			return allOf(PoiType.ANVIL);
		}
		if ("RANGE".equals(key) || "RANGES".equals(key) || "COOK".equals(key)
			|| "COOKING".equals(key) || "COOKING_RANGE".equals(key) || "STOVE".equals(key))
		{
			List<Poi> merged = new ArrayList<>();
			merged.addAll(allOf(PoiType.RANGE));
			merged.addAll(allOf(PoiType.COOKING_RANGE));
			return merged;
		}
		if ("GE".equals(key) || "GRAND_EXCHANGE".equals(key) || "EXCHANGE".equals(key))
		{
			return allOf(PoiType.GRAND_EXCHANGE);
		}
		if ("DEPOSIT".equals(key) || "DEPOSIT_BOX".equals(key) || "DEPOSITBOX".equals(key))
		{
			return allOf(PoiType.DEPOSIT_BOX);
		}
		if ("FISH".equals(key) || "FISHING".equals(key) || "FISHING_SPOT".equals(key)
			|| "FISHING_SPOTS".equals(key))
		{
			return allOf(PoiType.FISHING_SPOT);
		}
		if ("ALTAR".equals(key) || "ALTARS".equals(key) || "PRAY".equals(key) || "PRAYER".equals(key))
		{
			return allOf(PoiType.ALTAR);
		}

		// ---- ores: ORE_IRON, IRON_ORE, IRON_ROCK, IRON, ROCK, ORE ----
		if (key.startsWith("ORE_") || key.endsWith("_ORE") || key.endsWith("_ROCK") || key.endsWith("_ROCKS")
			|| key.equals("ROCK") || key.equals("ROCKS") || key.equals("ORE")
			|| key.equals("MINE") || key.equals("MINING")
			|| isBareOreMetal(key))
		{
			if (key.equals("ROCK") || key.equals("ROCKS") || key.equals("ORE")
				|| key.equals("MINE") || key.equals("MINING"))
			{
				return allOrePois();
			}
			String metal = bareOreMetal(key);
			if (metal != null)
			{
				try
				{
					return allOf(PoiType.valueOf("ORE_" + metal));
				}
				catch (IllegalArgumentException ex)
				{
					return Collections.emptyList();
				}
			}
			return Collections.emptyList();
		}

		// ---- trees: TREE_MAGIC, MAGIC_TREE, MAGIC, YEW, TREE, WOODCUT ----
		if (key.startsWith("TREE_") || key.endsWith("_TREE") || key.equals("TREE") || key.equals("TREES")
			|| key.equals("WOOD") || key.equals("WOODCUT") || key.equals("WOODCUTTING")
			|| isBareTreeSpecies(key))
		{
			if (key.equals("TREE") || key.equals("TREES") || key.equals("WOOD")
				|| key.equals("WOODCUT") || key.equals("WOODCUTTING"))
			{
				return allTreePois();
			}
			String species = bareTreeSpecies(key);
			if (species != null)
			{
				try
				{
					return allOf(PoiType.valueOf("TREE_" + species));
				}
				catch (IllegalArgumentException ex)
				{
					return Collections.emptyList();
				}
			}
			return Collections.emptyList();
		}

		try
		{
			return allOf(PoiType.valueOf(key));
		}
		catch (IllegalArgumentException ex)
		{
			return Collections.emptyList();
		}
	}

	private static List<Poi> allOrePois()
	{
		return BY_TYPE.entrySet().stream()
			.filter(e -> e.getKey().name().startsWith("ORE_"))
			.flatMap(e -> e.getValue().stream())
			.collect(Collectors.toList());
	}

	private static List<Poi> allTreePois()
	{
		return BY_TYPE.entrySet().stream()
			.filter(e -> e.getKey().name().startsWith("TREE_"))
			.flatMap(e -> e.getValue().stream())
			.collect(Collectors.toList());
	}

	private static boolean isBareOreMetal(String key)
	{
		return bareOreMetal(key) != null && !key.startsWith("ORE_") && !key.endsWith("_ORE")
			&& !key.endsWith("_ROCK") && !key.endsWith("_ROCKS");
	}

	/** IRON / ORE_IRON / IRON_ORE / IRON_ROCK → IRON; null if not an ore metal token. */
	private static String bareOreMetal(String key)
	{
		String k = key;
		if (k.startsWith("ORE_"))
		{
			k = k.substring(4);
		}
		else if (k.endsWith("_ORE"))
		{
			k = k.substring(0, k.length() - 4);
		}
		else if (k.endsWith("_ROCKS"))
		{
			k = k.substring(0, k.length() - 6);
		}
		else if (k.endsWith("_ROCK"))
		{
			k = k.substring(0, k.length() - 5);
		}
		switch (k)
		{
			case "COPPER":
			case "TIN":
			case "IRON":
			case "COAL":
			case "MITHRIL":
			case "ADAMANTITE":
			case "ADAMANT":
			case "RUNITE":
			case "RUNE":
			case "GOLD":
			case "SILVER":
			case "AMETHYST":
				if ("ADAMANT".equals(k))
				{
					return "ADAMANTITE";
				}
				if ("RUNE".equals(k))
				{
					return "RUNITE";
				}
				return k;
			default:
				return null;
		}
	}

	private static boolean isBareTreeSpecies(String key)
	{
		return bareTreeSpecies(key) != null && !key.startsWith("TREE_") && !key.endsWith("_TREE");
	}

	/** MAGIC / TREE_MAGIC / MAGIC_TREE / YEWS → MAGIC; null if not a tree species. */
	private static String bareTreeSpecies(String key)
	{
		String k = key;
		if (k.startsWith("TREE_"))
		{
			k = k.substring(5);
		}
		else if (k.endsWith("_TREE"))
		{
			k = k.substring(0, k.length() - 5);
		}
		// plural convenience
		if (k.endsWith("S") && k.length() > 2)
		{
			String singular = k.substring(0, k.length() - 1);
			if (treeSpeciesToken(singular) != null)
			{
				k = singular;
			}
		}
		return treeSpeciesToken(k);
	}

	private static String treeSpeciesToken(String k)
	{
		switch (k)
		{
			case "NORMAL":
			case "REGULAR":
			case "BASIC":
				return "NORMAL";
			case "OAK":
				return "OAK";
			case "WILLOW":
				return "WILLOW";
			case "MAPLE":
				return "MAPLE";
			case "YEW":
				return "YEW";
			case "MAGIC":
				return "MAGIC";
			default:
				return null;
		}
	}

	public static List<String> knownTargets()
	{
		return Arrays.stream(PoiType.values())
			.filter(t -> t != PoiType.CUSTOM)
			.map(Enum::name)
			.collect(Collectors.toList());
	}
}
