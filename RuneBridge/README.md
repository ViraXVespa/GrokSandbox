# RuneBridge

Bridge **RuneLite game events** into a **.NET (C#)** library so you can write extension logic like:

```csharp
server.Events.InventoryChanged += (_, e) =>
{
    // react to inventory changes
};
```

or attribute-based handlers:

```csharp
[RuneEvent("InventoryChanged")]
public void OnInventoryChanged(InventoryChangedEventArgs e) { ... }
```

```
┌─────────────────┐   NDJSON/TCP :17473   ┌──────────────────────┐
│  RuneLite       │ ───────────────────► │  VXV.RuneBridge     │
│  RuneBridge     │                      │  (C# library)        │
│  plugin (Java)  │                      │  + your handlers     │
└─────────────────┘                      └──────────────────────┘
```

## Layout

```
RuneBridge/
  RuneLite/                 # RuneLite external plugin (Java 11)
  csharp/
    VXV.RuneBridge.sln
    src/VXV.RuneBridge/     # Library
    src/VXV.RuneBridge.Host/# Sample console host
  README.md
```

## Quick start

### 1. Start the C# host

```powershell
cd RuneBridge\csharp
dotnet run --project src\VXV.RuneBridge.Host
```

Listens on `127.0.0.1:17473`.

### 2. Run the RuneLite plugin

```powershell
cd RuneBridge\RuneLite
.\gradlew.bat run
```

Enable **RuneBridge** in the plugin list (developer mode). Config:

| Setting | Default | Meaning |
|---------|---------|---------|
| Enable bridge | on | Send events |
| Host | 127.0.0.1 | C# server |
| Port | 17473 | TCP port |
| Emit game ticks | off | High frequency |
| Emit chat | on | Chat messages |
| Emit scene | on | NPC/object/item spawns |
| Emit menu | on | Menu clicks |

## Events emitted

| Type | When |
|------|------|
| `ClientHello` / `ClientGoodbye` | Plugin connect / stop |
| `GameStateChanged` | Login, hopping, lobby, … |
| `GameTick` | Each game tick (optional) |
| `ItemContainerChanged` | Any container |
| `InventoryChanged` | Inventory only |
| `EquipmentChanged` | Equipment only |
| `BankChanged` | Bank only |
| `StatChanged` | Skill XP / level |
| `ChatMessage` | Game chat |
| `HitsplatApplied` | Hitsplats |
| `ActorDeath` | NPC/player death |
| `AnimationChanged` / `GraphicChanged` | Anim / gfx |
| `InteractingChanged` | Target change |
| `ProjectileMoved` | Projectiles |
| `NpcSpawned` / `NpcDespawned` | NPCs |
| `PlayerSpawned` / `PlayerDespawned` | Players |
| `GameObjectSpawned` / `GameObjectDespawned` | Objects |
| `GroundItemSpawned` / `GroundItemDespawned` | Ground items |
| `MenuOptionClicked` | Menu click |
| `WidgetLoaded` | Interface load |
| `VarbitChanged` | Varbits / varps |
| `GrandExchangeOfferChanged` | GE slots |

## Wire protocol

Newline-delimited JSON (UTF-8):

```json
{"v":1,"type":"InventoryChanged","ts":1710000000000,"data":{"containerId":93,"containerKind":"Inventory","items":[{"slot":0,"id":995,"quantity":1000}]}}
```

## C# library usage

```csharp
using VXV.RuneBridge;
using VXV.RuneBridge.Events;
using VXV.RuneBridge.Handlers;

var server = new RuneBridgeServer(17473);

// Classic events
server.Events.InventoryChanged += OnInventory;

// Attribute handlers on a class
server.RegisterHandlers(new MyHandlers());

await server.StartAsync();

void OnInventory(object? s, InventoryChangedEventArgs e)
{
    foreach (var item in e.Items)
        Console.WriteLine($"slot {item.Slot}: {item.Id} x{item.Quantity}");
}

class MyHandlers : IRuneBridgeHandler
{
    [RuneEvent("InventoryChanged")]
    public void OnInventoryChanged(InventoryChangedEventArgs e) { /* ... */ }

    [RuneEvent("StatChanged")]
    public void OnStat(StatChangedEventArgs e) { /* ... */ }

    [RuneEvent("*")] // every event as raw JSON
    public void OnAny(RawRuneEventArgs e) { }
}
```

Reference the project:

```xml
<ProjectReference Include="path\to\VXV.RuneBridge.csproj" />
```

## Pathfinding — `FindNearest`

The C# library can ask RuneLite for the **shortest walking route** to interactables (banks, furnaces, ore rocks, trees, etc.).

### How it works

1. C# calls `server.Pathfinding.FindNearestAsync(RuneTarget.Bank)`.
2. A duplex TCP **Request** is sent to the Java plugin.
3. Java:
   - picks nearest candidates from a **static POI catalog** + **live scene scan**
   - runs **A\*** on the loaded scene collision map (true walking distance, not crow-flies)
   - returns **min-click** `steps` (sparse Walk waypoints + final Interact — not one step per tile)
   - also returns full **`routeTiles`** (every path tile) for debug overlays
4. If the destination is outside the loaded scene, you get an **estimated** straight route and a note to walk closer for full obstacle checks.

### C# example

```csharp
// debug: true → GDI arrows (~3s):
//   on-screen  → each visible instance
//   off-screen → every route tile along the path
var route = await server.Pathfinding.FindNearestAsync(RuneTarget.Bank, debug: true);

if (route.Ok)
{
    if (route.OnScreen)
    {
        foreach (var tile in route.InstanceTiles)
        {
            // World tile helpers:
            ScreenPoint? mouse = tile.ToScreen();           // uses cached projection
            // or: await tile.ToScreenAsync(server);       // live WorldToScreen via RuneLite
        }
    }
    else
    {
        // Min-click sequence (use these for automation):
        foreach (var step in route.Steps)
        {
            WorldTile t = step.ToWorldTile(); // sparse Walk… then Interact
        }
        // Full path (use for debug / visualization):
        foreach (var tile in route.RouteTiles) { /* every walk tile */ }
    }
}

await server.Pathfinding.FindNearestAsync("FURNACE");
await server.Pathfinding.FindNearestAsync("ORE_RUNITE", debug: true);
```

### `WorldTile` helpers

| API | Meaning |
|-----|---------|
| `WorldTile(x, y, plane)` | Map tile |
| `tile.ToScreen()` | Cached canvas/screen → `ScreenPoint` (desktop pixels) |
| `await tile.ToScreenAsync(server)` | Live project via plugin (`WorldToScreen`) |
| `ScreenPoint.X/Y` | Absolute mouse/desktop coords |
| `ScreenPoint.CanvasX/Y` | Relative to RuneLite game canvas |

Host demo: `debug bank` draws arrows; `nearest bank` does not.

**Behavior**

| Situation | Result |
|-----------|--------|
| ≥1 matching object projects onto the **game canvas** | `OnScreen = true`, `Instances` = all those tiles (sorted nearest first), `Steps` / `RouteTiles` empty |
| None on canvas | `OnScreen = false`, min-click `Steps` + full `RouteTiles` path to nearest POI |

### Targets

`Bank`, `Furnace`, `Anvil`, `Range`, `GrandExchange`, `DepositBox`,  
`OreIron` / `OreCoal` / `OreRunite` / `OreAmethyst` / …,  
`TreeYew` / `TreeMagic` / …, `FishingSpot`, `Altar`, `WaterSource`, and more  
(see `RuneTarget` enum and `ListTargetsAsync()`).

### Host demo

With the sample host running and the plugin connected:

```
nearest bank
nearest furnace
nearest ore_runite
targets
```

## Extending

### New event from Java

1. `@Subscribe` in `RuneBridgePlugin.java`
2. `emit("YourEventName", map)`
3. Add `YourEventEventArgs` + case in `EventDispatcher.cs`
4. Subscribe in C#

### New C# consumer only

No Java changes — subscribe to existing events or use `[RuneEvent("*")]` and inspect `RawRuneEventArgs.Data`.

### New pathfinding target

1. Add POIs in `PoiCatalog.java` and/or live scan rules in `RoutePlanner.scanSceneForTarget`
2. Add enum value in `RuneTarget.cs` + `ToWireName()`

## Safety notes

- Binds to **localhost** by default — do not expose the port publicly.
- Scene events can be high-volume; disable **Emit scene** / ticks if your handlers are heavy.
- Pathfinding returns **suggested walk tiles**; automating clicks/input can violate Jagex rules.
- Off-scene routes are estimates until the area is loaded.

## License

Use freely in this sandbox; keep a permissive license if you publish.
