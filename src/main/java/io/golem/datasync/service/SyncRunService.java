package io.golem.datasync.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.golem.datasync.api.ApiModels.JobValidationResponse;
import io.golem.datasync.api.ApiModels.RunEventResponse;
import io.golem.datasync.api.ApiModels.SyncRunResponse;
import io.golem.datasync.api.ConflictException;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.api.ResourceNotFoundException;
import io.golem.datasync.domain.EngineType;
import io.golem.datasync.domain.RunStatus;
import io.golem.datasync.engine.EngineCapability;
import io.golem.datasync.engine.EngineCompatibilityService;
import io.golem.datasync.engine.EngineJobSnapshot;
import io.golem.datasync.engine.EngineRegistry;
import io.golem.datasync.engine.GeneratedEngineConfig;
import io.golem.datasync.engine.JobEngineClient;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.persistence.SyncRunEntity;
import io.golem.datasync.persistence.SyncRunEventEntity;
import io.golem.datasync.persistence.SyncRunEventMapper;
import io.golem.datasync.persistence.SyncRunMapper;
import io.golem.datasync.seatunnel.SeaTunnelConfigGenerator;
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
    private final EngineRegistry engineRegistry;
    private final EngineCompatibilityService compatibility;
    private final CryptoService cryptoService;
    private final SecretSanitizer sanitizer;

    public SyncRunService(
            SyncRunMapper runMapper,
            SyncRunEventMapper eventMapper,
            SyncJobMapper jobMapper,
            SyncJobService jobService,
            DataSourceService dataSourceService,
            SeaTunnelConfigGenerator configGenerator,
            EngineRegistry engineRegistry,
            EngineCompatibilityService compatibility,
            CryptoService cryptoService,
            SecretSanitizer sanitizer) {
        this.runMapper = runMapper;
        this.eventMapper = eventMapper;
        this.jobMapper = jobMapper;
        this.jobService = jobService;
        this.dataSourceService = dataSourceService;
        this.configGenerator = configGenerator;
        this.engineRegistry = engineRegistry;
        this.compatibility = compatibility;
        this.cryptoService = cryptoService;
        this.sanitizer = sanitizer;
    }

    public SyncRunResponse start(String jobId) {
        return start(jobId, "zeta-local");
    }

    public synchronized SyncRunResponse start(String jobId, String engineProfileId) {
        SyncJobEntity job = jobService.require(jobId);
        ensureNoActiveRun(jobId);
        JobEngineClient engine = engineRegistry.require(engineProfileId);
        compatibility.validate(engine);
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
        GeneratedEngineConfig redacted = configGenerator.forEngine(engine.engineType(), job, source, target, true);
        GeneratedEngineConfig executable = configGenerator.forEngine(engine.engineType(), job, source, target, false);
        LocalDateTime now = LocalDateTime.now();
        SyncRunEntity run = new SyncRunEntity();
        run.id = UUID.randomUUID().toString();
        run.jobId = job.id;
        run.engineType = engine.engineType().name();
        run.engineProfileId = engine.profileId();
        run.metricsAvailable = engine.capabilities().contains(EngineCapability.METRICS);
        run.status = RunStatus.SUBMITTING.name();
        run.sourceReadCount = 0L;
        run.sinkWriteCount = 0L;
        run.sourceQps = 0D;
        run.sinkQps = 0D;
        run.sourceBytes = 0L;
        run.sinkBytes = 0L;
        run.configSnapshot = redacted.content();
        run.createdAt = now;
        run.updatedAt = now;
        runMapper.insert(run);
        addEvent(run.id, RunStatus.SUBMITTING, "正在向 " + engine.displayName() + " 提交任务");

        try {
            var submission = engine.submit(executable, run.id, job.name);
            run.externalJobId = submission.externalJobId();
            run.trackingUrl = submission.trackingUrl();
            if (engine.engineType() == EngineType.ZETA) run.seatunnelJobId = submission.externalJobId();
            transition(run, RunStatus.PENDING, engine.displayName() + " 已接收任务，等待调度");
        } catch (Exception exception) {
            String error = sanitize(exception, source, target);
            run.errorMessage = error;
            run.finishedAt = LocalDateTime.now();
            transition(run, RunStatus.FAILED, "提交失败：" + error);
            log.warn("Engine submission failed, runId={}, profile={}, error={}", run.id, engine.profileId(), error);
        }
        return toResponse(run);
    }

    public List<SyncRunResponse> list(String jobId, int limit) {
        var query = Wrappers.<SyncRunEntity>lambdaQuery()
                .orderByDesc(SyncRunEntity::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 100)));
        if (jobId != null && !jobId.isBlank()) query.eq(SyncRunEntity::getJobId, jobId);
        return runMapper.selectList(query).stream().map(this::toResponse).toList();
    }

    public SyncRunResponse get(String id) { return toResponse(require(id)); }

    public SyncRunEntity require(String id) {
        SyncRunEntity run = runMapper.selectById(id);
        if (run == null) throw new ResourceNotFoundException("Sync run not found: " + id);
        return run;
    }

    public synchronized SyncRunResponse stop(String id) {
        SyncRunEntity run = require(id);
        RunStatus status = RunStatus.valueOf(run.status);
        if (status.isTerminal()) throw new ConflictException("Sync run is already finished");
        if (externalJobId(run) == null) throw new ConflictException("Sync run has not been accepted by the engine");
        JobEngineClient engine = engineRegistry.require(profileId(run));
        engine.stop(externalJobId(run));
        transition(run, RunStatus.STOPPING, "已向 " + engine.displayName() + " 发送停止请求");
        return toResponse(run);
    }

    @Scheduled(fixedDelayString = "${data-sync.poll-delay:${data-sync.seatunnel.poll-delay:2s}}")
    public void reconcileActiveRuns() {
        List<SyncRunEntity> runs = runMapper.selectList(Wrappers.<SyncRunEntity>lambdaQuery()
                .in(SyncRunEntity::getStatus, ACTIVE_STATUSES));
        for (SyncRunEntity run : runs) {
            if (externalJobId(run) == null) continue;
            try {
                JobEngineClient engine = engineRegistry.require(profileId(run));
                reconcile(run, engine.jobInfo(externalJobId(run)));
            } catch (Exception exception) {
                markStale(run, exception);
            }
        }
    }

    @Transactional
    void reconcile(SyncRunEntity run, EngineJobSnapshot info) {
        RunStatus next = info.status();
        run.sourceReadCount = value(info.sourceReadCount(), run.sourceReadCount);
        run.sinkWriteCount = value(info.sinkWriteCount(), run.sinkWriteCount);
        run.sourceQps = decimal(info.sourceQps(), run.sourceQps);
        run.sinkQps = decimal(info.sinkQps(), run.sinkQps);
        run.sourceBytes = value(info.sourceBytes(), run.sourceBytes);
        run.sinkBytes = value(info.sinkBytes(), run.sinkBytes);
        run.staleSince = null;
        run.lastPollError = null;
        if (next == RunStatus.RUNNING && run.startedAt == null) run.startedAt = LocalDateTime.now();
        if (next.isTerminal()) {
            if (run.startedAt == null) run.startedAt = run.createdAt;
            run.finishedAt = LocalDateTime.now();
        }
        if (next == RunStatus.FAILED || next == RunStatus.UNKNOWN) {
            String message = info.errorMessage() == null ? "Engine returned " + next : info.errorMessage();
            run.errorMessage = sanitizeEngineMessage(run, message);
        }
        RunStatus current = RunStatus.valueOf(run.status);
        if (current != next) transition(run, next, eventMessage(next));
        else {
            run.updatedAt = LocalDateTime.now();
            runMapper.updateById(run);
        }
    }

    private void markStale(SyncRunEntity run, Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String error = sanitizeEngineMessage(run, message);
        if (run.staleSince == null) run.staleSince = LocalDateTime.now();
        run.lastPollError = error;
        run.updatedAt = LocalDateTime.now();
        runMapper.updateById(run);
        log.debug("Engine status unavailable, runId={}, profile={}, externalJobId={}",
                run.id, profileId(run), externalJobId(run));
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
        if (previous != status) addEvent(run.id, status, message);
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
                run.id, run.jobId, job == null ? "已归档任务" : job.name, run.seatunnelJobId,
                engineType(run), profileId(run), externalJobId(run), run.trackingUrl,
                !Boolean.FALSE.equals(run.metricsAvailable), RunStatus.valueOf(run.status),
                value(run.sourceReadCount), value(run.sinkWriteCount), decimal(run.sourceQps), decimal(run.sinkQps),
                value(run.sourceBytes), value(run.sinkBytes), run.errorMessage,
                run.staleSince != null, run.staleSince, run.lastPollError,
                run.startedAt, run.finishedAt, run.createdAt, run.updatedAt, events);
    }

    private String sanitize(Exception exception, DataSourceConfigEntity source, DataSourceConfigEntity target) {
        return truncate(sanitizer.sanitize(exception.getMessage(), List.of(
                cryptoService.decrypt(source.encryptedPassword), cryptoService.decrypt(target.encryptedPassword))), 2000);
    }

    private String sanitizeEngineMessage(SyncRunEntity run, String message) {
        SyncJobEntity job = jobMapper.selectById(run.jobId);
        if (job == null) return truncate(sanitizer.sanitize(message, List.of()), 2000);
        DataSourceConfigEntity source = dataSourceService.require(job.sourceDataSourceId);
        DataSourceConfigEntity target = dataSourceService.require(job.targetDataSourceId);
        return truncate(sanitizer.sanitize(message, List.of(
                cryptoService.decrypt(source.encryptedPassword),
                cryptoService.decrypt(target.encryptedPassword))), 2000);
    }

    private String eventMessage(RunStatus status) {
        return switch (status) {
            case PENDING -> "等待执行引擎分配运行资源";
            case RUNNING -> "数据同步正在运行";
            case STOPPING -> "任务正在停止";
            case SUCCEEDED -> "数据同步已完成";
            case FAILED -> "数据同步失败";
            case CANCELED -> "数据同步已取消";
            case UNKNOWN -> "执行引擎无法识别该任务状态";
            case SUBMITTING -> "正在提交任务";
        };
    }

    private EngineType engineType(SyncRunEntity run) {
        return run.engineType == null ? EngineType.ZETA : EngineType.valueOf(run.engineType);
    }
    private String profileId(SyncRunEntity run) {
        return run.engineProfileId == null ? "zeta-local" : run.engineProfileId;
    }
    private String externalJobId(SyncRunEntity run) {
        return run.externalJobId == null ? run.seatunnelJobId : run.externalJobId;
    }
    private long value(Long value) { return value == null ? 0 : value; }
    private long value(Long candidate, Long fallback) { return candidate == null ? value(fallback) : candidate; }
    private double decimal(Double value) { return value == null ? 0 : value; }
    private double decimal(Double candidate, Double fallback) { return candidate == null ? decimal(fallback) : candidate; }
    private String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() > length ? value.substring(0, length) : value;
    }
}
