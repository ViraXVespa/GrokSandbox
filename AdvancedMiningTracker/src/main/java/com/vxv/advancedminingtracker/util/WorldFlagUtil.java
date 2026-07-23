package com.vxv.advancedminingtracker.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.ImageIcon;
import net.runelite.http.api.worlds.WorldRegion;

/**
 * Lightweight region flag icons drawn at runtime (no bundled assets).
 */
public final class WorldFlagUtil
{
	private static final int WIDTH = 16;
	private static final int HEIGHT = 11;
	private static final Map<WorldRegion, ImageIcon> CACHE = new EnumMap<>(WorldRegion.class);
	private static final ImageIcon UNKNOWN_FLAG = createSolid(Color.DARK_GRAY, Color.LIGHT_GRAY, "??");

	private WorldFlagUtil()
	{
	}

	public static ImageIcon getFlag(WorldRegion region)
	{
		if (region == null)
		{
			return UNKNOWN_FLAG;
		}

		return CACHE.computeIfAbsent(region, WorldFlagUtil::createFlag);
	}

	public static String getRegionLabel(WorldRegion region)
	{
		if (region == null)
		{
			return "??";
		}

		switch (region)
		{
			case UNITED_STATES_OF_AMERICA:
				return "US";
			case UNITED_KINGDOM:
				return "UK";
			case AUSTRALIA:
				return "AU";
			case GERMANY:
				return "DE";
			case BRAZIL:
				return "BR";
			case JAPAN:
				return "JP";
			case SINGAPORE:
				return "SG";
			case SOUTH_AFRICA:
				return "ZA";
			default:
				return "??";
		}
	}

	private static ImageIcon createFlag(WorldRegion region)
	{
		switch (region)
		{
			case UNITED_STATES_OF_AMERICA:
				return createUsFlag();
			case UNITED_KINGDOM:
				return createUkFlag();
			case AUSTRALIA:
				return createSolid(new Color(0, 33, 71), Color.WHITE, "AU");
			case GERMANY:
				return createTriband(Color.BLACK, new Color(221, 0, 0), new Color(255, 206, 0));
			case BRAZIL:
				return createSolid(new Color(0, 155, 58), new Color(254, 223, 0), "BR");
			case JAPAN:
				return createJapanFlag();
			case SINGAPORE:
				return createSolid(new Color(237, 41, 57), Color.WHITE, "SG");
			case SOUTH_AFRICA:
				return createSolid(new Color(0, 119, 73), new Color(255, 182, 18), "ZA");
			default:
				return createSolid(Color.DARK_GRAY, Color.LIGHT_GRAY, getRegionLabel(region));
		}
	}

	private static ImageIcon createUsFlag()
	{
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// Stripes
		for (int i = 0; i < 5; i++)
		{
			g.setColor(i % 2 == 0 ? new Color(178, 34, 52) : Color.WHITE);
			g.fillRect(0, i * 2, WIDTH, 2);
		}
		// Canton
		g.setColor(new Color(60, 59, 110));
		g.fillRect(0, 0, 7, 6);
		g.dispose();
		return new ImageIcon(img);
	}

	private static ImageIcon createUkFlag()
	{
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(1, 33, 105));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setColor(Color.WHITE);
		g.fillRect(0, HEIGHT / 2 - 1, WIDTH, 3);
		g.fillRect(WIDTH / 2 - 1, 0, 3, HEIGHT);
		g.setColor(new Color(200, 16, 46));
		g.fillRect(0, HEIGHT / 2, WIDTH, 1);
		g.fillRect(WIDTH / 2, 0, 1, HEIGHT);
		g.dispose();
		return new ImageIcon(img);
	}

	private static ImageIcon createJapanFlag()
	{
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setColor(new Color(188, 0, 45));
		g.fillOval(WIDTH / 2 - 3, HEIGHT / 2 - 3, 6, 6);
		g.dispose();
		return new ImageIcon(img);
	}

	private static ImageIcon createTriband(Color top, Color mid, Color bottom)
	{
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		int h = HEIGHT / 3;
		g.setColor(top);
		g.fillRect(0, 0, WIDTH, h);
		g.setColor(mid);
		g.fillRect(0, h, WIDTH, h);
		g.setColor(bottom);
		g.fillRect(0, h * 2, WIDTH, HEIGHT - h * 2);
		g.dispose();
		return new ImageIcon(img);
	}

	private static ImageIcon createSolid(Color bg, Color fg, String text)
	{
		BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(bg);
		g.fillRect(0, 0, WIDTH, HEIGHT);
		g.setColor(fg);
		g.setFont(g.getFont().deriveFont(7f));
		g.drawString(text, 1, HEIGHT - 2);
		g.dispose();
		return new ImageIcon(img);
	}
}
