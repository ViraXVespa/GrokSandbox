using System.Text.Json.Serialization;

namespace VXV.RuneBridge.Events;

/// <summary>Shared payload pieces mirrored from the Java plugin.</summary>
public sealed class ItemStack
{
    [JsonPropertyName("slot")] public int Slot { get; set; }
    [JsonPropertyName("id")] public int Id { get; set; }
    [JsonPropertyName("quantity")] public int Quantity { get; set; }
}

public sealed class ActorInfo
{
    [JsonPropertyName("null")] public bool? IsNull { get; set; }
    [JsonPropertyName("kind")] public string? Kind { get; set; }
    [JsonPropertyName("name")] public string? Name { get; set; }
    [JsonPropertyName("combatLevel")] public int? CombatLevel { get; set; }
    [JsonPropertyName("animation")] public int? Animation { get; set; }
    [JsonPropertyName("graphic")] public int? Graphic { get; set; }
    [JsonPropertyName("orientation")] public int? Orientation { get; set; }
    [JsonPropertyName("healthRatio")] public int? HealthRatio { get; set; }
    [JsonPropertyName("healthScale")] public int? HealthScale { get; set; }
    [JsonPropertyName("worldX")] public int? WorldX { get; set; }
    [JsonPropertyName("worldY")] public int? WorldY { get; set; }
    [JsonPropertyName("plane")] public int? Plane { get; set; }
    [JsonPropertyName("regionId")] public int? RegionId { get; set; }
    [JsonPropertyName("npcId")] public int? NpcId { get; set; }
    [JsonPropertyName("index")] public int? Index { get; set; }
    [JsonPropertyName("local")] public bool? Local { get; set; }
    [JsonPropertyName("team")] public int? Team { get; set; }
}

public class WorldPointInfo
{
    [JsonPropertyName("worldX")] public int? WorldX { get; set; }
    [JsonPropertyName("worldY")] public int? WorldY { get; set; }
    [JsonPropertyName("plane")] public int? Plane { get; set; }
    [JsonPropertyName("regionId")] public int? RegionId { get; set; }
}

public sealed class GameObjectInfo : WorldPointInfo
{
    [JsonPropertyName("id")] public int? Id { get; set; }
    [JsonPropertyName("null")] public bool? IsNull { get; set; }
}

public sealed class GroundItemInfo : WorldPointInfo
{
    [JsonPropertyName("id")] public int? Id { get; set; }
    [JsonPropertyName("quantity")] public int? Quantity { get; set; }
}

/// <summary>Base for all typed game events.</summary>
public abstract class RuneGameEventArgs : EventArgs
{
    public string EventType { get; set; } = "";
    public DateTimeOffset Timestamp { get; set; }
    public long TimestampUnixMs { get; set; }
}

public sealed class RawRuneEventArgs : RuneGameEventArgs
{
    public System.Text.Json.JsonElement Data { get; init; }
}

// ── Typed event args ────────────────────────────────────────────────────────

public sealed class ClientHelloEventArgs : RuneGameEventArgs
{
    public string? Plugin { get; init; }
    public int? Protocol { get; init; }
}

public sealed class ClientGoodbyeEventArgs : RuneGameEventArgs
{
    public string? Reason { get; init; }
}

public sealed class GameStateChangedEventArgs : RuneGameEventArgs
{
    public string? GameState { get; init; }
    public int? World { get; init; }
}

public sealed class GameTickEventArgs : RuneGameEventArgs
{
    public int Tick { get; init; }
    public int Plane { get; init; }
    public ActorInfo? LocalPlayer { get; init; }
}

public sealed class ItemContainerChangedEventArgs : RuneGameEventArgs
{
    public int ContainerId { get; init; }
    public string? ContainerKind { get; init; }
    public IReadOnlyList<ItemStack> Items { get; init; } = Array.Empty<ItemStack>();
}

public sealed class InventoryChangedEventArgs : RuneGameEventArgs
{
    public int ContainerId { get; init; }
    public IReadOnlyList<ItemStack> Items { get; init; } = Array.Empty<ItemStack>();
}

public sealed class EquipmentChangedEventArgs : RuneGameEventArgs
{
    public int ContainerId { get; init; }
    public IReadOnlyList<ItemStack> Items { get; init; } = Array.Empty<ItemStack>();
}

public sealed class BankChangedEventArgs : RuneGameEventArgs
{
    public int ContainerId { get; init; }
    public IReadOnlyList<ItemStack> Items { get; init; } = Array.Empty<ItemStack>();
}

public sealed class StatChangedEventArgs : RuneGameEventArgs
{
    public string? Skill { get; init; }
    public int SkillId { get; init; }
    public int Xp { get; init; }
    public int Level { get; init; }
    public int BoostedLevel { get; init; }
}

public sealed class ChatMessageEventArgs : RuneGameEventArgs
{
    public string? Type { get; init; }
    public string? Name { get; init; }
    public string? Sender { get; init; }
    public string? Message { get; init; }
    /// <summary>In-game chat timestamp from the client (not the bridge wall-clock).</summary>
    public int ChatTimestamp { get; init; }
}

public sealed class HitsplatAppliedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Actor { get; init; }
    public int Amount { get; init; }
    public int HitsplatType { get; init; }
    public bool Mine { get; init; }
    public bool Others { get; init; }
}

public sealed class ActorDeathEventArgs : RuneGameEventArgs
{
    public ActorInfo? Actor { get; init; }
}

public sealed class AnimationChangedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Actor { get; init; }
    public int AnimationId { get; init; }
}

public sealed class GraphicChangedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Actor { get; init; }
    public int GraphicId { get; init; }
}

public sealed class InteractingChangedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Source { get; init; }
    public ActorInfo? Target { get; init; }
}

public sealed class ProjectileMovedEventArgs : RuneGameEventArgs
{
    public int Id { get; init; }
    public int RemainingCycles { get; init; }
}

public sealed class NpcSpawnedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Npc { get; init; }
}

public sealed class NpcDespawnedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Npc { get; init; }
}

public sealed class PlayerSpawnedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Player { get; init; }
}

public sealed class PlayerDespawnedEventArgs : RuneGameEventArgs
{
    public ActorInfo? Player { get; init; }
}

public sealed class GameObjectSpawnedEventArgs : RuneGameEventArgs
{
    public GameObjectInfo? GameObject { get; init; }
}

public sealed class GameObjectDespawnedEventArgs : RuneGameEventArgs
{
    public GameObjectInfo? GameObject { get; init; }
}

public sealed class GroundItemSpawnedEventArgs : RuneGameEventArgs
{
    public GroundItemInfo? Item { get; init; }
}

public sealed class GroundItemDespawnedEventArgs : RuneGameEventArgs
{
    public GroundItemInfo? Item { get; init; }
}

public sealed class MenuOptionClickedEventArgs : RuneGameEventArgs
{
    public string? Option { get; init; }
    public string? Target { get; init; }
    public int Id { get; init; }
    public int ItemId { get; init; }
    public int Param0 { get; init; }
    public int Param1 { get; init; }
    public string? MenuAction { get; init; }
}

public sealed class WidgetLoadedEventArgs : RuneGameEventArgs
{
    public int GroupId { get; init; }
}

public sealed class VarbitChangedEventArgs : RuneGameEventArgs
{
    public int VarbitId { get; init; }
    public int VarpId { get; init; }
    public int Value { get; init; }
}

public sealed class GrandExchangeOfferChangedEventArgs : RuneGameEventArgs
{
    public int Slot { get; init; }
    public int ItemId { get; init; }
    public int Quantity { get; init; }
    public int TotalQuantity { get; init; }
    public int Price { get; init; }
    public int Spent { get; init; }
    public string? State { get; init; }
}
