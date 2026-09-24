package io.golem.datasync.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.golem.datasync.api.ApiModels.ColumnInfo;
import io.golem.datasync.api.RequestValidationException;
import io.golem.datasync.api.ResourceNotFoundException;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TableBrowseServiceTest {
    final DataSourceService sources = mock(DataSourceService.class);
    final MysqlMetadataService metadata = mock(MysqlMetadataService.class);
    final Connection connection = mock(Connection.class);
    final PreparedStatement count = mock(PreparedStatement.class);
    final PreparedStatement query = mock(PreparedStatement.class);
    final ResultSet counts = mock(ResultSet.class);
    final ResultSet rows = mock(ResultSet.class);
    final DataSourceConfigEntity source = new DataSourceConfigEntity();
    final TableBrowseService service = new TableBrowseService(sources, metadata);

    @BeforeEach
    void setup() throws Exception {
        source.databaseName = "source_db";
        when(sources.require("source")).thenReturn(source);
        when(metadata.tableExists(source, "orders")).thenReturn(true);
        when(metadata.listColumns(source, "orders")).thenReturn(List.of(column("id", Types.BIGINT, true, 1)));
        when(metadata.open(source)).thenReturn(connection);
        when(connection.prepareStatement(startsWith("SELECT COUNT"))).thenReturn(count);
        when(connection.prepareStatement(startsWith("SELECT `"))).thenReturn(query);
        when(count.executeQuery()).thenReturn(counts);
        when(counts.next()).thenReturn(true);
        when(counts.getLong(1)).thenReturn(120L);
        when(query.executeQuery()).thenReturn(rows);
    }

    @Test
    void bindsPaginationOrdersAndPreservesCellRepresentations() throws Exception {
        when(metadata.listColumns(source, "orders")).thenReturn(List.of(
                column("id", Types.BIGINT, true, 1), column("amount", Types.DECIMAL, false, 2),
                column("note", Types.VARCHAR, false, 3), column("empty", Types.VARCHAR, false, 4),
                column("created_at", Types.TIMESTAMP, false, 5), column("payload", Types.VARBINARY, false, 6)));
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("9223372036854775807");
        when(rows.getString(2)).thenReturn("1234567890.12");
        when(rows.getString(3)).thenReturn(null);
        when(rows.getString(4)).thenReturn("");
        when(rows.getString(5)).thenReturn("2026-09-24 08:00:00");
        when(rows.getBytes(6)).thenReturn(new byte[]{0, 15, (byte)255});
        var result = service.rows("source", "orders", 3, 50);
        assertThat(result.rows().get(0)).containsExactly("9223372036854775807", "1234567890.12", null, "", "2026-09-24 08:00:00", "0x000fff");
        assertThat(result.total()).isEqualTo(120);
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.orderedByPrimaryKey()).isTrue();
        verify(connection).setReadOnly(true);
        verify(connection).prepareStatement("SELECT `id`, `amount`, `note`, `empty`, `created_at`, `payload` FROM `source_db`.`orders` ORDER BY `id` LIMIT ? OFFSET ?");
        verify(query).setInt(1, 50);
        verify(query).setLong(2, 100);
        verify(count).setQueryTimeout(10);
        verify(query).setQueryTimeout(10);
        verify(connection).close();
    }

    @Test
    void returnsEmptyPagesAndUsesLongOffsets() throws Exception {
        when(counts.getLong(1)).thenReturn(0L);
        var result = service.rows("source", "orders", Integer.MAX_VALUE, 100);
        assertThat(result.rows()).isEmpty();
        assertThat(result.total()).isZero();
        verify(query).setLong(2, 214748364600L);
    }

    @Test
    void rejectsInvalidInputsBeforeOpeningConnection() {
        for (int[] input : List.of(new int[]{0, 50}, new int[]{1, 0}, new int[]{1, 101})) {
            assertThatThrownBy(() -> service.rows("source", "orders", input[0], input[1])).isInstanceOf(RequestValidationException.class);
        }
        for (String table : List.of("orders;DROP TABLE x", "other.orders", "orders`", "orders%")) {
            assertThatThrownBy(() -> service.rows("source", table, 1, 50)).isInstanceOf(RequestValidationException.class);
        }
        verifyNoInteractions(sources, connection);
    }

    @Test
    void rejectsMissingTableWithoutRunningQueries() throws Exception {
        when(metadata.tableExists(source, "orders")).thenReturn(false);
        assertThatThrownBy(() -> service.rows("source", "orders", 1, 50)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(connection);
    }

    @Test
    void noPrimaryKeyAndEscapedColumnNames() throws Exception {
        when(metadata.listColumns(source, "orders")).thenReturn(List.of(column("a`b", Types.VARCHAR, false, 1)));
        assertThat(service.rows("source", "orders", 1, 20).orderedByPrimaryKey()).isFalse();
        verify(connection).prepareStatement("SELECT `a``b` FROM `source_db`.`orders` LIMIT ? OFFSET ?");
    }

    @Test
    void compositePrimaryKeyAndNullBinary() throws Exception {
        when(metadata.listColumns(source, "orders")).thenReturn(List.of(column("tenant", Types.INTEGER, true, 1), column("id", Types.BINARY, true, 2)));
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("1");
        assertThat(service.rows("source", "orders", 1, 20).rows().get(0)).containsExactly("1", null);
        verify(connection).prepareStatement("SELECT `tenant`, `id` FROM `source_db`.`orders` ORDER BY `tenant`, `id` LIMIT ? OFFSET ?");
    }

    @Test
    void translatesTimeoutAndClosesResources() throws Exception {
        when(query.executeQuery()).thenThrow(new SQLTimeoutException("private driver details"));
        assertThatThrownBy(() -> service.rows("source", "orders", 1, 50))
                .isInstanceOf(RequestValidationException.class).hasMessageContaining("10 秒").hasMessageNotContaining("private");
        verify(connection).close();
        verify(query).close();
    }

    @Test
    void hidesDriverCredentialsAndConnectionErrors() throws Exception {
        when(metadata.open(source)).thenThrow(new SQLException("password=my-secret host=private"));
        assertThatThrownBy(() -> service.rows("source", "orders", 1, 50))
                .isInstanceOf(RequestValidationException.class).hasMessageContaining("连接").hasMessageNotContaining("my-secret");
    }

    private ColumnInfo column(String name, int type, boolean primary, int ordinal) {
        return new ColumnInfo(name, JDBCType.valueOf(type).getName(), type, null, null, true, primary, ordinal);
    }
}
