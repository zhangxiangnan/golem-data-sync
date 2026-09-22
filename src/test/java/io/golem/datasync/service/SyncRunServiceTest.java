package io.golem.datasync.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.golem.datasync.api.ConflictException;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.persistence.SyncRunEventMapper;
import io.golem.datasync.persistence.SyncRunEntity;
import io.golem.datasync.persistence.SyncRunMapper;
import io.golem.datasync.seatunnel.SeaTunnelClient;
import io.golem.datasync.seatunnel.SeaTunnelConfigGenerator;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.security.SecretSanitizer;
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
                mock(SeaTunnelClient.class),
                mock(CryptoService.class),
                new SecretSanitizer());

        assertThatThrownBy(() -> service.start("job-1"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Sync job already has an active run");
    }
}
