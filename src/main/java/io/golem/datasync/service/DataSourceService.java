package io.golem.datasync.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.golem.datasync.api.ApiModels.ColumnInfo;
import io.golem.datasync.api.ApiModels.ConnectionTestResponse;
import io.golem.datasync.api.ApiModels.DataSourceRequest;
import io.golem.datasync.api.ApiModels.DataSourceResponse;
import io.golem.datasync.api.ApiModels.TableInfo;
import io.golem.datasync.api.ConflictException;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.api.ResourceNotFoundException;
import io.golem.datasync.domain.DataSourceStatus;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.persistence.DataSourceConfigMapper;
import io.golem.datasync.persistence.SyncJobEntity;
import io.golem.datasync.persistence.SyncJobMapper;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.security.SecretSanitizer;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DataSourceService {
    private final DataSourceConfigMapper mapper;
    private final SyncJobMapper jobMapper;
    private final CryptoService cryptoService;
    private final SecretSanitizer sanitizer;
    private final MysqlMetadataService metadataService;

    public DataSourceService(
            DataSourceConfigMapper mapper,
            SyncJobMapper jobMapper,
            CryptoService cryptoService,
            SecretSanitizer sanitizer,
            MysqlMetadataService metadataService) {
        this.mapper = mapper;
        this.jobMapper = jobMapper;
        this.cryptoService = cryptoService;
        this.sanitizer = sanitizer;
        this.metadataService = metadataService;
    }

    public List<DataSourceResponse> list() {
        return mapper.selectList(Wrappers.<DataSourceConfigEntity>lambdaQuery()
                        .orderByDesc(DataSourceConfigEntity::getUpdatedAt))
                .stream().map(this::toResponse).toList();
    }

    public DataSourceResponse get(String id) {
        return toResponse(require(id));
    }

    public DataSourceConfigEntity require(String id) {
        DataSourceConfigEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("Data source not found: " + id);
        }
        return entity;
    }

    @Transactional
    public DataSourceResponse create(DataSourceRequest request) {
        ensureUniqueName(request.name(), null);
        validateDatabaseIdentifier(request.database());
        LocalDateTime now = LocalDateTime.now();
        DataSourceConfigEntity entity = new DataSourceConfigEntity();
        entity.id = UUID.randomUUID().toString();
        apply(entity, request, false);
        entity.type = "MYSQL";
        entity.status = DataSourceStatus.UNKNOWN.name();
        entity.createdAt = now;
        entity.updatedAt = now;
        mapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public DataSourceResponse update(String id, DataSourceRequest request) {
        DataSourceConfigEntity entity = require(id);
        ensureUniqueName(request.name(), id);
        validateDatabaseIdentifier(request.database());
        apply(entity, request, true);
        entity.status = DataSourceStatus.UNKNOWN.name();
        entity.lastError = null;
        entity.updatedAt = LocalDateTime.now();
        mapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(String id) {
        require(id);
        long references = jobMapper.selectCount(Wrappers.<SyncJobEntity>lambdaQuery()
                .eq(SyncJobEntity::getSourceDataSourceId, id)
                .or()
                .eq(SyncJobEntity::getTargetDataSourceId, id));
        if (references > 0) {
            throw new ConflictException("Data source is referenced by sync jobs and cannot be deleted");
        }
        mapper.deleteById(id);
    }

    @Transactional
    public ConnectionTestResponse test(String id) {
        DataSourceConfigEntity entity = require(id);
        long start = System.nanoTime();
        try (Connection ignored = metadataService.open(entity)) {
            long latency = Duration.ofNanos(System.nanoTime() - start).toMillis();
            entity.status = DataSourceStatus.AVAILABLE.name();
            entity.lastError = null;
            entity.lastTestAt = LocalDateTime.now();
            entity.updatedAt = entity.lastTestAt;
            mapper.updateById(entity);
            return new ConnectionTestResponse(true, "连接成功", latency);
        } catch (Exception exception) {
            String password = cryptoService.decrypt(entity.encryptedPassword);
            String error = sanitizer.sanitize(exception.getMessage(), List.of(password));
            entity.status = DataSourceStatus.UNAVAILABLE.name();
            entity.lastError = error;
            entity.lastTestAt = LocalDateTime.now();
            entity.updatedAt = entity.lastTestAt;
            mapper.updateById(entity);
            return new ConnectionTestResponse(false, error, Duration.ofNanos(System.nanoTime() - start).toMillis());
        }
    }

    public List<TableInfo> tables(String id) {
        try {
            return metadataService.listTables(require(id));
        } catch (Exception exception) {
            throw new RequestValidationException("Unable to list tables: " + sanitizer.sanitize(exception.getMessage(), List.of()));
        }
    }

    public List<ColumnInfo> columns(String id, String table) {
        try {
            List<ColumnInfo> columns = metadataService.listColumns(require(id), table);
            if (columns.isEmpty()) {
                throw new ResourceNotFoundException("Table not found: " + table);
            }
            return columns;
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RequestValidationException("Unable to read table schema: " + sanitizer.sanitize(exception.getMessage(), List.of()));
        }
    }

    private void apply(DataSourceConfigEntity entity, DataSourceRequest request, boolean updating) {
        entity.name = request.name().trim();
        entity.host = request.host().trim();
        entity.port = request.port();
        entity.databaseName = request.database().trim();
        entity.username = request.username().trim();
        if (!updating || request.password() != null) {
            entity.encryptedPassword = cryptoService.encrypt(request.password() == null ? "" : request.password());
        }
    }

    private void ensureUniqueName(String name, String currentId) {
        DataSourceConfigEntity existing = mapper.selectOne(Wrappers.<DataSourceConfigEntity>lambdaQuery()
                .eq(DataSourceConfigEntity::getName, name.trim()));
        if (existing != null && !existing.id.equals(currentId)) {
            throw new ConflictException("Data source name already exists: " + name);
        }
    }

    private void validateDatabaseIdentifier(String database) {
        try {
            MysqlMetadataService.validateIdentifier(database, "database");
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException(exception.getMessage());
        }
    }

    private DataSourceResponse toResponse(DataSourceConfigEntity entity) {
        return new DataSourceResponse(
                entity.id, entity.name, entity.type, entity.host, entity.port, entity.databaseName,
                entity.username, StringUtils.hasText(entity.encryptedPassword),
                DataSourceStatus.valueOf(entity.status), entity.lastTestAt, entity.lastError,
                entity.createdAt, entity.updatedAt);
    }
}
