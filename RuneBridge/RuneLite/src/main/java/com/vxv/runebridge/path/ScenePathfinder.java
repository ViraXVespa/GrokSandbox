package com.vxv.runebridge.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * A* over the currently loaded scene collision map (walking distance, not tile crow-flies).
 */
public final class ScenePathfinder
{
	private static final int[] DX = {0, 0, -1, 1, -1, -1, 1, 1};
	private static final int[] DY = {-1, 1, 0, 0, -1, 1, -1, 1};
	private static final int[] COST = {10, 10, 10, 10, 14, 14, 14, 14};

	private ScenePathfinder()
	{
	}

	/**
	 * @return world-tile path inclusive of start and goal, or empty if unreachable in loaded scene
	 */
	public static List<WorldPoint> findPath(Client client, WorldPoint start, WorldPoint goal)
	{
		if (client == null || start == null || goal == null)
		{
			return Collections.emptyList();
		}
		if (start.getPlane() != goal.getPlane())
		{
			return Collections.emptyList();
		}
		if (start.equals(goal))
		{
			return List.of(start);
		}

		LocalPoint startLocal = LocalPoint.fromWorld(client, start);
		LocalPoint goalLocal = LocalPoint.fromWorld(client, goal);
		if (startLocal == null || goalLocal == null)
		{
			return Collections.emptyList();
		}

		CollisionData[] collisionMaps = client.getCollisionMaps();
		if (collisionMaps == null || start.getPlane() < 0 || start.getPlane() >= collisionMaps.length)
		{
			return Collections.emptyList();
		}
		CollisionData collision = collisionMaps[start.getPlane()];
		if (collision == null)
		{
			return Collections.emptyList();
		}
		int[][] flags = collision.getFlags();
		if (flags == null)
		{
			return Collections.emptyList();
		}

		int sx = startLocal.getSceneX();
		int sy = startLocal.getSceneY();
		int gx = goalLocal.getSceneX();
		int gy = goalLocal.getSceneY();
		if (!inBounds(sx, sy) || !inBounds(gx, gy))
		{
			return Collections.emptyList();
		}

		// Goal may be an occupied interactable tile — path to adjacent free tile if blocked
		if (isBlocked(flags, gx, gy))
		{
			int[] adj = nearestWalkableAdjacent(flags, gx, gy, sx, sy);
			if (adj == null)
			{
				return Collections.emptyList();
			}
			gx = adj[0];
			gy = adj[1];
		}

		Node startNode = new Node(sx, sy, 0, heuristic(sx, sy, gx, gy), null);
		PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.f));
		Map<Long, Integer> bestG = new HashMap<>();
		open.add(startNode);
		bestG.put(key(sx, sy), 0);

		Node end = null;
		int iterations = 0;
		final int maxIter = Constants.SCENE_SIZE * Constants.SCENE_SIZE * 2;

		while (!open.isEmpty() && iterations++ < maxIter)
		{
			Node cur = open.poll();
			if (cur.x == gx && cur.y == gy)
			{
				end = cur;
				break;
			}
			Integer known = bestG.get(key(cur.x, cur.y));
			if (known != null && cur.g > known)
			{
				continue;
			}

			for (int dir = 0; dir < 8; dir++)
			{
				int nx = cur.x + DX[dir];
				int ny = cur.y + DY[dir];
				if (!inBounds(nx, ny) || isBlocked(flags, nx, ny))
				{
					continue;
				}
				// Prevent corner-cutting through blocked diagonals
				if (dir >= 4)
				{
					if (isBlocked(flags, cur.x + DX[dir], cur.y) || isBlocked(flags, cur.x, cur.y + DY[dir]))
					{
						continue;
					}
				}
				int ng = cur.g + COST[dir];
				long k = key(nx, ny);
				Integer prev = bestG.get(k);
				if (prev != null && ng >= prev)
				{
					continue;
				}
				bestG.put(k, ng);
				open.add(new Node(nx, ny, ng, ng + heuristic(nx, ny, gx, gy), cur));
			}
		}

		if (end == null)
		{
			return Collections.emptyList();
		}

		List<WorldPoint> scenePath = new ArrayList<>();
		int baseX = client.getBaseX();
		int baseY = client.getBaseY();
		for (Node n = end; n != null; n = n.parent)
		{
			scenePath.add(new WorldPoint(baseX + n.x, baseY + n.y, start.getPlane()));
		}
		Collections.reverse(scenePath);

		// Append true goal if we stopped adjacent (for interact)
		if (!scenePath.isEmpty())
		{
			WorldPoint last = scenePath.get(scenePath.size() - 1);
			if (!last.equals(goal) && goal.getPlane() == last.getPlane()
				&& last.distanceTo(goal) <= 1)
			{
				scenePath.add(goal);
			}
		}
		return compress(scenePath);
	}

	/** Drop collinear intermediate tiles for a shorter click list. */
	static List<WorldPoint> compress(List<WorldPoint> path)
	{
		if (path.size() <= 2)
		{
			return path;
		}
		List<WorldPoint> out = new ArrayList<>();
		out.add(path.get(0));
		for (int i = 1; i < path.size() - 1; i++)
		{
			WorldPoint a = out.get(out.size() - 1);
			WorldPoint b = path.get(i);
			WorldPoint c = path.get(i + 1);
			int abx = Integer.signum(b.getX() - a.getX());
			int aby = Integer.signum(b.getY() - a.getY());
			int bcx = Integer.signum(c.getX() - b.getX());
			int bcy = Integer.signum(c.getY() - b.getY());
			if (abx != bcx || aby != bcy)
			{
				out.add(b);
			}
		}
		out.add(path.get(path.size() - 1));
		return out;
	}

	private static int[] nearestWalkableAdjacent(int[][] flags, int gx, int gy, int fromX, int fromY)
	{
		int best = Integer.MAX_VALUE;
		int[] bestTile = null;
		for (int dir = 0; dir < 8; dir++)
		{
			int nx = gx + DX[dir];
			int ny = gy + DY[dir];
			if (!inBounds(nx, ny) || isBlocked(flags, nx, ny))
			{
				continue;
			}
			int d = Math.max(Math.abs(nx - fromX), Math.abs(ny - fromY));
			if (d < best)
			{
				best = d;
				bestTile = new int[]{nx, ny};
			}
		}
		return bestTile;
	}

	private static boolean inBounds(int x, int y)
	{
		return x >= 0 && y >= 0 && x < Constants.SCENE_SIZE && y < Constants.SCENE_SIZE;
	}

	private static boolean isBlocked(int[][] flags, int x, int y)
	{
		if (!inBounds(x, y))
		{
			return true;
		}
		int f = flags[x][y];
		// Fully blocked movement
		return (f & (CollisionDataFlag.BLOCK_MOVEMENT_FULL
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
			| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION
			| CollisionDataFlag.BLOCK_MOVEMENT_OBJECT)) != 0;
	}

	private static int heuristic(int x, int y, int gx, int gy)
	{
		int dx = Math.abs(x - gx);
		int dy = Math.abs(y - gy);
		// octile
		return 10 * (dx + dy) + (14 - 2 * 10) * Math.min(dx, dy);
	}

	private static long key(int x, int y)
	{
		return (((long) x) << 32) ^ (y & 0xffffffffL);
	}

	private static final class Node
	{
		final int x, y, g, f;
		final Node parent;

		Node(int x, int y, int g, int f, Node parent)
		{
			this.x = x;
			this.y = y;
			this.g = g;
			this.f = f;
			this.parent = parent;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Node))
			{
				return false;
			}
			Node n = (Node) o;
			return x == n.x && y == n.y;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(x, y);
		}
	}
}
