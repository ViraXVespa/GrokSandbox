namespace VXV.RuneBridge.Pathfinding;

/// <summary>
/// Well-known interactable categories. Names match the Java POI catalog / FindNearest target strings.
/// </summary>
public enum RuneTarget
{
    Bank,
    BankBooth,
    BankChest,
    Furnace,
    Anvil,
    Range,
    CookingRange,
    WaterSource,
    Altar,
    GeneralStore,
    GrandExchange,
    DepositBox,
    Loom,
    SpinningWheel,
    PotteryOven,
    CraftingGuild,
    OreCopper,
    OreTin,
    OreIron,
    OreCoal,
    OreMithril,
    OreAdamantite,
    OreRunite,
    OreGold,
    OreSilver,
    OreAmethyst,
    /// <summary>Any catalogued ore rock hub.</summary>
    Ore,
    TreeNormal,
    TreeOak,
    TreeWillow,
    TreeMaple,
    TreeYew,
    TreeMagic,
    /// <summary>Any catalogued tree hub.</summary>
    Tree,
    FishingSpot,
    ToolLeprechaun,
}

public static class RuneTargetExtensions
{
    public static string ToWireName(this RuneTarget target) => target switch
    {
        RuneTarget.Bank => "BANK",
        RuneTarget.BankBooth => "BANK_BOOTH",
        RuneTarget.BankChest => "BANK_CHEST",
        RuneTarget.Furnace => "FURNACE",
        RuneTarget.Anvil => "ANVIL",
        RuneTarget.Range => "RANGE",
        RuneTarget.CookingRange => "COOKING_RANGE",
        RuneTarget.WaterSource => "WATER_SOURCE",
        RuneTarget.Altar => "ALTAR",
        RuneTarget.GeneralStore => "GENERAL_STORE",
        RuneTarget.GrandExchange => "GRAND_EXCHANGE",
        RuneTarget.DepositBox => "DEPOSIT_BOX",
        RuneTarget.Loom => "LOOM",
        RuneTarget.SpinningWheel => "SPINNING_WHEEL",
        RuneTarget.PotteryOven => "POTTERY_OVEN",
        RuneTarget.CraftingGuild => "CRAFTING_GUILD",
        RuneTarget.OreCopper => "ORE_COPPER",
        RuneTarget.OreTin => "ORE_TIN",
        RuneTarget.OreIron => "ORE_IRON",
        RuneTarget.OreCoal => "ORE_COAL",
        RuneTarget.OreMithril => "ORE_MITHRIL",
        RuneTarget.OreAdamantite => "ORE_ADAMANTITE",
        RuneTarget.OreRunite => "ORE_RUNITE",
        RuneTarget.OreGold => "ORE_GOLD",
        RuneTarget.OreSilver => "ORE_SILVER",
        RuneTarget.OreAmethyst => "ORE_AMETHYST",
        RuneTarget.Ore => "ORE",
        RuneTarget.TreeNormal => "TREE_NORMAL",
        RuneTarget.TreeOak => "TREE_OAK",
        RuneTarget.TreeWillow => "TREE_WILLOW",
        RuneTarget.TreeMaple => "TREE_MAPLE",
        RuneTarget.TreeYew => "TREE_YEW",
        RuneTarget.TreeMagic => "TREE_MAGIC",
        RuneTarget.Tree => "TREE",
        RuneTarget.FishingSpot => "FISHING_SPOT",
        RuneTarget.ToolLeprechaun => "TOOL_LEPRECHAUN",
        _ => target.ToString().ToUpperInvariant()
    };
}
