package com.vxv.advancedminingtracker.ui;

import com.vxv.advancedminingtracker.model.TrackedRock;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Single rock UI: ore icon + circular regen timer.
 */
public class RockTimerChip extends JPanel
{
	private final TrackedRock rock;
	private final CircularTimerPanel timerPanel;
	private final JLabel nameLabel;
	private final JLabel statusLabel;

	public RockTimerChip(TrackedRock rock, ItemManager itemManager)
	{
		this.rock = rock;
		setLayout(new BorderLayout(4, 0));
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 4, 4, 4)
		));
		setPreferredSize(new Dimension(90, 58));
		setMaximumSize(new Dimension(100, 58));

		JPanel left = new JPanel(new BorderLayout());
		left.setOpaque(false);

		JLabel iconLabel = new JLabel();
		iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
		iconLabel.setPreferredSize(new Dimension(28, 28));
		if (itemManager != null)
		{
			AsyncBufferedImage img = itemManager.getImage(rock.getOreType().getItemId());
			if (img != null)
			{
				img.addTo(iconLabel);
			}
			else
			{
				iconLabel.setIcon(placeholderIcon());
			}
		}
		else
		{
			iconLabel.setIcon(placeholderIcon());
		}
		left.add(iconLabel, BorderLayout.NORTH);

		nameLabel = new JLabel(rock.getOreType().getDisplayName());
		nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
		nameLabel.setFont(nameLabel.getFont().deriveFont(9f));
		left.add(nameLabel, BorderLayout.SOUTH);

		timerPanel = new CircularTimerPanel();
		statusLabel = new JLabel(rock.getRemainingLabel());
		statusLabel.setForeground(Color.WHITE);
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setFont(statusLabel.getFont().deriveFont(9f));

		JPanel right = new JPanel(new BorderLayout());
		right.setOpaque(false);
		right.add(timerPanel, BorderLayout.CENTER);
		right.add(statusLabel, BorderLayout.SOUTH);

		add(left, BorderLayout.WEST);
		add(right, BorderLayout.CENTER);

		refresh();
	}

	public TrackedRock getRock()
	{
		return rock;
	}

	public void refresh()
	{
		boolean ready = rock.isReady();
		float progress = rock.getProgress();
		String label = rock.getRemainingLabel();
		timerPanel.setProgress(progress, ready, ready ? "✓" : "");
		statusLabel.setText(label);
		statusLabel.setForeground(ready ? new Color(66, 227, 17) : Color.WHITE);
		setBackground(ready
			? new Color(40, 70, 40)
			: ColorScheme.DARKER_GRAY_COLOR);
		setToolTipText(rock.getObjectName() + " @ " + rock.getWorldPoint()
			+ (ready ? " — Ready" : " — " + label));
	}

	private static ImageIcon placeholderIcon()
	{
		BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		return new ImageIcon(img);
	}
}
