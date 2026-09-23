package io.golem.datasync.engine;

import io.golem.datasync.domain.EngineType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SparkEngineClient implements JobEngineClient {
    private final SparkPlatformClient client;
    private final EngineCompatibilityService compatibility;

    public SparkEngineClient(SparkPlatformClient client, EngineCompatibilityService compatibility) {
        this.client = client;
        this.compatibility = compatibility;
    }

    @Override public String profileId() { return "spark-local-mock"; }
    @Override public EngineType engineType() { return EngineType.SPARK; }
    @Override public String displayName() { return "Spark 本地模拟"; }
    @Override public boolean mock() { return true; }
    @Override public Set<EngineCapability> capabilities() { return compatibility.capabilities(engineType()); }
    @Override public EngineSubmission submit(GeneratedEngineConfig config, String runId, String jobName) {
        return client.submit(config, runId, jobName);
    }
    @Override public EngineJobSnapshot jobInfo(String externalJobId) { return client.jobInfo(externalJobId); }
    @Override public void stop(String externalJobId) { client.stop(externalJobId); }
    @Override public EngineHealth health() { return client.health(); }
}
