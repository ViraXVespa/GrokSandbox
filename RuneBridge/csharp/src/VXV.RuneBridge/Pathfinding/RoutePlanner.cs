using VXV.RuneBridge.Debug;

namespace VXV.RuneBridge.Pathfinding;

/// <summary>
/// High-level pathfinding API. Sends FindNearest requests to the connected RuneLite plugin.
/// </summary>
public sealed class RoutePlanner
{
    private readonly RuneBridgeServer _server;

    public RoutePlanner(RuneBridgeServer server)
    {
        _server = server ?? throw new ArgumentNullException(nameof(server));
    }

    /// <summary>
    /// Find nearest interactable. When <paramref name="debug"/> is true, draws GDI arrows
    /// over target tiles for ~3 seconds.
    /// </summary>
    public Task<RouteResult> FindNearestAsync(
        RuneTarget target,
        bool debug = false,
        CancellationToken cancellationToken = default)
        => FindNearestCoreAsync(target.ToWireName(), maxCandidates: 12, debug, timeout: null, cancellationToken);

    /// <inheritdoc cref="FindNearestAsync(RuneTarget,bool,CancellationToken)"/>
    public Task<RouteResult> FindNearestAsync(
        string target,
        bool debug = false,
        CancellationToken cancellationToken = default)
        => FindNearestCoreAsync(target, maxCandidates: 12, debug, timeout: null, cancellationToken);

    /// <summary>Overload with maxCandidates.</summary>
    public Task<RouteResult> FindNearestAsync(
        RuneTarget target,
        int maxCandidates,
        bool debug = false,
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
        => FindNearestCoreAsync(target.ToWireName(), maxCandidates, debug, timeout, cancellationToken);

    /// <summary>Overload with maxCandidates (string target).</summary>
    public Task<RouteResult> FindNearestAsync(
        string target,
        int maxCandidates,
        bool debug,
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
        => FindNearestCoreAsync(target, maxCandidates, debug, timeout, cancellationToken);

    private async Task<RouteResult> FindNearestCoreAsync(
        string target,
        int maxCandidates,
        bool debug,
        TimeSpan? timeout,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(target))
        {
            return new RouteResult { Ok = false, Error = "Target is required" };
        }

        if (_server.ConnectedClients <= 0)
        {
            return new RouteResult
            {
                Ok = false,
                Error = "No RuneLite client connected. Start the RuneBridge plugin and ensure it is enabled."
            };
        }

        var payload = new Dictionary<string, object?>
        {
            ["target"] = target.Trim(),
            ["maxCandidates"] = maxCandidates
        };

        RouteResult result;
        try
        {
            var response = await _server.RequestAsync<RouteResult>(
                "FindNearest",
                payload,
                timeout ?? TimeSpan.FromSeconds(5),
                cancellationToken).ConfigureAwait(false);

            result = response ?? new RouteResult { Ok = false, Error = "Empty response from plugin" };
            result.Target ??= target;
        }
        catch (Exception ex)
        {
            return new RouteResult { Ok = false, Error = ex.Message, Target = target };
        }

        if (debug && result.Ok)
        {
            await ShowDebugMarkersAsync(result, cancellationToken).ConfigureAwait(false);
        }

        return result;
    }

    public async Task<IReadOnlyList<string>> ListTargetsAsync(
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
    {
        if (_server.ConnectedClients <= 0)
        {
            return Array.Empty<string>();
        }

        try
        {
            var result = await _server.RequestAsync<ListTargetsResponse>(
                "ListTargets",
                new Dictionary<string, object?>(),
                timeout ?? TimeSpan.FromSeconds(3),
                cancellationToken).ConfigureAwait(false);
            return result?.Targets ?? (IReadOnlyList<string>)Array.Empty<string>();
        }
        catch
        {
            return Array.Empty<string>();
        }
    }

    private async Task ShowDebugMarkersAsync(RouteResult result, CancellationToken cancellationToken)
    {
        var markers = new List<(WorldTile Tile, string? Label)>();

        if (result.OnScreen && result.Instances.Count > 0)
        {
            // On-screen: arrow each visible instance.
            foreach (var inst in result.Instances)
            {
                markers.Add((inst.ToWorldTile(), inst.Name ?? result.Target));
            }
        }
        else
        {
            // Off-screen: draw the full route tile list (every walk tile), not just dest.
            // Prefer routeTiles from the plugin; fall back to min-click steps then destination.
            if (result.RouteTiles.Count > 0)
            {
                for (var i = 0; i < result.RouteTiles.Count; i++)
                {
                    var tile = result.RouteTiles[i];
                    string? label = null;
                    if (i == 0)
                    {
                        label = "start";
                    }
                    else if (i == result.RouteTiles.Count - 1)
                    {
                        label = result.Destination?.Name ?? result.Target ?? "end";
                    }
                    else if (i % 5 == 0)
                    {
                        // Light labels so long paths stay readable
                        label = i.ToString();
                    }

                    markers.Add((tile, label));
                }
            }
            else if (result.Steps.Count > 0)
            {
                for (var i = 0; i < result.Steps.Count; i++)
                {
                    var step = result.Steps[i];
                    var label = step.Kind == RouteStepKind.Interact
                        ? (step.Label ?? step.Action ?? "Interact")
                        : $"Walk {i + 1}";
                    markers.Add((step.ToWorldTile(), label));
                }
            }
            else if (result.Destination is not null)
            {
                markers.Add((result.Destination.ToWorldTile(), result.Destination.Name ?? result.Target));
            }
        }

        if (markers.Count == 0)
        {
            return;
        }

        var tiles = markers.Select(m => m.Tile).ToList();
        var needProject = tiles.Any(t => t.ScreenX is null || t.ScreenY is null);
        if (needProject)
        {
            try
            {
                var projected = await _server.ProjectTilesAsync(tiles, TimeSpan.FromSeconds(3), cancellationToken)
                    .ConfigureAwait(false);
                for (var i = 0; i < Math.Min(tiles.Count, projected.Count); i++)
                {
                    tiles[i].CopyProjectionFrom(projected[i]);
                }
            }
            catch
            {
                // use whatever we have
            }
        }

        DebugTileOverlay.ShowArrowsForTiles(markers, TimeSpan.FromSeconds(3));
    }

    private sealed class ListTargetsResponse
    {
        public List<string>? Targets { get; set; }
    }
}
