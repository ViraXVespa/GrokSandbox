namespace VXV.RuneBridge.Pathfinding;

/// <summary>
/// Pixel coordinates for UI / mouse / GDI use.
/// </summary>
public readonly struct ScreenPoint : IEquatable<ScreenPoint>
{
    public ScreenPoint(int x, int y, int? canvasX = null, int? canvasY = null, bool onScreen = true)
    {
        X = x;
        Y = y;
        CanvasX = canvasX;
        CanvasY = canvasY;
        OnScreen = onScreen;
    }

    /// <summary>Absolute screen coordinates (desktop pixels).</summary>
    public int X { get; }

    /// <summary>Absolute screen coordinates (desktop pixels).</summary>
    public int Y { get; }

    /// <summary>Coordinates relative to the RuneLite game canvas (if known).</summary>
    public int? CanvasX { get; }

    /// <summary>Coordinates relative to the RuneLite game canvas (if known).</summary>
    public int? CanvasY { get; }

    public bool OnScreen { get; }

    public bool IsEmpty => X == 0 && Y == 0 && CanvasX is null && !OnScreen;

    public System.Drawing.Point ToDrawingPoint() => new(X, Y);

    public override string ToString() =>
        OnScreen
            ? $"Screen({X},{Y})" + (CanvasX is int cx && CanvasY is int cy ? $" canvas=({cx},{cy})" : "")
            : "Screen(off-screen)";

    public bool Equals(ScreenPoint other) =>
        X == other.X && Y == other.Y && CanvasX == other.CanvasX && CanvasY == other.CanvasY && OnScreen == other.OnScreen;

    public override bool Equals(object? obj) => obj is ScreenPoint sp && Equals(sp);

    public override int GetHashCode() => HashCode.Combine(X, Y, CanvasX, CanvasY, OnScreen);

    public static bool operator ==(ScreenPoint a, ScreenPoint b) => a.Equals(b);
    public static bool operator !=(ScreenPoint a, ScreenPoint b) => !a.Equals(b);
}
