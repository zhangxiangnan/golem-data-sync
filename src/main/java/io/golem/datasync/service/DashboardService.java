package io.golem.datasync.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.golem.datasync.api.ApiModels.DashboardSummary;
import io.golem.datasync.api.ApiModels.SyncRunResponse;
import io.golem.datasync.api.ApiModels.SystemStatusResponse;
import io.golem.datasync.domain.RunStatus;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.DataSourceConfigMapper;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.persistence.SyncRunEntity;
import io.golem.datasync.persistence.SyncRunMapper;
import io.golem.datasync.seatunnel.SeaTunnelClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final DataSourceConfigMapper dataSourceMapper;
    private final SyncJobMapper jobMapper;
    private final SyncRunMapper runMapper;
    private final SyncRunService runService;
    private final SeaTunnelClient seaTunnelClient;
    private final EngineCatalogService engineCatalogService;

    public DashboardService(
            DataSourceConfigMapper dataSourceMapper,
            SyncJobMapper jobMapper,
            SyncRunMapper runMapper,
            SyncRunService runService,
            SeaTunnelClient seaTunnelClient,
            EngineCatalogService engineCatalogService) {
        this.dataSourceMapper = dataSourceMapper;
        this.jobMapper = jobMapper;
        this.runMapper = runMapper;
        this.runService = runService;
        this.seaTunnelClient = seaTunnelClient;
        this.engineCatalogService = engineCatalogService;
    }

    public DashboardSummary summary() {
        long sources = dataSourceMapper.selectCount(Wrappers.<DataSourceConfigEntity>lambdaQuery());
        long jobs = jobMapper.selectCount(Wrappers.<SyncJobEntity>lambdaQuery().eq(SyncJobEntity::getArchived, false));
        long running = runMapper.selectCount(Wrappers.<SyncRunEntity>lambdaQuery()
                .in(SyncRunEntity::getStatus, List.of("SUBMITTING", "PENDING", "RUNNING", "STOPPING")));
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long succeeded = runMapper.selectCount(Wrappers.<SyncRunEntity>lambdaQuery()
                .eq(SyncRunEntity::getStatus, RunStatus.SUCCEEDED.name())
                .ge(SyncRunEntity::getFinishedAt, today));
        long failed = runMapper.selectCount(Wrappers.<SyncRunEntity>lambdaQuery()
                .eq(SyncRunEntity::getStatus, RunStatus.FAILED.name())
                .ge(SyncRunEntity::getFinishedAt, today));
        long total = succeeded + failed;
        double successRate = total == 0 ? 0 : (succeeded * 100D / total);
        List<SyncRunResponse> recent = runService.list(null, 8);
        return new DashboardSummary(sources, jobs, running, succeeded, failed, successRate, recent);
    }

    public SystemStatusResponse systemStatus() {
        LocalDateTime now = LocalDateTime.now();
        var profiles = engineCatalogService.profiles();
        var zeta = profiles.stream().filter(profile -> profile.engineType() == io.golem.datasync.domain.EngineType.ZETA)
                .findFirst().orElse(null);
        boolean online = profiles.stream().anyMatch(profile -> profile.enabled() && profile.online());
        return new SystemStatusResponse(
                online,
                seaTunnelClient.baseUrl(),
                zeta == null ? null : zeta.version(),
                online ? "至少一个执行配置可用" : "没有可用的执行配置",
                now,
                profiles);
    }
}
