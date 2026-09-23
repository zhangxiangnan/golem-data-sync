package io.golem.datasync.engine;

/** 公司 Spark 平台适配边界；拿到真实 API 契约后替换实现即可。 */
public interface SparkPlatformClient {
    EngineSubmission submit(GeneratedEngineConfig config, String runId, String jobName);
    EngineJobSnapshot jobInfo(String externalJobId);
    void stop(String externalJobId);
    EngineHealth health();
}
