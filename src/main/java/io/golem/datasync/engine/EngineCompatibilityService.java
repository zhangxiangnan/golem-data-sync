package io.golem.datasync.engine;

import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.domain.EngineType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EngineCompatibilityService {
    private static final Set<EngineCapability> REQUIRED = EnumSet.of(
            EngineCapability.MYSQL_JDBC_BATCH_SINGLE_TABLE,
            EngineCapability.AUTO_CREATE_TARGET,
            EngineCapability.APPEND,
            EngineCapability.REPLACE,
            EngineCapability.STOP);

    public void validate(JobEngineClient client) {
        if (!client.capabilities().containsAll(REQUIRED)) {
            throw new RequestValidationException(
                    "Engine profile does not support the current MySQL batch synchronization chain: "
                            + client.profileId());
        }
    }

    public Set<EngineCapability> capabilities(EngineType type) {
        EnumSet<EngineCapability> capabilities = EnumSet.copyOf(REQUIRED);
        capabilities.add(EngineCapability.METRICS);
        if (type == EngineType.ZETA) {
            capabilities.add(EngineCapability.TIMER_FLUSH);
        }
        return Set.copyOf(capabilities);
    }

    public List<String> limitations(EngineType type) {
        return switch (type) {
            case ZETA -> List.of("当前仅开放 MySQL JDBC 单表批量同步");
            case SPARK -> List.of("JDBC timer flush 不生效", "当前使用本地模拟执行器，尚未连接公司 Spark 平台");
            case FLINK -> List.of("JDBC timer flush 不生效", "尚未配置 Flink 提交与观测适配器");
        };
    }
}
