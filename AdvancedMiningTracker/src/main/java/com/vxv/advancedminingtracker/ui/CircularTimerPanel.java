package com.vxv.advancedminingtracker.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.JPanel;

/**
 * Circular progress ring counting up toward rock regeneration.
 */
public class CircularTimerPanel extends JPanel
{
	private static final int SIZE = 36;
	private static final float STROKE = 3.5f;

	private float progress;
	private boolean ready;
	private String centerText = "";

	public CircularTimerPanel()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(SIZE, SIZE));
		setMinimumSize(new Dimension(SIZE, SIZE));
		setMaximumSize(new Dimension(SIZE, SIZE));
	}

	public void setProgress(float progress, boolean ready, String centerText)
	{
		this.progress = Math.max(0f, Math.min(1f, progress));
		this.ready = ready;
		this.centerText = centerText != null ? centerText : "";
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int pad = 3;
		int d = Math.min(getWidth(), getHeight()) - pad * 2;
		int x = (getWidth() - d) / 2;
		int y = (getHeight() - d) / 2;

		// Track
		g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(new Color(60, 60, 60));
		g2.drawOval(x, y, d, d);

		Color arcColor = ready
			? new Color(66, 227, 17)
			: interpolate(new Color(220, 80, 60), new Color(255, 200, 40), progress);

		g2.setColor(arcColor);
		double extent = -360.0 * progress;
		g2.draw(new Arc2D.Double(x, y, d, d, 90, extent, Arc2D.OPEN));

		if (centerText != null && !centerText.isEmpty())
		{
			g2.setColor(ready ? new Color(66, 227, 17) : Color.WHITE);
			g2.setFont(g2.getFont().deriveFont(9f));
			int tw = g2.getFontMetrics().stringWidth(centerText);
			int th = g2.getFontMetrics().getAscent();
			g2.drawString(centerText, (getWidth() - tw) / 2, (getHeight() + th) / 2 - 2);
		}

		g2.dispose();
	}

	private static Color interpolate(Color a, Color b, float t)
	{
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
		int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
		return new Color(r, g, bl);
	}
}
