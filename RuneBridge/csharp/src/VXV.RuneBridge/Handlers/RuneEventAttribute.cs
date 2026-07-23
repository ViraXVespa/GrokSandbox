namespace VXV.RuneBridge.Handlers;

/// <summary>
/// Marks a method as a RuneBridge event handler.
/// Method signature: void Method(TEventArgs e) where TEventArgs : RuneGameEventArgs
/// or void Method(RawRuneEventArgs e) for catch-all raw JSON.
/// </summary>
[AttributeUsage(AttributeTargets.Method, AllowMultiple = true)]
public sealed class RuneEventAttribute : Attribute
{
    public RuneEventAttribute(string eventType)
    {
        EventType = eventType;
    }

    /// <summary>Wire event type, e.g. "InventoryChanged". Use "*" for all raw events.</summary>
    public string EventType { get; }
}
