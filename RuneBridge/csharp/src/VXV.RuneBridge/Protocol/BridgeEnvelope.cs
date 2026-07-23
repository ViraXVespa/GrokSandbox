using System.Text.Json;
using System.Text.Json.Serialization;

namespace VXV.RuneBridge.Protocol;

/// <summary>
/// Wire envelope: one NDJSON line from the RuneLite RuneBridge plugin.
/// </summary>
public sealed class BridgeEnvelope
{
    [JsonPropertyName("v")]
    public int Version { get; set; }

    [JsonPropertyName("type")]
    public string Type { get; set; } = "";

    [JsonPropertyName("ts")]
    public long TimestampUnixMs { get; set; }

    [JsonPropertyName("data")]
    public JsonElement Data { get; set; }

    public DateTimeOffset Timestamp =>
        DateTimeOffset.FromUnixTimeMilliseconds(TimestampUnixMs);
}
