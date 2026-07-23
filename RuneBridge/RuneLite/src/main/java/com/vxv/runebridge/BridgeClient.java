package com.vxv.runebridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/**
 * Duplex NDJSON-over-TCP client.
 * Outbound: game events. Inbound: C# requests (e.g. FindNearest) → JSON responses.
 */
@Slf4j
final class BridgeClient implements AutoCloseable
{
	private final Gson gson;
	private final String host;
	private final int port;
	private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(2048);
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread worker;
	private final ExecutorService requestPool = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "runebridge-requests");
		t.setDaemon(true);
		return t;
	});

	/** Handles request data JSON → response data object (run off client thread if needed). */
	private volatile Function<JsonObject, Object> requestHandler;

	BridgeClient(Gson gson, String host, int port)
	{
		this.gson = gson;
		this.host = host;
		this.port = port;
	}

	void setRequestHandler(Function<JsonObject, Object> handler)
	{
		this.requestHandler = handler;
	}

	void start()
	{
		if (!running.compareAndSet(false, true))
		{
			return;
		}
		worker = new Thread(this::runLoop, "runebridge-tcp");
		worker.setDaemon(true);
		worker.start();
		emit("ClientHello", Map.of(
			"plugin", "RuneBridge",
			"protocol", 2,
			"features", java.util.List.of("events", "FindNearest")
		));
	}

	void emit(String type, Object data)
	{
		if (!running.get())
		{
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("v", 1);
		root.addProperty("type", type);
		root.addProperty("ts", System.currentTimeMillis());
		root.add("data", gson.toJsonTree(data == null ? Map.of() : data));
		enqueue(gson.toJson(root));
	}

	void emitResponse(String requestId, boolean ok, Object data, String error)
	{
		JsonObject root = new JsonObject();
		root.addProperty("v", 1);
		root.addProperty("type", "Response");
		root.addProperty("ts", System.currentTimeMillis());
		JsonObject body = new JsonObject();
		body.addProperty("id", requestId);
		body.addProperty("ok", ok);
		if (error != null)
		{
			body.addProperty("error", error);
		}
		if (data != null)
		{
			body.add("result", gson.toJsonTree(data));
		}
		root.add("data", body);
		enqueue(gson.toJson(root));
	}

	private void enqueue(String line)
	{
		if (!queue.offer(line))
		{
			queue.poll();
			queue.offer(line);
			log.debug("RuneBridge queue full — dropped oldest event");
		}
	}

	private void runLoop()
	{
		while (running.get())
		{
			try (Socket socket = new Socket())
			{
				socket.connect(new InetSocketAddress(host, port), 3000);
				socket.setTcpNoDelay(true);
				log.info("RuneBridge connected to {}:{}", host, port);
				try (BufferedWriter out = new BufferedWriter(
					new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
					BufferedReader in = new BufferedReader(
						new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)))
				{
					// reader thread
					Thread reader = new Thread(() -> readRequests(in), "runebridge-read");
					reader.setDaemon(true);
					reader.start();

					while (running.get() && socket.isConnected() && !socket.isClosed())
					{
						String line = queue.poll(500, TimeUnit.MILLISECONDS);
						if (line == null)
						{
							continue;
						}
						out.write(line);
						out.write('\n');
						out.flush();
					}
					reader.interrupt();
				}
			}
			catch (IOException e)
			{
				if (running.get())
				{
					log.debug("RuneBridge disconnected ({}). Reconnecting in 2s…", e.getMessage());
					try
					{
						Thread.sleep(2000);
					}
					catch (InterruptedException ie)
					{
						Thread.currentThread().interrupt();
						return;
					}
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void readRequests(BufferedReader in)
	{
		try
		{
			String line;
			while (running.get() && (line = in.readLine()) != null)
			{
				line = line.trim();
				if (line.isEmpty())
				{
					continue;
				}
				handleInbound(line);
			}
		}
		catch (IOException e)
		{
			log.debug("RuneBridge read ended: {}", e.getMessage());
		}
	}

	private void handleInbound(String line)
	{
		try
		{
			JsonObject root = new JsonParser().parse(line).getAsJsonObject();
			String type = root.has("type") ? root.get("type").getAsString() : "";
			if (!"Request".equals(type))
			{
				return;
			}
			JsonObject data = root.has("data") && root.get("data").isJsonObject()
				? root.getAsJsonObject("data") : new JsonObject();
			String id = data.has("id") ? data.get("id").getAsString() : null;
			if (id == null)
			{
				return;
			}
			Function<JsonObject, Object> handler = requestHandler;
			if (handler == null)
			{
				emitResponse(id, false, null, "No request handler registered");
				return;
			}
			// Dispatch off reader thread
			requestPool.execute(() ->
			{
				try
				{
					Object result = handler.apply(data);
					emitResponse(id, true, result, null);
				}
				catch (Exception ex)
				{
					log.warn("Request failed: {}", ex.getMessage());
					emitResponse(id, false, null, ex.getMessage() != null ? ex.getMessage() : "error");
				}
			});
		}
		catch (Exception e)
		{
			log.debug("Bad inbound JSON: {}", e.getMessage());
		}
	}

	@Override
	public void close()
	{
		running.set(false);
		if (worker != null)
		{
			worker.interrupt();
		}
		requestPool.shutdownNow();
	}
}
