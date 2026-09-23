package io.golem.datasync.engine;

import io.golem.datasync.domain.RunStatus;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class MockSparkPlatformClient implements SparkPlatformClient {
    private final Map<String, MockJob> jobs = new ConcurrentHashMap<>();

    @Override
    public EngineSubmission submit(GeneratedEngineConfig config, String runId, String jobName) {
        if (!"hocon".equals(config.format())) {
            throw new IllegalArgumentException("Spark requires HOCON configuration");
        }
        String id = "spark-mock-" + UUID.randomUUID();
        jobs.put(id, new MockJob(jobName != null && jobName.toLowerCase().contains("mock-fail")));
        return new EngineSubmission(id, null);
    }

    @Override
    public EngineJobSnapshot jobInfo(String externalJobId) {
        MockJob job = jobs.get(externalJobId);
        if (job == null) throw new IllegalStateException("Mock Spark job was not found: " + externalJobId);
        if (job.canceled) return snapshot(RunStatus.CANCELED, 0, null);
        int poll = job.polls.incrementAndGet();
        if (poll == 1) return snapshot(RunStatus.RUNNING, 320, null);
        if (job.fail) return snapshot(RunStatus.FAILED, 640, "Mock Spark failure requested by job name");
        return snapshot(RunStatus.SUCCEEDED, 1000, null);
    }

    @Override
    public void stop(String externalJobId) {
        MockJob job = jobs.get(externalJobId);
        if (job == null) throw new IllegalStateException("Mock Spark job was not found: " + externalJobId);
        job.canceled = true;
    }

    @Override public EngineHealth health() { return new EngineHealth(true, "mock", "Spark 本地模拟执行器可用"); }

    private EngineJobSnapshot snapshot(RunStatus status, long rows, String error) {
        double qps = status == RunStatus.RUNNING ? 320D : 0D;
        return new EngineJobSnapshot(status, rows, rows, qps, qps, rows * 128, rows * 128, error);
    }

    private static final class MockJob {
        private final AtomicInteger polls = new AtomicInteger();
        private final boolean fail;
        private volatile boolean canceled;
        private MockJob(boolean fail) { this.fail = fail; }
    }
}
