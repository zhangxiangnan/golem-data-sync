package io.golem.datasync.engine;

import io.golem.datasync.domain.EngineType;
import java.util.Set;

public interface JobEngineClient {
    String profileId();
    EngineType engineType();
    String displayName();
    boolean mock();
    Set<EngineCapability> capabilities();
    EngineSubmission submit(GeneratedEngineConfig config, String runId, String jobName);
    EngineJobSnapshot jobInfo(String externalJobId);
    void stop(String externalJobId);
    EngineHealth health();
}
