package com.vxv.runebridge.path;

/**
 * Interactable categories for nearest-route queries from C#.
 * Keep names stable — they match VXV.RuneBridge.Pathfinding.RuneTarget.
 */
public enum PoiType
{
	BANK,
	BANK_BOOTH,
	BANK_CHEST,
	FURNACE,
	ANVIL,
	RANGE,
	COOKING_RANGE,
	WATER_SOURCE,
	ALTAR,
	GENERAL_STORE,
	GRAND_EXCHANGE,
	DEPOSIT_BOX,
	LOOM,
	SPINNING_WHEEL,
	POTTERY_OVEN,
	CRAFTING_GUILD,
	// Mining
	ORE_COPPER,
	ORE_TIN,
	ORE_IRON,
	ORE_COAL,
	ORE_MITHRIL,
	ORE_ADAMANTITE,
	ORE_RUNITE,
	ORE_GOLD,
	ORE_SILVER,
	ORE_AMETHYST,
	// Woodcutting
	TREE_NORMAL,
	TREE_OAK,
	TREE_WILLOW,
	TREE_MAPLE,
	TREE_YEW,
	TREE_MAGIC,
	// Farming / other
	TOOL_LEPRECHAUN,
	// Fishing
	FISHING_SPOT,
	// Fallback generic
	CUSTOM
}
