package com.vxv.advancedminingtracker.ui;

import com.vxv.advancedminingtracker.model.TrackedRock;
import com.vxv.advancedminingtracker.util.WorldFlagUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.http.api.worlds.WorldRegion;

/**
 * Parent container for one world: flag + world id on the left, rock timers on the right.
 * Clickable only when at least one rock on this world has regenerated.
 */
public class WorldGroupPanel extends JPanel
{
	private final int worldId;
	private final WorldRegion region;
	private final IntConsumer hopCallback;
	private final ItemManager itemManager;

	private final JPanel rocksRow;
	private final JLabel worldLabel;
	private final JLabel statusLabel;
	private final List<RockTimerChip> chips = new ArrayList<>();

	private boolean hopEnabled;

	public WorldGroupPanel(int worldId, WorldRegion region, ItemManager itemManager, IntConsumer hopCallback)
	{
		this.worldId = worldId;
		this.region = region;
		this.itemManager = itemManager;
		this.hopCallback = hopCallback;

		setLayout(new BorderLayout(8, 0));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)
		));
		setAlignmentX(LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
		left.setOpaque(false);
		left.setPreferredSize(new Dimension(56, 48));

		JLabel flagLabel = new JLabel(WorldFlagUtil.getFlag(region));
		flagLabel.setAlignmentX(CENTER_ALIGNMENT);

		worldLabel = new JLabel(String.valueOf(worldId));
		worldLabel.setFont(FontManager.getRunescapeBoldFont());
		worldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		worldLabel.setAlignmentX(CENTER_ALIGNMENT);

		statusLabel = new JLabel("Waiting");
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setAlignmentX(CENTER_ALIGNMENT);

		left.add(flagLabel);
		left.add(Box.createVerticalStrut(2));
		left.add(worldLabel);
		left.add(statusLabel);

		rocksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		rocksRow.setOpaque(false);

		add(left, BorderLayout.WEST);
		add(rocksRow, BorderLayout.CENTER);

		MouseAdapter click = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && hopEnabled && hopCallback != null)
				{
					hopCallback.accept(worldId);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (hopEnabled)
				{
					setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
					setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(hopEnabled
					? new Color(45, 70, 45)
					: ColorScheme.DARK_GRAY_COLOR);
				setCursor(Cursor.getDefaultCursor());
			}
		};
		addMouseListener(click);
		left.addMouseListener(click);
		rocksRow.addMouseListener(click);
	}

	public int getWorldId()
	{
		return worldId;
	}

	public void setRocks(List<TrackedRock> rocks)
	{
		rocksRow.removeAll();
		chips.clear();

		for (TrackedRock rock : rocks)
		{
			RockTimerChip chip = new RockTimerChip(rock, itemManager);
			chips.add(chip);
			rocksRow.add(chip);
		}

		refreshState();
		revalidate();
		repaint();
	}

	public void refreshTimers()
	{
		for (RockTimerChip chip : chips)
		{
			chip.refresh();
		}
		refreshState();
	}

	private void refreshState()
	{
		boolean anyReady = false;
		boolean allReady = !chips.isEmpty();
		for (RockTimerChip chip : chips)
		{
			if (chip.getRock().isReady())
			{
				anyReady = true;
			}
			else
			{
				allReady = false;
			}
		}

		hopEnabled = anyReady;
		if (allReady && anyReady)
		{
			statusLabel.setText("Ready");
			statusLabel.setForeground(new Color(66, 227, 17));
			worldLabel.setForeground(new Color(66, 227, 17));
			setBackground(new Color(45, 70, 45));
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(66, 227, 17)),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)
			));
			setToolTipText("Click to hop to world " + worldId);
		}
		else if (anyReady)
		{
			statusLabel.setText("Partial");
			statusLabel.setForeground(new Color(255, 200, 40));
			worldLabel.setForeground(new Color(255, 200, 40));
			setBackground(new Color(50, 55, 35));
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(180, 150, 40)),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)
			));
			setToolTipText("Click to hop to world " + worldId + " (some rocks ready)");
		}
		else
		{
			statusLabel.setText("Waiting");
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			worldLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)
			));
			setToolTipText("World " + worldId + " — rocks still regenerating");
			setCursor(Cursor.getDefaultCursor());
		}
	}
}
