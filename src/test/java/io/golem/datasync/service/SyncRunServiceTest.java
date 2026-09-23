package io.golem.datasync.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.golem.datasync.api.ConflictException;
import io.golem.datasync.engine.EngineCompatibilityService;
import io.golem.datasync.engine.EngineJobSnapshot;
import io.golem.datasync.engine.EngineRegistry;
import io.golem.datasync.engine.JobEngineClient;
import io.golem.datasync.domain.RunStatus;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.persistence.SyncRunEventMapper;
import io.golem.datasync.persistence.SyncRunEntity;
import io.golem.datasync.persistence.SyncRunMapper;
import io.golem.datasync.seatunnel.SeaTunnelConfigGenerator;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.security.SecretSanitizer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncRunServiceTest {
    @Test
    void rejectsStartingASecondActiveRunForTheSameJob() {
        SyncRunMapper runMapper = mock(SyncRunMapper.class);
        SyncJobService jobService = mock(SyncJobService.class);
        SyncJobEntity job = new SyncJobEntity();
        job.id = "job-1";
        when(jobService.require("job-1")).thenReturn(job);
        when(runMapper.selectCount(org.mockito.ArgumentMatchers.<Wrapper<SyncRunEntity>>any())).thenReturn(1L);

        SyncRunService service = new SyncRunService(
                runMapper,
                mock(SyncRunEventMapper.class),
                mock(SyncJobMapper.class),
                jobService,
                mock(DataSourceService.class),
                mock(SeaTunnelConfigGenerator.class),
                mock(EngineRegistry.class),
                mock(EngineCompatibilityService.class),
                mock(CryptoService.class),
                new SecretSanitizer());

        assertThatThrownBy(() -> service.start("job-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sync job already has an active run");
    }

    @Test
    void resumesPollingWithTheProfileStoredOnTheRun() {
        SyncRunMapper runMapper = mock(SyncRunMapper.class);
        EngineRegistry registry = mock(EngineRegistry.class);
        JobEngineClient spark = mock(JobEngineClient.class);
        SyncRunEntity run = new SyncRunEntity();
        run.id = "run-1";
        run.jobId = "job-1";
        run.engineProfileId = "spark-local-mock";
        run.externalJobId = "spark-run-1";
        run.status = RunStatus.PENDING.name();
        run.createdAt = LocalDateTime.now();
        run.updatedAt = run.createdAt;
        when(runMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(run));
        when(registry.require("spark-local-mock")).thenReturn(spark);
        when(spark.jobInfo("spark-run-1")).thenReturn(new EngineJobSnapshot(
                RunStatus.RUNNING, 10L, 8L, 5D, 4D, 100L, 80L, null));

        SyncRunService service = new SyncRunService(
                runMapper,
                mock(SyncRunEventMapper.class),
                mock(SyncJobMapper.class),
                mock(SyncJobService.class),
                mock(DataSourceService.class),
                mock(SeaTunnelConfigGenerator.class),
                registry,
                mock(EngineCompatibilityService.class),
                mock(CryptoService.class),
                new SecretSanitizer());

        service.reconcileActiveRuns();

        verify(registry).require("spark-local-mock");
        verify(spark).jobInfo("spark-run-1");
        verify(runMapper).updateById(run);
        org.assertj.core.api.Assertions.assertThat(run.status).isEqualTo(RunStatus.RUNNING.name());
        org.assertj.core.api.Assertions.assertThat(run.sourceReadCount).isEqualTo(10L);
    }
}
