package io.golem.datasync.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.golem.datasync.domain.EngineType;
import io.golem.datasync.seatunnel.SeaTunnelClient;
import io.golem.datasync.seatunnel.SeaTunnelJobInfo;
import io.golem.datasync.seatunnel.SeaTunnelStatusMapper;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ZetaEngineClient implements JobEngineClient {
    private final SeaTunnelClient client;
    private final ObjectMapper objectMapper;
    private final EngineCompatibilityService compatibility;

    public ZetaEngineClient(
            SeaTunnelClient client, ObjectMapper objectMapper, EngineCompatibilityService compatibility) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.compatibility = compatibility;
    }

    @Override public String profileId() { return "zeta-local"; }
    @Override public EngineType engineType() { return EngineType.ZETA; }
    @Override public String displayName() { return "本机 Zeta"; }
    @Override public boolean mock() { return false; }
    @Override public Set<EngineCapability> capabilities() { return compatibility.capabilities(engineType()); }

    @Override
    public EngineSubmission submit(GeneratedEngineConfig config, String runId, String jobName) {
        if (!"json".equals(config.format())) {
            throw new IllegalArgumentException("Zeta requires JSON configuration");
        }
        try {
            ObjectNode json = (ObjectNode) objectMapper.readTree(config.content());
            return new EngineSubmission(client.submit(json, runId, jobName), null);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Unable to parse Zeta configuration", exception);
        }
    }

    @Override
    public EngineJobSnapshot jobInfo(String externalJobId) {
        SeaTunnelJobInfo info = client.jobInfo(externalJobId);
        return new EngineJobSnapshot(
                SeaTunnelStatusMapper.map(info.status()),
                info.sourceReadCount(), info.sinkWriteCount(), info.sourceQps(), info.sinkQps(),
                info.sourceBytes(), info.sinkBytes(), info.errorMessage());
    }

    @Override public void stop(String externalJobId) { client.stop(externalJobId); }

    @Override
    public EngineHealth health() {
        try {
            var overview = client.overview();
            String version = overview == null ? null : overview.path("projectVersion").asText(null);
            if (version == null && overview != null) version = overview.path("version").asText(null);
            return new EngineHealth(true, version, "SeaTunnel Zeta 连接正常");
        } catch (Exception exception) {
            return new EngineHealth(false, null, "无法连接 SeaTunnel Zeta");
        }
    }
}
