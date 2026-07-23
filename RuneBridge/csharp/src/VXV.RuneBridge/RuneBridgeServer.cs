using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using VXV.RuneBridge.Handlers;
using VXV.RuneBridge.Pathfinding;
using VXV.RuneBridge.Protocol;
using WorldTile = VXV.RuneBridge.Pathfinding.WorldTile;

namespace VXV.RuneBridge;

/// <summary>
/// TCP server that accepts NDJSON event lines from the RuneLite RuneBridge plugin
/// and can send request lines back (FindNearest, etc.).
/// </summary>
public sealed class RuneBridgeServer : IAsyncDisposable, IDisposable
{
    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        PropertyNameCaseInsensitive = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    private readonly RuneBridgeOptions _options;
    private readonly EventDispatcher _dispatcher = new();
    private readonly ConcurrentDictionary<string, TaskCompletionSource<JsonElement>> _pending = new();
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;
    private Task? _acceptLoop;
    private int _clientCount;

    // Active client writers (last connected wins for requests; multi-client fan-out possible later)
    private readonly object _writeGate = new();
    private StreamWriter? _activeWriter;

    public RuneBridgeServer(RuneBridgeOptions? options = null)
    {
        _options = options ?? new RuneBridgeOptions();
        Pathfinding = new RoutePlanner(this);
    }

    public RuneBridgeServer(int port) : this(new RuneBridgeOptions { Port = port })
    {
    }

    public EventDispatcher Events => _dispatcher;

    /// <summary>Collision-aware nearest-route planner (uses live RuneLite scene when connected).</summary>
    public RoutePlanner Pathfinding { get; }

    public bool IsRunning => _listener != null;

    public int ConnectedClients => _clientCount;

    public void RegisterHandlers(IRuneBridgeHandler handler) => _dispatcher.RegisterHandlers(handler);

    public void RegisterHandlers(object handler) => _dispatcher.RegisterHandlers(handler);

    public async Task StartAsync(CancellationToken cancellationToken = default)
    {
        if (_listener != null)
        {
            return;
        }

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _listener = new TcpListener(IPAddress.Parse(_options.BindAddress), _options.Port);
        _listener.Start();
        _acceptLoop = Task.Run(() => AcceptLoopAsync(_cts.Token), _cts.Token);
        await Task.CompletedTask.ConfigureAwait(false);
    }

    public void Start() => StartAsync().GetAwaiter().GetResult();

    public async Task StopAsync()
    {
        _cts?.Cancel();
        _listener?.Stop();
        if (_acceptLoop != null)
        {
            try { await _acceptLoop.ConfigureAwait(false); } catch { /* ignore */ }
        }
        foreach (var kv in _pending)
        {
            kv.Value.TrySetCanceled();
        }
        _pending.Clear();
        lock (_writeGate)
        {
            _activeWriter = null;
        }
        _listener = null;
        _cts?.Dispose();
        _cts = null;
    }

    public void Stop() => StopAsync().GetAwaiter().GetResult();

    /// <summary>
    /// Project world tiles to canvas / absolute screen coordinates via RuneLite.
    /// </summary>
    public async Task<IReadOnlyList<WorldTile>> ProjectTilesAsync(
        IEnumerable<WorldTile> tiles,
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
    {
        var list = tiles.Select(t => new { x = t.X, y = t.Y, plane = t.Plane }).ToList();
        if (list.Count == 0)
        {
            return Array.Empty<WorldTile>();
        }

        var response = await RequestAsync<WorldToScreenResponse>(
            "WorldToScreen",
            new Dictionary<string, object?> { ["tiles"] = list },
            timeout ?? TimeSpan.FromSeconds(3),
            cancellationToken).ConfigureAwait(false);

        return response?.Tiles ?? (IReadOnlyList<WorldTile>)Array.Empty<WorldTile>();
    }

    /// <summary>
    /// Send a request to the connected RuneLite plugin and await a Response line.
    /// </summary>
    public async Task<T?> RequestAsync<T>(
        string method,
        object? parameters,
        TimeSpan timeout,
        CancellationToken cancellationToken = default)
    {
        var id = Guid.NewGuid().ToString("N");
        var tcs = new TaskCompletionSource<JsonElement>(TaskCreationOptions.RunContinuationsAsynchronously);
        if (!_pending.TryAdd(id, tcs))
        {
            throw new InvalidOperationException("Failed to register pending request");
        }

        var envelope = new Dictionary<string, object?>
        {
            ["v"] = 1,
            ["type"] = "Request",
            ["ts"] = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            ["data"] = new Dictionary<string, object?>
            {
                ["id"] = id,
                ["method"] = method,
                ["params"] = parameters ?? new { }
            }
        };

        try
        {
            var line = JsonSerializer.Serialize(envelope);
            await WriteLineAsync(line, cancellationToken).ConfigureAwait(false);

            using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeoutCts.CancelAfter(timeout);
            await using var reg = timeoutCts.Token.Register(() => tcs.TrySetCanceled(timeoutCts.Token));

            var element = await tcs.Task.ConfigureAwait(false);

            if (element.ValueKind == JsonValueKind.Undefined || element.ValueKind == JsonValueKind.Null)
            {
                return default;
            }

            // Response data: { id, ok, error?, result? }
            if (element.TryGetProperty("ok", out var okProp) && okProp.ValueKind == JsonValueKind.False)
            {
                var err = element.TryGetProperty("error", out var ep) ? ep.GetString() : "request failed";
                throw new InvalidOperationException(err ?? "request failed");
            }

            if (element.TryGetProperty("result", out var resultProp))
            {
                return resultProp.Deserialize<T>(JsonOpts);
            }

            return element.Deserialize<T>(JsonOpts);
        }
        finally
        {
            _pending.TryRemove(id, out _);
        }
    }

    private async Task WriteLineAsync(string line, CancellationToken ct)
    {
        StreamWriter? writer;
        lock (_writeGate)
        {
            writer = _activeWriter;
        }
        if (writer == null)
        {
            throw new InvalidOperationException("No connected RuneLite client");
        }

        // StreamWriter is not fully thread-safe; serialize writes
        lock (_writeGate)
        {
            writer.WriteLine(line);
            writer.Flush();
        }
        await Task.CompletedTask.ConfigureAwait(false);
    }

    private async Task AcceptLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _listener != null)
        {
            TcpClient client;
            try
            {
                client = await _listener.AcceptTcpClientAsync(ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (SocketException) { break; }

            _ = Task.Run(() => HandleClientAsync(client, ct), ct);
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken ct)
    {
        Interlocked.Increment(ref _clientCount);
        _dispatcher.RaiseClientConnected();
        try
        {
            client.NoDelay = true;
            await using var stream = client.GetStream();
            using var reader = new StreamReader(stream, Encoding.UTF8);
            await using var writer = new StreamWriter(stream, Encoding.UTF8) { AutoFlush = true };

            lock (_writeGate)
            {
                _activeWriter = writer;
            }

            while (!ct.IsCancellationRequested && client.Connected)
            {
                string? line;
                try
                {
                    line = await reader.ReadLineAsync(ct).ConfigureAwait(false);
                }
                catch (OperationCanceledException)
                {
                    break;
                }

                if (line == null)
                {
                    break;
                }

                line = line.Trim();
                if (line.Length == 0)
                {
                    continue;
                }

                try
                {
                    using var doc = JsonDocument.Parse(line);
                    var root = doc.RootElement;
                    var type = root.TryGetProperty("type", out var t) ? t.GetString() : null;
                    if (string.Equals(type, "Response", StringComparison.OrdinalIgnoreCase))
                    {
                        HandleResponse(root);
                        continue;
                    }

                    var envelope = JsonSerializer.Deserialize<BridgeEnvelope>(line);
                    if (envelope != null && !string.IsNullOrEmpty(envelope.Type))
                    {
                        _dispatcher.Dispatch(envelope);
                    }
                }
                catch (JsonException)
                {
                    // ignore malformed
                }
            }
        }
        finally
        {
            lock (_writeGate)
            {
                if (_activeWriter != null)
                {
                    _activeWriter = null;
                }
            }
            Interlocked.Decrement(ref _clientCount);
            _dispatcher.RaiseClientDisconnected();
            client.Dispose();
        }
    }

    private void HandleResponse(JsonElement root)
    {
        if (!root.TryGetProperty("data", out var data))
        {
            return;
        }

        var id = data.TryGetProperty("id", out var idProp) ? idProp.GetString() : null;
        if (string.IsNullOrEmpty(id))
        {
            return;
        }

        if (_pending.TryRemove(id, out var tcs))
        {
            // Pass the data object (includes ok/result/error)
            tcs.TrySetResult(data.Clone());
        }
    }

    public async ValueTask DisposeAsync() => await StopAsync().ConfigureAwait(false);

    public void Dispose() => Stop();
}

public sealed class RuneBridgeOptions
{
    public int Port { get; set; } = 17473;
    public string BindAddress { get; set; } = "127.0.0.1";
}

internal sealed class WorldToScreenResponse
{
    public List<WorldTile>? Tiles { get; set; }
}
