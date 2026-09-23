package io.golem.datasync.service;

import io.golem.datasync.api.ApiModels.EngineCapabilityResponse;
import io.golem.datasync.api.ApiModels.EngineProfileResponse;
import io.golem.datasync.domain.EngineType;
import io.golem.datasync.engine.EngineCompatibilityService;
import io.golem.datasync.engine.EngineHealth;
import io.golem.datasync.engine.EngineRegistry;
import io.golem.datasync.engine.JobEngineClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EngineCatalogService {
    private final EngineRegistry registry;
    private final EngineCompatibilityService compatibility;

    public EngineCatalogService(EngineRegistry registry, EngineCompatibilityService compatibility) {
        this.registry = registry;
        this.compatibility = compatibility;
    }

    public List<EngineProfileResponse> profiles() {
        List<EngineProfileResponse> profiles = new ArrayList<>(registry.clients().stream()
                .map(this::profile)
                .sorted(Comparator.comparing(EngineProfileResponse::id))
                .toList());
        profiles.add(new EngineProfileResponse(
                "flink-unconfigured", "Flink（未配置）", EngineType.FLINK,
                false, false, false, null, "尚未配置 Flink 提交与观测适配器",
                names(compatibility.capabilities(EngineType.FLINK))));
        return List.copyOf(profiles);
    }

    public List<EngineCapabilityResponse> capabilities() {
        return List.of(EngineType.ZETA, EngineType.SPARK, EngineType.FLINK).stream()
                .map(type -> new EngineCapabilityResponse(
                        type,
                        type != EngineType.FLINK,
                        names(compatibility.capabilities(type)),
                        compatibility.limitations(type)))
                .toList();
    }

    private EngineProfileResponse profile(JobEngineClient client) {
        EngineHealth health;
        try {
            health = client.health();
        } catch (Exception exception) {
            health = new EngineHealth(false, null, "执行器健康检查失败");
        }
        return new EngineProfileResponse(
                client.profileId(), client.displayName(), client.engineType(), true, client.mock(),
                health.online(), health.version(), health.message(), names(client.capabilities()));
    }

    private List<String> names(java.util.Set<?> values) {
        return values.stream().map(Object::toString).sorted().toList();
    }
}
