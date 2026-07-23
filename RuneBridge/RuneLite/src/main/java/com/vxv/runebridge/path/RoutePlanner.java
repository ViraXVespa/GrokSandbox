package com.vxv.runebridge.path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.ObjectComposition;

/**
 * Plans walking routes to nearest interactables, or lists all on-screen instances
 * when the target is already visible.
 * <p>
 * Off-screen routes return:
 * <ul>
 *   <li>{@code steps} — minimum click sequence (sparse Walk waypoints + Interact)</li>
 *   <li>{@code routeTiles} — full A-star or estimate path (every tile) for debug overlays</li>
 * </ul>
 */
public final class RoutePlanner
{
	private final Client client;

	public RoutePlanner(Client client)
	{
		this.client = client;
	}

	public Map<String, Object> findNearest(String target, Integer maxCandidates)
	{
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("ok", false);
		result.put("target", target);
		result.put("onScreen", false);
		result.put("instances", List.of());
		result.put("instanceCount", 0);
		result.put("steps", List.of());
		result.put("stepCount", 0);

		if (client == null || client.getLocalPlayer() == null)
		{
			result.put("error", "Not logged in or client unavailable");
			return result;
		}

		Player local = client.getLocalPlayer();
		WorldPoint start = local.getWorldLocation();
		if (start == null)
		{
			result.put("error", "No local player position");
			return result;
		}

		result.put("start", pointMap(start));

		// Live scene matches for this target type
		List<Poi> sceneMatches = scanSceneForTarget(target, start);

		// On-screen = projectable to the game canvas right now
		List<Poi> onScreen = new ArrayList<>();
		for (Poi poi : sceneMatches)
		{
			if (isOnGameScreen(poi))
			{
				onScreen.add(poi);
			}
		}

		// Dedupe by tile+objectId
		onScreen = dedupePois(onScreen);
		// Nearest first for convenience
		onScreen.sort(Comparator.comparingInt(p -> p.chebyshevTo(start.getX(), start.getY())));

		if (!onScreen.isEmpty())
		{
			int originX = 0;
			int originY = 0;
			try
			{
				java.awt.Canvas awtCanvas = client.getCanvas();
				if (awtCanvas != null)
				{
					java.awt.Point loc = awtCanvas.getLocationOnScreen();
					originX = loc.x;
					originY = loc.y;
				}
			}
			catch (Exception ignored)
			{
			}

			List<Map<String, Object>> instances = new ArrayList<>();
			for (Poi poi : onScreen)
			{
				Map<String, Object> inst = poiMap(poi);
				Point canvas = canvasPoint(poi);
				if (canvas != null)
				{
					inst.put("canvasX", canvas.getX());
					inst.put("canvasY", canvas.getY());
					inst.put("screenX", originX + canvas.getX());
					inst.put("screenY", originY + canvas.getY());
					inst.put("onScreen", true);
				}
				inst.put("chebyshev", poi.chebyshevTo(start.getX(), start.getY()));
				instances.add(inst);
			}

			result.put("ok", true);
			result.put("onScreen", true);
			result.put("method", "on_screen_instances");
			result.put("instances", instances);
			result.put("instanceCount", instances.size());
			result.put("destination", instances.get(0)); // nearest on-screen
			result.put("walkTiles", 0);
			result.put("steps", List.of());
			result.put("stepCount", 0);
			result.put("note", "Target is on screen; returning all visible instance tiles (no path).");
			return result;
		}

		// Not on screen → pathfind to nearest catalog + scene candidate
		List<Poi> candidates = new ArrayList<>(PoiCatalog.resolveTypes(target));
		candidates.addAll(sceneMatches);

		if (candidates.isEmpty())
		{
			result.put("error", "Unknown target or no POIs. Known: " + PoiCatalog.knownTargets());
			result.put("knownTargets", PoiCatalog.knownTargets());
			return result;
		}

		int limit = maxCandidates == null ? 12 : Math.max(1, maxCandidates);
		candidates.sort(Comparator
			.comparingInt((Poi p) -> p.plane == start.getPlane() ? 0 : 1)
			.thenComparingInt(p -> p.chebyshevTo(start.getX(), start.getY())));

		List<Poi> top = candidates.subList(0, Math.min(limit, candidates.size()));

		RouteBest best = null;
		List<Map<String, Object>> considered = new ArrayList<>();

		for (Poi poi : top)
		{
			Map<String, Object> c = new LinkedHashMap<>();
			c.put("name", poi.name);
			c.put("x", poi.worldX);
			c.put("y", poi.worldY);
			c.put("plane", poi.plane);
			c.put("chebyshev", poi.chebyshevTo(start.getX(), start.getY()));

			WorldPoint goal = new WorldPoint(poi.worldX, poi.worldY, poi.plane);
			List<WorldPoint> path;
			String method;

			if (poi.plane != start.getPlane())
			{
				path = List.of();
				method = "different_plane";
			}
			else if (LocalPoint.fromWorld(client, goal) != null)
			{
				path = ScenePathfinder.findPath(client, start, goal);
				method = path.isEmpty() ? "unreachable_in_scene" : "scene_astar";
			}
			else
			{
				path = List.of(start, goal);
				method = "estimated_offscene";
			}

			int walkTiles = path.isEmpty() ? Integer.MAX_VALUE : Math.max(0, path.size() - 1);
			int score = walkTiles;
			if ("estimated_offscene".equals(method))
			{
				score = poi.chebyshevTo(start.getX(), start.getY()) + 10_000;
			}
			else if ("unreachable_in_scene".equals(method) || "different_plane".equals(method))
			{
				score = 100_000 + poi.chebyshevTo(start.getX(), start.getY());
			}

			c.put("method", method);
			c.put("walkTiles", walkTiles == Integer.MAX_VALUE ? null : walkTiles);
			c.put("score", score);
			considered.add(c);

			if (best == null || score < best.score)
			{
				best = new RouteBest(poi, path, method, score, walkTiles);
			}
		}

		result.put("considered", considered);
		result.put("onScreen", false);

		if (best == null || (best.path.isEmpty() && !"estimated_offscene".equals(best.method)))
		{
			result.put("error", "No walkable route found in loaded scene for nearest candidates");
			if (best != null)
			{
				result.put("nearestPoi", poiMap(best.poi));
				result.put("method", best.method);
			}
			return result;
		}

		List<WorldPoint> path = best.path;

		// Full A* / estimate tile list for debug overlays (every walk tile).
		List<Map<String, Object>> routeTiles = new ArrayList<>();
		for (WorldPoint wp : path)
		{
			routeTiles.add(tileMap(wp));
		}

		// Min-click steps: the game pathfinds to a clicked destination, so we only
		// emit sparse Walk waypoints (spaced by MAX_WALK_CLICK_RANGE along the path)
		// plus a final Interact — not one step per tile.
		List<Map<String, Object>> steps = buildMinClickSteps(path, best.poi);

		result.put("ok", true);
		result.put("method", best.method);
		result.put("destination", poiMap(best.poi));
		result.put("walkTiles", best.walkTiles == Integer.MAX_VALUE
			? best.poi.chebyshevTo(start.getX(), start.getY())
			: best.walkTiles);
		result.put("routeTiles", routeTiles);
		result.put("routeTileCount", routeTiles.size());
		result.put("steps", steps);
		result.put("stepCount", steps.size());
		result.put("note", "estimated_offscene".equals(best.method)
			? "Target not on screen / outside loaded scene; path is straight-line estimate. steps = min clicks; routeTiles = full tile list."
			: "Target not on screen; scene A* walking distance. steps = min clicks to destination; routeTiles = full path.");
		return result;
	}

	/**
	 * Max path-tile stride between successive walk-click waypoints.
	 * The client pathfinds to each clicked destination, so one click covers a long
	 * contiguous stretch; intermediate clicks only appear on very long routes.
	 * Scene size is 104 — 64 keeps typical bank/ore routes at a single Walk click.
	 */
	private static final int MAX_WALK_CLICK_RANGE = 64;

	/**
	 * Build the minimum click sequence: sparse Walk waypoints along {@code path}
	 * (skipping the start tile), then one Interact at the POI.
	 * If already on the destination tile, Interact only.
	 */
	private static List<Map<String, Object>> buildMinClickSteps(List<WorldPoint> path, Poi poi)
	{
		List<Map<String, Object>> steps = new ArrayList<>();
		if (path == null || path.isEmpty())
		{
			steps.add(interactStep(poi, poi.worldX, poi.worldY, poi.plane));
			return steps;
		}

		WorldPoint startWp = path.get(0);
		WorldPoint end = path.get(path.size() - 1);
		boolean alreadyThere = startWp.getX() == end.getX()
			&& startWp.getY() == end.getY()
			&& startWp.getPlane() == end.getPlane();

		if (!alreadyThere && path.size() > 1)
		{
			// Advance along the known path in strides of MAX_WALK_CLICK_RANGE tiles.
			// Path[0] is the player's current tile (not a click destination).
			int last = 0;
			while (last < path.size() - 1)
			{
				int next = Math.min(last + MAX_WALK_CLICK_RANGE, path.size() - 1);
				WorldPoint walkTo = path.get(next);
				Map<String, Object> walk = tileMap(walkTo);
				walk.put("kind", "Walk");
				steps.add(walk);
				last = next;
			}
		}

		// Interact at the POI (may share coords with the final walk tile).
		steps.add(interactStep(poi, end.getX(), end.getY(), end.getPlane()));
		return steps;
	}

	private static Map<String, Object> tileMap(WorldPoint wp)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("x", wp.getX());
		m.put("y", wp.getY());
		m.put("plane", wp.getPlane());
		return m;
	}

	private static Map<String, Object> interactStep(Poi poi, int x, int y, int plane)
	{
		Map<String, Object> step = new LinkedHashMap<>();
		step.put("x", x);
		step.put("y", y);
		step.put("plane", plane);
		step.put("kind", "Interact");
		step.put("action", poi.action != null ? poi.action : "Use");
		if (poi.objectId > 0)
		{
			step.put("objectId", poi.objectId);
		}
		step.put("label", poi.name);
		return step;
	}

	/** True if the POI's world tile projects onto the game canvas. */
	private boolean isOnGameScreen(Poi poi)
	{
		return canvasPoint(poi) != null;
	}

	private Point canvasPoint(Poi poi)
	{
		if (client == null || poi.plane != client.getPlane())
		{
			return null;
		}
		WorldPoint wp = new WorldPoint(poi.worldX, poi.worldY, poi.plane);
		LocalPoint lp = LocalPoint.fromWorld(client, wp);
		if (lp == null)
		{
			return null;
		}
		Point canvas = Perspective.localToCanvas(client, lp, poi.plane);
		if (canvas == null)
		{
			return null;
		}
		int w = client.getCanvasWidth();
		int h = client.getCanvasHeight();
		if (canvas.getX() < 0 || canvas.getY() < 0 || canvas.getX() >= w || canvas.getY() >= h)
		{
			return null;
		}
		return canvas;
	}

	private static List<Poi> dedupePois(List<Poi> input)
	{
		Set<String> seen = new LinkedHashSet<>();
		List<Poi> out = new ArrayList<>();
		for (Poi p : input)
		{
			String key = p.worldX + ":" + p.worldY + ":" + p.plane + ":" + p.objectId + ":" + p.name;
			if (seen.add(key))
			{
				out.add(p);
			}
		}
		return out;
	}

	private List<Poi> scanSceneForTarget(String target, WorldPoint start)
	{
		List<Poi> found = new ArrayList<>();
		Scene scene = client.getScene();
		if (scene == null)
		{
			return found;
		}
		String key = target == null ? "" : target.toLowerCase(Locale.ROOT);
		Tile[][][] tiles = scene.getTiles();
		if (tiles == null)
		{
			return found;
		}
		int z = client.getPlane();
		if (z < 0 || z >= tiles.length)
		{
			return found;
		}
		for (int x = 0; x < Constants.SCENE_SIZE; x++)
		{
			for (int y = 0; y < Constants.SCENE_SIZE; y++)
			{
				Tile tile = tiles[z][x][y];
				if (tile == null)
				{
					continue;
				}
				List<TileObject> objects = new ArrayList<>();
				if (tile.getGameObjects() != null)
				{
					for (GameObject go : tile.getGameObjects())
					{
						if (go != null)
						{
							objects.add(go);
						}
					}
				}
				WallObject wall = tile.getWallObject();
				if (wall != null)
				{
					objects.add(wall);
				}
				DecorativeObject deco = tile.getDecorativeObject();
				if (deco != null)
				{
					objects.add(deco);
				}
				GroundObject ground = tile.getGroundObject();
				if (ground != null)
				{
					objects.add(ground);
				}

				for (TileObject obj : objects)
				{
					ObjectComposition def = client.getObjectDefinition(obj.getId());
					if (def == null)
					{
						continue;
					}
					if (def.getImpostorIds() != null)
					{
						def = def.getImpostor();
						if (def == null)
						{
							continue;
						}
					}
					String name = def.getName();
					if (name == null || name.equals("null"))
					{
						continue;
					}
					String n = name.toLowerCase(Locale.ROOT);
					String[] actions = def.getActions();
					PoiType type = matchLiveObject(key, n, actions);
					if (type == null)
					{
						continue;
					}
					WorldPoint wp = obj.getWorldLocation();
					if (wp == null)
					{
						continue;
					}
					String action = firstAction(actions);
					found.add(new Poi(type, name, wp.getX(), wp.getY(), wp.getPlane(), obj.getId(), action));
				}
			}
		}
		return found;
	}

	/**
	 * Returns a {@link PoiType} only when this live object is a valid hit for {@code targetKey}.
	 * Specific targets (e.g. magic_tree, ore_iron) never fall through to other subtypes.
	 */
	private static PoiType matchLiveObject(String targetKey, String objectName, String[] actions)
	{
		String t = normalizeTargetKey(targetKey);
		String n = objectName == null ? "" : objectName.toLowerCase(Locale.ROOT);

		// --- classify the object by what it actually is ---
		PoiType objectType = classifyLiveObject(n, actions);
		if (objectType == null)
		{
			return null;
		}

		// --- accept only if the target asked for this object (or its generic parent) ---
		if (targetAccepts(t, objectType))
		{
			return objectType;
		}
		return null;
	}

	/** Normalize user/wire target: "Magic Tree" / "magic-tree" → "magic_tree". */
	private static String normalizeTargetKey(String targetKey)
	{
		if (targetKey == null)
		{
			return "";
		}
		return targetKey.trim().toLowerCase(Locale.ROOT)
			.replace('-', '_')
			.replace(' ', '_');
	}

	/**
	 * Identify what a live scene object is, independent of the query.
	 * Returns null if it is not a known interactable we track.
	 */
	private static PoiType classifyLiveObject(String objectName, String[] actions)
	{
		// Trees (Chop down) — check specific species before generic "tree"
		boolean choppable = hasAction(actions, "Chop down") || hasAction(actions, "Chop");
		if (choppable)
		{
			if (objectName.contains("magic"))
			{
				return PoiType.TREE_MAGIC;
			}
			if (objectName.contains("yew"))
			{
				return PoiType.TREE_YEW;
			}
			if (objectName.contains("maple"))
			{
				return PoiType.TREE_MAPLE;
			}
			if (objectName.contains("willow"))
			{
				return PoiType.TREE_WILLOW;
			}
			if (objectName.contains("oak"))
			{
				return PoiType.TREE_OAK;
			}
			// "Tree", "Dead tree", "Evergreen", "Jungle tree", etc.
			if (objectName.contains("tree") || objectName.contains("evergreen"))
			{
				return PoiType.TREE_NORMAL;
			}
		}

		// Ore rocks / veins
		boolean mineable = objectName.contains("rocks") || objectName.contains("vein")
			|| hasAction(actions, "Mine");
		if (mineable && (objectName.contains("rocks") || objectName.contains("vein")
			|| objectName.contains("ore") || objectName.contains("amethyst")))
		{
			if (objectName.contains("runite"))
			{
				return PoiType.ORE_RUNITE;
			}
			if (objectName.contains("amethyst"))
			{
				return PoiType.ORE_AMETHYST;
			}
			if (objectName.contains("adamant"))
			{
				return PoiType.ORE_ADAMANTITE;
			}
			if (objectName.contains("mithril"))
			{
				return PoiType.ORE_MITHRIL;
			}
			if (objectName.contains("coal"))
			{
				return PoiType.ORE_COAL;
			}
			if (objectName.contains("iron"))
			{
				return PoiType.ORE_IRON;
			}
			if (objectName.contains("gold"))
			{
				return PoiType.ORE_GOLD;
			}
			if (objectName.contains("silver"))
			{
				return PoiType.ORE_SILVER;
			}
			if (objectName.contains("copper"))
			{
				return PoiType.ORE_COPPER;
			}
			if (objectName.contains("tin"))
			{
				return PoiType.ORE_TIN;
			}
			// Unclassified mineable rock — only usable for generic ore/rock queries via targetAccepts
			return PoiType.CUSTOM;
		}

		if (objectName.contains("bank") || hasAction(actions, "Bank"))
		{
			return PoiType.BANK;
		}
		if (objectName.contains("deposit") && (objectName.contains("box") || hasAction(actions, "Deposit")))
		{
			return PoiType.DEPOSIT_BOX;
		}
		if (objectName.contains("furnace") || hasAction(actions, "Smelt"))
		{
			return PoiType.FURNACE;
		}
		if (objectName.contains("anvil") || hasAction(actions, "Smith"))
		{
			return PoiType.ANVIL;
		}
		// Cooking range / fire — avoid matching random objects with "range" in a longer word
		if ((objectName.equals("range") || objectName.contains("cooking range")
			|| objectName.endsWith(" range") || objectName.contains("stove"))
			|| (objectName.contains("fire") && hasAction(actions, "Cook")))
		{
			return PoiType.RANGE;
		}
		if (objectName.contains("fishing spot")
			|| hasAction(actions, "Net") || hasAction(actions, "Lure")
			|| hasAction(actions, "Bait") || hasAction(actions, "Cage")
			|| hasAction(actions, "Harpoon"))
		{
			return PoiType.FISHING_SPOT;
		}
		if (objectName.contains("altar") || hasAction(actions, "Pray-at") || hasAction(actions, "Pray"))
		{
			return PoiType.ALTAR;
		}
		if (objectName.contains("grand exchange") || objectName.contains("exchange booth"))
		{
			return PoiType.GRAND_EXCHANGE;
		}
		return null;
	}

	/**
	 * Whether query {@code t} (normalized) wants objects of {@code objectType}.
	 * Specific targets only accept that subtype; generics accept the whole family.
	 */
	private static boolean targetAccepts(String t, PoiType objectType)
	{
		if (t.isEmpty() || objectType == null)
		{
			return false;
		}

		// Exact enum / wire name
		if (t.equalsIgnoreCase(objectType.name())
			|| t.equals(objectType.name().toLowerCase(Locale.ROOT)))
		{
			return true;
		}

		// ---- Trees ----
		if (objectType.name().startsWith("TREE_"))
		{
			// Generic: all choppable trees
			if (t.equals("tree") || t.equals("trees") || t.equals("wood") || t.equals("woodcut")
				|| t.equals("woodcutting"))
			{
				return true;
			}
			// Species: magic_tree, tree_magic, magic, etc.
			String species = objectType.name().substring("TREE_".length()).toLowerCase(Locale.ROOT);
			if (species.equals("normal"))
			{
				return t.equals("tree_normal") || t.equals("normal_tree")
					|| t.equals("regular_tree") || t.equals("basic_tree");
			}
			return t.equals("tree_" + species)
				|| t.equals(species + "_tree")
				|| t.equals(species + "s")           // "yews", "magics"
				|| t.equals(species);                // "magic", "yew"
		}

		// ---- Ores ----
		if (objectType.name().startsWith("ORE_") || objectType == PoiType.CUSTOM)
		{
			boolean genericOre = t.equals("ore") || t.equals("rock") || t.equals("rocks")
				|| t.equals("mine") || t.equals("mining");
			if (objectType == PoiType.CUSTOM)
			{
				// Unknown rock only for generic ore queries
				return genericOre;
			}
			if (genericOre)
			{
				return true;
			}
			String metal = objectType.name().substring("ORE_".length()).toLowerCase(Locale.ROOT);
			return t.equals("ore_" + metal)
				|| t.equals(metal + "_ore")
				|| t.equals(metal + "_rock")
				|| t.equals(metal + "_rocks")
				|| t.equals(metal); // "iron", "runite", "coal"
		}

		// ---- Simple categories ----
		switch (objectType)
		{
			case BANK:
			case BANK_BOOTH:
			case BANK_CHEST:
				return t.equals("bank") || t.equals("banks")
					|| t.equals("bank_booth") || t.equals("bank_chest") || t.contains("bank");
			case FURNACE:
				return t.equals("furnace") || t.equals("furnaces") || t.equals("smelt") || t.equals("smelting");
			case ANVIL:
				return t.equals("anvil") || t.equals("anvils") || t.equals("smith") || t.equals("smithing");
			case RANGE:
			case COOKING_RANGE:
				// Avoid matching "orange", "arrange", etc. — require whole tokens
				return t.equals("range") || t.equals("ranges") || t.equals("cook")
					|| t.equals("cooking") || t.equals("cooking_range") || t.equals("stove");
			case FISHING_SPOT:
				return t.equals("fish") || t.equals("fishing") || t.equals("fishing_spot")
					|| t.equals("fishing_spots");
			case ALTAR:
				return t.equals("altar") || t.equals("altars") || t.equals("pray") || t.equals("prayer");
			case DEPOSIT_BOX:
				return t.equals("deposit") || t.equals("deposit_box") || t.equals("depositbox");
			case GRAND_EXCHANGE:
				return t.equals("ge") || t.equals("grand_exchange") || t.equals("exchange");
			default:
				return false;
		}
	}

	private static boolean hasAction(String[] actions, String want)
	{
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if (a != null && a.equalsIgnoreCase(want))
			{
				return true;
			}
		}
		return false;
	}

	private static String firstAction(String[] actions)
	{
		if (actions == null)
		{
			return "Use";
		}
		for (String a : actions)
		{
			if (a != null && !a.equals("null"))
			{
				return a;
			}
		}
		return "Use";
	}

	private static Map<String, Object> pointMap(WorldPoint wp)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("x", wp.getX());
		m.put("y", wp.getY());
		m.put("plane", wp.getPlane());
		m.put("regionId", wp.getRegionID());
		return m;
	}

	private static Map<String, Object> poiMap(Poi poi)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", poi.type.name());
		m.put("name", poi.name);
		m.put("x", poi.worldX);
		m.put("y", poi.worldY);
		m.put("plane", poi.plane);
		m.put("objectId", poi.objectId);
		m.put("action", poi.action);
		return m;
	}

	private static final class RouteBest
	{
		final Poi poi;
		final List<WorldPoint> path;
		final String method;
		final int score;
		final int walkTiles;

		RouteBest(Poi poi, List<WorldPoint> path, String method, int score, int walkTiles)
		{
			this.poi = poi;
			this.path = path;
			this.method = method;
			this.score = score;
			this.walkTiles = walkTiles;
		}
	}
}
