package com.vxv.runebridge;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.google.gson.JsonObject;
import com.vxv.runebridge.path.PoiCatalog;
import com.vxv.runebridge.path.RoutePlanner;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Captures RuneLite game events and streams them as NDJSON to a VXV.RuneBridge C# host.
 * Also answers C# requests such as FindNearest (collision-aware walking routes).
 */
@Slf4j
@PluginDescriptor(
	name = "RuneBridge",
	description = "Bridge RuneLite game events to a local C# library (VXV.RuneBridge)",
	tags = {"bridge", "csharp", "dotnet", "ipc", "vxv", "pathfinding"}
)
public class RuneBridgePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private RuneBridgeConfig config;

	@Inject
	private Gson gson;

	@Inject
	private ClientThread clientThread;

	private BridgeClient bridge;
	private RoutePlanner routePlanner;

	@Provides
	RuneBridgeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RuneBridgeConfig.class);
	}

	@Override
	protected void startUp()
	{
		routePlanner = new RoutePlanner(client);
		reconnect();
		log.info("RuneBridge started → {}:{}", config.host(), config.port());
	}

	@Override
	protected void shutDown()
	{
		if (bridge != null)
		{
			bridge.emit("ClientGoodbye", Map.of("reason", "plugin_stop"));
			bridge.close();
			bridge = null;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!RuneBridgeConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		reconnect();
	}

	private void reconnect()
	{
		if (bridge != null)
		{
			bridge.close();
			bridge = null;
		}
		if (config.enabled())
		{
			bridge = new BridgeClient(gson, config.host(), config.port());
			bridge.setRequestHandler(this::handleBridgeRequest);
			bridge.start();
		}
	}

	/**
	 * Handles duplex requests from C# (must marshal onto client thread for game state).
	 */
	private Object handleBridgeRequest(JsonObject data)
	{
		String method = data.has("method") ? data.get("method").getAsString() : "";
		JsonObject params = data.has("params") && data.get("params").isJsonObject()
			? data.getAsJsonObject("params") : new JsonObject();

		if ("ListTargets".equalsIgnoreCase(method) || "listTargets".equals(method))
		{
			return Map.of("targets", PoiCatalog.knownTargets());
		}

		if ("FindNearest".equalsIgnoreCase(method) || "findNearest".equals(method))
		{
			String target = params.has("target") ? params.get("target").getAsString() : "";
			Integer maxCandidates = params.has("maxCandidates")
				? params.get("maxCandidates").getAsInt() : 12;

			return invokeOnClientThread(() ->
			{
				if (routePlanner == null)
				{
					routePlanner = new RoutePlanner(client);
				}
				return routePlanner.findNearest(target, maxCandidates);
			}, 5);
		}

		if ("WorldToScreen".equalsIgnoreCase(method) || "worldToScreen".equals(method))
		{
			return invokeOnClientThread(() -> projectTiles(params), 3);
		}

		Map<String, Object> err = new LinkedHashMap<>();
		err.put("ok", false);
		err.put("error", "Unknown method: " + method);
		return err;
	}

	private Map<String, Object> projectTiles(JsonObject params)
	{
		Map<String, Object> out = new LinkedHashMap<>();
		List<Map<String, Object>> tilesOut = new ArrayList<>();
		out.put("tiles", tilesOut);

		if (!params.has("tiles") || !params.get("tiles").isJsonArray())
		{
			return out;
		}

		int originX = 0;
		int originY = 0;
		try
		{
			java.awt.Canvas canvas = client.getCanvas();
			if (canvas != null)
			{
				java.awt.Point loc = canvas.getLocationOnScreen();
				originX = loc.x;
				originY = loc.y;
			}
		}
		catch (Exception ignored)
		{
			// headless / not showing
		}

		for (var el : params.getAsJsonArray("tiles"))
		{
			if (!el.isJsonObject())
			{
				continue;
			}
			JsonObject t = el.getAsJsonObject();
			int x = t.has("x") ? t.get("x").getAsInt() : 0;
			int y = t.has("y") ? t.get("y").getAsInt() : 0;
			int plane = t.has("plane") ? t.get("plane").getAsInt() : client.getPlane();

			Map<String, Object> row = new LinkedHashMap<>();
			row.put("x", x);
			row.put("y", y);
			row.put("plane", plane);
			row.put("onScreen", false);

			WorldPoint wp = new WorldPoint(x, y, plane);
			LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp != null && plane == client.getPlane())
			{
				net.runelite.api.Point canvasPt = net.runelite.api.Perspective.localToCanvas(client, lp, plane);
				if (canvasPt != null)
				{
					int cx = canvasPt.getX();
					int cy = canvasPt.getY();
					boolean onCanvas = cx >= 0 && cy >= 0
						&& cx < client.getCanvasWidth() && cy < client.getCanvasHeight();
					row.put("canvasX", cx);
					row.put("canvasY", cy);
					row.put("screenX", originX + cx);
					row.put("screenY", originY + cy);
					row.put("onScreen", onCanvas);
					row.put("regionId", wp.getRegionID());
				}
			}
			tilesOut.add(row);
		}
		return out;
	}

	private Object invokeOnClientThread(java.util.concurrent.Callable<Object> work, int timeoutSeconds)
	{
		CompletableFuture<Object> future = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				future.complete(work.call());
			}
			catch (Exception e)
			{
				Map<String, Object> err = new LinkedHashMap<>();
				err.put("ok", false);
				err.put("error", e.getMessage() != null ? e.getMessage() : "request failed");
				future.complete(err);
			}
		});
		try
		{
			return future.get(timeoutSeconds, TimeUnit.SECONDS);
		}
		catch (Exception e)
		{
			Map<String, Object> err = new LinkedHashMap<>();
			err.put("ok", false);
			err.put("error", "Timed out waiting for client thread: " + e.getMessage());
			return err;
		}
	}

	private void emit(String type, Object data)
	{
		if (bridge != null && config.enabled())
		{
			bridge.emit(type, data);
		}
	}

	// ── Core lifecycle ──────────────────────────────────────────────────────

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("gameState", event.getGameState() != null ? event.getGameState().name() : null);
		d.put("world", client.getWorld());
		emit("GameStateChanged", d);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!config.emitGameTicks())
		{
			return;
		}
		Player local = client.getLocalPlayer();
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("tick", client.getTickCount());
		d.put("plane", client.getPlane());
		if (local != null)
		{
			d.put("localPlayer", actorSummary(local));
		}
		emit("GameTick", d);
	}

	// ── Inventory / equipment ───────────────────────────────────────────────

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemContainer container = event.getItemContainer();
		if (container == null)
		{
			return;
		}
		int containerId = event.getContainerId();
		String kind = "Other";
		if (containerId == InventoryID.INVENTORY.getId())
		{
			kind = "Inventory";
		}
		else if (containerId == InventoryID.EQUIPMENT.getId())
		{
			kind = "Equipment";
		}
		else if (containerId == InventoryID.BANK.getId())
		{
			kind = "Bank";
		}

		Map<String, Object> d = new LinkedHashMap<>();
		d.put("containerId", containerId);
		d.put("containerKind", kind);
		d.put("items", itemList(container));
		emit("ItemContainerChanged", d);

		if ("Inventory".equals(kind))
		{
			emit("InventoryChanged", d);
		}
		else if ("Equipment".equals(kind))
		{
			emit("EquipmentChanged", d);
		}
		else if ("Bank".equals(kind))
		{
			emit("BankChanged", d);
		}
	}

	// ── Skills / XP ─────────────────────────────────────────────────────────

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("skill", skill != null ? skill.getName() : null);
		d.put("skillId", skill != null ? skill.ordinal() : -1);
		d.put("xp", event.getXp());
		d.put("level", event.getLevel());
		d.put("boostedLevel", event.getBoostedLevel());
		emit("StatChanged", d);
	}

	// ── Chat ────────────────────────────────────────────────────────────────

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!config.emitChat())
		{
			return;
		}
		ChatMessageType type = event.getType();
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("type", type != null ? type.name() : null);
		d.put("name", event.getName());
		d.put("sender", event.getSender());
		d.put("message", event.getMessage());
		d.put("timestamp", event.getTimestamp());
		emit("ChatMessage", d);
	}

	// ── Combat / actors ─────────────────────────────────────────────────────

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("actor", actorSummary(event.getActor()));
		if (event.getHitsplat() != null)
		{
			d.put("amount", event.getHitsplat().getAmount());
			d.put("hitsplatType", event.getHitsplat().getHitsplatType());
			d.put("mine", event.getHitsplat().isMine());
			d.put("others", event.getHitsplat().isOthers());
		}
		emit("HitsplatApplied", d);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		emit("ActorDeath", Map.of("actor", actorSummary(event.getActor())));
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (actor == null)
		{
			return;
		}
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("actor", actorSummary(actor));
		d.put("animationId", actor.getAnimation());
		emit("AnimationChanged", d);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		if (actor == null)
		{
			return;
		}
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("actor", actorSummary(actor));
		d.put("graphicId", actor.getGraphic());
		emit("GraphicChanged", d);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("source", actorSummary(event.getSource()));
		d.put("target", actorSummary(event.getTarget()));
		emit("InteractingChanged", d);
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (event.getProjectile() == null)
		{
			return;
		}
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("id", event.getProjectile().getId());
		d.put("remainingCycles", event.getProjectile().getRemainingCycles());
		emit("ProjectileMoved", d);
	}

	// ── Scene: NPCs / players / objects / ground items ──────────────────────

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (config.emitScene())
		{
			emit("NpcSpawned", npcSummary(event.getNpc()));
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (config.emitScene())
		{
			emit("NpcDespawned", npcSummary(event.getNpc()));
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (config.emitScene())
		{
			emit("PlayerSpawned", actorSummary(event.getPlayer()));
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		if (config.emitScene())
		{
			emit("PlayerDespawned", actorSummary(event.getPlayer()));
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (config.emitScene())
		{
			emit("GameObjectSpawned", gameObjectSummary(event.getGameObject()));
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (config.emitScene())
		{
			emit("GameObjectDespawned", gameObjectSummary(event.getGameObject()));
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (config.emitScene())
		{
			emit("GroundItemSpawned", groundItemSummary(event.getItem(), event.getTile() != null
				? event.getTile().getWorldLocation() : null));
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		if (config.emitScene())
		{
			emit("GroundItemDespawned", groundItemSummary(event.getItem(), event.getTile() != null
				? event.getTile().getWorldLocation() : null));
		}
	}

	// ── UI / menu / vars ────────────────────────────────────────────────────

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.emitMenu())
		{
			return;
		}
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("option", event.getMenuOption());
		d.put("target", event.getMenuTarget());
		d.put("id", event.getId());
		d.put("itemId", event.getItemId());
		d.put("param0", event.getParam0());
		d.put("param1", event.getParam1());
		if (event.getMenuAction() != null)
		{
			d.put("menuAction", event.getMenuAction().name());
		}
		emit("MenuOptionClicked", d);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		emit("WidgetLoaded", Map.of("groupId", event.getGroupId()));
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("varbitId", event.getVarbitId());
		d.put("varpId", event.getVarpId());
		d.put("value", event.getValue());
		emit("VarbitChanged", d);
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("slot", event.getSlot());
		if (event.getOffer() != null)
		{
			d.put("itemId", event.getOffer().getItemId());
			d.put("quantity", event.getOffer().getQuantitySold());
			d.put("totalQuantity", event.getOffer().getTotalQuantity());
			d.put("price", event.getOffer().getPrice());
			d.put("spent", event.getOffer().getSpent());
			if (event.getOffer().getState() != null)
			{
				d.put("state", event.getOffer().getState().name());
			}
		}
		emit("GrandExchangeOfferChanged", d);
	}

	// ── Payload helpers ─────────────────────────────────────────────────────

	private List<Map<String, Object>> itemList(ItemContainer container)
	{
		List<Map<String, Object>> items = new ArrayList<>();
		Item[] arr = container.getItems();
		if (arr == null)
		{
			return items;
		}
		for (int i = 0; i < arr.length; i++)
		{
			Item item = arr[i];
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("slot", i);
			m.put("id", item.getId());
			m.put("quantity", item.getQuantity());
			items.add(m);
		}
		return items;
	}

	private Map<String, Object> actorSummary(Actor actor)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		if (actor == null)
		{
			m.put("null", true);
			return m;
		}
		m.put("name", actor.getName());
		m.put("combatLevel", actor.getCombatLevel());
		m.put("animation", actor.getAnimation());
		m.put("graphic", actor.getGraphic());
		m.put("orientation", actor.getOrientation());
		m.put("healthRatio", actor.getHealthRatio());
		m.put("healthScale", actor.getHealthScale());
		WorldPoint wp = actor.getWorldLocation();
		if (wp != null)
		{
			m.put("worldX", wp.getX());
			m.put("worldY", wp.getY());
			m.put("plane", wp.getPlane());
			m.put("regionId", wp.getRegionID());
		}
		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			m.put("kind", "npc");
			m.put("npcId", npc.getId());
			m.put("index", npc.getIndex());
		}
		else if (actor instanceof Player)
		{
			Player p = (Player) actor;
			m.put("kind", "player");
			m.put("local", p == client.getLocalPlayer());
			m.put("team", p.getTeam());
		}
		else
		{
			m.put("kind", "actor");
		}
		return m;
	}

	private Map<String, Object> npcSummary(NPC npc)
	{
		if (npc == null)
		{
			return Map.of("null", true);
		}
		return actorSummary(npc);
	}

	private Map<String, Object> gameObjectSummary(GameObject obj)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		if (obj == null)
		{
			m.put("null", true);
			return m;
		}
		m.put("id", obj.getId());
		WorldPoint wp = obj.getWorldLocation();
		if (wp != null)
		{
			m.put("worldX", wp.getX());
			m.put("worldY", wp.getY());
			m.put("plane", wp.getPlane());
			m.put("regionId", wp.getRegionID());
		}
		return m;
	}

	private Map<String, Object> groundItemSummary(TileItem item, WorldPoint wp)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		if (item != null)
		{
			m.put("id", item.getId());
			m.put("quantity", item.getQuantity());
		}
		if (wp != null)
		{
			m.put("worldX", wp.getX());
			m.put("worldY", wp.getY());
			m.put("plane", wp.getPlane());
		}
		return m;
	}
}
