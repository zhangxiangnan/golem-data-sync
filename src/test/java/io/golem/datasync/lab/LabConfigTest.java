package io.golem.datasync.lab;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.node.*;
import io.golem.datasync.service.*;
import io.golem.datasync.security.CryptoService;
import io.golem.datasync.persistence.DataSourceConfigEntity;
import java.util.*;
import org.junit.jupiter.api.*;

class LabConfigTest {
    LabCatalog catalog;LabConfigService service;DataSourceService sources;MysqlMetadataService metadata;CryptoService crypto;
    @BeforeEach void setup() throws Exception {
        catalog=spy(new LabCatalog(".runtime/apache-seatunnel-2.3.13"));doReturn(true).when(catalog).installed(anyString());
        sources=mock(DataSourceService.class);metadata=mock(MysqlMetadataService.class);crypto=mock(CryptoService.class);
        var ds=new DataSourceConfigEntity();ds.databaseName="lab";ds.username="test";ds.encryptedPassword="cipher";
        when(sources.require("ds")).thenReturn(ds);when(metadata.jdbcUrl(ds)).thenReturn("jdbc:mysql://localhost/lab");when(crypto.decrypt("cipher")).thenReturn("Secret!123");
        service=new LabConfigService(catalog,sources,metadata,crypto);
    }
    ObjectNode config(){return LabJson.obj("""
      {"env":{"job.mode":"BATCH","parallelism":1},"source":[{"plugin_name":"Jdbc","plugin_output":"raw","table_list":[{"table_path":"lab.orders","partition_num":4}]}],"transform":[],"sink":[{"plugin_name":"Jdbc","plugin_input":["raw"],"table":"orders","generate_sink_sql":true}]}
      """);}
    ObjectNode bindings(){return LabJson.obj("{\"source/0\":\"ds\",\"sink/0\":\"ds\"}");}
    @Test void unknownAndNestedConfigurationRoundTripsWithoutSecrets() {
        var c=config();c.withObject("env").set("future",LabJson.parse("{\"x\":[1,2,{\"y\":true}]}"));
        var prepared=service.prepare(c,bindings(),Map.of(),false);
        assertThat(prepared.report().path("valid").asBoolean()).isTrue();
        assertThat(prepared.authoring().path("env").path("future")).isEqualTo(c.path("env").path("future"));
        assertThat(prepared.execution().path("source").get(0).path("password").asText()).isEqualTo("Secret!123");
        assertThat(prepared.report().toString()).doesNotContain("Secret!123").contains("未验证","${datasource:source/0:password}");
        assertThat(service.prepare(prepared.authoring(),bindings(),Map.of(),false).authoring()).isEqualTo(prepared.authoring());
    }
    @Test void rejectsCyclesMissingReferencesDuplicatesAndInvalidTypes() {
        var c=config();c.withObject("env").put("parallelism",0);
        c.withArray("transform").add(LabJson.obj("{\"plugin_name\":\"Sql\",\"plugin_input\":[\"raw\"],\"plugin_output\":\"raw\",\"query\":\"select id from raw\"}"));
        ((ObjectNode)c.path("sink").get(0)).set("plugin_input",LabJson.parse("[\"missing\"]"));
        String errors=service.prepare(c,bindings(),Map.of(),false).report().path("errors").toString();
        assertThat(errors).contains("大于 0","重复输出","不存在的输出");
        ((ObjectNode)c.path("source").get(0)).set("plugin_input",LabJson.parse("[\"raw\"]"));
        assertThat(service.prepare(c,bindings(),Map.of(),false).report().path("errors").toString()).contains("环路");
    }
    @Test void validatesRequiredConditionalMutuallyExclusiveAndNestedOptions() {
        var c=config();((ObjectNode)c.path("source").get(0)).put("query","select 1");
        ((ObjectNode)c.path("sink").get(0)).put("is_exactly_once",true);
        ((ObjectNode)c.path("source").get(0).path("table_list").get(0)).put("partition_num","four");
        String errors=service.prepare(c,bindings(),Map.of(),false).report().path("errors").toString();
        assertThat(errors).contains("互斥","xa_data_source_class_name","类型应为 number");
    }
    @Test void secretsResolveOnlyInExecutionAndPlaintextIsRejected() {
        var c=config();((ObjectNode)c.path("sink").get(0)).put("secret_access_key","${secret:extra}");
        var p=service.prepare(c,bindings(),Map.of("extra","private-value"),false);
        assertThat(p.report().toString()).doesNotContain("private-value");assertThat(p.execution().toString()).contains("private-value");
        assertThat(service.prepare(c,bindings(),Map.of(),false).report().path("errors").toString()).contains("秘密引用未配置");
        ((ObjectNode)c.path("sink").get(0)).put("secret_access_key","literal");
        assertThat(service.prepare(c,bindings(),Map.of(),false).report().path("errors").toString()).contains("必须使用加密秘密引用");
    }
    @Test void savepointIsNeverSuccessfulCompletionAndMissingMetricsStayMissing() {
        assertThat(LabService.status("SAVEPOINT_DONE")).isEqualTo("SAVED");
        assertThat(LabService.status("DOING_SAVEPOINT")).isEqualTo("SAVING");
        assertThat(LabService.status("FINISHED")).isEqualTo("SUCCEEDED");
        assertThat(LabService.hasSavepoint(LabJson.parse("{\"jobId\":\"1\"}"))).isFalse();
        assertThat(LabService.hasSavepoint(LabJson.parse("{\"pipelines\":[{\"latestSavepoint\":{\"status\":\"COMPLETED\"}}]}"))).isTrue();
        assertThat(LabJson.redact(LabJson.parse("{\"metrics\":{}}"),List.of()).path("metrics").has("SinkWriteCount")).isFalse();
    }
    @Test void distinguishesDefiniteServerRejectionFromNetworkFailure() {
        assertThat(LabService.definiteRejection("{\"status\":\"fail\",\"message\":\"unsupported sql\"}")).isTrue();
        assertThat(LabService.definiteRejection("gateway timeout")).isFalse();
    }
    @Test void digestsIgnoreObjectOrderButKeepArrayOrder() {
        assertThat(LabJson.digest(LabJson.parse("{\"a\":1,\"b\":2}"))).isEqualTo(LabJson.digest(LabJson.parse("{\"b\":2,\"a\":1}")));
        assertThat(LabJson.digest(LabJson.parse("[1,2]"))).isNotEqualTo(LabJson.digest(LabJson.parse("[2,1]")));
    }
}
