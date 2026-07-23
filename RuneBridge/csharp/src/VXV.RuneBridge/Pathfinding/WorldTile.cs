using System.Text.Json.Serialization;

namespace VXV.RuneBridge.Pathfinding;

/// <summary>
/// An OSRS world tile (map coordinates + plane).
/// Optional canvas fields are filled when projected by RuneLite.
/// </summary>
public sealed class WorldTile : IEquatable<WorldTile>
{
    public WorldTile()
    {
    }

    public WorldTile(int x, int y, int plane = 0)
    {
        X = x;
        Y = y;
        Plane = plane;
    }

    [JsonPropertyName("x")] public int X { get; set; }
    [JsonPropertyName("y")] public int Y { get; set; }
    [JsonPropertyName("plane")] public int Plane { get; set; }
    [JsonPropertyName("regionId")] public int? RegionId { get; set; }

    /// <summary>Game-canvas-relative pixel X from last projection (if any).</summary>
    [JsonPropertyName("canvasX")] public int? CanvasX { get; set; }

    /// <summary>Game-canvas-relative pixel Y from last projection (if any).</summary>
    [JsonPropertyName("canvasY")] public int? CanvasY { get; set; }

    /// <summary>Absolute desktop pixel X (canvas origin + canvasX).</summary>
    [JsonPropertyName("screenX")] public int? ScreenX { get; set; }

    /// <summary>Absolute desktop pixel Y (canvas origin + canvasY).</summary>
    [JsonPropertyName("screenY")] public int? ScreenY { get; set; }

    [JsonPropertyName("onScreen")] public bool? OnScreen { get; set; }

    public static WorldTile From(int x, int y, int plane = 0) => new(x, y, plane);

    public WorldTile Clone() => new()
    {
        X = X,
        Y = Y,
        Plane = Plane,
        RegionId = RegionId,
        CanvasX = CanvasX,
        CanvasY = CanvasY,
        ScreenX = ScreenX,
        ScreenY = ScreenY,
        OnScreen = OnScreen,
    };

    /// <summary>
    /// Convert using already-populated screen/canvas fields (no network).
    /// Returns null if not projected / off-screen.
    /// </summary>
    public ScreenPoint? ToScreen()
    {
        if (OnScreen == false)
        {
            return null;
        }

        if (ScreenX is int sx && ScreenY is int sy)
        {
            return new ScreenPoint(sx, sy, CanvasX, CanvasY, true);
        }

        // Canvas-only: caller may offset by window themselves
        if (CanvasX is int cx && CanvasY is int cy)
        {
            return new ScreenPoint(cx, cy, cx, cy, true);
        }

        return null;
    }

    /// <summary>
    /// Ask the connected RuneLite plugin to project this tile to screen/mouse coordinates.
    /// Populates <see cref="CanvasX"/> / <see cref="ScreenX"/> on success.
    /// </summary>
    public async Task<ScreenPoint?> ToScreenAsync(
        RuneBridgeServer server,
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(server);
        var results = await server.ProjectTilesAsync(new[] { this }, timeout, cancellationToken)
            .ConfigureAwait(false);
        if (results.Count == 0)
        {
            return null;
        }

        var projected = results[0];
        CopyProjectionFrom(projected);
        return ToScreen();
    }

    /// <summary>Chebyshev distance on the same plane (infinite if planes differ).</summary>
    public int ChebyshevTo(WorldTile other)
    {
        if (Plane != other.Plane)
        {
            return int.MaxValue;
        }
        return Math.Max(Math.Abs(X - other.X), Math.Abs(Y - other.Y));
    }

    public int ManhattanTo(WorldTile other)
    {
        if (Plane != other.Plane)
        {
            return int.MaxValue;
        }
        return Math.Abs(X - other.X) + Math.Abs(Y - other.Y);
    }

    internal void CopyProjectionFrom(WorldTile other)
    {
        CanvasX = other.CanvasX;
        CanvasY = other.CanvasY;
        ScreenX = other.ScreenX;
        ScreenY = other.ScreenY;
        OnScreen = other.OnScreen;
        if (other.RegionId is not null)
        {
            RegionId = other.RegionId;
        }
    }

    public override string ToString() => $"WorldTile({X},{Y},{Plane})";

    public bool Equals(WorldTile? other) =>
        other is not null && X == other.X && Y == other.Y && Plane == other.Plane;

    public override bool Equals(object? obj) => obj is WorldTile t && Equals(t);

    public override int GetHashCode() => HashCode.Combine(X, Y, Plane);
}
