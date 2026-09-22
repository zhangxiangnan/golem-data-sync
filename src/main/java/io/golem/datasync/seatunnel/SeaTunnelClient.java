package io.golem.datasync.seatunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.golem.datasync.config.DataSyncProperties;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SeaTunnelClient {
    private final WebClient client;
    private final Duration timeout;
    private final String baseUrl;

    public SeaTunnelClient(DataSyncProperties properties, WebClient.Builder builder) {
        this.baseUrl = properties.seatunnel().baseUrl();
        this.timeout = properties.seatunnel().requestTimeout();
        this.client = builder.baseUrl(baseUrl).build();
    }

    public String submit(ObjectNode config, String runId, String jobName) {
        JsonNode response = client.post()
                .uri(uri -> uri.path("/submit-job")
                        .queryParam("jobId", numericJobId(runId))
                        .queryParam("jobName", jobName)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(config)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(timeout);
        JsonNode submitted = response != null && response.isArray() ? response.path(0) : response;
        if (submitted == null || !submitted.hasNonNull("jobId") || submitted.path("jobId").asText().isBlank()) {
            throw new IllegalStateException("SeaTunnel did not return a jobId");
        }
        return submitted.path("jobId").asText();
    }

    public SeaTunnelJobInfo jobInfo(String jobId) {
        JsonNode response = client.get()
                .uri("/job-info/{jobId}", jobId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(timeout);
        if (response == null || !response.hasNonNull("jobId") || response.path("jobId").asText().isBlank()) {
            throw new IllegalStateException("SeaTunnel job was not found: " + jobId);
        }
        JsonNode metrics = response.path("metrics");
        return new SeaTunnelJobInfo(
                response.path("jobId").asText(),
                response.path("jobName").asText(),
                response.path("jobStatus").asText(),
                longMetric(metrics, "SourceReceivedCount"),
                longMetric(metrics, "SinkWriteCount"),
                doubleMetric(metrics, "SourceReceivedQPS"),
                doubleMetric(metrics, "SinkWriteQPS"),
                longMetric(metrics, "SourceReceivedBytes"),
                longMetric(metrics, "SinkWriteBytes"),
                response.path("errorMsg").isNull() ? null : response.path("errorMsg").asText());
    }

    public void stop(String jobId) {
        ObjectNode body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        body.put("jobId", Long.parseLong(jobId));
        body.put("isStopWithSavePoint", false);
        client.post().uri("/stop-job")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block(timeout);
    }

    public JsonNode overview() {
        return client.get().uri("/overview").retrieve().bodyToMono(JsonNode.class).block(timeout);
    }

    public String baseUrl() {
        return baseUrl;
    }

    private String numericJobId(String runId) {
        long value = 17;
        String input = runId.replace("-", "").substring(0, 15);
        for (int index = 0; index < input.length(); index++) {
            value = value * 31 + input.charAt(index);
        }
        long positive = value & Long.MAX_VALUE;
        return Long.toString(positive == 0 ? 1 : positive);
    }

    private long longMetric(JsonNode metrics, String name) {
        String value = metrics.path(name).asText("0");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double doubleMetric(JsonNode metrics, String name) {
        String value = metrics.path(name).asText("0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
