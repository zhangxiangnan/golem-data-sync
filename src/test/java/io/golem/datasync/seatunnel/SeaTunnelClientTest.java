package io.golem.datasync.seatunnel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.golem.datasync.config.DataSyncProperties;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class SeaTunnelClientTest {
    private MockWebServer server;
    private SeaTunnelClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer(); server.start();
        client = new SeaTunnelClient(new DataSyncProperties(null,
                new DataSyncProperties.SeaTunnel(server.url("/").toString(), Duration.ofSeconds(2), Duration.ofSeconds(2))),
                WebClient.builder());
    }

    @AfterEach void tearDown() throws Exception { server.shutdown(); }

    @Test
    void submitsAndReadsMetrics() throws Exception {
        server.enqueue(json("[{\"jobId\":733584788375666689,\"jobName\":\"demo\"}]"));
        ObjectNode config = new ObjectMapper().createObjectNode(); config.putObject("env").put("job.mode", "BATCH");
        assertThat(client.submit(config, "12345678-1234-1234-1234-123456789012", "demo"))
                .isEqualTo("733584788375666689");
        assertThat(server.takeRequest().getPath()).startsWith("/submit-job?");

        server.enqueue(json("{\"jobId\":\"733584788375666689\",\"jobName\":\"demo\",\"jobStatus\":\"RUNNING\",\"metrics\":{\"SourceReceivedCount\":\"42\",\"SinkWriteCount\":\"40\",\"SourceReceivedQPS\":\"12.5\",\"SinkWriteQPS\":\"11.5\"},\"errorMsg\":null}"));
        SeaTunnelJobInfo info = client.jobInfo("733584788375666689");
        assertThat(info.status()).isEqualTo("RUNNING");
        assertThat(info.sourceReadCount()).isEqualTo(42);
        assertThat(info.sinkWriteCount()).isEqualTo(40);
    }

    @Test
    void sendsStopRequest() throws Exception {
        server.enqueue(json("{\"jobId\":\"1\"}"));
        client.stop("1");
        var request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/stop-job");
        assertThat(request.getBody().readUtf8()).contains("\"jobId\":1").doesNotContain("\"jobId\":\"1\"");
    }

    private MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
