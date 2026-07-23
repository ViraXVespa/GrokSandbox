package com.vxv.chatbet.discord;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.vxv.chatbet.bet.BetType;
import com.vxv.chatbet.bet.Poll;

/**
 * Thin HTTP client for the ChatBet Discord hub (default http://127.0.0.1:8766).
 * Best-effort: failures are logged and ignored so the plugin still works offline.
 */
@Slf4j
public class DiscordHubClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    public DiscordHubClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
            ? "http://127.0.0.1:8766"
            : baseUrl.replaceAll("/$", "");
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public void publishPoll(Poll poll) {
        if (poll == null) {
            return;
        }
        String optionsJson = poll.getOptions().stream()
            .map(this::jsonString)
            .collect(Collectors.joining(","));
        String body = "{"
            + "\"id\":" + poll.getId() + ","
            + "\"question\":" + jsonString(poll.getQuestion()) + ","
            + "\"bet_type\":" + jsonString(poll.getType() != null ? poll.getType().name() : BetType.MULTIPLE_CHOICE.name()) + ","
            + "\"options\":[" + optionsJson + "],"
            + "\"resolution_trigger\":" + (poll.getResolutionTrigger() == null ? "null" : jsonString(poll.getResolutionTrigger())) + ","
            + "\"source\":" + jsonString("runelite")
            + "}";
        post("/api/v1/polls", body);
    }

    public void resolvePoll(int pollId, int winningOptionIndex) {
        String body = "{\"winning_option_index\":" + winningOptionIndex + "}";
        post("/api/v1/polls/" + pollId + "/resolve", body);
    }

    public void placeBet(String username, int pollId, String option, long amount) {
        String body = "{"
            + "\"username\":" + jsonString(username) + ","
            + "\"poll_id\":" + pollId + ","
            + "\"option\":" + jsonString(option) + ","
            + "\"amount\":" + amount + ","
            + "\"source\":" + jsonString("runelite")
            + "}";
        post("/api/v1/bets", body);
    }

    public void resolveTrigger(String trigger, int winningOptionIndex) {
        if (trigger == null || trigger.isBlank()) {
            return;
        }
        String path = "/api/v1/polls/resolve-trigger?trigger="
            + URLEncoder.encode(trigger, StandardCharsets.UTF_8)
            + "&winning_option_index=" + winningOptionIndex;
        post(path, "{}");
    }

    private void post(String path, String jsonBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
            if (!apiKey.isBlank()) {
                builder.header("X-Api-Key", apiKey);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.debug("[DiscordHub] {} → HTTP {} {}", path, response.statusCode(), response.body());
            } else {
                log.debug("[DiscordHub] {} OK", path);
            }
        } catch (Exception e) {
            log.debug("[DiscordHub] {} failed (hub offline?): {}", path, e.getMessage());
        }
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    /** Convenience for tests / future batch publish. */
    public void publishPolls(List<Poll> polls) {
        if (polls == null) {
            return;
        }
        for (Poll poll : polls) {
            publishPoll(poll);
        }
    }
}
