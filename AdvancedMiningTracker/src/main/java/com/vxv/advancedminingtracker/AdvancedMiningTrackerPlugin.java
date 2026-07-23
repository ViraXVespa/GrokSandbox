package com.vxv.advancedminingtracker;

import com.google.inject.Provides;
import com.vxv.advancedminingtracker.model.TrackedRock;
import com.vxv.advancedminingtracker.ui.AdvancedMiningTrackerPanel;
import com.vxv.advancedminingtracker.util.OreType;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldResult;

@Slf4j
@PluginDescriptor(
	name = "Advanced Mining Tracker",
	description = "Track rocks you mine across worlds with regen timers and one-click hop when ready",
	tags = {"mining", "ore", "respawn", "timer", "world", "hop", "runite"}
)
public class AdvancedMiningTrackerPlugin extends Plugin
{
	private static final int DISPLAY_SWITCHER_MAX_ATTEMPTS = 3;
	private static final int PENDING_MINE_TTL_MS = 15_000;
	private static final int RECENT_MINE_WINDOW_MS = 8_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private AdvancedMiningTrackerConfig config;

	@Inject
	private AdvancedMiningTrackerPanel panel;

	@Inject
	private ItemManager itemManager;

	@Inject
	private WorldService worldService;

	@Inject
	private ChatMessageManager chatMessageManager;

	private NavigationButton navButton;

	/** Rocks depleted by the player, retained across world hops. */
	private final List<TrackedRock> trackedRocks = new CopyOnWriteArrayList<>();

	/** Last rock the player targeted with Mine. */
	private WorldPoint pendingMinePoint;
	private String pendingMineName;
	private OreType pendingOreType = OreType.UNKNOWN;
	private Instant pendingMineTime;

	/** Last time the player was actively mining (chat swing / manage to mine). */
	private Instant lastMineActivity;

	private net.runelite.api.World quickHopTargetWorld;
	private int displaySwitcherAttempts;

	@Provides
	AdvancedMiningTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AdvancedMiningTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		BufferedImage icon = createNavIcon();
		navButton = NavigationButton.builder()
			.tooltip("Advanced Mining Tracker")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		if (config.showSidebar())
		{
			clientToolbar.addNavigation(navButton);
		}

		rebuildPanel();
		log.debug("Advanced Mining Tracker started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		trackedRocks.clear();
		pendingMinePoint = null;
		quickHopTargetWorld = null;
		displaySwitcherAttempts = 0;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AdvancedMiningTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("showSidebar".equals(event.getKey()))
		{
			if (config.showSidebar())
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!"Mine".equals(event.getMenuOption()))
		{
			return;
		}

		TileObject object = findTargetObject(event);
		if (object == null)
		{
			return;
		}

		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition != null && composition.getImpostorIds() != null)
		{
			composition = composition.getImpostor();
		}

		String name = composition != null ? composition.getName() : "Rocks";
		pendingMinePoint = object.getWorldLocation();
		pendingMineName = name;
		pendingOreType = OreType.fromObjectName(name);
		pendingMineTime = Instant.now();
		lastMineActivity = Instant.now();

		log.debug("Pending mine target: {} ({}) at {}", name, pendingOreType, pendingMinePoint);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String msg = event.getMessage();
		if (msg == null)
		{
			return;
		}

		if (msg.startsWith("You swing your pick") || msg.contains("manage to mine"))
		{
			lastMineActivity = Instant.now();
			if (msg.contains("manage to mine"))
			{
				OreType fromChat = OreType.fromChatMessage(msg);
				if (fromChat != OreType.UNKNOWN)
				{
					pendingOreType = fromChat;
				}
			}
		}

		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& msg.equals("Please finish what you're doing before using the World Switcher."))
		{
			resetQuickHopper();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() != ScriptID.ADD_OVERLAYTIMER_LOC)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		if (args == null || args.length < 6)
		{
			return;
		}

		int locCoord = (int) args[1];
		int locId = (int) args[2];
		int ticks = (int) args[5];

		if (!isDepletedRockObject(locId))
		{
			return;
		}

		WorldPoint point = WorldPoint.fromCoord(locCoord);
		if (point == null)
		{
			return;
		}

		if (config.onlyUserMined() && !isAttributableToPlayer(point))
		{
			return;
		}

		OreType oreType = pendingOreType != null ? pendingOreType : OreType.UNKNOWN;
		String name = pendingMineName != null ? pendingMineName : oreType.getDisplayName();

		// Prefer matching pending target for ore type/name
		if (pendingMinePoint != null && pendingMinePoint.distanceTo(point) <= 1)
		{
			oreType = pendingOreType;
			name = pendingMineName;
		}

		int worldId = client.getWorld();
		int respawnMillis = ticks * Constants.GAME_TICK_LENGTH;

		// Replace existing timer for same world+tile
		trackedRocks.removeIf(r -> r.getWorldId() == worldId && r.getWorldPoint().equals(point));

		TrackedRock rock = new TrackedRock(worldId, point, oreType, Instant.now(), respawnMillis, name);
		trackedRocks.add(rock);

		log.debug("Tracked depleted rock {} on w{} at {} ({} ticks / {} ms)",
			oreType, worldId, point, ticks, respawnMillis);

		rebuildPanel();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		pruneExpiredReadyRocks();

		// Keep panel timers smooth enough without per-frame swing spam
		if (client.getTickCount() % 2 == 0)
		{
			SwingUtilities.invokeLater(panel::refreshTimers);
		}

		// World hop sequence (mirrors World Hopper plugin)
		if (quickHopTargetWorld == null)
		{
			return;
		}

		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();

			if (++displaySwitcherAttempts >= DISPLAY_SWITCHER_MAX_ATTEMPTS)
			{
				queueConsole("Failed to quick-hop after " + displaySwitcherAttempts + " attempts.");
				resetQuickHopper();
			}
		}
		else
		{
			client.hopToWorld(quickHopTargetWorld);
			if (config.clearReadyOnHop())
			{
				clearReadyRocksForWorld(quickHopTargetWorld.getId());
			}
			resetQuickHopper();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Intentionally keep tracked rocks across HOPPING — that is the point of this plugin.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Soft clear pending context only
			pendingMinePoint = null;
			pendingMineName = null;
			pendingOreType = OreType.UNKNOWN;
			pendingMineTime = null;
		}
	}

	public List<TrackedRock> getTrackedRocksSnapshot()
	{
		return Collections.unmodifiableList(new ArrayList<>(trackedRocks));
	}

	public void clearAllRocks()
	{
		trackedRocks.clear();
		rebuildPanel();
	}

	public void clearReadyRocks()
	{
		trackedRocks.removeIf(TrackedRock::isReady);
		rebuildPanel();
	}

	public void clearReadyRocksForWorld(int worldId)
	{
		trackedRocks.removeIf(r -> r.getWorldId() == worldId && r.isReady());
		rebuildPanel();
	}

	/**
	 * Called from the side panel (EDT) when a ready world group is clicked.
	 */
	public void hopToWorld(int worldId)
	{
		clientThread.invoke(() -> hop(worldId));
	}

	private void hop(int worldId)
	{
		assert client.isClientThread();

		if (client.getWorld() == worldId)
		{
			queueConsole("You are already on world " + worldId + ".");
			return;
		}

		WorldResult worldResult = worldService.getWorlds();
		if (worldResult == null)
		{
			queueConsole("World list is not available yet.");
			return;
		}

		World world = worldResult.findWorld(worldId);
		if (world == null)
		{
			queueConsole("Unknown world " + worldId + ".");
			return;
		}

		final net.runelite.api.World rsWorld = client.createWorld();
		rsWorld.setActivity(world.getActivity());
		rsWorld.setAddress(world.getAddress());
		rsWorld.setId(world.getId());
		rsWorld.setPlayerCount(world.getPlayers());
		rsWorld.setLocation(world.getLocation());
		rsWorld.setTypes(WorldUtil.toWorldTypes(world.getTypes()));

		if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			client.changeWorld(rsWorld);
			return;
		}

		if (config.showHopMessage())
		{
			String chatMessage = new ChatMessageBuilder()
				.append(ChatColorType.NORMAL)
				.append("Quick-hopping to World ")
				.append(ChatColorType.HIGHLIGHT)
				.append(Integer.toString(world.getId()))
				.append(ChatColorType.NORMAL)
				.append("..")
				.build();

			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(chatMessage)
				.build());
		}

		quickHopTargetWorld = rsWorld;
		displaySwitcherAttempts = 0;
	}

	private void resetQuickHopper()
	{
		quickHopTargetWorld = null;
		displaySwitcherAttempts = 0;
	}

	private void pruneExpiredReadyRocks()
	{
		int keepSeconds = config.keepReadySeconds();
		if (keepSeconds <= 0)
		{
			return;
		}

		boolean removed = false;
		// CopyOnWriteArrayList iterator does not support remove; rebuild list instead
		List<TrackedRock> keep = new ArrayList<>();
		for (TrackedRock rock : trackedRocks)
		{
			if (rock.isReady())
			{
				long readyFor = Duration.between(
					rock.getStartTime().plusMillis(rock.getRespawnMillis()), Instant.now()).getSeconds();
				if (readyFor > keepSeconds)
				{
					removed = true;
					continue;
				}
			}
			keep.add(rock);
		}

		if (removed)
		{
			trackedRocks.clear();
			trackedRocks.addAll(keep);
			rebuildPanel();
		}
	}

	private boolean isAttributableToPlayer(WorldPoint point)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return false;
		}

		int maxDist = config.maxDistance();
		WorldPoint playerPoint = local.getWorldLocation();
		if (playerPoint.distanceTo(point) > maxDist)
		{
			// Also accept if it matches the pending mine target within range
			if (pendingMinePoint == null || pendingMinePoint.distanceTo(point) > 1)
			{
				return false;
			}
		}

		Instant now = Instant.now();
		boolean recentActivity = lastMineActivity != null
			&& Duration.between(lastMineActivity, now).toMillis() <= RECENT_MINE_WINDOW_MS;
		boolean recentPending = pendingMineTime != null
			&& Duration.between(pendingMineTime, now).toMillis() <= PENDING_MINE_TTL_MS
			&& pendingMinePoint != null
			&& pendingMinePoint.distanceTo(point) <= 1;

		return recentActivity || recentPending;
	}

	private TileObject findTargetObject(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (action == MenuAction.GAME_OBJECT_FIRST_OPTION
			|| action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION
			|| action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION
			|| action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			return findTileObject(event.getParam0(), event.getParam1(), event.getId());
		}
		return null;
	}

	private TileObject findTileObject(int x, int y, int id)
	{
		int sceneX = x;
		int sceneY = y;
		if (sceneX < 0 || sceneY < 0 || sceneX >= Constants.SCENE_SIZE || sceneY >= Constants.SCENE_SIZE)
		{
			return null;
		}

		Tile[][][] tiles = client.getScene().getTiles();
		Tile tile = tiles[client.getPlane()][sceneX][sceneY];
		if (tile == null)
		{
			return null;
		}

		for (GameObject object : tile.getGameObjects())
		{
			if (object != null && object.getId() == id)
			{
				return object;
			}
		}

		if (tile.getWallObject() != null && tile.getWallObject().getId() == id)
		{
			return tile.getWallObject();
		}
		if (tile.getDecorativeObject() != null && tile.getDecorativeObject().getId() == id)
		{
			return tile.getDecorativeObject();
		}
		if (tile.getGroundObject() != null && tile.getGroundObject().getId() == id)
		{
			return tile.getGroundObject();
		}

		return null;
	}

	/**
	 * Object IDs the game uses when scheduling rock respawn overlay timers.
	 * Mirrors the core Mining plugin switch cases.
	 */
	private static boolean isDepletedRockObject(int locId)
	{
		switch (locId)
		{
			case ObjectID.MOTHERLODE_DEPLETED_SINGLE:
			case ObjectID.MOTHERLODE_DEPLETED_LEFT:
			case ObjectID.MOTHERLODE_DEPLETED_MIDDLE:
			case ObjectID.MOTHERLODE_DEPLETED_RIGHT:
			case ObjectID.DWARF_GOLD_DEPLETED:
			case ObjectID.VARLAMORE_MINING_ROCK_EMPTY:
			case ObjectID.VARLAMORE_MINING_ROCK_EMPTY02:
			case ObjectID.VARLAMORE_MINING_ROCK_EMPTY03:
			case ObjectID.VARLAMORE_MINING_ROCK_EMPTY04:
			case ObjectID.ROCKS1:
			case ObjectID.ROCKS2:
			case ObjectID.ROCKS3:
			case ObjectID.LEADROCK1_EMPTY:
			case ObjectID.NICKELROCK1_EMPTY:
			case ObjectID.MY2ARM_SALTROCK_EMPTY:
			case ObjectID.PRIF_MINE_ROCKS1_EMPTY:
			case ObjectID.FOSSIL_ASHPILE_EMPTY:
			case ObjectID.RUBIUMROCK1_EMPTY:
			case ObjectID.AMETHYSTROCK_EMPTY:
			case ObjectID.CAMDOZAALROCK1_EMPTY:
			case ObjectID.CAMDOZAALROCK2_EMPTY:
				return true;
			default:
				return false;
		}
	}

	private void rebuildPanel()
	{
		SwingUtilities.invokeLater(() -> panel.rebuild(getTrackedRocksSnapshot()));
	}

	private void queueConsole(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.value(message)
			.build());
	}

	private static BufferedImage createNavIcon()
	{
		// Simple pickaxe-ish glyph for the toolbar
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setColor(new java.awt.Color(200, 160, 60));
		g.fillRect(7, 2, 2, 10);
		g.setColor(new java.awt.Color(140, 140, 140));
		g.fillRect(3, 2, 10, 4);
		g.setColor(new java.awt.Color(100, 100, 100));
		g.fillOval(5, 10, 6, 5);
		g.dispose();
		return img;
	}
}
