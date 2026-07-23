package com.vxv.advancedminingtracker.model;

import com.vxv.advancedminingtracker.util.OreType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * A rock the local player depleted on a specific world.
 */
@Getter
public class TrackedRock
{
	private final String id;
	private final int worldId;
	private final WorldPoint worldPoint;
	private final OreType oreType;
	private final Instant startTime;
	private final int respawnMillis;
	private final String objectName;

	public TrackedRock(int worldId, WorldPoint worldPoint, OreType oreType, Instant startTime,
		int respawnMillis, String objectName)
	{
		this.id = UUID.randomUUID().toString();
		this.worldId = worldId;
		this.worldPoint = worldPoint;
		this.oreType = oreType != null ? oreType : OreType.UNKNOWN;
		this.startTime = startTime;
		this.respawnMillis = Math.max(respawnMillis, 1);
		this.objectName = objectName != null ? objectName : oreType.getDisplayName();
	}

	/**
	 * Progress from 0.0 (just depleted) toward 1.0 (fully regenerated).
	 */
	public float getProgress()
	{
		long elapsed = Duration.between(startTime, Instant.now()).toMillis();
		return Math.min(1.0f, elapsed / (float) respawnMillis);
	}

	public boolean isReady()
	{
		return Instant.now().isAfter(startTime.plusMillis(respawnMillis));
	}

	public long getRemainingMillis()
	{
		long remaining = respawnMillis - Duration.between(startTime, Instant.now()).toMillis();
		return Math.max(0L, remaining);
	}

	public String getRemainingLabel()
	{
		long remaining = getRemainingMillis();
		if (remaining <= 0)
		{
			return "Ready";
		}

		long totalSeconds = remaining / 1000L;
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		if (minutes > 0)
		{
			return String.format("%d:%02d", minutes, seconds);
		}
		return totalSeconds + "s";
	}
}
