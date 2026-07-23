package com.vxv.runebridge.path;

/** A known interactable world location (static catalog and/or live scene object). */
public final class Poi
{
	public final PoiType type;
	public final String name;
	public final int worldX;
	public final int worldY;
	public final int plane;
	/** Optional game object id when known (for Interact step). */
	public final int objectId;
	public final String action;

	public Poi(PoiType type, String name, int worldX, int worldY, int plane, int objectId, String action)
	{
		this.type = type;
		this.name = name;
		this.worldX = worldX;
		this.worldY = worldY;
		this.plane = plane;
		this.objectId = objectId;
		this.action = action;
	}

	public int chebyshevTo(int x, int y)
	{
		return Math.max(Math.abs(worldX - x), Math.abs(worldY - y));
	}
}
