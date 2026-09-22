package io.golem.datasync.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.golem.datasync.api.ApiModels.JobValidationResponse;
import io.golem.datasync.api.ApiModels.RunEventResponse;
import io.golem.datasync.api.ApiModels.SyncRunResponse;
import io.golem.datasync.api.ConflictException;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.api.ResourceNotFoundException;
import io.golem.datasync.domain.RunStatus;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.persistence.SyncRunEntity;
import io.golem.datasync.persistence.SyncRunEventEntity;
import io.golem.datasync.persistence.SyncRunEventMapper;
import io.golem.datasync.persistence.SyncRunMapper;
import io.golem.datasync.seatunnel.SeaTunnelClient;
import io.golem.datasync.seatunnel.SeaTunnelConfigGenerator;
import io.golem.datasync.seatunnel.SeaTunnelJobInfo;
import io.golem.datasync.seatunnel.SeaTunnelStatusMapper;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.security.SecretSanitizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncRunService {
    private static final Logger log = LoggerFactory.getLogger(SyncRunService.class);
    private static final List<String> ACTIVE_STATUSES = List.of(
            RunStatus.SUBMITTING.name(), RunStatus.PENDING.name(), RunStatus.RUNNING.name(), RunStatus.STOPPING.name());
    private final SyncRunMapper runMapper;
    private final SyncRunEventMapper eventMapper;
    private final SyncJobMapper jobMapper;
    private final SyncJobService jobService;
    private final DataSourceService dataSourceService;
    private final SeaTunnelConfigGenerator configGenerator;
    private final SeaTunnelClient seaTunnelClient;
    private final CryptoService cryptoService;
    private final SecretSanitizer sanitizer;

    public SyncRunService(
            SyncRunMapper runMapper,
            SyncRunEventMapper eventMapper,
            SyncJobMapper jobMapper,
            SyncJobService jobService,
            DataSourceService dataSourceService,
            SeaTunnelConfigGenerator configGenerator,
            SeaTunnelClient seaTunnelClient,
            CryptoService cryptoService,
            SecretSanitizer sanitizer) {
        this.runMapper = runMapper;
        this.eventMapper = eventMapper;
        this.jobMapper = jobMapper;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
        this.configGenerator = configGenerator;
        this.seaTunnelClient = seaTunnelClient;
        this.cryptoService = cryptoService;
        this.sanitizer = sanitizer;
    }

    public synchronized SyncRunResponse start(String jobId) {
        SyncJobEntity job = jobService.require(jobId);
        ensureNoActiveRun(jobId);
        JobValidationResponse validation = jobService.validateEntity(job);
        if (!validation.valid()) {
            String detail = validation.issues().stream()
                    .filter(issue -> "ERROR".equals(issue.level()))
                    .map(issue -> issue.message())
                    .findFirst().orElse("同步任务校验失败");
            throw new RequestValidationException(detail);
        }

        DataSourceConfigEntity source = dataSourceService.require(job.sourceDataSourceId);
        DataSourceConfigEntity target = dataSourceService.require(job.targetDataSourceId);
        LocalDateTime now = LocalDateTime.now();
        SyncRunEntity run = new SyncRunEntity();
        run.id = UUID.randomUUID().toString();
        run.jobId = job.id;
        run.status = RunStatus.SUBMITTING.name();
        run.sourceReadCount = 0L;
        run.sinkWriteCount = 0L;
        run.sourceQps = 0D;
        run.sinkQps = 0D;
        run.sourceBytes = 0L;
        run.sinkBytes = 0L;
        run.configSnapshot = configGenerator.pretty(configGenerator.generate(job, source, target, true));
        run.createdAt = now;
        run.updatedAt = now;
        runMapper.insert(run);
        addEvent(run.id, RunStatus.SUBMITTING, "正在向 SeaTunnel 提交任务");

        try {
            String seatunnelJobId = seaTunnelClient.submit(
                    configGenerator.generate(job, source, target, false), run.id, job.name);
            run.seatunnelJobId = seatunnelJobId;
            transition(run, RunStatus.PENDING, "SeaTunnel 已接收任务，等待调度");
        } catch (Exception exception) {
            String error = sanitize(exception, source, target);
            run.errorMessage = error;
            run.finishedAt = LocalDateTime.now();
            transition(run, RunStatus.FAILED, "提交失败：" + error);
            log.warn("SeaTunnel submission failed, runId={}, error={}", run.id, error);
        }
        return toResponse(run);
    }

    public List<SyncRunResponse> list(String jobId, int limit) {
        var query = Wrappers.<SyncRunEntity>lambdaQuery()
                .orderByDesc(SyncRunEntity::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        if (jobId != null && !jobId.isBlank()) {
            query.eq(SyncRunEntity::getJobId, jobId);
        }
        return runMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public SyncRunResponse get(String id) {
        return toResponse(require(id));
    }

    public SyncRunEntity require(String id) {
        SyncRunEntity run = runMapper.selectById(id);
        if (run == null) {
            throw new ResourceNotFoundException("Sync run not found: " + id);
        }
        return run;
    }

    public synchronized SyncRunResponse stop(String id) {
        SyncRunEntity run = require(id);
        RunStatus status = RunStatus.valueOf(run.status);
        if (status.isTerminal()) {
            throw new ConflictException("Sync run is already finished");
        }
        if (run.seatunnelJobId == null) {
            throw new ConflictException("Sync run has not been accepted by SeaTunnel");
        }
        seaTunnelClient.stop(run.seatunnelJobId);
        transition(run, RunStatus.STOPPING, "已发送停止请求，等待 SeaTunnel 结束任务");
        return toResponse(run);
    }

    @Scheduled(fixedDelayString = "${data-sync.seatunnel.poll-delay:2s}")
    public void reconcileActiveRuns() {
        List<SyncRunEntity> runs = runMapper.selectList(Wrappers.<SyncRunEntity>lambdaQuery()
                .in(SyncRunEntity::getStatus, ACTIVE_STATUSES));
        for (SyncRunEntity run : runs) {
            if (run.seatunnelJobId == null) {
                continue;
            }
            try {
                reconcile(run, seaTunnelClient.jobInfo(run.seatunnelJobId));
            } catch (Exception exception) {
                markStale(run, exception);
            }
        }
    }

    @Transactional
    void reconcile(SyncRunEntity run, SeaTunnelJobInfo info) {
        RunStatus next = SeaTunnelStatusMapper.map(info.status());
        run.sourceReadCount = info.sourceReadCount();
        run.sinkWriteCount = info.sinkWriteCount();
        run.sourceQps = info.sourceQps();
        run.sinkQps = info.sinkQps();
        run.sourceBytes = info.sourceBytes();
        run.sinkBytes = info.sinkBytes();
        run.staleSince = null;
        run.lastPollError = null;
        if (next == RunStatus.RUNNING && run.startedAt == null) {
            run.startedAt = LocalDateTime.now();
        }
        if (next.isTerminal()) {
            if (run.startedAt == null) {
                run.startedAt = run.createdAt;
            }
            run.finishedAt = LocalDateTime.now();
        }
        if (next == RunStatus.FAILED || next == RunStatus.UNKNOWN) {
            run.errorMessage = sanitizeEngineMessage(run, info);
        }
        RunStatus current = RunStatus.valueOf(run.status);
        if (current != next) {
            transition(run, next, eventMessage(next));
        } else {
            run.updatedAt = LocalDateTime.now();
            runMapper.updateById(run);
        }
    }

    private void markStale(SyncRunEntity run, Exception exception) {
        String error = sanitizeEngineMessage(run,
                exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
        if (run.staleSince == null) {
            run.staleSince = LocalDateTime.now();
        }
        run.lastPollError = error;
        run.updatedAt = LocalDateTime.now();
        runMapper.updateById(run);
        log.debug("SeaTunnel status unavailable, runId={}, jobId={}", run.id, run.seatunnelJobId);
    }

    private void ensureNoActiveRun(String jobId) {
        if (runMapper.selectCount(Wrappers.<SyncRunEntity>lambdaQuery()
                .eq(SyncRunEntity::getJobId, jobId)
                .in(SyncRunEntity::getStatus, ACTIVE_STATUSES)) > 0) {
            throw new ConflictException("Sync job already has an active run");
        }
    }

    private void transition(SyncRunEntity run, RunStatus status, String message) {
        RunStatus previous = RunStatus.valueOf(run.status);
        run.status = status.name();
        run.updatedAt = LocalDateTime.now();
        runMapper.updateById(run);
        if (previous != status) {
            addEvent(run.id, status, message);
        }
    }

    private void addEvent(String runId, RunStatus status, String message) {
        SyncRunEventEntity event = new SyncRunEventEntity();
        event.id = UUID.randomUUID().toString();
        event.runId = runId;
        event.status = status.name();
        event.message = truncate(message, 1000);
        event.createdAt = LocalDateTime.now();
        eventMapper.insert(event);
    }

    private SyncRunResponse toResponse(SyncRunEntity run) {
        SyncJobEntity job = jobMapper.selectById(run.jobId);
        List<RunEventResponse> events = eventMapper.selectList(Wrappers.<SyncRunEventEntity>lambdaQuery()
                        .eq(SyncRunEventEntity::getRunId, run.id)
                        .orderByAsc(SyncRunEventEntity::getCreatedAt))
                .stream()
                .map(event -> new RunEventResponse(
                        event.id, RunStatus.valueOf(event.status), event.message, event.createdAt))
                .toList();
        return new SyncRunResponse(
                run.id, run.jobId, job == null ? "已归档任务" : job.name,
                run.seatunnelJobId, RunStatus.valueOf(run.status), value(run.sourceReadCount), value(run.sinkWriteCount),
                decimal(run.sourceQps), decimal(run.sinkQps), value(run.sourceBytes), value(run.sinkBytes),
                run.errorMessage, run.staleSince != null, run.staleSince, run.lastPollError,
                run.startedAt, run.finishedAt, run.createdAt, run.updatedAt, events);
    }

    private String sanitize(Exception exception, DataSourceConfigEntity source, DataSourceConfigEntity target) {
        return sanitizer.sanitize(exception.getMessage(), List.of(
                cryptoService.decrypt(source.encryptedPassword), cryptoService.decrypt(target.encryptedPassword)));
    }

    private String sanitizeEngineMessage(SyncRunEntity run, SeaTunnelJobInfo info) {
        String message = info.errorMessage() == null ? "SeaTunnel returned " + info.status() : info.errorMessage();
        return sanitizeEngineMessage(run, message);
    }

    private String sanitizeEngineMessage(SyncRunEntity run, String message) {
        SyncJobEntity job = jobMapper.selectById(run.jobId);
        if (job == null) {
            return truncate(sanitizer.sanitize(message, List.of()), 2000);
        }
        DataSourceConfigEntity source = dataSourceService.require(job.sourceDataSourceId);
        DataSourceConfigEntity target = dataSourceService.require(job.targetDataSourceId);
        return truncate(sanitizer.sanitize(message, List.of(
                cryptoService.decrypt(source.encryptedPassword),
                cryptoService.decrypt(target.encryptedPassword))), 2000);
    }

    private String eventMessage(RunStatus status) {
        return switch (status) {
            case PENDING -> "等待 SeaTunnel 分配运行资源";
            case RUNNING -> "数据同步正在运行";
            case STOPPING -> "任务正在停止";
            case SUCCEEDED -> "数据同步已完成";
            case FAILED -> "数据同步失败";
            case CANCELED -> "数据同步已取消";
            case UNKNOWN -> "SeaTunnel 无法识别该任务状态";
            case SUBMITTING -> "正在提交任务";
        };
    }

    private long value(Long value) { return value == null ? 0 : value; }
    private double decimal(Double value) { return value == null ? 0 : value; }
    private String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() > length ? value.substring(0, length) : value;
    }
}
