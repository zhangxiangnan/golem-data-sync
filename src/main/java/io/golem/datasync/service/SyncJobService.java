package io.golem.datasync.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.golem.datasync.api.ApiModels.ColumnInfo;
import io.golem.datasync.api.ApiModels.ConfigPreviewResponse;
import io.golem.datasync.api.ApiModels.JobValidationResponse;
import io.golem.datasync.api.ApiModels.RunSummary;
import io.golem.datasync.api.ApiModels.SyncJobRequest;
import io.golem.datasync.api.ApiModels.SyncJobResponse;
import io.golem.datasync.api.ApiModels.ValidationIssue;
import io.golem.datasync.api.ConflictException;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.api.ResourceNotFoundException;
import io.golem.datasync.domain.RunStatus;
import io.golem.datasync.domain.WriteMode;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.persistence.SyncRunEntity;
import io.golem.datasync.persistence.SyncRunMapper;
import io.golem.datasync.seatunnel.SeaTunnelConfigGenerator;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncJobService {
    private static final List<String> ACTIVE_STATUSES = List.of(
            RunStatus.SUBMITTING.name(), RunStatus.PENDING.name(), RunStatus.RUNNING.name(), RunStatus.STOPPING.name());
    private final SyncJobMapper mapper;
    private final SyncRunMapper runMapper;
    private final DataSourceService dataSourceService;
    private final MysqlMetadataService metadataService;
    private final SeaTunnelConfigGenerator configGenerator;

    public SyncJobService(
            SyncJobMapper mapper,
            SyncRunMapper runMapper,
            DataSourceService dataSourceService,
            MysqlMetadataService metadataService,
            SeaTunnelConfigGenerator configGenerator) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.dataSourceService = dataSourceService;
        this.metadataService = metadataService;
        this.configGenerator = configGenerator;
    }

    public List<SyncJobResponse> list() {
        return mapper.selectList(Wrappers.<SyncJobEntity>lambdaQuery()
                        .eq(SyncJobEntity::getArchived, false)
                        .orderByDesc(SyncJobEntity::getUpdatedAt))
                .stream().map(this::toResponse).toList();
    }

    public SyncJobResponse get(String id) {
        return toResponse(require(id));
    }

    public SyncJobEntity require(String id) {
        SyncJobEntity entity = mapper.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.archived)) {
            throw new ResourceNotFoundException("Sync job not found: " + id);
        }
        return entity;
    }

    @Transactional
    public SyncJobResponse create(SyncJobRequest request) {
        ensureUniqueName(request.name(), null);
        validateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        SyncJobEntity entity = new SyncJobEntity();
        entity.id = UUID.randomUUID().toString();
        apply(entity, request);
        entity.archived = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        mapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public SyncJobResponse update(String id, SyncJobRequest request) {
        SyncJobEntity entity = require(id);
        ensureNoActiveRun(id);
        ensureUniqueName(request.name(), id);
        validateRequest(request);
        apply(entity, request);
        entity.updatedAt = LocalDateTime.now();
        mapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional
    public void archive(String id) {
        SyncJobEntity entity = require(id);
        ensureNoActiveRun(id);
        entity.archived = true;
        entity.updatedAt = LocalDateTime.now();
        mapper.updateById(entity);
    }

    public JobValidationResponse validate(String id) {
        return validateEntity(require(id));
    }

    public JobValidationResponse validateEntity(SyncJobEntity job) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<ColumnInfo> sourceColumns = List.of();
        DataSourceConfigEntity source = dataSourceService.require(job.sourceDataSourceId);
        DataSourceConfigEntity target = dataSourceService.require(job.targetDataSourceId);
        if (samePhysicalTable(source, job.sourceTable, target, job.targetTable)) {
            issues.add(error("SAME_TABLE", "源表和目标表不能是同一张物理表"));
        }
        try {
            sourceColumns = metadataService.listColumns(source, job.sourceTable);
            if (sourceColumns.isEmpty()) {
                issues.add(error("SOURCE_TABLE_NOT_FOUND", "源表不存在或没有字段"));
            }
        } catch (SQLException exception) {
            issues.add(error("SOURCE_UNREACHABLE", "无法读取源表结构：" + safe(exception.getMessage())));
        }
        try {
            if (metadataService.tableExists(target, job.targetTable)) {
                List<ColumnInfo> targetColumns = metadataService.listColumns(target, job.targetTable);
                compareSchema(sourceColumns, targetColumns, issues);
            } else {
                issues.add(new ValidationIssue("INFO", "TARGET_WILL_BE_CREATED", "目标表不存在，运行时将自动创建"));
            }
        } catch (SQLException exception) {
            issues.add(error("TARGET_UNREACHABLE", "无法读取目标表结构：" + safe(exception.getMessage())));
        }
        if (WriteMode.valueOf(job.writeMode) == WriteMode.APPEND) {
            issues.add(new ValidationIssue("WARNING", "APPEND_DUPLICATES", "追加模式重复运行可能产生重复数据"));
        } else {
            issues.add(new ValidationIssue("WARNING", "TARGET_DATA_REMOVED", "清空重写会在写入前删除目标表现有数据"));
        }
        boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.level()));
        return new JobValidationResponse(valid, issues, sourceColumns);
    }

    public ConfigPreviewResponse configPreview(String id) {
        SyncJobEntity job = require(id);
        String content = configGenerator.pretty(configGenerator.generate(
                job,
                dataSourceService.require(job.sourceDataSourceId),
                dataSourceService.require(job.targetDataSourceId),
                true));
        return new ConfigPreviewResponse("json", content);
    }

    private void compareSchema(List<ColumnInfo> source, List<ColumnInfo> target, List<ValidationIssue> issues) {
        Map<String, ColumnInfo> targetByName = target.stream()
                .collect(Collectors.toMap(column -> column.name().toLowerCase(), Function.identity(), (left, right) -> left));
        for (ColumnInfo sourceColumn : source) {
            ColumnInfo targetColumn = targetByName.get(sourceColumn.name().toLowerCase());
            if (targetColumn == null) {
                issues.add(error("TARGET_COLUMN_MISSING", "目标表缺少字段：" + sourceColumn.name()));
            } else if (!typeFamily(sourceColumn.typeName()).equals(typeFamily(targetColumn.typeName()))) {
                issues.add(error("TYPE_MISMATCH", "字段 " + sourceColumn.name() + " 类型不兼容："
                        + sourceColumn.typeName() + " → " + targetColumn.typeName()));
            }
        }
    }

    private String typeFamily(String type) {
        String value = type == null ? "" : type.toUpperCase();
        if (value.contains("INT") || value.equals("YEAR")) return "INTEGER";
        if (value.contains("DECIMAL") || value.contains("NUMERIC") || value.contains("DOUBLE") || value.contains("FLOAT")) return "NUMBER";
        if (value.contains("CHAR") || value.contains("TEXT") || value.contains("JSON") || value.contains("ENUM")) return "STRING";
        if (value.contains("DATE") || value.contains("TIME")) return "TEMPORAL";
        if (value.contains("BLOB") || value.contains("BINARY")) return "BINARY";
        return value;
    }

    private boolean samePhysicalTable(
            DataSourceConfigEntity source, String sourceTable, DataSourceConfigEntity target, String targetTable) {
        return source.host.equalsIgnoreCase(target.host)
                && source.port.equals(target.port)
                && source.databaseName.equalsIgnoreCase(target.databaseName)
                && sourceTable.equalsIgnoreCase(targetTable);
    }

    private void validateRequest(SyncJobRequest request) {
        dataSourceService.require(request.sourceDataSourceId());
        dataSourceService.require(request.targetDataSourceId());
        try {
            MysqlMetadataService.validateIdentifier(request.sourceTable(), "sourceTable");
            MysqlMetadataService.validateIdentifier(request.targetTable(), "targetTable");
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage());
        }
    }

    private void ensureUniqueName(String name, String currentId) {
        SyncJobEntity existing = mapper.selectOne(Wrappers.<SyncJobEntity>lambdaQuery()
                .eq(SyncJobEntity::getName, name.trim()));
        if (existing != null && !existing.id.equals(currentId)) {
            throw new ConflictException("Sync job name already exists: " + name);
        }
    }

    private void ensureNoActiveRun(String jobId) {
        if (runMapper.selectCount(Wrappers.<SyncRunEntity>lambdaQuery()
                .eq(SyncRunEntity::getJobId, jobId)
                .in(SyncRunEntity::getStatus, ACTIVE_STATUSES)) > 0) {
            throw new ConflictException("Sync job has an active run");
        }
    }

    private void apply(SyncJobEntity entity, SyncJobRequest request) {
        entity.name = request.name().trim();
        entity.description = request.description();
        entity.sourceDataSourceId = request.sourceDataSourceId();
        entity.sourceTable = request.sourceTable();
        entity.targetDataSourceId = request.targetDataSourceId();
        entity.targetTable = request.targetTable();
        entity.writeMode = request.writeMode().name();
        entity.parallelism = request.parallelism();
        entity.batchSize = request.batchSize();
    }

    private SyncJobResponse toResponse(SyncJobEntity entity) {
        DataSourceConfigEntity source = dataSourceService.require(entity.sourceDataSourceId);
        DataSourceConfigEntity target = dataSourceService.require(entity.targetDataSourceId);
        SyncRunEntity latest = runMapper.selectOne(Wrappers.<SyncRunEntity>lambdaQuery()
                .eq(SyncRunEntity::getJobId, entity.id)
                .orderByDesc(SyncRunEntity::getCreatedAt)
                .last("LIMIT 1"));
        RunSummary latestRun = latest == null ? null : new RunSummary(
                latest.id, RunStatus.valueOf(latest.status), value(latest.sourceReadCount), value(latest.sinkWriteCount),
                latest.startedAt, latest.finishedAt);
        return new SyncJobResponse(
                entity.id, entity.name, entity.description,
                source.id, source.name, entity.sourceTable,
                target.id, target.name, entity.targetTable,
                WriteMode.valueOf(entity.writeMode), entity.parallelism, entity.batchSize,
                Boolean.TRUE.equals(entity.archived), latestRun, entity.createdAt, entity.updatedAt);
    }

    private ValidationIssue error(String code, String message) {
        return new ValidationIssue("ERROR", code, message);
    }

    private String safe(String message) {
        if (message == null) return "unknown error";
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
