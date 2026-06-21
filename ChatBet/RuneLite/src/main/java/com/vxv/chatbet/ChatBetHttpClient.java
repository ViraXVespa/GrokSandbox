package com.vxv.chatbet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ExecutorServiceExceptionLogger;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * HTTP client for communicating with the local Stream Bet Bridge (FastAPI on localhost:8765).
 * The RuneLite plugin polls /chatbet/state for viewer chat commands/requests
 * (e.g. !odds, !bet) and can optionally push game stats via /game/update.
 *
 * Usage in ChatBetPlugin:
 *   - Inject this client
 *   - Call startPolling() in startUp()
 *   - Implement onChatRequest(CommandRequest request) to react (update overlay, etc.)
 *   - Optionally schedule pushGameState() periodically or on significant changes.
 */
@Slf4j
@Singleton
public class ChatBetHttpClient
{
    private static final String DEFAULT_BRIDGE_URL = "http://127.0.0.1:8765";
    private static final int POLL_INTERVAL_SECONDS = 2; // Adjust as needed; 1-3s is responsive without much overhead
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;

    private String bridgeBaseUrl;
    private volatile boolean running = false;
    private long lastProcessedTimestamp = 0;

    // Callback for when a new chat command/request arrives from stream chat
    private Consumer<CommandRequest> chatRequestHandler;

    // Simple DTOs matching the Python bridge responses
    public static class CommandRequest
    {
        public String user;
        public String command;
        public java.util.List<String> args;
        public long timestamp;
        public String raw_message;
    }

    public static class BridgeState
    {
        public CommandRequest active_request;
        public int recent_messages_count;
        public long last_updated;
        public java.util.Map<String, Object> game_stats;
    }

    @Inject
    public ChatBetHttpClient()
    {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
        this.scheduler = new ExecutorServiceExceptionLogger(
            Executors.newSingleThreadScheduledExecutor());
        this.bridgeBaseUrl = DEFAULT_BRIDGE_URL;
    }

    public void setBridgeUrl(String url)
    {
        this.bridgeBaseUrl = url != null && !url.isEmpty() ? url : DEFAULT_BRIDGE_URL;
    }

    public void setChatRequestHandler(Consumer<CommandRequest> handler)
    {
        this.chatRequestHandler = handler;
    }

    /**
     * Start background polling of the bridge for new chat requests.
     * Call from plugin startUp().
     */
    public void startPolling()
    {
        if (running)
        {
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(this::pollOnce, 1, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("ChatBet bridge polling started (every {}s) -> {}", POLL_INTERVAL_SECONDS, bridgeBaseUrl);
    }

    public void stopPolling()
    {
        running = false;
        scheduler.shutdownNow();
        log.info("ChatBet bridge polling stopped");
    }

    private void pollOnce()
    {
        if (!running)
        {
            return;
        }

        try
        {
            Request request = new Request.Builder()
                .url(bridgeBaseUrl + "/chatbet/state")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute())
            {
                if (!response.isSuccessful())
                {
                    log.debug("Bridge poll failed: HTTP {}", response.code());
                    return;
                }

                String body = response.body() != null ? response.body().string() : "{}";
                BridgeState state = gson.fromJson(body, BridgeState.class);

                if (state != null && state.active_request != null)
                {
                    CommandRequest req = state.active_request;
                    if (req.timestamp > lastProcessedTimestamp)
                    {
                        lastProcessedTimestamp = req.timestamp;
                        log.debug("New chat request from {}: !{} {}", req.user, req.command, req.args);

                        if (chatRequestHandler != null)
                        {
                            chatRequestHandler.accept(req);
                        }

                        // Acknowledge so bridge clears it (prevents re-processing on next polls)
                        ackRequest(req.timestamp);
                    }
                }
            }
        }
        catch (IOException e)
        {
            // Bridge not running or network issue - common during dev, log at debug
            log.debug("Failed to poll chat bet bridge: {}", e.getMessage());
        }
        catch (Exception e)
        {
            log.warn("Unexpected error polling bridge", e);
        }
    }

    private void ackRequest(long timestamp)
    {
        try
        {
            String json = gson.toJson(Map.of("processed_timestamp", timestamp));
            RequestBody body = RequestBody.create(JSON, json);
            Request request = new Request.Builder()
                .url(bridgeBaseUrl + "/chatbet/ack")
                .post(body)
                .build();

            // Fire and forget (non-blocking for game thread)
            httpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.debug("Ack failed: {}", e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    response.close();
                }
            });
        }
        catch (Exception e)
        {
            log.debug("Error sending ack", e);
        }
    }

    /**
     * Push current game/tracking stats to the bridge (optional but recommended
     * for dynamic odds, future web dashboard, or if bridge ever responds to chat queries).
     * Call this from onGameTick or when stats change significantly.
     */
    public void pushGameState(Map<String, Object> stats)
    {
        if (stats == null || stats.isEmpty())
        {
            return;
        }

        try
        {
            String json = gson.toJson(stats);
            RequestBody body = RequestBody.create(JSON, json);
            Request request = new Request.Builder()
                .url(bridgeBaseUrl + "/game/update")
                .post(body)
                .build();

            httpClient.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException e)
                {
                    log.debug("Game state push failed: {}", e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    response.close();
                }
            });
        }
        catch (Exception e)
        {
            log.debug("Error pushing game state", e);
        }
    }

    /**
     * Convenience: push common ChatBet tracking data.
     */
    public void pushChatBetStats(int attempts, int successes, int etcsObtained,
                                 double successRate, double etcProbability,
                                 int xpToGoal, int elvesToThirtyPct, boolean etcGoalActive)
    {
        Map<String, Object> stats = new HashMap<>();
        stats.put("attempts", attempts);
        stats.put("successes", successes);
        stats.put("etcsObtained", etcsObtained);
        stats.put("successRate", successRate);
        stats.put("etcProbability", etcProbability);
        stats.put("xpToGoal", xpToGoal);
        stats.put("elvesToThirtyPct", elvesToThirtyPct);
        stats.put("etcGoalActive", etcGoalActive);
        stats.put("updatedAt", Instant.now().toEpochMilli());
        pushGameState(stats);
    }

    public boolean isRunning()
    {
        return running;
    }
}
