using VXV.RuneBridge;
using VXV.RuneBridge.Events;
using VXV.RuneBridge.Handlers;
using VXV.RuneBridge.Pathfinding;

Console.Title = "VXV.RuneBridge Host";
Console.WriteLine("VXV.RuneBridge sample host");
Console.WriteLine("Listening on 127.0.0.1:17473 — enable RuneBridge in RuneLite.\n");
Console.WriteLine("Commands: nearest bank | nearest furnace | nearest ore_runite | targets | quit\n");

await using var server = new RuneBridgeServer(new RuneBridgeOptions
{
    Port = 17473,
    BindAddress = "127.0.0.1",
});

server.Events.ClientConnected += (_, _) =>
    Console.WriteLine($"[{DemoHandlers.Stamp()}] Client connected");
server.Events.ClientDisconnected += (_, _) =>
    Console.WriteLine($"[{DemoHandlers.Stamp()}] Client disconnected");
server.Events.ClientHello += (_, e) =>
    Console.WriteLine($"[{DemoHandlers.Stamp()}] Hello from {e.Plugin} (protocol {e.Protocol})");

server.Events.InventoryChanged += (_, e) =>
{
    Console.WriteLine($"[{DemoHandlers.Stamp()}] InventoryChanged — {e.Items.Count} stacks");
};

server.RegisterHandlers(new DemoHandlers());
await server.StartAsync();

// Interactive pathfinding demo loop
_ = Task.Run(async () =>
{
    while (true)
    {
        var line = Console.ReadLine();
        if (line == null)
        {
            break;
        }
        line = line.Trim();
        if (line.Equals("quit", StringComparison.OrdinalIgnoreCase))
        {
            break;
        }
        if (line.Equals("targets", StringComparison.OrdinalIgnoreCase))
        {
            var list = await server.Pathfinding.ListTargetsAsync();
            Console.WriteLine("Targets: " + string.Join(", ", list));
            continue;
        }
        if (line.StartsWith("nearest ", StringComparison.OrdinalIgnoreCase)
            || line.StartsWith("debug ", StringComparison.OrdinalIgnoreCase))
        {
            var debug = line.StartsWith("debug ", StringComparison.OrdinalIgnoreCase);
            var target = debug
                ? line.Substring("debug ".Length).Trim()
                : line.Substring("nearest ".Length).Trim();
            Console.WriteLine($"Finding nearest {target}" + (debug ? " (debug overlay)" : "") + "…");
            var route = await server.Pathfinding.FindNearestAsync(target, debug: debug);
            if (!route.Ok)
            {
                Console.WriteLine($"  FAIL: {route.Error}");
                continue;
            }
            Console.WriteLine($"  OnScreen: {route.OnScreen}  Method: {route.Method}");
            if (route.OnScreen)
            {
                Console.WriteLine($"  Instances on screen ({route.InstanceCount}):");
                foreach (var inst in route.Instances)
                {
                    var canvas = inst.CanvasX.HasValue
                        ? $" canvas=({inst.CanvasX},{inst.CanvasY})"
                        : "";
                    Console.WriteLine(
                        $"    {inst.Name} tile=({inst.X},{inst.Y},{inst.Plane}) obj={inst.ObjectId} action={inst.Action}{canvas}");
                }
            }
            else
            {
                Console.WriteLine($"  Dest: {route.Destination?.Name} @ ({route.Destination?.X},{route.Destination?.Y},{route.Destination?.Plane})");
                Console.WriteLine($"  walkTiles≈{route.WalkTiles}  routeTiles={route.RouteTiles.Count}  min-click steps ({route.Steps.Count}):");
                foreach (var step in route.Steps)
                {
                    Console.WriteLine($"    [{step.Kind}] ({step.X},{step.Y},{step.Plane}) {step.Action} {step.Label}");
                }
            }
            if (!string.IsNullOrEmpty(route.Note))
            {
                Console.WriteLine($"  Note: {route.Note}");
            }
            continue;
        }
        if (line.Equals("bank", StringComparison.OrdinalIgnoreCase))
        {
            var route = await server.Pathfinding.FindNearestAsync(RuneTarget.Bank);
            PrintRoute(route);
        }
    }
});

var tcs = new TaskCompletionSource();
Console.CancelKeyPress += (_, e) =>
{
    e.Cancel = true;
    tcs.TrySetResult();
};
await tcs.Task;
await server.StopAsync();
Console.WriteLine("Stopped.");

static void PrintRoute(RouteResult route)
{
    if (!route.Ok)
    {
        Console.WriteLine($"FAIL: {route.Error}");
        return;
    }
    Console.WriteLine($"{route.Destination?.Name}: {route.Steps.Count} steps, walkTiles={route.WalkTiles}");
}

sealed class DemoHandlers : IRuneBridgeHandler
{
    internal static string Stamp() => DateTime.Now.ToString("HH:mm:ss");

    [RuneEvent("GameStateChanged")]
    public void OnGameState(GameStateChangedEventArgs e)
    {
        Console.WriteLine($"[{Stamp()}] GameState → {e.GameState}");
    }
}
