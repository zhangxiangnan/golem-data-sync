package io.golem.datasync.api;

import io.golem.datasync.domain.DataSourceStatus;
import io.golem.datasync.domain.EngineType;
import io.golem.datasync.domain.RunStatus;
import io.golem.datasync.domain.WriteMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public record DataSourceRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 255) String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 128) String database,
            @NotBlank @Size(max = 128) String username,
            @Size(max = 1000) String password) {}

    public record DataSourceResponse(
            String id,
            String name,
            String type,
            String host,
            int port,
            String database,
            String username,
            boolean passwordConfigured,
            DataSourceStatus status,
            LocalDateTime lastTestAt,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record ConnectionTestResponse(boolean success, String message, long latencyMs) {}

    public record TableInfo(String name, String type) {}

    public record TableRowsResponse(
            List<ColumnInfo> columns, List<List<String>> rows, long total,
            int page, int pageSize, boolean orderedByPrimaryKey) {}

    public record ColumnInfo(
            String name,
            String typeName,
            int jdbcType,
            Integer size,
            Integer scale,
            boolean nullable,
            boolean primaryKey,
            int ordinal) {}

    public record SyncJobRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @NotBlank String sourceDataSourceId,
            @NotBlank @Size(max = 128) String sourceTable,
            @NotBlank String targetDataSourceId,
            @NotBlank @Size(max = 128) String targetTable,
            @NotNull WriteMode writeMode,
            @NotNull @Min(1) @Max(32) Integer parallelism,
            @NotNull @Min(1) @Max(100000) Integer batchSize) {}

    public record SyncJobResponse(
            String id,
            String name,
            String description,
            String sourceDataSourceId,
            String sourceDataSourceName,
            String sourceTable,
            String targetDataSourceId,
            String targetDataSourceName,
            String targetTable,
            WriteMode writeMode,
            int parallelism,
            int batchSize,
            boolean archived,
            RunSummary latestRun,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record RunSummary(
            String id,
            RunStatus status,
            EngineType engineType,
            long sourceReadCount,
            long sinkWriteCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {}

    public record ValidationIssue(String level, String code, String message) {}

    public record JobValidationResponse(
            boolean valid, List<ValidationIssue> issues, List<ColumnInfo> sourceColumns) {}

    public record ConfigPreviewResponse(String format, String content) {}

    public record RunStartRequest(@Size(max = 80) String engineProfileId) {}

    public record EngineProfileResponse(
            String id,
            String name,
            EngineType engineType,
            boolean enabled,
            boolean mock,
            boolean online,
            String version,
            String message,
            List<String> capabilities) {}

    public record EngineCapabilityResponse(
            EngineType engineType,
            boolean configured,
            List<String> supported,
            List<String> limitations) {}

    public record RunEventResponse(String id, RunStatus status, String message, LocalDateTime createdAt) {}

    public record SyncRunResponse(
            String id,
            String jobId,
            String jobName,
            String seatunnelJobId,
            EngineType engineType,
            String engineProfileId,
            String externalJobId,
            String trackingUrl,
            boolean metricsAvailable,
            RunStatus status,
            long sourceReadCount,
            long sinkWriteCount,
            double sourceQps,
            double sinkQps,
            long sourceBytes,
            long sinkBytes,
            String errorMessage,
            boolean stale,
            LocalDateTime staleSince,
            String lastPollError,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<RunEventResponse> events) {}

    public record DashboardSummary(
            long dataSourceCount,
            long jobCount,
            long runningCount,
            long todaySucceeded,
            long todayFailed,
            double successRate,
            List<SyncRunResponse> recentRuns) {}

    public record SystemStatusResponse(
            boolean engineOnline,
            String engineBaseUrl,
            String engineVersion,
            String message,
            LocalDateTime checkedAt,
            List<EngineProfileResponse> profiles) {}
}
