using System.Text.Json.Serialization;

namespace VXV.RuneBridge.Pathfinding;

public enum RouteStepKind
{
    Walk,
    Interact
}

public sealed class DestinationPoi
{
    [JsonPropertyName("type")] public string? Type { get; set; }
    [JsonPropertyName("name")] public string? Name { get; set; }
    [JsonPropertyName("x")] public int X { get; set; }
    [JsonPropertyName("y")] public int Y { get; set; }
    [JsonPropertyName("plane")] public int Plane { get; set; }
    [JsonPropertyName("objectId")] public int ObjectId { get; set; }
    [JsonPropertyName("action")] public string? Action { get; set; }
    [JsonPropertyName("canvasX")] public int? CanvasX { get; set; }
    [JsonPropertyName("canvasY")] public int? CanvasY { get; set; }
    [JsonPropertyName("screenX")] public int? ScreenX { get; set; }
    [JsonPropertyName("screenY")] public int? ScreenY { get; set; }
    [JsonPropertyName("onScreen")] public bool? OnScreen { get; set; }
    [JsonPropertyName("chebyshev")] public int? Chebyshev { get; set; }

    public WorldTile ToWorldTile() => new()
    {
        X = X,
        Y = Y,
        Plane = Plane,
        CanvasX = CanvasX,
        CanvasY = CanvasY,
        ScreenX = ScreenX,
        ScreenY = ScreenY,
        OnScreen = OnScreen
    };
}

/// <summary>
/// One step in a route — typically Walk waypoints culminating in Interact at the destination.
/// </summary>
public sealed class RouteStep
{
    [JsonPropertyName("x")] public int X { get; set; }
    [JsonPropertyName("y")] public int Y { get; set; }
    [JsonPropertyName("plane")] public int Plane { get; set; }
    [JsonPropertyName("kind")] public string? KindRaw { get; set; }
    [JsonPropertyName("action")] public string? Action { get; set; }
    [JsonPropertyName("objectId")] public int? ObjectId { get; set; }
    [JsonPropertyName("label")] public string? Label { get; set; }
    [JsonPropertyName("canvasX")] public int? CanvasX { get; set; }
    [JsonPropertyName("canvasY")] public int? CanvasY { get; set; }
    [JsonPropertyName("screenX")] public int? ScreenX { get; set; }
    [JsonPropertyName("screenY")] public int? ScreenY { get; set; }

    /// <summary>Parsed step kind. Ignored by JSON (wire field is <see cref="KindRaw"/>).</summary>
    [JsonIgnore]
    public RouteStepKind Kind =>
        string.Equals(KindRaw, "Interact", StringComparison.OrdinalIgnoreCase)
            ? RouteStepKind.Interact
            : RouteStepKind.Walk;

    public WorldTile ToWorldTile() => new()
    {
        X = X,
        Y = Y,
        Plane = Plane,
        CanvasX = CanvasX,
        CanvasY = CanvasY,
        ScreenX = ScreenX,
        ScreenY = ScreenY,
        OnScreen = ScreenX.HasValue || CanvasX.HasValue
    };

    [JsonIgnore]
    public WorldTile Tile => ToWorldTile();
}

public sealed class RouteResult
{
    [JsonPropertyName("ok")] public bool Ok { get; set; }
    [JsonPropertyName("error")] public string? Error { get; set; }
    [JsonPropertyName("target")] public string? Target { get; set; }
    [JsonPropertyName("method")] public string? Method { get; set; }
    [JsonPropertyName("note")] public string? Note { get; set; }
    [JsonPropertyName("walkTiles")] public int? WalkTiles { get; set; }
    [JsonPropertyName("stepCount")] public int? StepCount { get; set; }
    [JsonPropertyName("start")] public WorldTile? Start { get; set; }
    [JsonPropertyName("destination")] public DestinationPoi? Destination { get; set; }
    /// <summary>Min-click sequence (sparse Walk waypoints + final Interact).</summary>
    [JsonPropertyName("steps")] public List<RouteStep> Steps { get; set; } = new();
    /// <summary>Full A*/estimate path (every tile) for debug drawing when off-screen.</summary>
    [JsonPropertyName("routeTiles")] public List<WorldTile> RouteTiles { get; set; } = new();
    [JsonPropertyName("routeTileCount")] public int? RouteTileCount { get; set; }
    [JsonPropertyName("knownTargets")] public List<string>? KnownTargets { get; set; }

    [JsonPropertyName("onScreen")] public bool OnScreen { get; set; }
    [JsonPropertyName("instances")] public List<DestinationPoi> Instances { get; set; } = new();
    [JsonPropertyName("instanceCount")] public int InstanceCount { get; set; }

    public IEnumerable<RouteStep> WalkSteps => Steps.Where(s => s.Kind == RouteStepKind.Walk);

    public RouteStep? InteractStep => Steps.LastOrDefault(s => s.Kind == RouteStepKind.Interact);

    public IEnumerable<WorldTile> InstanceTiles => Instances.Select(i => i.ToWorldTile());
}
