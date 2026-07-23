package com.vxv.advancedminingtracker.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Known ore types with stable inventory item IDs used for rock icons.
 * IDs match classic OSRS item definitions (unchanged for years).
 */
@Getter
@RequiredArgsConstructor
public enum OreType
{
	COPPER("Copper", 436),
	TIN("Tin", 438),
	IRON("Iron", 440),
	SILVER("Silver", 442),
	COAL("Coal", 453),
	GOLD("Gold", 444),
	MITHRIL("Mithril", 447),
	ADAMANTITE("Adamantite", 449),
	RUNITE("Runite", 451),
	AMETHYST("Amethyst", 21347),
	BLURITE("Blurite", 668),
	ELEMENTAL("Elemental", 2892),
	DAEYALT("Daeyalt", 24706),
	LUNAR("Lunar", 9076),
	SOFT_CLAY("Soft clay", 1761),
	SANDSTONE("Sandstone", 6977),
	GRANITE("Granite", 6979),
	GEM("Gem rock", 1623),
	BARRONITE("Barronite", 25676),
	CALCIFIED("Calcified deposit", 29088),
	UNKNOWN("Ore", 440);

	private final String displayName;
	private final int itemId;

	/**
	 * Infer ore type from an object composition name (e.g. "Runite rocks", "Iron rocks").
	 */
	public static OreType fromObjectName(String name)
	{
		if (name == null || name.isEmpty())
		{
			return UNKNOWN;
		}

		String lower = name.toLowerCase();

		if (lower.contains("runite"))
		{
			return RUNITE;
		}
		if (lower.contains("adamant"))
		{
			return ADAMANTITE;
		}
		if (lower.contains("mithril"))
		{
			return MITHRIL;
		}
		if (lower.contains("coal"))
		{
			return COAL;
		}
		if (lower.contains("gold"))
		{
			return GOLD;
		}
		if (lower.contains("silver"))
		{
			return SILVER;
		}
		if (lower.contains("iron"))
		{
			return IRON;
		}
		if (lower.contains("copper"))
		{
			return COPPER;
		}
		if (lower.contains("tin"))
		{
			return TIN;
		}
		if (lower.contains("amethyst"))
		{
			return AMETHYST;
		}
		if (lower.contains("blurite"))
		{
			return BLURITE;
		}
		if (lower.contains("elemental"))
		{
			return ELEMENTAL;
		}
		if (lower.contains("daeyalt"))
		{
			return DAEYALT;
		}
		if (lower.contains("lunar"))
		{
			return LUNAR;
		}
		if (lower.contains("clay"))
		{
			return SOFT_CLAY;
		}
		if (lower.contains("sandstone"))
		{
			return SANDSTONE;
		}
		if (lower.contains("granite"))
		{
			return GRANITE;
		}
		if (lower.contains("gem"))
		{
			return GEM;
		}
		if (lower.contains("barronite"))
		{
			return BARRONITE;
		}
		if (lower.contains("calcified"))
		{
			return CALCIFIED;
		}

		return UNKNOWN;
	}

	/**
	 * Infer from chat lines such as "You manage to mine some runite ore."
	 */
	public static OreType fromChatMessage(String message)
	{
		if (message == null)
		{
			return UNKNOWN;
		}
		return fromObjectName(message);
	}
}
