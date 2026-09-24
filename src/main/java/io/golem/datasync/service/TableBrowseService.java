package io.golem.datasync.service;

import io.golem.datasync.api.ApiModels.ColumnInfo;
import io.golem.datasync.api.ApiModels.TableRowsResponse;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.api.ResourceNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TableBrowseService {
    private final DataSourceService sources;
    private final MysqlMetadataService metadata;

    public TableBrowseService(DataSourceService sources, MysqlMetadataService metadata) {
        this.sources = sources;
        this.metadata = metadata;
    }

    public TableRowsResponse rows(String id, String table, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new RequestValidationException("page 必须大于 0，pageSize 必须在 1 到 100 之间");
        }
        try {
            MysqlMetadataService.validateIdentifier(table, "table");
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("表名包含不支持的字符");
        }
        var source = sources.require(id);
        try {
            if (!metadata.tableExists(source, table)) {
                throw new ResourceNotFoundException("表不存在：" + table);
            }
            List<ColumnInfo> columns = metadata.listColumns(source, table);
            if (columns.isEmpty()) throw new ResourceNotFoundException("表不存在：" + table);
            String tablePath = quote(source.databaseName) + "." + quote(table);
            String projection = columns.stream().map(c -> quote(c.name())).collect(Collectors.joining(", "));
            String primaryKeys = columns.stream().filter(ColumnInfo::primaryKey)
                    .map(c -> quote(c.name())).collect(Collectors.joining(", "));
            String query = "SELECT " + projection + " FROM " + tablePath
                    + (primaryKeys.isEmpty() ? "" : " ORDER BY " + primaryKeys) + " LIMIT ? OFFSET ?";
            try (var connection = metadata.open(source)) {
                connection.setReadOnly(true);
                long total;
                try (var count = connection.prepareStatement("SELECT COUNT(*) FROM " + tablePath)) {
                    count.setQueryTimeout(10);
                    try (var result = count.executeQuery()) {
                        result.next();
                        total = result.getLong(1);
                    }
                }
                List<List<String>> rows = new ArrayList<>();
                try (var statement = connection.prepareStatement(query)) {
                    statement.setQueryTimeout(10);
                    statement.setInt(1, pageSize);
                    statement.setLong(2, (long) (page - 1) * pageSize);
                    try (var result = statement.executeQuery()) {
                        while (result.next()) {
                            List<String> row = new ArrayList<>();
                            for (int i = 0; i < columns.size(); i++) {
                                row.add(cell(result, i + 1, columns.get(i).jdbcType()));
                            }
                            rows.add(row);
                        }
                    }
                }
                return new TableRowsResponse(columns, rows, total, page, pageSize, !primaryKeys.isEmpty());
            }
        } catch (SQLTimeoutException exception) {
            throw new RequestValidationException("读取表数据超时（10 秒），请稍后重试");
        } catch (SQLException exception) {
            // Driver messages can include credentials or connection details; never return them.
            throw new RequestValidationException("无法读取表数据，请检查数据库连接、表是否存在及读取权限");
        }
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static String cell(ResultSet result, int index, int jdbcType) throws SQLException {
        return switch (jdbcType) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] bytes = result.getBytes(index);
                yield bytes == null ? null : "0x" + HexFormat.of().formatHex(bytes);
            }
            default -> result.getString(index);
        };
    }
}
