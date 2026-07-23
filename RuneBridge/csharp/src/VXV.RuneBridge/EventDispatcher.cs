using System.Reflection;
using System.Text.Json;
using VXV.RuneBridge.Events;
using VXV.RuneBridge.Handlers;
using VXV.RuneBridge.Protocol;

namespace VXV.RuneBridge;

/// <summary>
/// Deserializes envelopes and raises both classic .NET events and attribute-based handlers.
/// </summary>
public sealed class EventDispatcher
{
    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        PropertyNameCaseInsensitive = true,
    };

    private readonly List<(string EventType, object Target, MethodInfo Method, Type ArgType)> _handlers = new();

    // ── Classic .NET events (subscribe with +=) ─────────────────────────────

    public event EventHandler<ClientHelloEventArgs>? ClientHello;
    public event EventHandler<ClientGoodbyeEventArgs>? ClientGoodbye;
    public event EventHandler<GameStateChangedEventArgs>? GameStateChanged;
    public event EventHandler<GameTickEventArgs>? GameTick;
    public event EventHandler<ItemContainerChangedEventArgs>? ItemContainerChanged;
    public event EventHandler<InventoryChangedEventArgs>? InventoryChanged;
    public event EventHandler<EquipmentChangedEventArgs>? EquipmentChanged;
    public event EventHandler<BankChangedEventArgs>? BankChanged;
    public event EventHandler<StatChangedEventArgs>? StatChanged;
    public event EventHandler<ChatMessageEventArgs>? ChatMessage;
    public event EventHandler<HitsplatAppliedEventArgs>? HitsplatApplied;
    public event EventHandler<ActorDeathEventArgs>? ActorDeath;
    public event EventHandler<AnimationChangedEventArgs>? AnimationChanged;
    public event EventHandler<GraphicChangedEventArgs>? GraphicChanged;
    public event EventHandler<InteractingChangedEventArgs>? InteractingChanged;
    public event EventHandler<ProjectileMovedEventArgs>? ProjectileMoved;
    public event EventHandler<NpcSpawnedEventArgs>? NpcSpawned;
    public event EventHandler<NpcDespawnedEventArgs>? NpcDespawned;
    public event EventHandler<PlayerSpawnedEventArgs>? PlayerSpawned;
    public event EventHandler<PlayerDespawnedEventArgs>? PlayerDespawned;
    public event EventHandler<GameObjectSpawnedEventArgs>? GameObjectSpawned;
    public event EventHandler<GameObjectDespawnedEventArgs>? GameObjectDespawned;
    public event EventHandler<GroundItemSpawnedEventArgs>? GroundItemSpawned;
    public event EventHandler<GroundItemDespawnedEventArgs>? GroundItemDespawned;
    public event EventHandler<MenuOptionClickedEventArgs>? MenuOptionClicked;
    public event EventHandler<WidgetLoadedEventArgs>? WidgetLoaded;
    public event EventHandler<VarbitChangedEventArgs>? VarbitChanged;
    public event EventHandler<GrandExchangeOfferChangedEventArgs>? GrandExchangeOfferChanged;

    /// <summary>Fired for every event after typed dispatch (raw JSON available).</summary>
    public event EventHandler<RawRuneEventArgs>? AnyEvent;

    /// <summary>Fired when a client connects at the TCP level.</summary>
    public event EventHandler? ClientConnected;

    /// <summary>Fired when a client disconnects.</summary>
    public event EventHandler? ClientDisconnected;

    public void RegisterHandlers(object handlerInstance)
    {
        ArgumentNullException.ThrowIfNull(handlerInstance);
        var type = handlerInstance.GetType();
        foreach (var method in type.GetMethods(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic))
        {
            var attrs = method.GetCustomAttributes<RuneEventAttribute>(inherit: true);
            foreach (var attr in attrs)
            {
                var parameters = method.GetParameters();
                if (parameters.Length != 1)
                {
                    throw new InvalidOperationException(
                        $"Handler {type.Name}.{method.Name} must take exactly one parameter.");
                }
                _handlers.Add((attr.EventType, handlerInstance, method, parameters[0].ParameterType));
            }
        }
    }

    internal void RaiseClientConnected() => ClientConnected?.Invoke(this, EventArgs.Empty);
    internal void RaiseClientDisconnected() => ClientDisconnected?.Invoke(this, EventArgs.Empty);

    public void Dispatch(BridgeEnvelope envelope)
    {
        var ts = envelope.Timestamp;
        var type = envelope.Type ?? "";

        void RaiseTyped<T>(EventHandler<T>? handler, T args) where T : RuneGameEventArgs
        {
            handler?.Invoke(this, args);
            InvokeAttributeHandlers(type, args);
        }

        switch (type)
        {
            case "ClientHello":
            {
                var d = DeserializeData<ClientHelloData>(envelope.Data);
                RaiseTyped(ClientHello, Fill(new ClientHelloEventArgs
                {
                    Plugin = d?.Plugin,
                    Protocol = d?.Protocol
                }, envelope, ts));
                break;
            }
            case "ClientGoodbye":
            {
                var d = DeserializeData<ClientGoodbyeData>(envelope.Data);
                RaiseTyped(ClientGoodbye, Fill(new ClientGoodbyeEventArgs { Reason = d?.Reason }, envelope, ts));
                break;
            }
            case "GameStateChanged":
            {
                var d = DeserializeData<GameStateData>(envelope.Data);
                RaiseTyped(GameStateChanged, Fill(new GameStateChangedEventArgs
                {
                    GameState = d?.GameState,
                    World = d?.World
                }, envelope, ts));
                break;
            }
            case "GameTick":
            {
                var d = DeserializeData<GameTickData>(envelope.Data);
                RaiseTyped(GameTick, Fill(new GameTickEventArgs
                {
                    Tick = d?.Tick ?? 0,
                    Plane = d?.Plane ?? 0,
                    LocalPlayer = d?.LocalPlayer
                }, envelope, ts));
                break;
            }
            case "ItemContainerChanged":
            {
                var d = DeserializeData<ContainerData>(envelope.Data);
                RaiseTyped(ItemContainerChanged, Fill(new ItemContainerChangedEventArgs
                {
                    ContainerId = d?.ContainerId ?? 0,
                    ContainerKind = d?.ContainerKind,
                    Items = (IReadOnlyList<ItemStack>)(d?.Items ?? (IReadOnlyList<ItemStack>)Array.Empty<ItemStack>())
                }, envelope, ts));
                break;
            }
            case "InventoryChanged":
            {
                var d = DeserializeData<ContainerData>(envelope.Data);
                RaiseTyped(InventoryChanged, Fill(new InventoryChangedEventArgs
                {
                    ContainerId = d?.ContainerId ?? 0,
                    Items = (IReadOnlyList<ItemStack>)(d?.Items ?? (IReadOnlyList<ItemStack>)Array.Empty<ItemStack>())
                }, envelope, ts));
                break;
            }
            case "EquipmentChanged":
            {
                var d = DeserializeData<ContainerData>(envelope.Data);
                RaiseTyped(EquipmentChanged, Fill(new EquipmentChangedEventArgs
                {
                    ContainerId = d?.ContainerId ?? 0,
                    Items = (IReadOnlyList<ItemStack>)(d?.Items ?? (IReadOnlyList<ItemStack>)Array.Empty<ItemStack>())
                }, envelope, ts));
                break;
            }
            case "BankChanged":
            {
                var d = DeserializeData<ContainerData>(envelope.Data);
                RaiseTyped(BankChanged, Fill(new BankChangedEventArgs
                {
                    ContainerId = d?.ContainerId ?? 0,
                    Items = (IReadOnlyList<ItemStack>)(d?.Items ?? (IReadOnlyList<ItemStack>)Array.Empty<ItemStack>())
                }, envelope, ts));
                break;
            }
            case "StatChanged":
            {
                var d = DeserializeData<StatData>(envelope.Data);
                RaiseTyped(StatChanged, Fill(new StatChangedEventArgs
                {
                    Skill = d?.Skill,
                    SkillId = d?.SkillId ?? -1,
                    Xp = d?.Xp ?? 0,
                    Level = d?.Level ?? 0,
                    BoostedLevel = d?.BoostedLevel ?? 0
                }, envelope, ts));
                break;
            }
            case "ChatMessage":
            {
                var d = DeserializeData<ChatData>(envelope.Data);
                RaiseTyped(ChatMessage, Fill(new ChatMessageEventArgs
                {
                    Type = d?.Type,
                    Name = d?.Name,
                    Sender = d?.Sender,
                    Message = d?.Message,
                    ChatTimestamp = d?.Timestamp ?? 0
                }, envelope, ts));
                break;
            }
            case "HitsplatApplied":
            {
                var d = DeserializeData<HitsplatData>(envelope.Data);
                RaiseTyped(HitsplatApplied, Fill(new HitsplatAppliedEventArgs
                {
                    Actor = d?.Actor,
                    Amount = d?.Amount ?? 0,
                    HitsplatType = d?.HitsplatType ?? 0,
                    Mine = d?.Mine ?? false,
                    Others = d?.Others ?? false
                }, envelope, ts));
                break;
            }
            case "ActorDeath":
            {
                var d = DeserializeData<ActorWrap>(envelope.Data);
                RaiseTyped(ActorDeath, Fill(new ActorDeathEventArgs { Actor = d?.Actor }, envelope, ts));
                break;
            }
            case "AnimationChanged":
            {
                var d = DeserializeData<AnimData>(envelope.Data);
                RaiseTyped(AnimationChanged, Fill(new AnimationChangedEventArgs
                {
                    Actor = d?.Actor,
                    AnimationId = d?.AnimationId ?? 0
                }, envelope, ts));
                break;
            }
            case "GraphicChanged":
            {
                var d = DeserializeData<GraphicData>(envelope.Data);
                RaiseTyped(GraphicChanged, Fill(new GraphicChangedEventArgs
                {
                    Actor = d?.Actor,
                    GraphicId = d?.GraphicId ?? 0
                }, envelope, ts));
                break;
            }
            case "InteractingChanged":
            {
                var d = DeserializeData<InteractData>(envelope.Data);
                RaiseTyped(InteractingChanged, Fill(new InteractingChangedEventArgs
                {
                    Source = d?.Source,
                    Target = d?.Target
                }, envelope, ts));
                break;
            }
            case "ProjectileMoved":
            {
                var d = DeserializeData<ProjectileData>(envelope.Data);
                RaiseTyped(ProjectileMoved, Fill(new ProjectileMovedEventArgs
                {
                    Id = d?.Id ?? 0,
                    RemainingCycles = d?.RemainingCycles ?? 0
                }, envelope, ts));
                break;
            }
            case "NpcSpawned":
                RaiseTyped(NpcSpawned, Fill(new NpcSpawnedEventArgs
                {
                    Npc = DeserializeData<ActorInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "NpcDespawned":
                RaiseTyped(NpcDespawned, Fill(new NpcDespawnedEventArgs
                {
                    Npc = DeserializeData<ActorInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "PlayerSpawned":
                RaiseTyped(PlayerSpawned, Fill(new PlayerSpawnedEventArgs
                {
                    Player = DeserializeData<ActorInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "PlayerDespawned":
                RaiseTyped(PlayerDespawned, Fill(new PlayerDespawnedEventArgs
                {
                    Player = DeserializeData<ActorInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "GameObjectSpawned":
                RaiseTyped(GameObjectSpawned, Fill(new GameObjectSpawnedEventArgs
                {
                    GameObject = DeserializeData<GameObjectInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "GameObjectDespawned":
                RaiseTyped(GameObjectDespawned, Fill(new GameObjectDespawnedEventArgs
                {
                    GameObject = DeserializeData<GameObjectInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "GroundItemSpawned":
                RaiseTyped(GroundItemSpawned, Fill(new GroundItemSpawnedEventArgs
                {
                    Item = DeserializeData<GroundItemInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "GroundItemDespawned":
                RaiseTyped(GroundItemDespawned, Fill(new GroundItemDespawnedEventArgs
                {
                    Item = DeserializeData<GroundItemInfo>(envelope.Data)
                }, envelope, ts));
                break;
            case "MenuOptionClicked":
            {
                var d = DeserializeData<MenuData>(envelope.Data);
                RaiseTyped(MenuOptionClicked, Fill(new MenuOptionClickedEventArgs
                {
                    Option = d?.Option,
                    Target = d?.Target,
                    Id = d?.Id ?? 0,
                    ItemId = d?.ItemId ?? 0,
                    Param0 = d?.Param0 ?? 0,
                    Param1 = d?.Param1 ?? 0,
                    MenuAction = d?.MenuAction
                }, envelope, ts));
                break;
            }
            case "WidgetLoaded":
            {
                var d = DeserializeData<WidgetData>(envelope.Data);
                RaiseTyped(WidgetLoaded, Fill(new WidgetLoadedEventArgs
                {
                    GroupId = d?.GroupId ?? 0
                }, envelope, ts));
                break;
            }
            case "VarbitChanged":
            {
                var d = DeserializeData<VarbitData>(envelope.Data);
                RaiseTyped(VarbitChanged, Fill(new VarbitChangedEventArgs
                {
                    VarbitId = d?.VarbitId ?? 0,
                    VarpId = d?.VarpId ?? 0,
                    Value = d?.Value ?? 0
                }, envelope, ts));
                break;
            }
            case "GrandExchangeOfferChanged":
            {
                var d = DeserializeData<GeData>(envelope.Data);
                RaiseTyped(GrandExchangeOfferChanged, Fill(new GrandExchangeOfferChangedEventArgs
                {
                    Slot = d?.Slot ?? 0,
                    ItemId = d?.ItemId ?? 0,
                    Quantity = d?.Quantity ?? 0,
                    TotalQuantity = d?.TotalQuantity ?? 0,
                    Price = d?.Price ?? 0,
                    Spent = d?.Spent ?? 0,
                    State = d?.State
                }, envelope, ts));
                break;
            }
            default:
                InvokeAttributeHandlers(type, Fill(new RawRuneEventArgs { Data = envelope.Data }, envelope, ts));
                break;
        }

        var raw = Fill(new RawRuneEventArgs { Data = envelope.Data }, envelope, ts);
        AnyEvent?.Invoke(this, raw);
        InvokeAttributeHandlers("*", raw);
    }

    private void InvokeAttributeHandlers(string eventType, RuneGameEventArgs args)
    {
        foreach (var (etype, target, method, argType) in _handlers)
        {
            if (etype != eventType && etype != "*")
            {
                continue;
            }
            try
            {
                if (argType.IsInstanceOfType(args))
                {
                    method.Invoke(target, new object[] { args });
                }
                else if (argType == typeof(RawRuneEventArgs) && args is not RawRuneEventArgs)
                {
                    // skip typed-only
                }
            }
            catch (TargetInvocationException ex)
            {
                System.Diagnostics.Debug.WriteLine($"Handler error: {ex.InnerException}");
            }
        }
    }

    private static T Fill<T>(T args, BridgeEnvelope env, DateTimeOffset ts) where T : RuneGameEventArgs
    {
        args.EventType = env.Type;
        args.Timestamp = ts;
        args.TimestampUnixMs = env.TimestampUnixMs;
        return args;
    }

    private static T? DeserializeData<T>(JsonElement data)
    {
        try
        {
            return data.Deserialize<T>(JsonOpts);
        }
        catch
        {
            return default;
        }
    }

    // Internal DTO shapes matching Java maps
    private sealed class ClientHelloData
    {
        public string? Plugin { get; set; }
        public int? Protocol { get; set; }
    }
    private sealed class ClientGoodbyeData { public string? Reason { get; set; } }
    private sealed class GameStateData { public string? GameState { get; set; } public int? World { get; set; } }
    private sealed class GameTickData { public int Tick { get; set; } public int Plane { get; set; } public ActorInfo? LocalPlayer { get; set; } }
    private sealed class ContainerData
    {
        public int ContainerId { get; set; }
        public string? ContainerKind { get; set; }
        public List<ItemStack>? Items { get; set; }
    }
    private sealed class StatData
    {
        public string? Skill { get; set; }
        public int SkillId { get; set; }
        public int Xp { get; set; }
        public int Level { get; set; }
        public int BoostedLevel { get; set; }
    }
    private sealed class ChatData
    {
        public string? Type { get; set; }
        public string? Name { get; set; }
        public string? Sender { get; set; }
        public string? Message { get; set; }
        public int Timestamp { get; set; }
    }
    private sealed class HitsplatData
    {
        public ActorInfo? Actor { get; set; }
        public int Amount { get; set; }
        public int HitsplatType { get; set; }
        public bool Mine { get; set; }
        public bool Others { get; set; }
    }
    private sealed class ActorWrap { public ActorInfo? Actor { get; set; } }
    private sealed class AnimData { public ActorInfo? Actor { get; set; } public int AnimationId { get; set; } }
    private sealed class GraphicData { public ActorInfo? Actor { get; set; } public int GraphicId { get; set; } }
    private sealed class InteractData { public ActorInfo? Source { get; set; } public ActorInfo? Target { get; set; } }
    private sealed class ProjectileData { public int Id { get; set; } public int RemainingCycles { get; set; } }
    private sealed class MenuData
    {
        public string? Option { get; set; }
        public string? Target { get; set; }
        public int Id { get; set; }
        public int ItemId { get; set; }
        public int Param0 { get; set; }
        public int Param1 { get; set; }
        public string? MenuAction { get; set; }
    }
    private sealed class WidgetData { public int GroupId { get; set; } }
    private sealed class VarbitData { public int VarbitId { get; set; } public int VarpId { get; set; } public int Value { get; set; } }
    private sealed class GeData
    {
        public int Slot { get; set; }
        public int ItemId { get; set; }
        public int Quantity { get; set; }
        public int TotalQuantity { get; set; }
        public int Price { get; set; }
        public int Spent { get; set; }
        public string? State { get; set; }
    }
}
