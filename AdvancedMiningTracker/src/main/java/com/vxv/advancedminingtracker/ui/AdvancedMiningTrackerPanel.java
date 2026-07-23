package com.vxv.advancedminingtracker.ui;

import com.vxv.advancedminingtracker.AdvancedMiningTrackerPlugin;
import com.vxv.advancedminingtracker.model.TrackedRock;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.WorldService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;

/**
 * Side panel: vertically stacked world groups of rock regeneration timers.
 */
public class AdvancedMiningTrackerPanel extends PluginPanel
{
	private final AdvancedMiningTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final WorldService worldService;

	private final JPanel listContainer;
	private final JLabel emptyLabel;
	private final Map<Integer, WorldGroupPanel> worldPanels = new LinkedHashMap<>();

	@Inject
	public AdvancedMiningTrackerPanel(AdvancedMiningTrackerPlugin plugin, ItemManager itemManager,
		WorldService worldService)
	{
		super(false);
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.worldService = worldService;

		setLayout(new BorderLayout(0, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Mining Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		title.setHorizontalAlignment(JLabel.CENTER);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(title, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);

		JButton clearReady = new JButton("Clear ready");
		clearReady.setFocusPainted(false);
		clearReady.addActionListener(e -> plugin.clearReadyRocks());

		JButton clearAll = new JButton("Clear all");
		clearAll.setFocusPainted(false);
		clearAll.addActionListener(e -> plugin.clearAllRocks());

		buttons.add(clearReady);
		buttons.add(clearAll);
		header.add(buttons, BorderLayout.SOUTH);
		header.setBorder(new EmptyBorder(0, 0, 8, 0));

		listContainer = new JPanel();
		listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
		listContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		emptyLabel = new JLabel("<html><center>Mine a rock to start tracking.<br>"
			+ "Timers stay across world hops.</center></html>");
		emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		emptyLabel.setFont(FontManager.getRunescapeSmallFont());
		emptyLabel.setHorizontalAlignment(JLabel.CENTER);
		emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));

		JScrollPane scroll = new JScrollPane(listContainer);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(header, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);

		rebuild(plugin.getTrackedRocksSnapshot());
	}

	public void rebuild(List<TrackedRock> rocks)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> rebuild(rocks));
			return;
		}

		Map<Integer, List<TrackedRock>> byWorld = rocks.stream()
			.sorted(Comparator.comparingInt(TrackedRock::getWorldId)
				.thenComparing(TrackedRock::getStartTime))
			.collect(Collectors.groupingBy(TrackedRock::getWorldId, LinkedHashMap::new, Collectors.toList()));

		// Sort worlds: any-ready first, then by world id
		List<Integer> worldOrder = new ArrayList<>(byWorld.keySet());
		worldOrder.sort((a, b) ->
		{
			boolean aReady = byWorld.get(a).stream().anyMatch(TrackedRock::isReady);
			boolean bReady = byWorld.get(b).stream().anyMatch(TrackedRock::isReady);
			if (aReady != bReady)
			{
				return aReady ? -1 : 1;
			}
			return Integer.compare(a, b);
		});

		listContainer.removeAll();
		worldPanels.clear();

		if (worldOrder.isEmpty())
		{
			listContainer.add(emptyLabel);
		}
		else
		{
			for (Integer worldId : worldOrder)
			{
				WorldRegion region = resolveRegion(worldId);
				WorldGroupPanel group = new WorldGroupPanel(worldId, region, itemManager, plugin::hopToWorld);
				group.setRocks(byWorld.get(worldId));
				worldPanels.put(worldId, group);
				listContainer.add(group);
				listContainer.add(Box.createVerticalStrut(6));
			}
		}

		listContainer.add(Box.createVerticalGlue());
		listContainer.revalidate();
		listContainer.repaint();
	}

	public void refreshTimers()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::refreshTimers);
			return;
		}

		// WorldGroupPanel.refreshTimers() already flips hop affordances as rocks ready
		for (WorldGroupPanel group : worldPanels.values())
		{
			group.refreshTimers();
		}
	}

	private WorldRegion resolveRegion(int worldId)
	{
		WorldResult result = worldService.getWorlds();
		if (result == null)
		{
			return null;
		}
		World world = result.findWorld(worldId);
		return world != null ? world.getRegion() : null;
	}
}
