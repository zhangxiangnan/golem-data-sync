package io.golem.datasync.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.golem.datasync.domain.RunStatus;
import org.junit.jupiter.api.Test;

class MockSparkPlatformClientTest {
    private final MockSparkPlatformClient client = new MockSparkPlatformClient();
    private final GeneratedEngineConfig config = new GeneratedEngineConfig("hocon", "env { job.mode = BATCH }");

    @Test
    void advancesThroughRunningAndSuccessWithMetrics() {
        String id = client.submit(config, "run-1", "orders").externalJobId();
        assertThat(client.jobInfo(id).status()).isEqualTo(RunStatus.RUNNING);
        EngineJobSnapshot finished = client.jobInfo(id);
        assertThat(finished.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(finished.sinkWriteCount()).isEqualTo(1000);
    }

    @Test
    void supportsStopAndFailureInjection() {
        String stopped = client.submit(config, "run-2", "orders").externalJobId();
        client.stop(stopped);
        assertThat(client.jobInfo(stopped).status()).isEqualTo(RunStatus.CANCELED);

        String failed = client.submit(config, "run-3", "mock-fail-orders").externalJobId();
        assertThat(client.jobInfo(failed).status()).isEqualTo(RunStatus.RUNNING);
        assertThat(client.jobInfo(failed).status()).isEqualTo(RunStatus.FAILED);
    }
}
