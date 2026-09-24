package io.golem.datasync.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.golem.datasync.persistence.DataSourceConfigEntity;
import io.golem.datasync.security.CryptoService;
import java.sql.*;
import org.junit.jupiter.api.Test;

class MysqlMetadataPatternTest {
    @Test
    void escapesUnderscoresInExactTableLookups() throws Exception {
        var service = spy(new MysqlMetadataService(mock(CryptoService.class)));
        var source = new DataSourceConfigEntity(); source.databaseName = "source_db";
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        var result = mock(ResultSet.class);
        doReturn(connection).when(service).open(source);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getTables(eq("source_db"), isNull(), eq("order\\_items"), any())).thenReturn(result);
        when(metadata.getColumns("source_db", null, "order\\_items", "%")).thenReturn(result);
        when(metadata.getPrimaryKeys("source_db", null, "order_items")).thenReturn(result);
        assertThat(service.tableExists(source, "order_items")).isFalse();
        assertThat(service.listColumns(source, "order_items")).isEmpty();
        verify(metadata).getColumns("source_db", null, "order\\_items", "%");
    }
}
