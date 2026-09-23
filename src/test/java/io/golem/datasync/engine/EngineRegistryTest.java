package io.golem.datasync.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.domain.EngineType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EngineRegistryTest {
    @Test
    void resolvesDefaultAndRejectsUnknownProfile() {
        JobEngineClient zeta = client("zeta-local", EngineType.ZETA);
        EngineRegistry registry = new EngineRegistry(List.of(zeta));

        assertThat(registry.require(null)).isSameAs(zeta);
        assertThatThrownBy(() -> registry.require("missing"))
                .isInstanceOf(RequestValidationException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void rejectsDuplicateProfileIds() {
        assertThatThrownBy(() -> new EngineRegistry(List.of(
                client("same", EngineType.ZETA), client("same", EngineType.SPARK))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("same");
    }

    private JobEngineClient client(String id, EngineType type) {
        return new JobEngineClient() {
            public String profileId() { return id; }
            public EngineType engineType() { return type; }
            public String displayName() { return id; }
            public boolean mock() { return false; }
            public Set<EngineCapability> capabilities() { return Set.of(); }
            public EngineSubmission submit(GeneratedEngineConfig config, String runId, String jobName) { return null; }
            public EngineJobSnapshot jobInfo(String externalJobId) { return null; }
            public void stop(String externalJobId) {}
            public EngineHealth health() { return new EngineHealth(true, null, "ok"); }
        };
    }
}
