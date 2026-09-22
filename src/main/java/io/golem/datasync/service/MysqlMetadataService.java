package io.golem.datasync.service;

import io.golem.datasync.api.ApiModels.ColumnInfo;
import io.golem.datasync.api.ApiModels.TableInfo;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.security.CryptoService;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MysqlMetadataService {
    private final CryptoService cryptoService;

    public MysqlMetadataService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public Connection open(DataSourceConfigEntity dataSource) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(dataSource), dataSource.username,
                cryptoService.decrypt(dataSource.encryptedPassword));
    }

    public String jdbcUrl(DataSourceConfigEntity dataSource) {
        return "jdbc:mysql://" + dataSource.host + ":" + dataSource.port + "/" + dataSource.databaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
    }

    public List<TableInfo> listTables(DataSourceConfigEntity dataSource) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection connection = open(dataSource);
                ResultSet result = connection.getMetaData().getTables(dataSource.databaseName, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(new TableInfo(result.getString("TABLE_NAME"), result.getString("TABLE_TYPE")));
            }
        }
        tables.sort(Comparator.comparing(TableInfo::name));
        return tables;
    }

    public List<ColumnInfo> listColumns(DataSourceConfigEntity dataSource, String table) throws SQLException {
        validateIdentifier(table, "table");
        Set<String> primaryKeys = new HashSet<>();
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection connection = open(dataSource)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet keys = metaData.getPrimaryKeys(dataSource.databaseName, null, table)) {
                while (keys.next()) {
                    primaryKeys.add(keys.getString("COLUMN_NAME"));
                }
            }
            try (ResultSet result = metaData.getColumns(dataSource.databaseName, null, table, "%")) {
                while (result.next()) {
                    String name = result.getString("COLUMN_NAME");
                    columns.add(new ColumnInfo(
                            name,
                            result.getString("TYPE_NAME"),
                            result.getInt("DATA_TYPE"),
                            nullableInt(result, "COLUMN_SIZE"),
                            nullableInt(result, "DECIMAL_DIGITS"),
                            result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls,
                            primaryKeys.contains(name),
                            result.getInt("ORDINAL_POSITION")));
                }
            }
        }
        columns.sort(Comparator.comparingInt(ColumnInfo::ordinal));
        return columns;
    }

    public boolean tableExists(DataSourceConfigEntity dataSource, String table) throws SQLException {
        validateIdentifier(table, "table");
        try (Connection connection = open(dataSource);
                ResultSet result = connection.getMetaData().getTables(
                        dataSource.databaseName, null, table, new String[] {"TABLE"})) {
            return result.next();
        }
    }

    public static void validateIdentifier(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9_$]+")) {
            throw new IllegalArgumentException(label + " must contain only letters, digits, _, or $");
        }
    }

    private Integer nullableInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }
}
